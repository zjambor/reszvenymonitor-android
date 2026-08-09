package hu.jamborz.reszvenymonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * A README-ben dokumentált mért invariánsok VALÓS, a web-app adatbázisából
 * exportált sorokkal (fixtures/). A README az IWDA.AS-sel mérte a napi-vs-spot
 * eltérést (27 763 vs 25 388 Ft, 9,4%); az IWDA.AS ma nem felvett ticker, ezért
 * ugyanazt az invariánst az SXR8.DE-vel rögzítjük: 150 759,98 vs 137 664,38 Ft,
 * 8,69% — ugyanaz a jelenség, ugyanaz a nagyságrend, élő adatból.
 */
class FxRealDataTest {

    private val fx2023 = Fixtures.fxRows("fx_2023januar.json")
    private val fx2026 = Fixtures.fxRows("fx_2026augusztus.json")

    @Test
    fun `naponkenti atvaltas nem spot rataval - SXR8_DE 2023-01-02 zaro HUF-ban`() {
        val rows = Fixtures.ohlcRows("sxr8_de_2023eleje.json")
        val first = rows.first()
        assertEquals("2023-01-02", first.date)
        assertEquals(379.36, first.close, 1e-9)

        // Korabeli (2023-01-02) rátával:
        val fx = FxConverter().apply { setRates(fx2023) }
        val historical = fx.convertValue(first.close, "EUR", "HUF", first.date)!!
        assertEquals(150_759.98, historical, 0.05)

        // Mai (2026-08-07) rátával — a spot-hiba szemléltetése:
        val fxNow = FxConverter().apply { setRates(fx2026) }
        val withTodayRate = fxNow.convertValue(first.close, "EUR", "HUF", "2026-08-07")!!
        assertEquals(137_664.38, withTodayRate, 0.05)

        val diffPct = (historical - withTodayRate) / historical * 100
        assertTrue("A napi-vs-spot eltérésnek jelentősnek kell lennie, most: $diffPct%", diffPct > 5.0)
    }

    @Test
    fun `keresztproba - SXR8_DE szor EURUSD egyezik CSPX_L-lel (ugyanaz az alap)`() {
        val pairs = Fixtures.prices("sxr8_cspx_2026.json")
        val sxr8 = pairs.filter { it.ticker == "SXR8.DE" }.associateBy { it.date }
        val cspx = pairs.filter { it.ticker == "CSPX.L" }.associateBy { it.date }
        val fx = FxConverter().apply { setRates(fx2026) }

        var checked = 0
        for (date in sxr8.keys.intersect(cspx.keys).sorted()) {
            val eurUsd = fx.rateAsOf("EUR", date) ?: continue
            if (date < fx2026.minOf { it.date }) continue // ráta-lefedettségen kívül
            val converted = fx.convertValue(sxr8.getValue(date).close, "EUR", "USD", date)!!
            val reference = cspx.getValue(date).close
            val relDiffPct = abs(converted - reference) / reference * 100
            assertTrue(
                "$date: SXR8×EURUSD=$converted vs CSPX=$reference — eltérés $relDiffPct% (tolerancia 0,5%)",
                relDiffPct < 0.5,
            )
            checked++
        }
        assertTrue("Legalább 3 közös napot ellenőrizni kell, most: $checked", checked >= 3)
    }

    @Test
    fun `GBp valos adaton - AIAG_L zaroja USD-ben es fontban`() {
        val aiag = Fixtures.ohlcRows("aiag_l_2026.json")
        val last = aiag.last()
        assertEquals("2026-08-07", last.date)
        assertEquals(2967.0, last.close, 1e-9)

        val fx = FxConverter().apply { setRates(fx2026) }
        // 2 967 p = 29,67 £ = 40,0329 $ (GBPUSD 1,349272728 mellett)
        assertEquals(29.67, fx.convertValue(last.close, "GBp", "GBP", last.date)!!, 1e-9)
        assertEquals(40.0329, fx.convertValue(last.close, "GBp", "USD", last.date)!!, 0.001)
    }
}
