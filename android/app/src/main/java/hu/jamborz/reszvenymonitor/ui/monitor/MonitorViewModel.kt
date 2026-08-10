package hu.jamborz.reszvenymonitor.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.jamborz.reszvenymonitor.BuildConfig
import hu.jamborz.reszvenymonitor.data.ApiException
import hu.jamborz.reszvenymonitor.data.FxRepository
import hu.jamborz.reszvenymonitor.data.PortfolioRepository
import hu.jamborz.reszvenymonitor.data.PriceRepository
import hu.jamborz.reszvenymonitor.data.SettingsRepository
import hu.jamborz.reszvenymonitor.data.TickerRepository
import hu.jamborz.reszvenymonitor.data.dto.PortfolioDto
import hu.jamborz.reszvenymonitor.data.dto.PortfolioItemDto
import hu.jamborz.reszvenymonitor.data.dto.TickerDto
import hu.jamborz.reszvenymonitor.domain.FxConverter
import hu.jamborz.reszvenymonitor.domain.OhlcRow
import hu.jamborz.reszvenymonitor.domain.PortfolioCalc
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
    private val portfolioRepo: PortfolioRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Státuszüzenet a képernyő alján (a webes ui.setStatus). */
    data class StatusMessage(val text: String, val tone: Tone = Tone.NEUTRAL) {
        enum class Tone { NEUTRAL, SUCCESS, ERROR }
    }

    data class UiState(
        val tickers: List<TickerDto> = emptyList(),
        val portfolios: List<PortfolioDto> = emptyList(),
        val selected: Instrument? = null,
        /** Megjelenítési devizás NAPI sorok (a chart aggregáltat kap). */
        val daily: List<OhlcRow> = emptyList(),
        val stats: Transform.Stats? = null,
        /** Portfólió-nézetben az utolsó stat-kártya P/L-t mutat („N/M elem"). */
        val pnl: PortfolioCalc.PnlResult? = null,
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
        /** A portfólió tagjainak tényleges devizái — a fejléc badge-eihez. */
        val portfolioCurrencies: List<String> = emptyList(),
    ) {
        val isPortfolioView: Boolean get() = selected?.isPortfolio == true

        /** Portfóliónál nincs értelmes volumen (6. invariáns) — a kapcsoló tiltott. */
        val volumeEnabled: Boolean get() = !isPortfolioView

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

    /** A kiválasztott TICKER natív devizás sorai — a devizaváltás forrása. */
    private var nativeDaily: List<OhlcRow> = emptyList()
    private var nativeCurrency: String = "USD"

    /**
     * Portfólió-nézetben: tag → NATÍV devizás sorok, és tag → jegyzési deviza.
     * A devizaváltás ezekből számol újra, hálózat nélkül (2. invariáns).
     */
    private var pfNative: Map<String, List<OhlcRow>> = emptyMap()
    private var pfCurrency: Map<String, String> = emptyMap()
    private var pfItems: List<PortfolioItemDto> = emptyList()

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
            val portfolios = portfolioRepo.fetchPortfolios()
            // Az FX-hiba NEM boríthatja a betöltést: ilyenkor natív devizás nézet
            // marad, tiltott kapcsolóval (11. invariáns).
            val hasFx = fxRepo.refresh()

            _uiState.value = _uiState.value.copy(
                tickers = tickers,
                portfolios = portfolios,
                currencyEnabled = hasFx,
                error = if (hasFx) null else FX_MISSING_MESSAGE,
            )

            if (tickers.isEmpty() && portfolios.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    status = StatusMessage("Nincs elérhető ticker az adatbázisban.", StatusMessage.Tone.ERROR),
                )
                return
            }

            // A mentett választás lehet portfólió is (kulcs: „p-<id>").
            val target = instrumentFor(preferredSymbol, tickers, portfolios)
            target?.let { select(it) }
        } catch (e: ApiException) {
            lastFailedAction = { viewModelScope.launch { loadTickers(preferredSymbol) } }
            _uiState.value = _uiState.value.copy(
                loading = false,
                status = null,
                error = "Nem sikerült betölteni a tickereket. ${e.message}",
            )
        }
    }

    /** Mentett kulcs → instrumentum; visszaesés a DEFAULT_TICKER-re, majd az elsőre. */
    private fun instrumentFor(
        key: String?,
        tickers: List<TickerDto>,
        portfolios: List<PortfolioDto>,
    ): Instrument? {
        portfolios.firstOrNull { "p-${it.id}" == key }?.let { return Instrument.Portfolio(it) }
        tickers.firstOrNull { it.symbol == key || "t-${it.symbol}" == key }
            ?.let { return Instrument.Stock(it) }
        tickers.firstOrNull { it.symbol == BuildConfig.DEFAULT_TICKER }
            ?.let { return Instrument.Stock(it) }
        tickers.firstOrNull()?.let { return Instrument.Stock(it) }
        return portfolios.firstOrNull()?.let { Instrument.Portfolio(it) }
    }

    // -----------------------------------------------------------------------
    // Tickerválasztás (versenykezeléssel)
    // -----------------------------------------------------------------------

    fun select(instrument: Instrument, force: Boolean = false) {
        when (instrument) {
            is Instrument.Stock -> selectTicker(instrument, force)
            is Instrument.Portfolio -> selectPortfolio(instrument, force)
        }
    }

    private fun selectTicker(instrument: Instrument.Stock, force: Boolean) {
        val ticker = instrument.ticker
        // Az előző betöltés megszakítása — csak az utolsó választás eredménye
        // jut a UI-ra (a webes kérés-token + AbortController párja).
        loadJob?.cancel()
        nativeCurrency = ticker.nativeCurrency
        pfNative = emptyMap()
        pfItems = emptyList()

        val cached = priceRepo.hasCached(ticker.symbol) && !force
        _uiState.value = _uiState.value.copy(
            selected = instrument,
            loading = !cached,
            error = null,
            status = if (cached) null else StatusMessage("Betöltés: ${ticker.symbol}…"),
            daily = if (cached) _uiState.value.daily else emptyList(),
            stats = if (cached) _uiState.value.stats else null,
            pnl = null,
            portfolioCurrencies = emptyList(),
        )

        loadJob = viewModelScope.launch {
            settings.setLastTicker(instrument.key)
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
                lastFailedAction = { select(instrument, force) }
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

    /**
     * Portfólió-nézet: a tagok napi sorai (cache-ből vagy hálózatról) → szintetikus
     * összeg-idősor + P/L. A tagsorokat NATÍVAN őrizzük meg, az összegzés a
     * [recompute]-ban fut — így a devizaváltás itt sem jár hálózati kéréssel.
     */
    private fun selectPortfolio(instrument: Instrument.Portfolio, force: Boolean) {
        loadJob?.cancel()
        val portfolio = instrument.portfolio
        val items = portfolio.items
        pfItems = items

        _uiState.value = _uiState.value.copy(
            selected = instrument,
            error = null,
            pnl = null,
            // Portfóliónál nincs egységes natív deviza — a volumen is értelmetlen.
            showVolume = false,
        )

        if (items.isEmpty()) {
            pfNative = emptyMap()
            pfCurrency = emptyMap()
            _uiState.value = _uiState.value.copy(
                loading = false,
                daily = emptyList(),
                stats = null,
                lastDate = null,
                stale = false,
                portfolioCurrencies = emptyList(),
                status = StatusMessage("A portfólió üres — adj hozzá elemeket a szerkesztőben."),
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            loading = true,
            daily = emptyList(),
            stats = null,
            status = StatusMessage("Portfólió betöltése: ${portfolio.name}…"),
        )

        loadJob = viewModelScope.launch {
            settings.setLastTicker(instrument.key)
            try {
                val tickersBySymbol = _uiState.value.tickers.associateBy { it.symbol }
                val rows = items.associate { item ->
                    item.ticker to priceRepo.getDaily(item.ticker, force = force)
                }
                pfNative = rows
                pfCurrency = items.associate { item ->
                    item.ticker to (tickersBySymbol[item.ticker]?.nativeCurrency ?: "USD")
                }
                recompute()
                val series = _uiState.value.daily
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    status = if (series.isNotEmpty()) {
                        StatusMessage(
                            "Betöltve: ${portfolio.name} (${items.size} elem, ${series.size} kereskedési nap).",
                            StatusMessage.Tone.SUCCESS,
                        )
                    } else {
                        StatusMessage("Nincs megjeleníthető adat: ${portfolio.name}.", StatusMessage.Tone.ERROR)
                    },
                )
            } catch (e: ApiException) {
                lastFailedAction = { select(instrument, force) }
                pfNative = emptyMap()
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    daily = emptyList(),
                    stats = null,
                    lastDate = null,
                    stale = false,
                    status = null,
                    error = "Nem sikerült betölteni a portfóliót (${portfolio.name}). ${e.message}",
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
        val selected = _uiState.value.selected ?: return
        if (_uiState.value.refreshing) return
        when (selected) {
            is Instrument.Portfolio -> refreshPortfolio(selected)
            is Instrument.Stock -> refreshTicker(selected)
        }
    }

    /** Portfólió-frissítés: minden tag EGYETLEN sync-prices hívással. */
    private fun refreshPortfolio(instrument: Instrument.Portfolio) {
        val items = instrument.portfolio.items
        if (items.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            refreshing = true,
            error = null,
            status = StatusMessage("Friss árfolyamok kérése a portfólió ${items.size} eleméhez…"),
        )
        viewModelScope.launch {
            try {
                val results = tickerRepo.syncPrices(items.map { it.ticker })
                val failed = results.filter { it.isFailure }
                val upToDate = results.count { it.isUpToDate }
                val synced = results.size - failed.size - upToDate

                select(instrument, force = true)
                loadJob?.join()

                _uiState.value = _uiState.value.copy(
                    refreshing = false,
                    status = if (failed.isNotEmpty()) {
                        StatusMessage(
                            "Portfólió frissítve: $synced elem; hiba: ${failed.mapNotNull { it.symbol }.joinToString(", ")}.",
                            StatusMessage.Tone.ERROR,
                        )
                    } else {
                        val upText = if (upToDate > 0) ", $upToDate már naprakész volt" else ""
                        StatusMessage("Portfólió frissítve: $synced elem szinkronizálva$upText.", StatusMessage.Tone.SUCCESS)
                    },
                )
            } catch (e: ApiException) {
                lastFailedAction = { refreshPortfolio(instrument) }
                _uiState.value = _uiState.value.copy(
                    refreshing = false,
                    status = null,
                    error = "Hiba történt a portfólió frissítése közben. ${e.message}",
                )
            }
        }
    }

    private fun refreshTicker(instrument: Instrument.Stock) {
        val ticker = instrument.ticker
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

                select(instrument, force = true)
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
                val portfolios = portfolioRepo.fetchPortfolios()
                _uiState.value = _uiState.value.copy(tickers = tickers, portfolios = portfolios)
                if (tickers.isEmpty() && portfolios.isEmpty()) {
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
                instrumentFor(symbol, tickers, portfolios)?.let { select(it) }
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(error = "Nem sikerült frissíteni a tickerlistát. ${e.message}")
            }
        }
    }

    /**
     * A portfóliólista újratöltése a CRUD-műveletek után. Ha az épp nézett
     * portfólió változott (tag hozzáadás/törlés, átnevezés), a nézet is frissül;
     * ha törölték, visszaesünk az alapértelmezett instrumentumra.
     */
    fun reloadPortfolios(selectPortfolioId: String? = null) {
        viewModelScope.launch {
            try {
                val portfolios = portfolioRepo.fetchPortfolios()
                _uiState.value = _uiState.value.copy(portfolios = portfolios)

                val currentId = _uiState.value.selected?.asPortfolio?.id
                val targetId = selectPortfolioId ?: currentId ?: return@launch
                val fresh = portfolios.firstOrNull { it.id == targetId }
                if (fresh != null) {
                    // force=false: a tagok árai a cache-ből jönnek, csak az
                    // összetétel számolódik újra.
                    select(Instrument.Portfolio(fresh))
                } else if (currentId != null) {
                    // A nézett portfóliót törölték — vissza az alapértelmezettre.
                    instrumentFor(null, _uiState.value.tickers, portfolios)?.let { select(it) }
                }
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(error = "Nem sikerült frissíteni a portfóliókat. ${e.message}")
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
        if (state.selected?.isPortfolio == true) {
            recomputePortfolio()
            return
        }
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
            pnl = null,
        )
    }

    /**
     * Portfólió-újraszámolás a megőrzött NATÍV tagsorokból.
     *
     * A SORREND KÖTÖTT (5. invariáns): közös dátumtengely → előre-töltés NATÍV
     * devizában → CSAK EZUTÁN átváltás. Fordítva egy elavult árú tag (pl. egy
     * amerikai papír, amikor az európai tőzsdék már zártak) a RÉGI napi
     * árfolyamon ragadna, és a súlyok devizafüggővé válnának.
     */
    private fun recomputePortfolio() {
        val state = _uiState.value
        val portfolio = state.selected?.asPortfolio ?: return
        val members = pfItems.filter { pfNative[it.ticker]?.isNotEmpty() == true }
        if (members.isEmpty()) {
            _uiState.value = state.copy(
                daily = emptyList(),
                stats = null,
                pnl = null,
                lastDate = null,
                stale = false,
                effectiveCurrency = effectivePortfolioCurrency(portfolio),
                portfolioCurrencies = emptyList(),
            )
            return
        }

        val effective = effectivePortfolioCurrency(portfolio)
        val axis = PortfolioCalc.commonAxis(members.map { pfNative.getValue(it.ticker) })
        val convertedMap = members.associate { item ->
            val native = pfNative.getValue(item.ticker)
            val from = pfCurrency[item.ticker] ?: "USD"
            val filled = PortfolioCalc.fillForward(native, axis) // NATÍV devizában!
            item.ticker to fxRepo.converter.convertRows(filled, from, effective)
        }

        val calcItems = members.map { PortfolioCalc.Item(it.ticker, it.quantity, it.purchasePrice) }
        val series = PortfolioCalc.buildPortfolioSeries(calcItems, convertedMap)

        // A bekerülési ár a VÁSÁRLÁS NAPI árfolyamán vált át (7. invariáns) —
        // így a P/L a tényleges, devizahatással együtt mért eredményt mutatja.
        val costedItems = members.map { item ->
            val from = pfCurrency[item.ticker] ?: "USD"
            val price = item.purchasePrice?.let { p ->
                if (from == effective) p
                else fxRepo.converter.convertValue(p, from, effective, item.purchaseDate ?: Transform.todayISO())
            }
            PortfolioCalc.Item(item.ticker, item.quantity, price)
        }
        val pnl = PortfolioCalc.computePnL(costedItems, convertedMap)

        val last = series.lastOrNull()?.date
        val stale = last?.let { Transform.daysBetween(it, Transform.todayISO()) >= STALE_AFTER_DAYS } ?: false

        _uiState.value = state.copy(
            daily = series,
            stats = statsFor(series, state.preset),
            pnl = pnl,
            effectiveCurrency = effective,
            lastDate = last,
            stale = stale,
            portfolioCurrencies = members.mapNotNull { pfCurrency[it.ticker] }.distinct().sorted(),
        )
    }

    /** FX nélkül a portfólió saját (létrehozáskori) devizája a tartalék. */
    private fun effectivePortfolioCurrency(portfolio: PortfolioDto): String =
        if (fxRepo.hasRates()) _uiState.value.displayCurrency else (portfolio.currency ?: "USD")

    /**
     * A nézett portfólió összetétele a Részletek-panelhez — a MÁR BETÖLTÖTT
     * natív sorokból, HÁLÓZATI KÉRÉS NÉLKÜL (így repülő módban is működik).
     *
     * Itt NINCS közös tengely és előre-töltés: az összetétel az egyes tagok
     * saját utolsó zárójából számol (a webes openPortfolioDetails is így),
     * de az átváltás a megjelenítési devizára KELL — különben egy vegyes
     * devizájú portfólióban a súlyok almát adnának a körtéhez.
     */
    fun portfolioComposition(): PortfolioCalc.Composition? {
        val state = _uiState.value
        val portfolio = state.selected?.asPortfolio ?: return null
        if (pfItems.isEmpty()) return null
        val effective = effectivePortfolioCurrency(portfolio)
        val convertedMap = pfItems.associate { item ->
            val native = pfNative[item.ticker].orEmpty()
            val from = pfCurrency[item.ticker] ?: "USD"
            item.ticker to fxRepo.converter.convertRows(native, from, effective)
        }
        val calcItems = pfItems.map { PortfolioCalc.Item(it.ticker, it.quantity, it.purchasePrice) }
        return PortfolioCalc.computeComposition(calcItems, convertedMap)
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
