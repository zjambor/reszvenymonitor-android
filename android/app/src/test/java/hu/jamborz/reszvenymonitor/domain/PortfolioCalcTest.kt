package hu.jamborz.reszvenymonitor.domain

import hu.jamborz.reszvenymonitor.domain.PortfolioCalc.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PortfolioCalcTest {

    private fun row(date: String, close: Double, open: Double = close, volume: Double = 100.0) =
        OhlcRow(date, open, close + 0.5, close - 0.5, close, volume)

    @Test
    fun `commonAxis a legkesobbi elso adatnaptol indul`() {
        val a = listOf(row("2026-01-01", 1.0), row("2026-01-02", 1.1), row("2026-01-03", 1.2))
        val b = listOf(row("2026-01-02", 2.0), row("2026-01-03", 2.1), row("2026-01-06", 2.2))
        assertEquals(listOf("2026-01-02", "2026-01-03", "2026-01-06"), PortfolioCalc.commonAxis(listOf(a, b)))
        assertTrue(PortfolioCalc.commonAxis(emptyList()).isEmpty())
    }

    @Test
    fun `fillForward lapos sort ad a hianyzo napokra es eloreteker a tengely ele`() {
        val rows = listOf(row("2026-01-01", 9.0), row("2026-01-02", 10.0), row("2026-01-06", 12.0))
        val axis = listOf("2026-01-02", "2026-01-03", "2026-01-06")
        val out = PortfolioCalc.fillForward(rows, axis)
        assertEquals(3, out.size)
        assertEquals(10.0, out[0].close, 0.0)
        // 01-03: nem kereskedett → lapos O=H=L=C az utolsó záróval, volume 0
        assertEquals(OhlcRow("2026-01-03", 10.0, 10.0, 10.0, 10.0, 0.0), out[1])
        assertEquals(12.0, out[2].close, 0.0)
    }

    @Test
    fun `buildPortfolioSeries darabszam-sulyozott osszeg, tag-hianyos napon lapos hozzajarulas`() {
        val dailyMap = mapOf(
            "AAA" to listOf(row("2026-01-01", 5.0), row("2026-01-02", 6.0), row("2026-01-03", 7.0)),
            "BBB" to listOf(row("2026-01-02", 10.0), row("2026-01-05", 11.0)),
        )
        val items = listOf(Item("AAA", 2.0), Item("BBB", 3.0))
        val series = PortfolioCalc.buildPortfolioSeries(items, dailyMap)

        // A tengely BBB első napjától (2026-01-02) indul; AAA 01-01-es sora kimarad.
        assertEquals(listOf("2026-01-02", "2026-01-03", "2026-01-05"), series.map { it.date })
        assertEquals(2 * 6.0 + 3 * 10.0, series[0].close, 1e-9) // 42
        assertEquals(2 * 7.0 + 3 * 10.0, series[1].close, 1e-9) // BBB lapos (10) → 44
        assertEquals(2 * 7.0 + 3 * 11.0, series[2].close, 1e-9) // AAA lapos (7) → 47
        assertTrue(series.all { it.volume == 0.0 })
    }

    @Test
    fun `computePnL reszleges koltsegadatnal N per M elem`() {
        val dailyMap = mapOf(
            "AAA" to listOf(row("2026-01-02", 7.0)),
            "BBB" to listOf(row("2026-01-02", 10.0)),
        )
        val items = listOf(
            Item("AAA", 2.0, purchasePrice = 5.0), // (7−5)×2 = +4
            Item("BBB", 1.0, purchasePrice = null), // nincs bekerülési ár → kimarad
        )
        val pnl = PortfolioCalc.computePnL(items, dailyMap)
        assertEquals(1, pnl.costedCount)
        assertEquals(2, pnl.totalCount)
        assertEquals(4.0, pnl.pnl!!, 1e-9)
        assertEquals(40.0, pnl.pnlPct!!, 1e-9)

        val none = PortfolioCalc.computePnL(listOf(Item("BBB", 1.0)), dailyMap)
        assertNull(none.pnl)
        assertEquals(0, none.costedCount)
    }

    @Test
    fun `computeComposition ertek szerint csokkeno, ar nelkuli tag null ertekkel`() {
        val dailyMap = mapOf(
            "AAA" to listOf(row("2026-01-02", 7.0)),
            "BBB" to listOf(row("2026-01-02", 10.0)),
        )
        val comp = PortfolioCalc.computeComposition(
            listOf(Item("AAA", 2.0), Item("BBB", 3.0), Item("CCC", 1.0)),
            dailyMap,
        )
        assertEquals(44.0, comp.totalValue, 1e-9)
        assertEquals(2, comp.pricedCount)
        assertEquals(3, comp.totalCount)
        assertEquals(listOf("BBB", "AAA", "CCC"), comp.rows.map { it.ticker })
        assertEquals(30.0 / 44.0 * 100, comp.rows[0].weight!!, 1e-9)
        assertNull(comp.rows[2].value)
        // A súlyok összege 100 (az árazott tagokra).
        assertEquals(100.0, comp.rows.mapNotNull { it.weight }.sum(), 1e-9)
    }

    /**
     * A 5. invariáns: a sorrend kötött — előbb közös tengely + előre-töltés NATÍV
     * devizában, csak utána átváltás. Így a súlyok devizafüggetlenek; fordított
     * sorrendnél az elavult árú tag a régi napi árfolyamon ragadna.
     * Valós fx-sorokkal (2026. augusztus), vegyes natív devizájú tagokkal.
     */
    @Test
    fun `portfolio-sulyok devizafuggetlenek - a helyes sorrend natif toltes utan valt`() {
        val fx = FxConverter().apply { setRates(Fixtures.fxRows("fx_2026augusztus.json")) }

        // A tag (EUR): teljes sor 08-07-ig; B tag (USD): az utolsó napja hiányzik.
        val aRows = listOf(row("2026-08-05", 100.0), row("2026-08-06", 102.0), row("2026-08-07", 104.0))
        val bRows = listOf(row("2026-08-05", 50.0), row("2026-08-06", 51.0))
        val qtyA = 2.0
        val qtyB = 10.0

        val axis = PortfolioCalc.commonAxis(listOf(aRows, bRows))
        assertEquals(listOf("2026-08-05", "2026-08-06", "2026-08-07"), axis)

        fun weightAIn(display: String): Double {
            val filledA = PortfolioCalc.fillForward(aRows, axis)
            val filledB = PortfolioCalc.fillForward(bRows, axis)
            val convA = fx.convertRows(filledA, "EUR", display)
            val convB = fx.convertRows(filledB, "USD", display)
            val vA = qtyA * convA.last().close
            val vB = qtyB * convB.last().close
            return vA / (vA + vB) * 100
        }

        val wUsd = weightAIn("USD")
        val wHuf = weightAIn("HUF")
        val wEur = weightAIn("EUR")
        assertEquals("USD vs HUF súly", wUsd, wHuf, 1e-9)
        assertEquals("USD vs EUR súly", wUsd, wEur, 1e-9)

        // Ellenpróba: FORDÍTOTT sorrend (előbb átváltás, aztán töltés) más súlyt ad,
        // mert B utolsó ismert sora a 08-06-i árfolyamon ragad.
        val wrongB = PortfolioCalc.fillForward(fx.convertRows(bRows, "USD", "HUF"), axis)
        val rightB = fx.convertRows(PortfolioCalc.fillForward(bRows, axis), "USD", "HUF")
        val wrongClose = wrongB.last().close
        val rightClose = rightB.last().close
        assertTrue(
            "A fordított sorrendnek el KELL térnie (wrong=$wrongClose, right=$rightClose)",
            abs(wrongClose - rightClose) > 1.0,
        )
    }
}
