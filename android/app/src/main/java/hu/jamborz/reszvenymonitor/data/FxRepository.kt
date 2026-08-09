package hu.jamborz.reszvenymonitor.data

import hu.jamborz.reszvenymonitor.BuildConfig
import hu.jamborz.reszvenymonitor.data.dto.FxRateDto
import hu.jamborz.reszvenymonitor.domain.FxConverter
import hu.jamborz.reszvenymonitor.domain.FxRateRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * A stocks.fx_rates letöltése (lapozó ciklussal — devizánként ~950 sor, több
 * deviza együtt átlépi a PostgREST 1000 soros plafonját) és a domain
 * [FxConverter] feltöltése.
 *
 * 11. invariáns: az FX-kiesés NEM boríthatja az app betöltését — ilyenkor a
 * devizakapcsoló tiltott, minden a natív jegyzési devizájában látszik. Ezért a
 * [refresh] a nem-auth hibákat false-szá nyeli (mint a webes loadTickers
 * `.catch(...  return [])` ága); a 401 viszont továbbmegy (login-képernyő).
 */
class FxRepository(
    private val client: SupabaseClient,
    private val guard: ApiGuard,
) {
    /** A naponkénti átváltó — a ViewModel-ek a cache FÖLÖTT ezzel váltanak. */
    val converter = FxConverter()

    /** Az utolsó sikeres betöltés sormennyisége (lapozás-diagnosztikához). */
    @Volatile
    var loadedRowCount: Int = 0
        private set

    fun hasRates(): Boolean = converter.hasRates()

    /**
     * Árfolyamok (újra)töltése. Siker: true (és a converter feltöltve);
     * nem-auth hiba: false — natív devizás fallback; 401: ApiException továbbdobva.
     */
    suspend fun refresh(): Boolean {
        val rows = try {
            fetchAllRates()
        } catch (e: ApiException) {
            if (e.status == 401) throw e
            return false
        }
        converter.setRates(rows)
        loadedRowCount = rows.size
        return hasRates()
    }

    private suspend fun fetchAllRates(): List<FxRateRow> = guard.run {
        val all = mutableListOf<FxRateRow>()
        var offset = 0L
        while (true) {
            val page = client.postgrest.from("fx_rates").select(
                columns = Columns.list("currency", "date", "usd_rate")
            ) {
                filter { gte("date", BuildConfig.START_DATE) }
                order("currency", Order.ASCENDING)
                order("date", Order.ASCENDING)
                range(offset, offset + PriceRepository.PAGE_SIZE - 1)
            }.decodeList<FxRateDto>()

            page.mapTo(all) { FxRateRow(it.date, it.currency, it.usdRate) }
            if (page.size < PriceRepository.PAGE_SIZE) break
            offset += PriceRepository.PAGE_SIZE
        }
        all
    }
}
