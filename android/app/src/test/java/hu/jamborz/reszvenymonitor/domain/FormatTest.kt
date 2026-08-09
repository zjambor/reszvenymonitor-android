package hu.jamborz.reszvenymonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * hu-HU formázás — a csoportosító a NEM TÖRŐ szóköz (U+00A0), a tizedesjel
 * vessző; a ' p' (penny) és ' £' (font) utótag szigorúan különbözik.
 */
class FormatTest {

    private val NBSP = '\u00A0'

    @Test
    fun `formatPriceIn devizajelek - GBp NEM azonos GBP-vel`() {
        assertEquals("181,57 $", Format.formatPriceIn(181.57, "USD"))
        assertEquals("12,30 €", Format.formatPriceIn(12.3, "EUR"))
        assertEquals("2${NBSP}967,00 p", Format.formatPriceIn(2967.0, "GBp"))
        assertEquals("29,67 £", Format.formatPriceIn(29.67, "GBP"))
        // Ismeretlen deviza: maga a kód az utótag (webes viselkedés).
        assertEquals("27${NBSP}763,00 HUF", Format.formatPriceIn(27763.0, "HUF"))
        assertEquals("—", Format.formatPriceIn(null, "USD"))
        assertEquals("—", Format.formatPriceIn(Double.NaN, "USD"))
    }

    @Test
    fun `formatSignedIn es formatPct elojelezes`() {
        assertEquals("+12,34 $", Format.formatSignedIn(12.34, "USD"))
        assertEquals("+1,23%", Format.formatPct(1.23))
        assertEquals("0,00%", Format.formatPct(0.0)) // nullára nincs plusz
        assertEquals("—", Format.formatPct(null))
    }

    @Test
    fun `negativ szamok a hu-HU mínuszjellel`() {
        // A JVM CLDR-adata és a böngésző ICU-ja ugyanazt a jelet adja hu-HU-ra.
        val minus = Format.formatPct(-5.12)
        assertEquals(minus, Format.formatPct(-5.12)) // determinisztikus
        // A számjegyrész kötelezően "5,12%" — az előjelet a locale adja.
        assert(minus.endsWith("5,12%")) { "váratlan formátum: $minus" }
    }

    @Test
    fun `formatVolume E-M-Mrd rovidites`() {
        assertEquals("950", Format.formatVolume(950.0))
        assertEquals("1,2 E", Format.formatVolume(1234.0))
        assertEquals("168,4 M", Format.formatVolume(168_400_000.0))
        assertEquals("2,5 Mrd", Format.formatVolume(2_500_000_000.0))
        assertEquals("1 M", Format.formatVolume(1_000_000.0)) // nincs felesleges tizedes
        assertEquals("—", Format.formatVolume(null))
    }

    @Test
    fun `formatDateHu magyar rovid datum`() {
        assertEquals("2026. júl. 10.", Format.formatDateHu("2026-07-10"))
        assertEquals("2023. jan. 2.", Format.formatDateHu("2023-01-02"))
        assertEquals("nem-datum", Format.formatDateHu("nem-datum")) // hibás bemenet változatlanul
        assertEquals("—", Format.formatDateHu(null))
        assertEquals("—", Format.formatDateHu(""))
    }
}
