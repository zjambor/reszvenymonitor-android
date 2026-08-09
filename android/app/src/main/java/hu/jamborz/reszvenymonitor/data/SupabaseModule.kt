package hu.jamborz.reszvenymonitor.data

import hu.jamborz.reszvenymonitor.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

/**
 * Supabase-kliens összeállítása — a web-app supabase-js (auth) + kézi
 * PostgREST-fetch kombinációját egyetlen könyvtár váltja ki.
 *
 * - Auth: session-tárolás SharedPreferences-alapon + automatikus token-frissítés
 *   (supabase-kt Android-alapértelmezés) — a webes localStorage megfelelője.
 * - Postgrest: defaultSchema = "stocks" → minden kérés Accept-/Content-Profile
 *   fejléccel megy (az 1. fázis füsttesztje igazolta a helyes routingot).
 * - Functions: sync-prices / asset-details hívások a bejelentkezett user JWT-jével.
 */
object SupabaseModule {

    /**
     * @param sessionManager csak JVM-tesztből kell (MemorySessionManager) —
     * Androidon a null az alapértelmezett SharedPreferences-tárolót jelenti.
     */
    fun create(sessionManager: SessionManager? = null): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        // A DTO-k csak a használt oszlopokat deklarálják — a select=* többi
        // mezőjét az ignoreUnknownKeys engedi el.
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
        install(Auth) {
            sessionManager?.let {
                // JVM-teszt: mindkét Settings-alapú tároló memóriásra cserélve.
                this.sessionManager = it
                this.codeVerifierCache = MemoryCodeVerifierCache()
            }
        }
        install(Postgrest) {
            defaultSchema = "stocks"
        }
        install(Functions)
    }
}
