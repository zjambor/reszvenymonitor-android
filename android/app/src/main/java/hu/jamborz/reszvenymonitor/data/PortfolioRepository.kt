package hu.jamborz.reszvenymonitor.data

import hu.jamborz.reszvenymonitor.data.dto.PortfolioDto
import hu.jamborz.reszvenymonitor.data.dto.PortfolioItemDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Portfóliók CRUD-ja KÖZVETLENÜL PostgREST-en, a bejelentkezett user JWT-jével
 * (owner-only RLS) — nem Edge Functionön át, mert az `authenticated` szerepkörnek
 * van írási grantja ezekre a táblákra. Ugyanaz az út, mint a weben.
 */
class PortfolioRepository(
    private val client: SupabaseClient,
    private val guard: ApiGuard,
) {

    /** Portfóliók az elemeikkel együtt (resource embedding az FK-n). */
    suspend fun fetchPortfolios(): List<PortfolioDto> = guard.run {
        client.postgrest.from("portfolios").select(
            columns = Columns.raw("*,portfolio_items(*)")
        ) {
            order("created_at", Order.ASCENDING)
        }.decodeList<PortfolioDto>()
    }

    suspend fun createPortfolio(name: String, currency: String?): PortfolioDto? = guard.run {
        client.postgrest.from("portfolios").insert(
            buildJsonObject {
                put("name", name)
                currency?.let { put("currency", it) }
            }
        ) { select() }.decodeSingleOrNull<PortfolioDto>()
    }

    suspend fun renamePortfolio(id: String, name: String) {
        guard.run {
            client.postgrest.from("portfolios").update(
                buildJsonObject { put("name", name) }
            ) { filter { eq("id", id) } }
        }
    }

    /** Törlés — a tételeket az FK ON DELETE CASCADE viszi. */
    suspend fun deletePortfolio(id: String) {
        guard.run {
            client.postgrest.from("portfolios").delete { filter { eq("id", id) } }
        }
    }

    /**
     * Beszúrás VAGY módosítás a (portfolio_id, ticker) kulcson — a webes
     * `Prefer: resolution=merge-duplicates` megfelelője.
     */
    suspend fun upsertItem(item: PortfolioItemDto) {
        guard.run {
            client.postgrest.from("portfolio_items").upsert(item)
        }
    }

    suspend fun deleteItem(portfolioId: String, ticker: String) {
        guard.run {
            client.postgrest.from("portfolio_items").delete {
                filter {
                    eq("portfolio_id", portfolioId)
                    eq("ticker", ticker)
                }
            }
        }
    }
}
