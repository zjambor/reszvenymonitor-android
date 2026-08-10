package hu.jamborz.reszvenymonitor.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.jamborz.reszvenymonitor.data.ApiException
import hu.jamborz.reszvenymonitor.data.PriceRepository
import hu.jamborz.reszvenymonitor.data.TickerRepository
import hu.jamborz.reszvenymonitor.data.dto.PortfolioDto
import hu.jamborz.reszvenymonitor.data.dto.SearchHitDto
import hu.jamborz.reszvenymonitor.data.dto.TickerDto
import hu.jamborz.reszvenymonitor.domain.Suggest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A kereső-képernyő állapotgépe — a webes TickerCombobox logikájának portja.
 *
 * A megjelenítés sorrendje pontosan a webes renderOptions szerinti:
 * 1. helyi találatok csoportosítva (Részvények / ETF-ek),
 * 2. találat híján „Talán erre gondoltál" (Levenshtein a már felvettek közül),
 * 3. „«X» felvétele új tickerként" — ha a beírás szimbólumszerű,
 * 4. „«…» keresése a tőzsdéken" — 2+ karaktertől, helyi találat MELLETT is
 *    (lehet, hogy a felvett VWCE.DE mellé épp a VWCE.MI jegyzés kell).
 */
class SearchViewModel(
    private val tickerRepo: TickerRepository,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val tickers: List<TickerDto> = emptyList(),
        val portfolios: List<PortfolioDto> = emptyList(),
        /** Helyi találatok (szimbólum VAGY név szerint), csoportosítva jelennek meg. */
        val localHits: List<TickerDto> = emptyList(),
        /** Név szerint szűrt portfóliók — a lista élén jelennek meg. */
        val portfolioHits: List<PortfolioDto> = emptyList(),
        /** „Talán erre gondoltál" — csak ha nincs helyi találat. */
        val suggestions: List<TickerDto> = emptyList(),
        /** A felvételre kínált szimbólum (nagybetűsítve), ha a beírás szimbólumszerű. */
        val addCandidate: String? = null,
        val searching: Boolean = false,
        val searchResults: List<SearchHitDto>? = null,
        val searchError: String? = null,
        val busyMessage: String? = null,
        val error: String? = null,
        /** Sikeres művelet után a hívó ide navigál vissza (kiválasztott szimbólum). */
        val completedSymbol: String? = null,
        val statusMessage: String? = null,
    ) {
        val isIsinQuery: Boolean get() = Suggest.ISIN_LIKE.matches(query.trim().uppercase())
        val canSearchExchanges: Boolean get() = query.trim().length >= 2
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun setCatalog(tickers: List<TickerDto>, portfolios: List<PortfolioDto>) {
        _uiState.value = _uiState.value.copy(tickers = tickers, portfolios = portfolios)
        applyQuery(_uiState.value.query)
    }

    /** Beírás — helyi szűrés azonnal, tőzsdei keresés CSAK külön kérésre (mint a weben). */
    fun onQueryChange(query: String) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = query,
            searching = false,
            searchResults = null,
            searchError = null,
        )
        applyQuery(query)
    }

    private fun applyQuery(query: String) {
        val state = _uiState.value
        val q = query.trim()
        if (q.isEmpty()) {
            _uiState.value = state.copy(
                localHits = state.tickers,
                portfolioHits = state.portfolios,
                suggestions = emptyList(),
                addCandidate = null,
            )
            return
        }

        val needle = q.lowercase()
        val hits = state.tickers.filter {
            it.symbol.lowercase().contains(needle) || it.name.orEmpty().lowercase().contains(needle)
        }
        val portfolioHits = state.portfolios.filter { it.name.lowercase().contains(needle) }
        // Elgépelés-javaslat CSAK találat híján (webes sorrend).
        val suggestions = if (hits.isEmpty()) {
            val near = Suggest.nearSymbols(q, state.tickers.map { it.symbol })
            near.mapNotNull { sym -> state.tickers.firstOrNull { it.symbol == sym } }
        } else {
            emptyList()
        }
        val addCandidate = if (hits.isEmpty() && Suggest.SYMBOL_LIKE.matches(q)) q.uppercase() else null

        _uiState.value = state.copy(
            localHits = hits,
            portfolioHits = portfolioHits,
            suggestions = suggestions,
            addCandidate = addCandidate,
        )
    }

    /** „«…» keresése a tőzsdéken" — sync-prices `search`. */
    fun searchExchanges() {
        val query = _uiState.value.query.trim()
        if (query.length < 2) return
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(searching = true, searchResults = null, searchError = null)
        searchJob = viewModelScope.launch {
            try {
                val response = tickerRepo.searchSymbols(query)
                if (_uiState.value.query.trim() != query) return@launch // közben továbbírt
                if (response.ok) {
                    _uiState.value = _uiState.value.copy(searching = false, searchResults = response.results)
                } else {
                    _uiState.value = _uiState.value.copy(
                        searching = false,
                        searchError = response.message ?: "A keresés nem sikerült.",
                    )
                }
            } catch (e: ApiException) {
                if (_uiState.value.query.trim() != query) return@launch
                _uiState.value = _uiState.value.copy(searching = false, searchError = e.message)
            }
        }
    }

    /**
     * Ticker felvétele (sync-prices `add`). Sikernél — és `already-exists`
     * esetén is — a completedSymbol jelzi a hívónak, mit válasszon ki.
     */
    fun addTicker(symbol: String) {
        if (_uiState.value.busyMessage != null) return
        _uiState.value = _uiState.value.copy(
            busyMessage = "Új ticker felvétele: $symbol — ellenőrzés és adatletöltés 2023-tól…",
            error = null,
        )
        viewModelScope.launch {
            try {
                val response = tickerRepo.addTicker(symbol)
                when {
                    response.ok && response.added != null -> {
                        val added = response.added
                        // Ha a papírt a START_DATE után vezették be, a rövid adatsor
                        // nem letöltési hiba — jelezzük.
                        val ipoNote = added.firstTradeDate
                            ?.takeIf { it > hu.jamborz.reszvenymonitor.BuildConfig.START_DATE }
                            ?.let { " Adatok a tőzsdei bevezetéstől: $it." }
                            .orEmpty()
                        _uiState.value = _uiState.value.copy(
                            busyMessage = null,
                            completedSymbol = added.symbol,
                            statusMessage = "Új ticker felvéve: ${added.symbol}" +
                                (added.name?.let { " — $it" }.orEmpty()) +
                                (added.rows?.let { " ($it kereskedési nap)." } ?: ".") + ipoNote,
                        )
                    }
                    response.code == "already-exists" -> {
                        val existing = response.symbol ?: symbol
                        _uiState.value = _uiState.value.copy(
                            busyMessage = null,
                            completedSymbol = existing,
                            statusMessage = "A(z) $existing már szerepelt a listában — betöltve.",
                        )
                    }
                    else -> {
                        // invalid-symbol / unknown-symbol: a szerver magyar üzenete.
                        _uiState.value = _uiState.value.copy(
                            busyMessage = null,
                            error = response.message ?: "Nem sikerült felvenni: $symbol.",
                        )
                    }
                }
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(
                    busyMessage = null,
                    error = "Nem sikerült felvenni a(z) $symbol tickert. ${e.message}",
                )
            }
        }
    }

    fun consumeCompletion() {
        _uiState.value = _uiState.value.copy(completedSymbol = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
