package hu.jamborz.reszvenymonitor.data

import hu.jamborz.reszvenymonitor.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A webes auth.js portja: bejelentkezés felhasználónév→e-mail leképezéssel,
 * kijelentkezés, session-állapot. A jelszót kizárólag a Supabase Auth szerver
 * ellenőrzi; a session tárolását és frissítését a supabase-kt végzi.
 */
open class AuthRepository(private val client: SupabaseClient) {

    /** Bejelentkezési hibafajták — a szöveget a UI rendeli hozzá (egynyelvű app, de a réteg tiszta marad). */
    sealed class SignInError : Exception() {
        /** Rossz név/jelszó VAGY ismeretlen felhasználónév — szándékosan egyetlen, általános hiba. */
        object InvalidCredentials : SignInError() { private fun readResolve(): Any = InvalidCredentials }
        /** Hálózati/szerver-hiba — a hitelesítő adatok nem minősíthetők. */
        object Network : SignInError() { private fun readResolve(): Any = Network }
    }

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    /** Igaz, ha a sessiont futás közben vesztettük el (401 / refresh-hiba) — a login-képernyő üzenetéhez. */
    private val _sessionLost = MutableStateFlow(false)
    val sessionLost: StateFlow<Boolean> = _sessionLost.asStateFlow()

    /**
     * Bejelentkezés a web-app szabályaival: `@`-ot tartalmazó név e-mailként megy;
     * az APP_USER (kisbetű-érzéketlenül) az APP_EMAIL-re képződik; minden más
     * azonnali [SignInError.InvalidCredentials] — hálózati kérés nélkül.
     */
    suspend fun signIn(name: String, password: String) {
        val raw = name.trim()
        val email = when {
            raw.contains('@') -> raw
            raw.equals(BuildConfig.APP_USER, ignoreCase = true) -> BuildConfig.APP_EMAIL
            else -> throw SignInError.InvalidCredentials
        }
        try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        } catch (e: AuthRestException) {
            // Auth-szintű elutasítás (rossz jelszó, nem létező fiók…) → általános üzenet.
            throw SignInError.InvalidCredentials
        } catch (e: Exception) {
            throw SignInError.Network
        }
        _sessionLost.value = false
    }

    /**
     * Kijelentkezés. Ha a szerveroldali visszavonás nem érhető el (pl. nincs
     * hálózat), a helyi session akkor is törlődik — adatkérés többé nem indulhat.
     */
    suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (e: Exception) {
            client.auth.clearSession()
        }
        _sessionLost.value = false
    }

    /**
     * 401-elfogó (a webes handleAuthLoss megfelelője): bármely REST-hívás
     * UnauthorizedRestException-jénél hívandó — a helyi session törlésével a
     * gyökér-UI a login-képernyőre vált, „Lejárt a munkamenet" üzenettel.
     * A 4. fázis repository-i használják majd.
     */
    open suspend fun onAuthLoss() {
        _sessionLost.value = true
        client.auth.clearSession()
    }

    /** A fejléc user-chipjének felirata (webes displayName-port). */
    fun displayName(): String {
        val email = client.auth.currentUserOrNull()?.email.orEmpty()
        return if (email == BuildConfig.APP_EMAIL) BuildConfig.APP_USER else email
    }
}
