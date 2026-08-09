package hu.jamborz.reszvenymonitor.data.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A sync-prices Edge Function VALÓS válaszainak dekódolása (fixtures/resp_*.json).
 *
 * MIÉRT: a `search` válasz `isin` mezője **boolean** (nem string, ahogy elsőre
 * feltételeztem) — ez éles futásnál JsonDecodingException-nel omlasztotta az
 * appot. Azóta a DTO helyes, az ApiGuard pedig a séma-hibát is kezelhető
 * ApiException-né alakítja; ez a teszt a valós válaszokon őrzi a szerződést.
 */
class ResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "Hiányzó fixture: $name" }
            .bufferedReader().readText().removePrefix("﻿").trim()

    @Test
    fun `search valasz ISIN-re - az isin mezo BOOLEAN, a talalatok devizaval jonnek`() {
        val response = json.decodeFromString<SearchResponseDto>(fixture("resp_search_isin.json"))
        assertTrue(response.ok)
        assertTrue("A szerver ISIN-ként dolgozta fel a kérést", response.isin)
        assertEquals("IE00B5BMR087", response.query)
        assertTrue("Több jegyzést várunk", response.results.size >= 5)

        // A deviza a legfontosabb megkülönböztető — minden találatnál legyen.
        assertTrue(response.results.all { !it.currency.isNullOrBlank() })
        // Ugyanaz az alap több devizában fut — ez a kereső létjogosultsága.
        assertTrue("Legalább két különböző devizát várunk", response.results.mapNotNull { it.currency }.toSet().size >= 2)
        assertTrue("ETF-ként kell jelölni", response.results.any { it.isEtf })
    }

    @Test
    fun `delete valasz portfolio-tagsagnal - in-portfolio kod es magyar uzenet`() {
        val response = json.decodeFromString<DeleteResponseDto>(fixture("resp_delete_inportfolio.json"))
        assertFalse(response.ok)
        assertEquals("in-portfolio", response.code)
        val message = response.message.orEmpty()
        assertTrue("A szerver üzenete sorolja fel a portfóliókat: $message", message.contains("portfólió"))
    }

    @Test
    fun `add valasz mar felvett tickerre - already-exists kod`() {
        val response = json.decodeFromString<AddResponseDto>(fixture("resp_add_exists.json"))
        assertFalse(response.ok)
        assertEquals("already-exists", response.code)
        assertEquals("NVDA", response.symbol)
    }

    @Test
    fun `sync valasz - up-to-date NEM hiba`() {
        val response = json.decodeFromString<SyncResponseDto>(fixture("resp_sync.json"))
        val result = response.results.single()
        assertEquals("NVDA", result.symbol)
        assertFalse("Az up-to-date nem valódi hiba (12. invariáns)", result.isFailure)
    }
}
