package hu.jamborz.reszvenymonitor.ui.monitor

import hu.jamborz.reszvenymonitor.BuildConfig
import hu.jamborz.reszvenymonitor.data.ApiException
import hu.jamborz.reszvenymonitor.data.ApiGuard
import hu.jamborz.reszvenymonitor.data.AuthRepository
import hu.jamborz.reszvenymonitor.data.FxRepository
import hu.jamborz.reszvenymonitor.data.PortfolioRepository
import hu.jamborz.reszvenymonitor.data.PriceRepository
import hu.jamborz.reszvenymonitor.data.SettingsStore
import hu.jamborz.reszvenymonitor.data.TickerRepository
import hu.jamborz.reszvenymonitor.data.dto.PortfolioDto
import hu.jamborz.reszvenymonitor.data.dto.SyncResultDto
import hu.jamborz.reszvenymonitor.data.dto.TickerDto
import hu.jamborz.reszvenymonitor.domain.FxRateRow
import hu.jamborz.reszvenymonitor.domain.OhlcRow
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A 10. fázis ÉLÁLLAPOT-próbái, determinisztikusan (TERV-ANDROID.md):
 * FX-kiesés → tiltott devizakapcsoló; lejárt session munka közben; hálózat
 * nélküli indulás és felépülés.
 *
 * MIÉRT ITT: ezek a helyzetek valós szerverrel nem idézhetők elő
 * megbízhatóan (az `fx_rates` kiesését nem lehet „megrendelni", a session
 * lejártát kivárni sem reális). A repository-k viszont a ViewModel felé szűk
 * felületűek, így hiteles helyettesekkel a TELJES állapotgép végigfuttatható.
 * A hálózat nélküli indulást emulátoron is végigpróbáltam (repülő mód).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorViewModelEdgeCaseTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    // --- Helyettesek ---------------------------------------------------------
    //
    // A repository-k valódi osztályok (a Supabase-kliens JVM-en is felépül,
    // hálózat nélkül), csak a ViewModel által hívott metódusokat írjuk felül —
    // így a teszt a VALÓDI típusokkal dolgozik, nem külön absztrakcióval.

    /**
     * PLUGIN NÉLKÜLI kliens: a helyettesek minden hálózati metódust felülírnak,
     * a példány csak a konstruktor-paraméterek kitöltésére kell. Az Auth plugin
     * szándékosan marad ki — az Android process-életciklusra iratkozna fel
     * (`Looper.getMainLooper`), ami JVM-tesztben nem létezik.
     */
    private val client by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {}
    }
    private val auth by lazy { AuthRepository(client) }
    private val guard by lazy { ApiGuard(auth) }

    private val nvda = TickerDto(symbol = "NVDA", name = "NVIDIA", currency = "USD")
    private val sxr8 = TickerDto(symbol = "SXR8.DE", name = "iShares Core S&P 500", currency = "EUR")

    private val rows = listOf(
        OhlcRow("2026-08-05", 100.0, 110.0, 95.0, 105.0, 1_000.0),
        OhlcRow("2026-08-06", 105.0, 115.0, 100.0, 110.0, 1_200.0),
    )

    private inner class FakeTickers(
        val tickers: List<TickerDto> = listOf(nvda, sxr8),
        val failure: ApiException? = null,
    ) : TickerRepository(client, guard) {
        var calls = 0
        override suspend fun fetchTickers(): List<TickerDto> {
            calls++
            failure?.let { throw it }
            return tickers
        }
        override suspend fun syncPrices(symbols: List<String>): List<SyncResultDto> = emptyList()
    }

    private inner class FakePrices(
        val failure: ApiException? = null,
    ) : PriceRepository(client, guard) {
        override fun hasCached(symbol: String) = false
        override suspend fun getDaily(symbol: String, force: Boolean): List<OhlcRow> {
            failure?.let { throw it }
            return rows
        }
        override fun dailyFlow(symbol: String, force: Boolean): Flow<DailyUpdate> = flow {
            failure?.let { throw it }
            emit(DailyUpdate.FromNetwork(rows))
        }
    }

    /** [rates] üresen hagyva = FX-kiesés (a converter üres marad, refresh false). */
    private inner class FakeFx(val rates: List<FxRateRow> = emptyList()) : FxRepository(client, guard) {
        override suspend fun refresh(): Boolean {
            if (rates.isEmpty()) return false // a webes „catch → natív deviza" ág
            converter.setRates(rates)
            return true
        }
    }

    private inner class FakePortfolios(
        val portfolios: List<PortfolioDto> = emptyList(),
        val failure: ApiException? = null,
    ) : PortfolioRepository(client, guard) {
        override suspend fun fetchPortfolios(): List<PortfolioDto> {
            failure?.let { throw it }
            return portfolios
        }
    }

    private class FakeSettings(currency: String = "HUF") : SettingsStore {
        override val displayCurrency = MutableStateFlow(currency)
        override val lastTicker = MutableStateFlow<String?>(null)
        var savedTicker: String? = null
        override suspend fun setDisplayCurrency(currency: String) { displayCurrency.value = currency }
        override suspend fun setLastTicker(symbol: String) { savedTicker = symbol }
    }

    private fun viewModel(
        tickers: TickerRepository = FakeTickers(),
        prices: PriceRepository = FakePrices(),
        fx: FxRepository = FakeFx(fullRates()),
        portfolios: PortfolioRepository = FakePortfolios(),
        settings: SettingsStore = FakeSettings(),
    ) = MonitorViewModel(tickers, prices, fx, portfolios, settings)

    /** Minimális, de teljes árfolyamtábla a tesztsorok napjaira. */
    private fun fullRates(): List<FxRateRow> = listOf(
        FxRateRow("2026-08-05", "EUR", 1.16), FxRateRow("2026-08-06", "EUR", 1.16),
        FxRateRow("2026-08-05", "HUF", 0.0029), FxRateRow("2026-08-06", "HUF", 0.0029),
    )

    // -----------------------------------------------------------------------
    // 1. FX-kiesés
    // -----------------------------------------------------------------------

    @Test
    fun `FX-kieses - tiltott devizakapcsolo, NATIV deviza, hibauzenet, de az app hasznalhato`() = runTest(dispatcher) {
        val vm = viewModel(fx = FakeFx(rates = emptyList()), settings = FakeSettings(currency = "HUF"))
        advanceUntilIdle()

        val state = vm.uiState.value
        // A TARTÓS jelzés a tiltott kapcsoló: a hibakártya szövegét a ticker
        // kiválasztása törli (a web is így viselkedik — js/app.js loadTicker →
        // ui.hideError), ezért a felület a kapcsoló mellé „Nincs árfolyam"
        // chipet tesz, a webes tooltip mobil megfelelőjeként.
        assertFalse("A devizakapcsolónak tiltottnak kell lennie", state.currencyEnabled)

        // A LÉNYEG: az app nem borul — az adatsor betöltődik, csak a saját
        // jegyzési devizájában (11. invariáns).
        assertEquals("USD", state.effectiveCurrency)
        assertEquals(rows.size, state.daily.size)
        assertEquals(rows.last().close, state.daily.last().close, 1e-12)
        assertNotNull(state.stats)
    }

    @Test
    fun `FX-kieses - a devizavaltas KERESET nelkul is hatastalan marad`() = runTest(dispatcher) {
        val settings = FakeSettings(currency = "USD")
        val vm = viewModel(fx = FakeFx(rates = emptyList()), settings = settings)
        advanceUntilIdle()

        vm.setDisplayCurrency("EUR")
        advanceUntilIdle()

        assertEquals("A tiltott kapcsoló nem válthat devizát", "USD", vm.uiState.value.displayCurrency)
        assertEquals("USD", vm.uiState.value.effectiveCurrency)
    }

    @Test
    fun `mukodo FX - az EUR-ban jegyzett papir a megjelenitesi devizaban latszik`() = runTest(dispatcher) {
        val settings = FakeSettings(currency = "HUF")
        val vm = viewModel(settings = settings)
        advanceUntilIdle()

        vm.select(Instrument.Stock(sxr8))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.currencyEnabled)
        assertEquals("HUF", state.effectiveCurrency)
        // 110 EUR × 1,16 USD/EUR ÷ 0,0029 USD/HUF = 44 000 HUF
        assertEquals(44_000.0, state.daily.last().close, 1e-6)
    }

    // -----------------------------------------------------------------------
    // 2. Lejárt session munka közben
    // -----------------------------------------------------------------------

    @Test
    fun `lejart session tickervaltaskor - magyar uzenet, a nezet nem mutat felig-kesz adatot`() = runTest(dispatcher) {
        val vm = viewModel(prices = FakePrices(failure = ApiException("Lejárt a munkamenet — jelentkezz be újra.", 401)))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("A hibaüzenetnek a session-vesztést kell közölnie: ${state.error}",
            state.error.orEmpty().contains("Lejárt a munkamenet"))
        assertTrue("Nem maradhat félig betöltött adatsor", state.daily.isEmpty())
        assertNull(state.stats)
        assertFalse(state.loading)
    }

    @Test
    fun `401 az ApiGuardban - a session-vesztes jelzese elindul`() = runTest(dispatcher) {
        // Az ApiGuard felelőssége, hogy a 401-et session-vesztéssé fordítsa —
        // erre épül a gyökér-UI login-képernyőre váltása. A kivételhez VALÓDI
        // HttpResponse kell (a statusCode onnan jön), ezért Ktor MockEngine.
        val lost = mutableListOf<Boolean>()
        val fakeAuth = object : AuthRepository(client) {
            override suspend fun onAuthLoss() { lost += true }
        }
        val response = httpResponse(HttpStatusCode.Unauthorized)

        val error = runCatching {
            ApiGuard(fakeAuth).run {
                throw UnauthorizedRestException("JWT expired", response, "permission denied")
            }
        }.exceptionOrNull()

        assertTrue("ApiException-t várunk, nem nyers könyvtári hibát: $error", error is ApiException)
        assertEquals(401, (error as ApiException).status)
        assertTrue("Magyar üzenetet várunk: ${error.message}",
            error.message.orEmpty().contains("Lejárt a munkamenet"))
        assertEquals("A session-vesztést pontosan egyszer kell jelezni", listOf(true), lost)
    }

    @Test
    fun `egyeb szerverhiba az ApiGuardban - NEM jelez session-vesztest`() = runTest(dispatcher) {
        val lost = mutableListOf<Boolean>()
        val fakeAuth = object : AuthRepository(client) {
            override suspend fun onAuthLoss() { lost += true }
        }
        val response = httpResponse(HttpStatusCode.InternalServerError)

        val error = runCatching {
            ApiGuard(fakeAuth).run { throw RestException("boom", "szerverhiba", response) }
        }.exceptionOrNull()

        assertEquals(500, (error as ApiException).status)
        assertTrue("500-nál nem szabad kijelentkeztetni", lost.isEmpty())
    }

    // -----------------------------------------------------------------------
    // 3. Hálózat nélküli indulás és felépülés
    // -----------------------------------------------------------------------

    @Test
    fun `halozat nelkuli indulas - hibakartya, ures nezet, majd az Ujra betolt`() = runTest(dispatcher) {
        val offline = ApiException("Hálózati hiba: az adatbázis nem érhető el. Ellenőrizd az internetkapcsolatot.")
        // Ugyanaz a példány: az első hívás elbukik, a másodikra „visszatér a hálózat".
        val tickers = object : TickerRepository(client, guard) {
            var calls = 0
            override suspend fun fetchTickers(): List<TickerDto> {
                calls++
                if (calls == 1) throw offline
                return listOf(nvda)
            }
        }
        val vm = viewModel(tickers = tickers)
        advanceUntilIdle()

        val offlineState = vm.uiState.value
        assertTrue("Hálózati hibaüzenetet várunk: ${offlineState.error}",
            offlineState.error.orEmpty().contains("Hálózati hiba"))
        assertFalse(offlineState.loading)
        assertNull("Indulásnál nincs mit kiválasztani", offlineState.selected)
        assertTrue(offlineState.tickers.isEmpty())

        // A hibakártya „Újra" gombja: a legutóbbi művelet megismétlése.
        vm.retry()
        advanceUntilIdle()

        val recovered = vm.uiState.value
        assertNull("A felépülés után nem maradhat hibakártya", recovered.error)
        assertEquals(listOf(nvda), recovered.tickers)
        assertEquals("NVDA", recovered.selected?.asStock?.symbol)
        assertEquals(rows.size, recovered.daily.size)
        assertEquals(2, tickers.calls)
    }

    @Test
    fun `ures adatbazis - nem hiba, hanem magyar statuszuzenet`() = runTest(dispatcher) {
        val vm = viewModel(tickers = FakeTickers(tickers = emptyList()))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.selected)
        assertEquals(
            MonitorViewModel.StatusMessage.Tone.ERROR,
            state.status?.tone,
        )
        assertTrue(state.status?.text.orEmpty().contains("Nincs elérhető ticker"))
    }

    /** Valódi [HttpResponse] adott státusszal, hálózat nélkül (Ktor MockEngine). */
    private suspend fun httpResponse(status: HttpStatusCode): HttpResponse =
        HttpClient(MockEngine { respond(content = "", status = status) }).get("https://example.invalid/")
}
