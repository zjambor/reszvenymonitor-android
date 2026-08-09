package hu.jamborz.reszvenymonitor.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.jamborz.reszvenymonitor.BuildConfig
import hu.jamborz.reszvenymonitor.data.ApiException
import hu.jamborz.reszvenymonitor.data.FxRepository
import hu.jamborz.reszvenymonitor.data.PriceRepository
import hu.jamborz.reszvenymonitor.data.SettingsRepository
import hu.jamborz.reszvenymonitor.data.TickerRepository
import hu.jamborz.reszvenymonitor.data.dto.TickerDto
import hu.jamborz.reszvenymonitor.domain.FxConverter
import hu.jamborz.reszvenymonitor.domain.OhlcRow
import hu.jamborz.reszvenymonitor.domain.Transform
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A fő nézet állapotgépe — a webes `app.js` megfelelője.
 *
 * A kulcs-invariánsok, amiket ez az osztály őriz:
 * - a **natív** devizás sorok külön élnek ([nativeDaily]); a megjelenítési
 *   deviza váltása CSAK újraszámol, hálózati kérés nélkül (2. invariáns);
 * - a stat-kártyák MINDIG a napi (nem aggregált), átváltott sorokból készülnek,
 *   az aktuális preset-ablakra — a grafikon kapja az aggregált barokat;
 * - FX-kiesésnél a natív jegyzési deviza marad, a kapcsoló tiltott (11.);
 * - tickerváltásnál a korábbi betöltés Job-ja megszakad (a webes kérés-token +
 *   AbortController párja).
 */
class MonitorViewModel(
    private val tickerRepo: TickerRepository,
    private val priceRepo: PriceRepository,
    private val fxRepo: FxRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Státuszüzenet a képernyő alján (a webes ui.setStatus). */
    data class StatusMessage(val text: String, val tone: Tone = Tone.NEUTRAL) {
        enum class Tone { NEUTRAL, SUCCESS, ERROR }
    }

    data class UiState(
        val tickers: List<TickerDto> = emptyList(),
        val selected: TickerDto? = null,
        /** Megjelenítési devizás NAPI sorok (a chart aggregáltat kap). */
        val daily: List<OhlcRow> = emptyList(),
        val stats: Transform.Stats? = null,
        val preset: Transform.Preset = Transform.Preset.M6,
        val resolution: Transform.Resolution = Transform.Resolution.DAILY,
        val chartType: ChartType = ChartType.CANDLE,
        val displayCurrency: String = "USD",
        /** A ténylegesen használt deviza (FX-kiesésnél a natív jegyzési). */
        val effectiveCurrency: String = "USD",
        val currencyEnabled: Boolean = false,
        val showVolume: Boolean = true,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val error: String? = null,
        val status: StatusMessage? = null,
        val lastDate: String? = null,
        val stale: Boolean = false,
        val fitNonce: Int = 0,
    ) {
        /** A grafikonnak átadott, felbontás szerint aggregált barok. */
        val bars: List<OhlcRow> get() = Transform.aggregate(daily, resolution)

        /** A preset-ablak kezdő indexe a bar-listában (a chart nézetéhez). */
        val windowStartIndex: Int?
            get() {
                if (daily.isEmpty()) return null
                if (preset == Transform.Preset.MIND) return 0
                val range = Transform.presetRange(daily, preset) ?: return null
                val list = bars
                return list.indexOfFirst { it.date >= range.from }.takeIf { it >= 0 }
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** A kiválasztott instrumentum NATÍV devizás sorai — a devizaváltás forrása. */
    private var nativeDaily: List<OhlcRow> = emptyList()
    private var nativeCurrency: String = "USD"

    /** A folyamatban lévő betöltés — tickerváltásnál megszakad (versenykezelés). */
    private var loadJob: Job? = null

    /** Az utolsó sikertelen művelet — a hibakártya „Újra" gombjához. */
    private var lastFailedAction: (() -> Unit)? = null

    init {
        bootstrap()
    }

    // -----------------------------------------------------------------------
    // Indulás: beállítások + tickerlista + FX, majd az utolsó (vagy alap) ticker
    // -----------------------------------------------------------------------

    private fun bootstrap() {
        viewModelScope.launch {
            val savedCurrency = settings.displayCurrency.first()
            val savedTicker = settings.lastTicker.first()
            _uiState.value = _uiState.value.copy(
                displayCurrency = savedCurrency,
                loading = true,
                status = StatusMessage("Tickerek betöltése…"),
            )
            loadTickers(savedTicker)
        }
    }

    private suspend fun loadTickers(preferredSymbol: String?) {
        try {
            val tickers = tickerRepo.fetchTickers()
            // Az FX-hiba NEM boríthatja a betöltést: ilyenkor natív devizás nézet
            // marad, tiltott kapcsolóval (11. invariáns).
            val hasFx = fxRepo.refresh()

            _uiState.value = _uiState.value.copy(
                tickers = tickers,
                currencyEnabled = hasFx,
                error = if (hasFx) null else FX_MISSING_MESSAGE,
            )

            if (tickers.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    status = StatusMessage("Nincs elérhető ticker az adatbázisban.", StatusMessage.Tone.ERROR),
                )
                return
            }

            val target = tickers.firstOrNull { it.symbol == preferredSymbol }
                ?: tickers.firstOrNull { it.symbol == BuildConfig.DEFAULT_TICKER }
                ?: tickers.first()
            select(target)
        } catch (e: ApiException) {
            lastFailedAction = { viewModelScope.launch { loadTickers(preferredSymbol) } }
            _uiState.value = _uiState.value.copy(
                loading = false,
                status = null,
                error = "Nem sikerült betölteni a tickereket. ${e.message}",
            )
        }
    }

    // -----------------------------------------------------------------------
    // Tickerválasztás (versenykezeléssel)
    // -----------------------------------------------------------------------

    fun select(ticker: TickerDto, force: Boolean = false) {
        // Az előző betöltés megszakítása — csak az utolsó választás eredménye
        // jut a UI-ra (a webes kérés-token + AbortController párja).
        loadJob?.cancel()
        nativeCurrency = ticker.nativeCurrency

        val cached = priceRepo.hasCached(ticker.symbol) && !force
        _uiState.value = _uiState.value.copy(
            selected = ticker,
            loading = !cached,
            error = null,
            status = if (cached) null else StatusMessage("Betöltés: ${ticker.symbol}…"),
            daily = if (cached) _uiState.value.daily else emptyList(),
            stats = if (cached) _uiState.value.stats else null,
        )

        loadJob = viewModelScope.launch {
            settings.setLastTicker(ticker.symbol)
            try {
                priceRepo.dailyFlow(ticker.symbol, force = force).collect { update ->
                    nativeDaily = update.rows
                    recompute()
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        status = statusForUpdate(ticker, update),
                    )
                }
            } catch (e: ApiException) {
                lastFailedAction = { select(ticker, force) }
                nativeDaily = emptyList()
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    daily = emptyList(),
                    stats = null,
                    lastDate = null,
                    stale = false,
                    status = null,
                    error = "Nem sikerült betölteni a(z) ${ticker.symbol} adatait. ${e.message}",
                )
            }
        }
    }

    private fun statusForUpdate(ticker: TickerDto, update: PriceRepository.DailyUpdate): StatusMessage =
        when {
            update.rows.isEmpty() ->
                StatusMessage("Nincs elérhető árfolyamadat: ${ticker.symbol}.", StatusMessage.Tone.ERROR)
            update is PriceRepository.DailyUpdate.Revalidated ->
                StatusMessage(
                    "Frissítve háttérben: ${ticker.symbol} (${update.rows.size} kereskedési nap).",
                    StatusMessage.Tone.SUCCESS,
                )
            else ->
                StatusMessage(
                    "Betöltve: ${ticker.symbol} (${update.rows.size} kereskedési nap).",
                    StatusMessage.Tone.SUCCESS,
                )
        }

    // -----------------------------------------------------------------------
    // Vezérlők
    // -----------------------------------------------------------------------

    fun setPreset(preset: Transform.Preset) {
        _uiState.value = _uiState.value.copy(preset = preset)
        recomputeStatsOnly()
    }

    fun setResolution(resolution: Transform.Resolution) {
        _uiState.value = _uiState.value.copy(resolution = resolution)
    }

    fun setChartType(type: ChartType) {
        _uiState.value = _uiState.value.copy(chartType = type)
    }

    fun toggleVolume() {
        _uiState.value = _uiState.value.copy(showVolume = !_uiState.value.showVolume)
    }

    fun fitAll() {
        _uiState.value = _uiState.value.copy(fitNonce = _uiState.value.fitNonce + 1)
    }

    /**
     * Megjelenítési deviza váltása — HÁLÓZATI KÉRÉS NÉLKÜL: a natív sorokat
     * számoljuk újra (2. invariáns). A választás DataStore-ba mentődik.
     */
    fun setDisplayCurrency(currency: String) {
        if (currency !in FxConverter.DISPLAY_CURRENCIES) return
        if (!_uiState.value.currencyEnabled) return // FX-kiesésnél tiltott
        if (currency == _uiState.value.displayCurrency) return
        _uiState.value = _uiState.value.copy(displayCurrency = currency)
        recompute()
        viewModelScope.launch { settings.setDisplayCurrency(currency) }
    }

    /** Frissítés gomb: sync-prices, majd cache-kerülő újratöltés (webes refresh). */
    fun refresh() {
        val ticker = _uiState.value.selected ?: return
        if (_uiState.value.refreshing) return
        _uiState.value = _uiState.value.copy(
            refreshing = true,
            error = null,
            status = StatusMessage("Friss árfolyamok kérése a szervertől: ${ticker.symbol}…"),
        )
        viewModelScope.launch {
            try {
                val result = tickerRepo.syncPrices(listOf(ticker.symbol))
                    .firstOrNull { it.symbol == ticker.symbol }

                if (result != null && result.isFailure) {
                    lastFailedAction = { refresh() }
                    _uiState.value = _uiState.value.copy(
                        refreshing = false,
                        status = null,
                        error = "A frissítés hibába ütközött (${ticker.symbol}): ${result.error}",
                    )
                    return@launch
                }

                select(ticker, force = true)
                loadJob?.join() // a friss sorok beérkezéséig ne írjuk felül a státuszt

                val message = if (result != null && result.isUpToDate) {
                    "${ticker.symbol} már naprakész, nincs új adat."
                } else {
                    val rows = result?.rows?.let { " — $it új sor" }.orEmpty()
                    val source = result?.source?.let { " (forrás: $it)" }.orEmpty()
                    "Frissítve: ${ticker.symbol}$rows$source."
                }
                _uiState.value = _uiState.value.copy(
                    refreshing = false,
                    status = StatusMessage(message, StatusMessage.Tone.SUCCESS),
                )
            } catch (e: ApiException) {
                lastFailedAction = { refresh() }
                _uiState.value = _uiState.value.copy(
                    refreshing = false,
                    status = null,
                    error = "Hiba történt a frissítés közben. ${e.message}",
                )
            }
        }
    }

    /**
     * A kiválasztott ticker törlése a teljes adatsorával (sync-prices `delete`).
     * A törlés a FŐ nézetről indul, ezért az eredménye is itt jelenik meg: az
     * üzleti elutasítás (jellemzően `in-portfolio`, a szerver felsorolja az
     * érintett portfóliókat) a hibakártyára kerül, nem vész el.
     */
    fun deleteTicker(ticker: TickerDto) {
        if (_uiState.value.refreshing) return
        _uiState.value = _uiState.value.copy(
            refreshing = true,
            error = null,
            status = StatusMessage("Ticker törlése: ${ticker.symbol}…"),
        )
        viewModelScope.launch {
            try {
                val response = tickerRepo.deleteTicker(ticker.symbol)
                if (!response.ok) {
                    lastFailedAction = null // üzleti tiltás — az „Újra" nem segítene
                    _uiState.value = _uiState.value.copy(
                        refreshing = false,
                        status = null,
                        error = response.message ?: "Nem sikerült törölni: ${ticker.symbol}.",
                    )
                    return@launch
                }
                // A cache-t is ejteni kell, különben a régi adat feléledne.
                priceRepo.evictCache(ticker.symbol)
                val deleted = response.deleted
                _uiState.value = _uiState.value.copy(refreshing = false)
                reloadTickersAndSelect(null)
                _uiState.value = _uiState.value.copy(
                    status = StatusMessage(
                        "Törölve: ${ticker.symbol}" +
                            (deleted?.name?.let { " — $it" }.orEmpty()) +
                            " (${deleted?.priceRows ?: 0} napi sor).",
                        StatusMessage.Tone.SUCCESS,
                    ),
                )
            } catch (e: ApiException) {
                lastFailedAction = { deleteTicker(ticker) }
                _uiState.value = _uiState.value.copy(
                    refreshing = false,
                    status = null,
                    error = "Nem sikerült törölni a(z) ${ticker.symbol} tickert. ${e.message}",
                )
            }
        }
    }

    /**
     * A tickerlista újratöltése, majd a megadott szimbólum kiválasztása
     * (felvétel után), vagy visszaesés az alapértelmezettre (törlés után).
     * Ha egyetlen ticker sem maradt, üres nézet marad.
     */
    fun reloadTickersAndSelect(symbol: String?) {
        viewModelScope.launch {
            try {
                val tickers = tickerRepo.fetchTickers()
                _uiState.value = _uiState.value.copy(tickers = tickers)
                if (tickers.isEmpty()) {
                    nativeDaily = emptyList()
                    _uiState.value = _uiState.value.copy(
                        selected = null,
                        daily = emptyList(),
                        stats = null,
                        lastDate = null,
                        stale = false,
                        status = StatusMessage("Nincs elérhető ticker az adatbázisban.", StatusMessage.Tone.ERROR),
                    )
                    return@launch
                }
                val target = tickers.firstOrNull { it.symbol == symbol }
                    ?: tickers.firstOrNull { it.symbol == BuildConfig.DEFAULT_TICKER }
                    ?: tickers.first()
                select(target)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(error = "Nem sikerült frissíteni a tickerlistát. ${e.message}")
            }
        }
    }

    /**
     * A hibakártya „Újra" gombja. Ha nincs megismételhető művelet (üzleti
     * tiltás, pl. `in-portfolio`), a gomb csak elrejti a kártyát — az
     * újrapróbálkozás ott úgysem segítene.
     */
    fun retry() {
        val action = lastFailedAction
        lastFailedAction = null
        _uiState.value = _uiState.value.copy(error = null)
        action?.invoke()
    }

    fun dismissStatus() {
        _uiState.value = _uiState.value.copy(status = null)
    }

    // -----------------------------------------------------------------------
    // Számítás
    // -----------------------------------------------------------------------

    /**
     * A megjelenítési devizás sorok és a statisztika újraszámolása a MEGŐRZÖTT
     * natív adatokból — hálózat nélkül (a webes rebuildFromNative).
     */
    private fun recompute() {
        val state = _uiState.value
        // FX-kiesésnél a natív jegyzési deviza marad (webes displayCurrencyFor).
        val effective = if (fxRepo.hasRates()) state.displayCurrency else nativeCurrency
        val converted = fxRepo.converter.convertRows(nativeDaily, nativeCurrency, effective)

        val last = converted.lastOrNull()?.date
        val stale = last?.let { Transform.daysBetween(it, Transform.todayISO()) >= STALE_AFTER_DAYS } ?: false

        _uiState.value = state.copy(
            daily = converted,
            effectiveCurrency = effective,
            lastDate = last,
            stale = stale,
            stats = statsFor(converted, state.preset),
        )
    }

    /** Csak a preset változott: a sorok maradnak, a statisztika újraszámol. */
    private fun recomputeStatsOnly() {
        val state = _uiState.value
        _uiState.value = state.copy(stats = statsFor(state.daily, state.preset))
    }

    /**
     * Statisztika a NAPI (nem aggregált) sorokból, a preset-ablakra —
     * pontosan úgy, ahogy a webes applyRangeAndStats teszi.
     */
    private fun statsFor(daily: List<OhlcRow>, preset: Transform.Preset): Transform.Stats? {
        if (daily.isEmpty()) return null
        val range = Transform.presetRange(daily, preset) ?: return null
        return Transform.computeStats(daily, range.from, range.to)
    }

    companion object {
        /** „Elavult lehet": ennyi napos vagy régebbi utolsó adatnapnál (webes érték). */
        const val STALE_AFTER_DAYS = 3

        const val FX_MISSING_MESSAGE =
            "Nincs elérhető devizaárfolyam — az árak a saját jegyzési devizájukban látszanak, " +
                "és a vegyes devizájú portfóliók értéke félrevezető lehet."
    }
}
