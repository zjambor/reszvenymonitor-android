package hu.jamborz.reszvenymonitor.domain

import hu.jamborz.reszvenymonitor.domain.Transform.Preset
import hu.jamborz.reszvenymonitor.domain.Transform.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransformTest {

    private fun row(date: String, open: Double, high: Double, low: Double, close: Double, volume: Double = 100.0) =
        OhlcRow(date, open, high, low, close, volume)

    // --- mondayKey: heti kulcs határnapokon (invariáns 9) ---

    @Test
    fun `mondayKey het kozepen es hetfon`() {
        assertEquals("2026-08-03", Transform.mondayKey("2026-08-05")) // szerda
        assertEquals("2026-08-03", Transform.mondayKey("2026-08-03")) // maga a hétfő
    }

    @Test
    fun `mondayKey vasarnap az ELOZO hetfore mutat`() {
        // JS: getUTCDay()===0 → −6 nap. 2026-08-09 vasárnap → 2026-08-03.
        assertEquals("2026-08-03", Transform.mondayKey("2026-08-09"))
    }

    @Test
    fun `mondayKey evhataron atnyulik`() {
        // 2026-01-01 csütörtök → hétfője 2025-12-29 (előző év!).
        assertEquals("2025-12-29", Transform.mondayKey("2026-01-01"))
    }

    @Test
    fun `monthKey egyszeru prefix`() {
        assertEquals("2026-02", Transform.monthKey("2026-02-28"))
    }

    // --- shiftDate: a JS Date hónapvégi ÁTGÖRDÜLÉSÉVEL (nem clamp!) ---

    @Test
    fun `shiftDate napokkal`() {
        assertEquals("2022-12-25", Transform.shiftDate("2023-01-01", days = 7))
    }

    @Test
    fun `shiftDate honapvegi atgordules mint a JS Date`() {
        // 2023-03-31 − 1 hónap = „február 31." → JS-ben 2023-03-03 (nem 02-28!).
        assertEquals("2023-03-03", Transform.shiftDate("2023-03-31", months = 1))
        // 2026-05-31 − 3 hónap = „február 31." → 2026-03-03.
        assertEquals("2026-03-03", Transform.shiftDate("2026-05-31", months = 3))
    }

    @Test
    fun `shiftDate szokonapi atgordules evlepesnel`() {
        // 2024-02-29 − 1 év = „2023-02-29" → JS-ben 2023-03-01.
        assertEquals("2023-03-01", Transform.shiftDate("2024-02-29", years = 1))
    }

    @Test
    fun `daysBetween elojeles kulonbseg`() {
        assertEquals(10, Transform.daysBetween("2023-01-01", "2023-01-11"))
        assertEquals(-10, Transform.daysBetween("2023-01-11", "2023-01-01"))
    }

    // --- aggregate ---

    @Test
    fun `aggregate daily masolatot ad`() {
        val daily = listOf(row("2026-08-03", 1.0, 2.0, 0.5, 1.5))
        val out = Transform.aggregate(daily, Resolution.DAILY)
        assertEquals(daily, out)
    }

    @Test
    fun `aggregate weekly hetfokulccsal tordel, bar-datum az elso kereskedesi nap`() {
        val daily = listOf(
            row("2026-07-31", 10.0, 11.0, 9.0, 10.5, 100.0), // péntek — előző hét
            row("2026-08-03", 11.0, 12.0, 10.0, 11.5, 200.0), // hétfő — új hét
            row("2026-08-04", 11.5, 13.0, 11.2, 12.8, 300.0), // kedd — ugyanaz a hét
        )
        val bars = Transform.aggregate(daily, Resolution.WEEKLY)
        assertEquals(2, bars.size)
        assertEquals(row("2026-07-31", 10.0, 11.0, 9.0, 10.5, 100.0), bars[0])
        // A heti bar: open az elsőé, close az utolsóé, high/low szélsőérték, volumen összeg.
        assertEquals(row("2026-08-03", 11.0, 13.0, 10.0, 12.8, 500.0), bars[1])
    }

    @Test
    fun `aggregate monthly honaphataron tordel`() {
        val daily = listOf(
            row("2026-01-29", 1.0, 1.2, 0.9, 1.1, 10.0),
            row("2026-01-30", 1.1, 1.4, 1.0, 1.3, 20.0),
            row("2026-02-02", 1.3, 1.5, 1.2, 1.4, 30.0),
        )
        val bars = Transform.aggregate(daily, Resolution.MONTHLY)
        assertEquals(2, bars.size)
        assertEquals("2026-01-29", bars[0].date)
        assertEquals(1.3, bars[0].close, 0.0)
        assertEquals(30.0, bars[0].volume, 0.0)
        assertEquals("2026-02-02", bars[1].date)
    }

    // --- presetRange ---

    @Test
    fun `presetRange YTD es MIND es clamp`() {
        val daily = listOf(
            row("2023-01-02", 1.0, 1.0, 1.0, 1.0),
            row("2026-08-07", 2.0, 2.0, 2.0, 2.0),
        )
        assertEquals(Transform.DateRange("2026-01-01", "2026-08-07"), Transform.presetRange(daily, Preset.YTD))
        assertEquals(Transform.DateRange("2023-01-02", "2026-08-07"), Transform.presetRange(daily, Preset.MIND))

        // Fiatal sor: az 1 éves ablak az első adatnapra clampelődik.
        val young = listOf(
            row("2026-06-01", 1.0, 1.0, 1.0, 1.0),
            row("2026-08-07", 2.0, 2.0, 2.0, 2.0),
        )
        assertEquals(Transform.DateRange("2026-06-01", "2026-08-07"), Transform.presetRange(young, Preset.EV1))
        assertNull(Transform.presetRange(emptyList(), Preset.MIND))
    }

    // --- computeStats ---

    @Test
    fun `computeStats ablakra szur, napi valtozas a teljes sor utolso ket napjabol`() {
        val daily = listOf(
            row("2026-08-03", 10.0, 10.5, 9.5, 10.0, 100.0),
            row("2026-08-04", 12.0, 12.5, 11.5, 12.0, 100.0),
            row("2026-08-05", 11.0, 11.5, 10.5, 11.0, 100.0),
            row("2026-08-06", 13.0, 13.5, 12.5, 13.0, 100.0),
        )
        val s = Transform.computeStats(daily, "2026-08-05", "2026-08-06")!!
        assertEquals(13.0, s.lastClose, 0.0)
        assertEquals(2.0, s.dayChange!!, 1e-9) // 13 − 11
        assertEquals(2.0 / 11.0 * 100, s.dayChangePct!!, 1e-9)
        assertEquals((13.0 - 11.0) / 11.0 * 100, s.periodChangePct!!, 1e-9) // ablak első zárójához képest
        assertEquals(13.5, s.periodHigh, 0.0)
        assertEquals(10.5, s.periodLow, 0.0)
        assertEquals(100.0, s.avgVolume, 0.0)
    }

    @Test
    fun `computeStats ures ablaknal a teljes sorra esik vissza`() {
        val daily = listOf(
            row("2026-08-03", 10.0, 10.5, 9.5, 10.0, 100.0),
            row("2026-08-06", 13.0, 13.5, 12.5, 13.0, 300.0),
        )
        val s = Transform.computeStats(daily, "2030-01-01", "2030-01-02")!!
        assertEquals((13.0 - 10.0) / 10.0 * 100, s.periodChangePct!!, 1e-9)
        assertEquals(200.0, s.avgVolume, 0.0)
        assertNull(Transform.computeStats(emptyList(), "a", "b"))
    }
}
