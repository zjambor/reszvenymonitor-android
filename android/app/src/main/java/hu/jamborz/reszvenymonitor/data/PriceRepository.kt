package hu.jamborz.reszvenymonitor.data

import hu.jamborz.reszvenymonitor.BuildConfig
import hu.jamborz.reszvenymonitor.data.dto.StockPriceDto
import hu.jamborz.reszvenymonitor.domain.OhlcRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A webes api.js árfolyam-oldala: lapozó letöltés, memória-cache,
 * stale-while-revalidate. KRITIKUS INVARIÁNSOK (TERV-ANDROID.md, Adatréteg):
 *
 * 1. Lapozás: a PostgREST 1000 soros plafonja miatt limit/offset ciklus, amíg
 *    egy oldal rövidebb nem lesz 1000-nél — enélkül ~2027-től némán csonkulna.
 * 2. A cache MINDIG NATÍV devizás sorokat tárol — a megjelenítési devizára
 *    váltás a cache fölött, tisztán memóriában történik (0 hálózati kérés).
 * 8. Stale-while-revalidate: cache-találatra azonnali emisszió + csendes
 *    háttér-újratöltés; az összehasonlítás a NATÍV sorokon fut.
 *
 * A versenykezelés a hívó oldalon coroutine-cancellation: a ViewModel
 * `collectLatest`/`flatMapLatest`-je az előző gyűjtést (és vele a folyamatban
 * lévő Ktor-kérést) megszakítja — a webes kérés-token + AbortController párja.
 */
open class PriceRepository(
    private val client: SupabaseClient,
    private val guard: ApiGuard,
) {
    companion object {
        const val PAGE_SIZE = 1000
    }

    /** Egy napi adatsor-emisszió eredete. */
    sealed class DailyUpdate {
        abstract val rows: List<OhlcRow>

        /** Cache-találat — azonnali kirajzolásra. */
        data class FromCache(override val rows: List<OhlcRow>) : DailyUpdate()

        /** Friss letöltés (nem volt cache, vagy force). */
        data class FromNetwork(override val rows: List<OhlcRow>) : DailyUpdate()

        /** Háttér-revalidálás eredménye — CSAK ha tényleg változott az adatsor. */
        data class Revalidated(override val rows: List<OhlcRow>) : DailyUpdate()
    }

    /** symbol → napi sorok NATÍV devizában (dátum szerint növekvő). A sessionre él. */
    private val dailyCache = ConcurrentHashMap<String, List<OhlcRow>>()

    open fun hasCached(symbol: String): Boolean = dailyCache.containsKey(symbol)

    /** Törlés után: ne éledjen újra a régi adat. */
    open fun evictCache(symbol: String) {
        dailyCache.remove(symbol)
    }

    /**
     * Egy ticker összes napi sora START_DATE-től, dátum szerint növekvően —
     * lapozó ciklussal (1. invariáns).
     */
    suspend fun fetchDailyRows(symbol: String): List<OhlcRow> = guard.run {
        val all = mutableListOf<OhlcRow>()
        var offset = 0L
        while (true) {
            val page = client.postgrest.from("stock_prices").select(
                columns = Columns.list("date", "open", "high", "low", "close", "volume")
            ) {
                filter {
                    eq("ticker", symbol)
                    gte("date", BuildConfig.START_DATE)
                }
                order("date", Order.ASCENDING)
                range(offset, offset + PAGE_SIZE - 1)
            }.decodeList<StockPriceDto>()

            page.mapTo(all) { OhlcRow(it.date, it.open, it.high, it.low, it.close, it.volume) }
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        all
    }

    /**
     * Napi adatok cache-ből vagy hálózatról (a webes getDaily megfelelője) —
     * a portfólió-tagbetöltés (8. fázis) is ezt használja. force=true cache-kerülés.
     */
    open suspend fun getDaily(symbol: String, force: Boolean = false): List<OhlcRow> {
        if (!force) dailyCache[symbol]?.let { return it }
        val rows = fetchDailyRows(symbol)
        dailyCache[symbol] = rows
        return rows
    }

    /**
     * Tickerváltás adatfolyama stale-while-revalidate-tel:
     * - cache-találat → azonnal [DailyUpdate.FromCache], majd csendes háttér-
     *   újratöltés, és CSAK változásnál [DailyUpdate.Revalidated] (8. invariáns);
     * - nincs cache (vagy force) → [DailyUpdate.FromNetwork].
     * A háttér-revalidálás hibája nem ronthatja el a már kirajzolt nézetet —
     * elnyelődik (a cancellation kivételével), mint a webes console.warn-ág.
     */
    open fun dailyFlow(symbol: String, force: Boolean = false): Flow<DailyUpdate> = flow {
        val cached = if (force) null else dailyCache[symbol]
        if (cached != null) {
            emit(DailyUpdate.FromCache(cached))
            val fresh = try {
                fetchDailyRows(symbol)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@flow // csendes háttérhiba — marad a cache-elt nézet
            }
            dailyCache[symbol] = fresh
            if (rowsChanged(cached, fresh)) emit(DailyUpdate.Revalidated(fresh))
        } else {
            val fresh = fetchDailyRows(symbol)
            dailyCache[symbol] = fresh
            emit(DailyUpdate.FromNetwork(fresh))
        }
    }

    /**
     * A webes revalidateDaily összehasonlítása, NATÍV sorokon: hossz, utolsó
     * dátum, utolsó záró. (Átváltott adaton devizaváltás után minden nap
     * „változottnak" tűnne — 8. invariáns.)
     */
    private fun rowsChanged(old: List<OhlcRow>, fresh: List<OhlcRow>): Boolean =
        fresh.size != old.size ||
            (fresh.isNotEmpty() && (
                fresh.last().date != old.last().date ||
                    fresh.last().close != old.last().close
                ))
}
