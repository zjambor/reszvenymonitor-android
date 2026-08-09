package hu.jamborz.reszvenymonitor.data

import hu.jamborz.reszvenymonitor.data.PriceRepository.DailyUpdate
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * A 4. fázis ellenőrzései VALÓS hálózattal (TERV-ANDROID.md):
 * lapozás több oldalon át; cache; stale-while-revalidate; versenykezelés
 * coroutine-cancellationnel; anon-only 401; up-to-date frissítés-válasz.
 *
 * A hitelesítők a MONITOR_TEST_EMAIL / MONITOR_TEST_PASSWORD környezeti
 * változókból jönnek (a web-app .env-jéből indítva) — nélkülük a tesztosztály
 * KIHAGYJA magát, jelszó a repóba nem kerül.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataLayerIntegrationTest {

    companion object {
        private val email: String? = System.getenv("MONITOR_TEST_EMAIL")
        private val password: String? = System.getenv("MONITOR_TEST_PASSWORD")

        private val client by lazy { SupabaseModule.create(MemorySessionManager()) }
        private val auth by lazy { AuthRepository(client) }
        private val guard by lazy { ApiGuard(auth) }

        private var signedIn = false

        @JvmStatic
        @BeforeClass
        fun signInOnce() {
            // JVM-en nincs Android Main dispatcher — a supabase-kt háttérmunkái
            // (session-kezelés) egy dedikált szálra kerülnek.
            Dispatchers.setMain(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
            if (email == null || password == null) return
            runBlocking {
                client.auth.signInWith(Email) {
                    this.email = this@Companion.email!!
                    this.password = this@Companion.password!!
                }
            }
            signedIn = true
        }
    }

    @Before
    fun requireCreds() {
        assumeTrue(
            "MONITOR_TEST_EMAIL/PASSWORD nincs beállítva — hálózati integrációs teszt kihagyva",
            signedIn,
        )
    }

    @Test
    fun `fx lapozas tobb oldalon at - 1000 folotti sorszam bizonyitja a ciklust`() = runBlocking {
        val fxRepo = FxRepository(client, guard)
        assertTrue("Az fx-frissítésnek sikerülnie kell", fxRepo.refresh())
        assertTrue(fxRepo.hasRates())
        // A PostgREST oldalplafonja 1000: ennél több sor CSAK többoldalas
        // lapozással jöhet át — ez az 1. invariáns élő bizonyítéka.
        assertTrue(
            "Több mint 1000 fx-sort várunk (most: ${fxRepo.loadedRowCount})",
            fxRepo.loadedRowCount > 1000,
        )
        assertTrue(fxRepo.converter.knownCurrencies().containsAll(listOf("USD", "EUR", "HUF")))
    }

    @Test
    fun `cache es stale-while-revalidate - masodik lekeres cache-bol, futas nelkul`() = runBlocking {
        val priceRepo = PriceRepository(client, guard)
        val tickers = TickerRepository(client, guard).fetchTickers()
        assumeTrue("Nincs aktív ticker", tickers.isNotEmpty())
        val symbol = tickers.first().symbol

        val first = priceRepo.getDaily(symbol)
        assertTrue("Üres adatsor: $symbol", first.isNotEmpty())
        assertTrue("Dátum szerint növekvő sorrend kell", first.zipWithNext().all { (a, b) -> a.date < b.date })

        // Cache-találat: ugyanaz a példány jön vissza — hálózati kérés nélkül.
        val second = priceRepo.getDaily(symbol)
        assertSame(first, second)
        assertTrue(priceRepo.hasCached(symbol))

        // SWR-folyam: első emisszió a cache-ből; Revalidated csak valódi változásra
        // (másodperceken belül nincs új adatnap, tehát legfeljebb 1 emisszió).
        val updates = priceRepo.dailyFlow(symbol).toList()
        assertTrue(updates.first() is DailyUpdate.FromCache)
        assertTrue(updates.size <= 2)

        priceRepo.evictCache(symbol)
        assertFalse(priceRepo.hasCached(symbol))
    }

    @Test
    fun `versenykezeles - flatMapLatest mellett csak az utolso szimbolum eredmenye erkezik meg`() = runBlocking {
        val priceRepo = PriceRepository(client, guard)
        val tickers = TickerRepository(client, guard).fetchTickers()
        assumeTrue("Legalább 2 aktív ticker kell", tickers.size >= 2)
        val (a, b) = tickers.map { it.symbol }

        val received = mutableListOf<Pair<String, DailyUpdate>>()
        flow {
            emit(a)
            delay(30) // az első kérés még úton van, amikor jön a váltás
            emit(b)
        }.flatMapLatest { sym ->
            priceRepo.dailyFlow(sym, force = true).map { sym to it }
        }.toList(received)

        assertTrue("Legalább egy emissziót várunk", received.isNotEmpty())
        assertTrue(
            "Csak az utolsó szimbólum ($b) eredménye juthat át, kaptuk: ${received.map { it.first }}",
            received.all { it.first == b },
        )
    }

    @Test
    fun `anon-only keres 401 - RLS el, es a sessionLost jelzodik`() = runBlocking {
        // Külön kliens bejelentkezés nélkül: anon kulcs megy Bearerként.
        val anonClient = SupabaseModule.create(MemorySessionManager())
        val anonAuth = AuthRepository(anonClient)
        val anonRepo = PriceRepository(anonClient, ApiGuard(anonAuth))

        val error = try {
            anonRepo.getDaily("NVDA")
            null
        } catch (e: ApiException) {
            e
        }
        checkNotNull(error) { "Anon kéréssel hibát várunk — az RLS-nek zárnia kell" }
        assertEquals("A webes viselkedés: anon-only kérés → 401", 401, error.status ?: 0)
        assertTrue("A session-vesztés jelzésének be kell állnia", anonAuth.sessionLost.value)
    }

    @Test
    fun `sync-prices frissites - up-to-date valasz nem hiba`() = runBlocking {
        val tickerRepo = TickerRepository(client, guard)
        val symbol = tickerRepo.fetchTickers().firstOrNull()?.symbol
        assumeTrue("Nincs aktív ticker", symbol != null)

        val results = tickerRepo.syncPrices(listOf(symbol!!))
        assertEquals(1, results.size)
        val r = results.single()
        assertEquals(symbol, r.symbol)
        // Napközbeni ismételt frissítésre a tipikus válasz "up-to-date" — ez NEM hiba.
        assertFalse("Váratlan hibaválasz: ${r.error}", r.isFailure)
    }
}
