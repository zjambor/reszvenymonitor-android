package hu.jamborz.reszvenymonitor.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.jamborz.reszvenymonitor.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A login-képernyő állapotgépe (a webes onLogin handler megfelelője):
 * busy a kérés alatt, hibafajta a sikertelen próbálkozás után; a
 * shakeNonce minden hibánál nő — ez triggereli a kártya-rázást.
 */
class AuthViewModel(private val auth: AuthRepository) : ViewModel() {

    data class LoginUiState(
        val busy: Boolean = false,
        val error: AuthRepository.SignInError? = null,
        val shakeNonce: Int = 0,
    )

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signIn(username: String, password: String) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                auth.signIn(username, password)
                // Sikernél nincs teendő: a sessionStatus vált, a gyökér-UI továbblép.
                _uiState.value = _uiState.value.copy(busy = false)
            } catch (e: AuthRepository.SignInError) {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    error = e,
                    shakeNonce = _uiState.value.shakeNonce + 1,
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
