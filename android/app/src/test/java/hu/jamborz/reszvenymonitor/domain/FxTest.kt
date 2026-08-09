package hu.jamborz.reszvenymonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Szintetikus adatos Fx-tesztek — a valós adatosak az FxRealDataTest-ben. */
class FxTest {

    private fun converter(): FxConverter = FxConverter().apply {
        setRates(
            listOf(
                FxRateRow("2026-01-02", "EUR", 1.1),
                FxRateRow("2026-01-05", "EUR", 1.2),
                FxRateRow("2026-01-02", "GBP", 1.25),
                FxRateRow("2026-01-02", "HUF", 0.0025),
            )
        )
    }

    @Test
    fun `rateAsOf forward-fill - koztes, korabbi es kesobbi datumra`() {
        val fx = converter()
        assertEquals(1.1, fx.rateAsOf("EUR", "2026-01-03")!!, 0.0) // hétvégi lyuk → utolsó ismert
        assertEquals(1.1, fx.rateAsOf("EUR", "2025-12-01")!!, 0.0) // minden ránál korábbi → legelső
        assertEquals(1.2, fx.rateAsOf("EUR", "2026-02-01")!!, 0.0) // utolsó után → legutolsó
        assertNull(fx.rateAsOf("CHF", "2026-01-02")) // ismeretlen deviza
    }

    @Test
    fun `convertValue USD-bazisu keplet mindket iranyban`() {
        val fx = converter()
        assertEquals(110.0, fx.convertValue(100.0, "EUR", "USD", "2026-01-02")!!, 1e-9)
        assertEquals(40000.0, fx.convertValue(100.0, "USD", "HUF", "2026-01-02")!!, 1e-9)
        // EUR → HUF: 100 × 1.1 / 0.0025
        assertEquals(44000.0, fx.convertValue(100.0, "EUR", "HUF", "2026-01-02")!!, 1e-9)
    }

    @Test
    fun `GBp a font SZAZADRESZE - a 100-as osztas elvetese 100x hibat adna`() {
        val fx = converter()
        // 250 penny = 2,5 font = 3,125 USD (1,25-ös GBPUSD mellett)
        assertEquals(3.125, fx.convertValue(250.0, "GBp", "USD", "2026-01-02")!!, 1e-9)
        assertEquals(2.5, fx.convertValue(250.0, "GBp", "GBP", "2026-01-02")!!, 1e-9)
        // A 100× hibás érték kifejezetten NEM jöhet ki:
        assertTrue(fx.convertValue(250.0, "GBp", "USD", "2026-01-02")!! < 4.0)
    }

    @Test
    fun `convertRows azonos devizanal ugyanaz a lista, hianyzo ratanal kimarad a sor`() {
        val fx = converter()
        val rows = listOf(OhlcRow("2026-01-02", 1.0, 2.0, 0.5, 1.5, 42.0))
        assertSame(rows, fx.convertRows(rows, "EUR", "EUR"))
        // CHF-re nincs ráta → minden sor kimarad
        assertTrue(fx.convertRows(rows, "CHF", "USD").isEmpty())
        // A volumen átváltásnál változatlan
        val converted = fx.convertRows(rows, "EUR", "USD")
        assertEquals(42.0, converted.single().volume, 0.0)
        assertEquals(1.65, converted.single().close, 1e-9)
    }

    @Test
    fun `setRates a hibas sorokat kihagyja`() {
        val fx = FxConverter()
        fx.setRates(
            listOf(
                FxRateRow("2026-01-02", "EUR", 0.0),
                FxRateRow("2026-01-02", "HUF", -1.0),
                FxRateRow("2026-01-02", "GBP", Double.NaN),
            )
        )
        assertFalse(fx.hasRates())
        assertEquals(listOf("USD"), fx.knownCurrencies())
    }
}
