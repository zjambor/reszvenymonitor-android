package hu.jamborz.reszvenymonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestTest {

    @Test
    fun `editDistance alapesetek`() {
        assertEquals(1, Suggest.editDistance("SKR8", "SXR8"))
        assertEquals(3, Suggest.editDistance("", "ABC"))
        assertEquals(3, Suggest.editDistance("ABC", ""))
        assertEquals(3, Suggest.editDistance("kitten", "sitting"))
        assertEquals(0, Suggest.editDistance("NVDA", "NVDA"))
    }

    @Test
    fun `nearSymbols - az SKR8 to SXR8_DE motivalo eset, utotag nelkuli alakkal`() {
        // Az „SKR8" nem részszövege az „SXR8.DE"-nek — a substring-szűrő üres.
        // A Levenshtein a gyökeret (SXR8) nézve 1 távolságot ad → javaslat.
        val symbols = listOf("NVDA", "MSFT", "SXR8.DE", "CSPX.L")
        assertEquals(listOf("SXR8.DE"), Suggest.nearSymbols("SKR8", symbols))
    }

    @Test
    fun `nearSymbols - 3 karakter alatt nincs javaslat`() {
        assertTrue(Suggest.nearSymbols("NV", listOf("NVDA")).isEmpty())
    }

    @Test
    fun `nearSymbols - rovid beirasnal 1, hosszabbnal 2 eltures`() {
        // 4 karakter: csak 1 elütés fér bele.
        assertEquals(listOf("NVDA"), Suggest.nearSymbols("NVDX", listOf("NVDA", "AMD")))
        assertTrue(Suggest.nearSymbols("NVXY", listOf("NVDA")).isEmpty()) // távolság 2 > 1
        // 5+ karakter: 2 elütés is belefér.
        assertEquals(listOf("CSPX.L"), Suggest.nearSymbols("CSPXL", listOf("CSPX.L", "NVDA")))
    }

    @Test
    fun `nearSymbols - tavolsag szerint rendez es 5-re vag`() {
        val symbols = listOf("AAAA", "AAAB", "AAAC", "AAAD", "AAAE", "AAAF", "AAAG")
        val out = Suggest.nearSymbols("AAAB", symbols)
        assertEquals(5, out.size)
        assertEquals("AAAB", out.first()) // 0 távolság előre
    }

    @Test
    fun `szimbolum- es ISIN-mintak`() {
        assertTrue(Suggest.SYMBOL_LIKE.matches("SNOW"))
        assertTrue(Suggest.SYMBOL_LIKE.matches("BRK.B"))
        assertTrue(!Suggest.SYMBOL_LIKE.matches("1ABC"))
        assertTrue(Suggest.ISIN_LIKE.matches("IE00B5BMR087"))
        assertTrue(!Suggest.ISIN_LIKE.matches("IE00B5BMR08"))
    }
}
