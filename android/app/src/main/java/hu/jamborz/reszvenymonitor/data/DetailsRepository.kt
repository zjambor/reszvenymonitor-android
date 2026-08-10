package hu.jamborz.reszvenymonitor.data

import hu.jamborz.reszvenymonitor.data.dto.AssetDetailsResponseDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Instrumentum-részletek az `asset-details` Edge Functionből (a webes
 * api.getAssetDetails párja). A profil-lekérést a szerver végzi (FMP/Yahoo/
 * Alpaca) és 7 napig gyorsítótárazza — a kliens sosem hív külső API-t.
 *
 * A hívás a bejelentkezett user JWT-jét viszi: a függvény owner-ellenőrzést
 * végez, mert az FMP-kvótát védeni kell az anonim kérésektől.
 *
 * Portfóliónál NINCS hívás — az összetétel helyben, a már betöltött árakból
 * számolódik (lásd MonitorViewModel.portfolioComposition).
 */
class DetailsRepository(
    private val client: SupabaseClient,
    private val guard: ApiGuard,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Egy szimbólum részletei. Az üzleti hibák (`ok:false` + `unknown-symbol` /
     * `no-profile-data`) NEM kivételek — a hívó dolgozza fel őket.
     *
     * @param force a szerveroldali gyorsítótár megkerülése (a UI nem használja,
     *   de a függvény szerződésének része — kézi frissítéshez).
     */
    suspend fun getAssetDetails(symbol: String, force: Boolean = false): AssetDetailsResponseDto = guard.run {
        val body = buildJsonObject {
            put("symbol", symbol)
            put("force", force)
        }
        val response = client.functions.invoke(function = "asset-details", body = body)
        json.decodeFromString<AssetDetailsResponseDto>(response.bodyAsText())
    }
}
