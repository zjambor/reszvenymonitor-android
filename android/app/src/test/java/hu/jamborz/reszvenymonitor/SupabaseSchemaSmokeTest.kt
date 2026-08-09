package hu.jamborz.reszvenymonitor

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Füstteszt (TERV-ANDROID.md, Kockázatok): a supabase-kt a `stocks` sémát
 * helyesen route-olja-e (Accept-Profile fejléc). Valós hálózati hívás!
 *
 * Anon kulccsal, bejelentkezés nélkül futtatjuk — jelszó nélkül a RLS-en nem
 * jutunk át, ezért itt a HIBA MINŐSÉGE a mérés:
 *  - jó séma-routing → jogosultsági hiba (permission denied / 401) vagy üres lista;
 *  - rossz routing → „tábla nem található a séma-cache-ben" (PGRST205) — az bukás.
 * A user-JWT-s (owner-RLS) út teljes igazolása a 2. fázis belépésével történik.
 */
class SupabaseSchemaSmokeTest {

    @Test
    fun `stocks sema routing - anon keres nem sema-hibaval bukik`() = runBlocking {
        val client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Postgrest) {
                defaultSchema = "stocks"
            }
        }
        try {
            val data = client.postgrest.from("tickers").select().data
            // Lockdown mellett ide jellemzően nem jutunk el; ha mégis (RLS üresre
            // szűr), az is séma-szinten helyes routing — üres tömb a válasz.
            assertTrue(
                "Anon kéréssel nem várunk adatsort, csak üres választ, ezt kaptuk: $data",
                data.trim() == "[]",
            )
        } catch (e: RestException) {
            val message = e.message.orEmpty()
            assertFalse(
                "Séma-routing hiba (a PostgREST a public sémában kereste a táblát): $message",
                message.contains("PGRST205") || message.contains("schema cache"),
            )
            // A várt kimenet: jogosultsági elutasítás — a RLS él, a séma stimmel.
            println("Várt jogosultsági elutasítás (RLS él): $message")
        }
    }
}
