package hu.jamborz.reszvenymonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.jamborz.reszvenymonitor.ui.login.AuthViewModel
import hu.jamborz.reszvenymonitor.ui.login.LoginScreen
import hu.jamborz.reszvenymonitor.ui.monitor.Instrument
import hu.jamborz.reszvenymonitor.ui.monitor.MonitorScreen
import hu.jamborz.reszvenymonitor.ui.monitor.MonitorViewModel
import hu.jamborz.reszvenymonitor.ui.portfolio.PortfolioScreen
import hu.jamborz.reszvenymonitor.ui.portfolio.PortfolioViewModel
import hu.jamborz.reszvenymonitor.ui.search.SearchScreen
import hu.jamborz.reszvenymonitor.ui.search.SearchViewModel
import hu.jamborz.reszvenymonitor.ui.theme.LocalMonitorColors
import hu.jamborz.reszvenymonitor.ui.theme.MonitorTheme
import hu.jamborz.reszvenymonitor.ui.theme.auroraBackground
import io.github.jan.supabase.auth.status.SessionStatus

/** Az app egyetlen Activity-je — a képernyők Compose-ban váltakoznak benne. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MonitorApp).container
        setContent {
            MonitorTheme {
                Root(container)
            }
        }
    }
}

/**
 * Auth-kapu (a webes login-overlay logikájának megfelelője): sikeres belépésig
 * kizárólag a login-képernyő létezik — adatkérő képernyő be sem komponálódik.
 * Session-vesztésnél (401 / refresh-hiba / kijelentkezés) ide esünk vissza.
 */
@Composable
private fun Root(container: AppContainer) {
    val auth = container.authRepository
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(auth) }
    val status by auth.sessionStatus.collectAsStateWithLifecycle()
    val sessionLost by auth.sessionLost.collectAsStateWithLifecycle()
    val loginState by authViewModel.uiState.collectAsStateWithLifecycle()

    when (status) {
        is SessionStatus.Authenticated -> Monitor(
            container = container,
            userName = auth.displayName(),
            onLogout = { authViewModel.signOut() },
        )
        is SessionStatus.Initializing -> InitializingScreen()
        // NotAuthenticated és RefreshFailure egyaránt: login-képernyő
        else -> LoginScreen(
            uiState = loginState,
            sessionExpired = sessionLost || status is SessionStatus.RefreshFailure,
            onSignIn = { name, pass -> authViewModel.signIn(name, pass) },
        )
    }
}

/**
 * A fő nézet a saját ViewModeljével, és a kereső-képernyő. A `key` a
 * bejelentkezéshez köti: új belépéskor friss ViewModel épül (nem marad benne az
 * előző munkamenet állapota).
 */
@Composable
private fun Monitor(container: AppContainer, userName: String, onLogout: () -> Unit) {
    val viewModel: MonitorViewModel = viewModel(key = "monitor-$userName") {
        MonitorViewModel(
            tickerRepo = container.tickerRepository,
            priceRepo = container.priceRepository,
            fxRepo = container.fxRepository,
            portfolioRepo = container.portfolioRepository,
            settings = container.settingsRepository,
        )
    }
    val searchViewModel: SearchViewModel = viewModel(key = "search-$userName") {
        SearchViewModel(tickerRepo = container.tickerRepository)
    }
    val portfolioViewModel: PortfolioViewModel = viewModel(key = "portfolio-$userName") {
        PortfolioViewModel(repo = container.portfolioRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val portfolioState by portfolioViewModel.uiState.collectAsStateWithLifecycle()
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showPortfolios by rememberSaveable { mutableStateOf(false) }

    // A kereső és a kezelő mindig a friss listákból dolgozik.
    LaunchedEffect(state.tickers, state.portfolios) {
        searchViewModel.setCatalog(state.tickers, state.portfolios)
        portfolioViewModel.setCatalog(state.portfolios, state.tickers)
    }

    // Portfólió-írás után a fő nézet (és vele a kereső listája) frissül.
    LaunchedEffect(portfolioState.changeNonce) {
        if (portfolioState.changeNonce > 0) viewModel.reloadPortfolios()
    }

    // Felvétel vagy „már felvéve" után: lista újratöltése, majd az érintett
    // ticker kiválasztása és visszalépés a fő nézetre (webes viselkedés).
    LaunchedEffect(searchState.completedSymbol) {
        val symbol = searchState.completedSymbol ?: return@LaunchedEffect
        viewModel.reloadTickersAndSelect(symbol)
        searchViewModel.consumeCompletion()
        showSearch = false
    }

    if (showSearch) {
        SearchScreen(
            state = searchState,
            onQueryChange = searchViewModel::onQueryChange,
            onPickTicker = { ticker ->
                viewModel.select(Instrument.Stock(ticker))
                showSearch = false
            },
            onPickPortfolio = { portfolio ->
                viewModel.select(Instrument.Portfolio(portfolio))
                showSearch = false
            },
            onPickHit = { hit -> searchViewModel.addTicker(hit.symbol) },
            onAdd = searchViewModel::addTicker,
            onSearchExchanges = searchViewModel::searchExchanges,
            onBack = { showSearch = false },
        )
        BackHandler { showSearch = false }
        return
    }

    if (showPortfolios) {
        PortfolioScreen(
            state = portfolioState,
            onOpenEditor = portfolioViewModel::openEditor,
            onCreate = portfolioViewModel::createPortfolio,
            onRename = portfolioViewModel::renamePortfolio,
            onDeletePortfolio = portfolioViewModel::deletePortfolio,
            onUpsertItem = portfolioViewModel::upsertItem,
            onDeleteItem = portfolioViewModel::deleteItem,
            onOpenPortfolioView = { portfolio ->
                viewModel.select(Instrument.Portfolio(portfolio))
                showPortfolios = false
            },
            onBack = { showPortfolios = false },
        )
        BackHandler {
            if (portfolioState.editingId != null) portfolioViewModel.openEditor(null)
            else showPortfolios = false
        }
        return
    }

    MonitorScreen(
        state = state,
        userName = userName,
        onPreset = viewModel::setPreset,
        onResolution = viewModel::setResolution,
        onChartType = viewModel::setChartType,
        onToggleVolume = viewModel::toggleVolume,
        onCurrency = viewModel::setDisplayCurrency,
        onFit = viewModel::fitAll,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLogout = onLogout,
        onOpenSearch = {
            searchViewModel.onQueryChange("")
            showSearch = true
        },
        onOpenPortfolios = {
            portfolioViewModel.openEditor(null)
            showPortfolios = true
        },
        onDeleteTicker = viewModel::deleteTicker,
    )
}

/** Rövid, session-visszatöltés alatti állapot — aurora + töltésjelző. */
@Composable
private fun InitializingScreen() {
    val palette = LocalMonitorColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground(palette),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = palette.accent)
    }
}
