package hu.jamborz.reszvenymonitor.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Valós, a web-app Supabase-adatbázisából exportált sorok betöltése
 * (src/test/resources/fixtures) — a terv szerint a két implementáció számainak
 * bizonyítottan egyeznie kell, ezért a referencia-adat élő sorokból származik.
 */
object Fixtures {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class PriceRow(
        val ticker: String? = null,
        val date: String,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double,
    )

    @Serializable
    private data class FxJsonRow(val date: String, val currency: String, val usd_rate: Double)

    private fun text(name: String): String {
        val stream = checkNotNull(Fixtures::class.java.getResourceAsStream("/fixtures/$name")) {
            "Hiányzó fixture: $name"
        }
        // A PowerShell-export UTF-8 BOM-mal írt — lecsípjük.
        return stream.bufferedReader().readText().removePrefix("﻿")
    }

    fun prices(name: String): List<PriceRow> = json.decodeFromString(text(name))

    fun ohlcRows(name: String): List<OhlcRow> =
        prices(name).map { OhlcRow(it.date, it.open, it.high, it.low, it.close, it.volume) }

    fun fxRows(name: String): List<FxRateRow> =
        json.decodeFromString<List<FxJsonRow>>(text(name))
            .map { FxRateRow(it.date, it.currency, it.usd_rate) }
}
