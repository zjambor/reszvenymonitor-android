package hu.jamborz.reszvenymonitor.data

import hu.jamborz.reszvenymonitor.data.dto.AddResponseDto
import hu.jamborz.reszvenymonitor.data.dto.DeleteResponseDto
import hu.jamborz.reszvenymonitor.data.dto.SearchResponseDto
import hu.jamborz.reszvenymonitor.data.dto.SyncResponseDto
import hu.jamborz.reszvenymonitor.data.dto.SyncResultDto
import hu.jamborz.reszvenymonitor.data.dto.TickerDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Tickers-lista + a sync-prices Edge Function frissítés-útja. A felvétel/
 * keresés/törlés akciók a 7. fázisban kerülnek ide. A function-hívás a
 * bejelentkezett user JWT-jét viszi (a sync-prices owner-ellenőrzése miatt
 * kötelező) — ezt a supabase-kt automatikusan intézi.
 */
open class TickerRepository(
    private val client: SupabaseClient,
    private val guard: ApiGuard,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Aktív tickerek sort_order szerint (szimbólum a döntetlen-feloldó).
     * A rendezés kliensoldalon fut — determinisztikus, és nem függ attól,
     * hogy a könyvtár hány order-paramétert enged.
     */
    open suspend fun fetchTickers(): List<TickerDto> = guard.run {
        client.postgrest.from("tickers").select(columns = Columns.ALL) {
            filter { eq("is_active", true) }
            order("sort_order", Order.ASCENDING)
        }.decodeList<TickerDto>()
            .sortedWith(compareBy({ it.sortOrder ?: Int.MAX_VALUE }, { it.symbol }))
    }

    /**
     * Árfolyam-frissítés (Frissítés gomb / portfólió-frissítés EGY hívással):
     * `POST {"symbols":[…]}`. A válaszelemek `error === "up-to-date"` értéke
     * NEM hiba (12. invariáns) — a [SyncResultDto.isUpToDate] jelzi; a
     * Yahoo-throttling retry a szerveren van, a klienst nem érinti.
     */
    open suspend fun syncPrices(symbols: List<String>): List<SyncResultDto> = guard.run {
        val body = buildJsonObject {
            putJsonArray("symbols") { symbols.forEach { add(it) } }
        }
        val response = client.functions.invoke(function = "sync-prices", body = body)
        json.decodeFromString<SyncResponseDto>(response.bodyAsText()).results
    }

    /**
     * Szimbólum-keresés a tőzsdéken (sync-prices `search`). ISIN-t és szabad
     * szöveget is elfogad; a rangsorolás és a vágás a SZERVEREN történik.
     * A találatok már szűrve vannak arra, ami ténylegesen felvehető.
     */
    suspend fun searchSymbols(query: String): SearchResponseDto = guard.run {
        val body = buildJsonObject {
            put("action", "search")
            put("query", query)
        }
        val response = client.functions.invoke(function = "sync-prices", body = body)
        json.decodeFromString<SearchResponseDto>(response.bodyAsText())
    }

    /**
     * Új ticker felvétele (Yahoo-validálás + teljes adatsor-letöltés a szerveren).
     * Az üzleti hibák (`ok:false` + kód) NEM kivételek — a hívó dolgozza fel.
     */
    suspend fun addTicker(symbol: String): AddResponseDto = guard.run {
        val body = buildJsonObject {
            put("action", "add")
            put("symbol", symbol)
        }
        val response = client.functions.invoke(function = "sync-prices", body = body)
        json.decodeFromString<AddResponseDto>(response.bodyAsText())
    }

    /**
     * Ticker törlése a teljes adatsorával (service role a szerveren; a
     * stock_prices/asset_profiles sorokat az FK ON DELETE CASCADE viszi).
     * Portfólió-tagság esetén `in-portfolio` kóddal utasít vissza.
     */
    open suspend fun deleteTicker(symbol: String): DeleteResponseDto = guard.run {
        val body = buildJsonObject {
            put("action", "delete")
            put("symbol", symbol)
        }
        val response = client.functions.invoke(function = "sync-prices", body = body)
        json.decodeFromString<DeleteResponseDto>(response.bodyAsText())
    }
}
