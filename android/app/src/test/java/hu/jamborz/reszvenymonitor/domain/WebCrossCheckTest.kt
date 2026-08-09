package hu.jamborz.reszvenymonitor.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A 6. fázis ellenőrzése: **számjegyre egyezés a web-appal**.
 *
 * A várt értékeket a web-app EREDETI js/ moduljai állítják elő Node-ban
 * (`node tools/xcheck-web.mjs`), ugyanazokra a valós, adatbázisból exportált
 * sorokra, amiket ez a teszt is használ. Így nem szemrevételezés dönt: a két
 * implementáció ugyanarra a bemenetre bitre azonos számot és karakterre azonos
 * formázott szöveget kell adjon.
 *
 * Lefedve: NVDA (USD), SXR8.DE (EUR) és AIAG.L (GBp jegyzés!) × mind a 7 preset
 * × mind a 3 megjelenítési deviza = 63 eset, plusz felbontásonként az aggregált
 * barok (a grafikon bemenete).
 */
class WebCrossCheckTest {

    @Serializable
    private data class Expected(val cases: List<Case>)

    @Serializable
    private data class Case(
        val ticker: String,
        val nativeCurrency: String,
        val displayCurrency: String,
        val preset: String,
        val rowCount: Int,
        val from: String,
        val to: String,
        val stats: Stats,
        val formatted: Formatted,
        val bars: Map<String, Bars>,
    )

    @Serializable
    private data class Stats(
        val lastClose: Double,
        val dayChange: Double? = null,
        val dayChangePct: Double? = null,
        val periodChangePct: Double? = null,
        val periodHigh: Double,
        val periodLow: Double,
        val avgVolume: Double,
    )

    @Serializable
    private data class Formatted(
        val lastClose: String,
        val dayChange: String,
        val periodChangePct: String,
        val periodHigh: String,
        val periodLow: String,
        val avgVolume: String,
        val lastDate: String,
    )

    @Serializable
    private data class Bars(
        val count: Int,
        val lastDate: String? = null,
        val lastOpen: Double? = null,
        val lastHigh: Double? = null,
        val lastLow: Double? = null,
        val lastClose: Double? = null,
        val lastVolume: Double? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val expected: Expected by lazy {
        val text = checkNotNull(
            javaClass.getResourceAsStream("/fixtures/xcheck_expected.json")
        ) { "Hiányzik a fixtures/xcheck_expected.json — futtasd: node tools/xcheck-web.mjs" }
            .bufferedReader().readText().removePrefix("﻿")
        json.decodeFromString(text)
    }

    private val fixtureFor = mapOf(
        "NVDA" to "xcheck_nvda.json",
        "SXR8.DE" to "xcheck_sxr8de.json",
        "AIAG.L" to "xcheck_aiagl.json",
    )

    private val presetFor = mapOf(
        "1HET" to Transform.Preset.HET1,
        "1M" to Transform.Preset.M1,
        "3M" to Transform.Preset.M3,
        "6M" to Transform.Preset.M6,
        "YTD" to Transform.Preset.YTD,
        "1EV" to Transform.Preset.EV1,
        "MIND" to Transform.Preset.MIND,
    )

    private val resolutionFor = mapOf(
        "daily" to Transform.Resolution.DAILY,
        "weekly" to Transform.Resolution.WEEKLY,
        "monthly" to Transform.Resolution.MONTHLY,
    )

    private val fx: FxConverter by lazy {
        FxConverter().apply { setRates(Fixtures.fxRows("xcheck_fx.json")) }
    }

    private val nativeRows: Map<String, List<OhlcRow>> by lazy {
        fixtureFor.mapValues { (_, file) -> Fixtures.ohlcRows(file) }
    }

    /** Az abszolút értékek IEEE-754 doubles — relatív 1e-12 bőven szigorú. */
    private fun assertClose(label: String, expectedValue: Double?, actual: Double?) {
        if (expectedValue == null) {
            assertTrue("$label: a web null-t ad, az Android $actual-t", actual == null)
            return
        }
        checkNotNull(actual) { "$label: a web $expectedValue-t ad, az Android null-t" }
        val tolerance = kotlin.math.abs(expectedValue) * 1e-12 + 1e-12
        assertEquals(label, expectedValue, actual, tolerance)
    }

    @Test
    fun `stat-kartyak szamjegyre egyeznek a webbel - 3 ticker x 7 preset x 3 deviza`() {
        assertEquals("63 esetet várunk a generált fájlban", 63, expected.cases.size)

        for (c in expected.cases) {
            val label = "${c.ticker}/${c.preset}/${c.displayCurrency}"
            val native = nativeRows.getValue(c.ticker)

            // A webes toDisplay(): natív sorok → megjelenítési deviza, naponta.
            val daily = fx.convertRows(native, c.nativeCurrency, c.displayCurrency)
            assertEquals("$label: sorszám", c.rowCount, daily.size)

            val range = checkNotNull(Transform.presetRange(daily, presetFor.getValue(c.preset))) {
                "$label: nincs preset-ablak"
            }
            assertEquals("$label: ablak kezdete", c.from, range.from)
            assertEquals("$label: ablak vége", c.to, range.to)

            val stats = checkNotNull(Transform.computeStats(daily, range.from, range.to)) {
                "$label: nincs statisztika"
            }
            assertClose("$label: lastClose", c.stats.lastClose, stats.lastClose)
            assertClose("$label: dayChange", c.stats.dayChange, stats.dayChange)
            assertClose("$label: dayChangePct", c.stats.dayChangePct, stats.dayChangePct)
            assertClose("$label: periodChangePct", c.stats.periodChangePct, stats.periodChangePct)
            assertClose("$label: periodHigh", c.stats.periodHigh, stats.periodHigh)
            assertClose("$label: periodLow", c.stats.periodLow, stats.periodLow)
            assertClose("$label: avgVolume", c.stats.avgVolume, stats.avgVolume)
        }
    }

    @Test
    fun `a stat-kartyak FORMAZOTT szovege karakterre egyezik a webbel`() {
        for (c in expected.cases) {
            val label = "${c.ticker}/${c.preset}/${c.displayCurrency}"
            val ccy = c.displayCurrency
            val native = nativeRows.getValue(c.ticker)
            val daily = fx.convertRows(native, c.nativeCurrency, ccy)
            val range = checkNotNull(Transform.presetRange(daily, presetFor.getValue(c.preset)))
            val s = checkNotNull(Transform.computeStats(daily, range.from, range.to))

            assertEquals("$label: Utolsó záró", c.formatted.lastClose, Format.formatPriceIn(s.lastClose, ccy))

            // A „Napi változás" kártya a webes ui.js setStats összefűzését követi.
            val dayText = s.dayChange?.let { d ->
                "${Format.formatSignedIn(d, ccy)} (${Format.formatPct(s.dayChangePct)})"
            } ?: "—"
            assertEquals("$label: Napi változás", c.formatted.dayChange, dayText)

            assertEquals("$label: Időszaki változás", c.formatted.periodChangePct, Format.formatPct(s.periodChangePct))
            assertEquals("$label: Időszak max", c.formatted.periodHigh, Format.formatPriceIn(s.periodHigh, ccy))
            assertEquals("$label: Időszak min", c.formatted.periodLow, Format.formatPriceIn(s.periodLow, ccy))
            assertEquals("$label: Átlagvolumen", c.formatted.avgVolume, Format.formatVolume(s.avgVolume))
            assertEquals("$label: utolsó adatnap", c.formatted.lastDate, Format.formatDateHu(range.to))
        }
    }

    @Test
    fun `az aggregalt barok (grafikon-bemenet) egyeznek a webbel mindharom felbontasban`() {
        for (c in expected.cases) {
            // Az aggregáció presetfüggetlen — elég devizánként egyszer.
            if (c.preset != "MIND") continue
            val native = nativeRows.getValue(c.ticker)
            val daily = fx.convertRows(native, c.nativeCurrency, c.displayCurrency)

            for ((key, expectedBars) in c.bars) {
                val label = "${c.ticker}/${c.displayCurrency}/$key"
                val bars = Transform.aggregate(daily, resolutionFor.getValue(key))
                assertEquals("$label: bar-szám", expectedBars.count, bars.size)
                val last = bars.lastOrNull()
                assertEquals("$label: utolsó bar dátuma", expectedBars.lastDate, last?.date)
                assertClose("$label: utolsó bar open", expectedBars.lastOpen, last?.open)
                assertClose("$label: utolsó bar high", expectedBars.lastHigh, last?.high)
                assertClose("$label: utolsó bar low", expectedBars.lastLow, last?.low)
                assertClose("$label: utolsó bar close", expectedBars.lastClose, last?.close)
                assertClose("$label: utolsó bar volumen", expectedBars.lastVolume, last?.volume)
            }
        }
    }
}
