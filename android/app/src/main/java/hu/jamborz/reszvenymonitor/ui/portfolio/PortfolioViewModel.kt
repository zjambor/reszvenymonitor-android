package hu.jamborz.reszvenymonitor.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.jamborz.reszvenymonitor.data.ApiException
import hu.jamborz.reszvenymonitor.data.PortfolioRepository
import hu.jamborz.reszvenymonitor.data.dto.PortfolioDto
import hu.jamborz.reszvenymonitor.data.dto.PortfolioItemDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A portfólió-kezelő állapotgépe. Minden írás KÖZVETLENÜL PostgREST-en megy a
 * user JWT-jével (owner-only RLS) — a művelet után a hívó frissíti a fő nézetet.
 */
class PortfolioViewModel(
    private val repo: PortfolioRepository,
) : ViewModel() {

    data class UiState(
        val portfolios: List<PortfolioDto> = emptyList(),
        /** Az épp szerkesztett portfólió azonosítója (null → lista-nézet). */
        val editingId: String? = null,
        val busy: Boolean = false,
        val error: String? = null,
        /** Nő minden sikeres írás után — a hívó ebből tudja, hogy frissítenie kell. */
        val changeNonce: Int = 0,
    ) {
        val editing: PortfolioDto? get() = portfolios.firstOrNull { it.id == editingId }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun setPortfolios(portfolios: List<PortfolioDto>) {
        _uiState.value = _uiState.value.copy(portfolios = portfolios)
    }

    fun openEditor(id: String?) {
        _uiState.value = _uiState.value.copy(editingId = id, error = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Közös írás-futtató: busy-jelzés, hibakezelés, és sikernél a changeNonce
     * léptetése. A [reload] a friss listát tölti be, hogy a szerkesztő azonnal
     * a mentett állapotot mutassa.
     */
    private fun mutate(errorPrefix: String, block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                block()
                val fresh = repo.fetchPortfolios()
                _uiState.value = _uiState.value.copy(
                    portfolios = fresh,
                    busy = false,
                    changeNonce = _uiState.value.changeNonce + 1,
                )
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(busy = false, error = "$errorPrefix ${e.message}")
            }
        }
    }

    fun createPortfolio(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate("Nem sikerült létrehozni a portfóliót.") {
            val created = repo.createPortfolio(trimmed, currency = null)
            created?.let { _uiState.value = _uiState.value.copy(editingId = it.id) }
        }
    }

    fun renamePortfolio(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate("Nem sikerült átnevezni a portfóliót.") { repo.renamePortfolio(id, trimmed) }
    }

    fun deletePortfolio(id: String) {
        mutate("Nem sikerült törölni a portfóliót.") {
            repo.deletePortfolio(id)
            _uiState.value = _uiState.value.copy(editingId = null)
        }
    }

    /**
     * Tag hozzáadása vagy módosítása. A (portfolio_id, ticker) kulcson upsert:
     * ugyanannak a tickernek az ismételt felvétele a meglévő tételt írja felül.
     */
    fun upsertItem(
        portfolioId: String,
        ticker: String,
        quantity: Double,
        purchasePrice: Double?,
        purchaseDate: String?,
    ) {
        val symbol = ticker.trim().uppercase()
        if (symbol.isEmpty() || !quantity.isFinite() || quantity <= 0) {
            _uiState.value = _uiState.value.copy(error = "Adj meg érvényes tickert és pozitív darabszámot.")
            return
        }
        mutate("Nem sikerült menteni a tételt.") {
            repo.upsertItem(
                PortfolioItemDto(
                    portfolioId = portfolioId,
                    ticker = symbol,
                    quantity = quantity,
                    purchasePrice = purchasePrice,
                    purchaseDate = purchaseDate?.takeIf { it.isNotBlank() },
                )
            )
        }
    }

    fun deleteItem(portfolioId: String, ticker: String) {
        mutate("Nem sikerült törölni a tételt.") { repo.deleteItem(portfolioId, ticker) }
    }
}
