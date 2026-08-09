package hu.jamborz.reszvenymonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.jamborz.reszvenymonitor.ui.login.AuthViewModel
import hu.jamborz.reszvenymonitor.ui.login.LoginScreen
import hu.jamborz.reszvenymonitor.ui.sample.SampleScreen
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
        is SessionStatus.Authenticated -> SampleScreen(
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
