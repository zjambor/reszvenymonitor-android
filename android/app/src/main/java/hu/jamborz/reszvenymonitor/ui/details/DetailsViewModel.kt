package hu.jamborz.reszvenymonitor.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.jamborz.reszvenymonitor.data.ApiException
import hu.jamborz.reszvenymonitor.data.DetailsRepository
import hu.jamborz.reszvenymonitor.data.dto.TickerDto
import hu.jamborz.reszvenymonitor.domain.PortfolioCalc
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A Részletek-panel állapotgépe (a webes onDetailsOpen két ága).
 *
 * A KÉT ÁG KÜLÖNBÖZŐSÉGE LÉNYEGES:
 * - **ticker/ETF** → `asset-details` Edge Function hívás (a szerver gyorsítótáraz
 *   7 napig; a kliens sosem hív külső API-t);
 * - **portfólió** → HELYBEN számolt összetétel a már betöltött árakból, hálózati
 *   kérés nélkül — így repülő módban is működik.
 */
class DetailsViewModel(
    private val repo: DetailsRepository,
) : ViewModel() {

    data class UiState(
        val visible: Boolean = false,
        val title: String = "",
        val loading: Boolean = false,
        val blocks: List<DetailsBlock> = emptyList(),
        val footer: String? = null,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** A folyamatban lévő lekérés — bezáráskor/új nyitáskor megszakad. */
    private var loadJob: Job? = null

    /** Részvény/ETF: profil lekérése a szervertől. */
    fun openAsset(ticker: TickerDto) {
        loadJob?.cancel()
        _uiState.value = UiState(visible = true, title = "${ticker.symbol} — részletek", loading = true)
        loadJob = viewModelScope.launch {
            try {
                val response = repo.getAssetDetails(ticker.symbol)
                val details = response.details
                if (!response.ok || details == null) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = if (response.code == "no-profile-data") {
                            "Ehhez az instrumentumhoz nincs elérhető profiladat."
                        } else {
                            response.message ?: "Nem sikerült lekérni a részleteket."
                        },
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    blocks = DetailsPresenter.assetBlocks(details),
                    footer = DetailsPresenter.sourceFooter(details, response.cached),
                )
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "Nem sikerült lekérni a részleteket. ${e.message}",
                )
            }
        }
    }

    /**
     * Portfólió: a HÍVÓ által (a betöltött árakból) számolt összetétel
     * megjelenítése — nincs lekérés, nincs betöltés-állapot.
     */
    fun openPortfolio(
        name: String,
        composition: PortfolioCalc.Composition?,
        names: Map<String, String?>,
        currency: String,
    ) {
        loadJob?.cancel()
        val title = "$name — összetétel"
        if (composition == null || composition.totalCount == 0) {
            _uiState.value = UiState(
                visible = true,
                title = title,
                error = "Ez a portfólió üres — adj hozzá elemeket a Portfóliók gombbal.",
            )
            return
        }
        _uiState.value = UiState(
            visible = true,
            title = title,
            blocks = DetailsPresenter.compositionBlocks(composition, names, currency),
        )
    }

    fun close() {
        loadJob?.cancel()
        _uiState.value = UiState()
    }
}
