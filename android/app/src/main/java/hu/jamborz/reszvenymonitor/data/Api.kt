package hu.jamborz.reszvenymonitor.data

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

/**
 * A webes ApiError megfelelője: magyar üzenet + opcionális HTTP-státusz.
 * A repository-k kifelé kizárólag ezt (vagy CancellationException-t) dobják.
 */
class ApiException(message: String, val status: Int? = null) : Exception(message)

/**
 * Közös hibaleképezés minden REST-/function-hívás köré (a webes pgFetch
 * hibakezelésének megfelelője):
 * - 401 → a webes handleAuthLoss mintája: a helyi session törlődik (a gyökér-UI
 *   a login-képernyőre vált), és „Lejárt a munkamenet" ApiException dobódik;
 * - egyéb REST-hiba → „Az adatbázis hibával válaszolt (HTTP …)";
 * - hálózati hiba → magyar üzenet státusz nélkül;
 * - a coroutine-cancellation ÉRINTETLENÜL továbbmegy (ez a versenykezelés alapja).
 */
class ApiGuard(private val auth: AuthRepository) {

    suspend fun <T> run(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RestException) {
        // A 401 nem mindig UnauthorizedRestException-ként érkezik (mérve:
        // a PostgREST permission denied sima RestException 401-gyel) —
        // a státuszkód a mérvadó.
        if (e is UnauthorizedRestException || e.statusCode == 401) {
            auth.onAuthLoss()
            throw ApiException("Lejárt a munkamenet — jelentkezz be újra.", 401)
        }
        val detail = e.error.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
        throw ApiException("Az adatbázis hibával válaszolt (HTTP ${e.statusCode}$detail).", e.statusCode)
    } catch (e: HttpRequestTimeoutException) {
        throw ApiException("Hálózati hiba: időtúllépés — próbáld újra.")
    } catch (e: HttpRequestException) {
        throw ApiException("Hálózati hiba: az adatbázis nem érhető el. Ellenőrizd az internetkapcsolatot.")
    } catch (e: java.io.IOException) {
        throw ApiException("Hálózati hiba: az adatbázis nem érhető el. Ellenőrizd az internetkapcsolatot.")
    }
}
