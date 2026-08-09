package hu.jamborz.reszvenymonitor.domain

/**
 * A webes portfolio.js portja — szintetikus portfólió-idősor és P/L.
 * Tiszta függvények, hálózat- és Android-mentesek; a napi sorokat a hívó tölti.
 */
object PortfolioCalc {

    /** Egy portfólió-tag a számításokhoz (a PostgREST-sor releváns mezői). */
    data class Item(
        val ticker: String,
        val quantity: Double,
        val purchasePrice: Double? = null,
    )

    data class PnlResult(
        val pnl: Double?,
        val pnlPct: Double?,
        val costedCount: Int,
        val totalCount: Int,
    )

    data class CompositionRow(
        val ticker: String,
        val quantity: Double,
        val price: Double?,
        val value: Double?,
        val weight: Double?,
    )

    data class Composition(
        val totalValue: Double,
        val pricedCount: Int,
        val totalCount: Int,
        val rows: List<CompositionRow>,
    )

    /**
     * A tagok közös dátumtengelye: a LEGKÉSŐBBI tag-első-adatnaptól, minden tag
     * minden napja. Ugyanaz a szabály, mint a [buildPortfolioSeries]-ben — külön
     * is létezik, hogy a devizaátváltás ugyanezen a tengelyen dolgozhasson
     * (lásd [fillForward]).
     */
    fun commonAxis(rowsList: List<List<OhlcRow>>): List<String> {
        val lists = rowsList.filter { it.isNotEmpty() }
        if (lists.isEmpty()) return emptyList()
        val start = lists.maxOf { it.first().date }
        val set = sortedSetOf<String>()
        for (rows in lists) {
            for (r in rows) if (r.date >= start) set.add(r.date)
        }
        return set.toList()
    }

    /**
     * Egy tag sorainak kiterjesztése a megadott dátumtengelyre, ELŐRE-TÖLTÉSSEL:
     * a nem kereskedett napokon az utolsó ismert záró ad lapos O=H=L=C sort (volume 0).
     *
     * MIÉRT KÜLÖN: devizaátváltásnál a SORREND számít. Ha előbb váltanánk át és
     * csak utána töltenénk elő, egy elavult árú tag a RÉGI napi árfolyamon ragadna —
     * az összérték és a súlyok devizafüggővé válnának. Ezért: előbb előre-töltés
     * natív devizában, utána átváltás.
     */
    fun fillForward(rows: List<OhlcRow>, dates: List<String>): List<OhlcRow> {
        if (rows.isEmpty() || dates.isEmpty()) return emptyList()

        val out = ArrayList<OhlcRow>(dates.size)
        var i = 0
        var last = rows.first().close
        // A tengely eleje elé eső sorokon előretekerünk — az első napokra is legyen mit tölteni.
        while (i < rows.size && rows[i].date < dates.first()) {
            last = rows[i].close
            i++
        }
        for (date in dates) {
            if (i < rows.size && rows[i].date == date) {
                val r = rows[i]
                out.add(OhlcRow(date, r.open, r.high, r.low, r.close, r.volume))
                last = r.close
                i++
            } else {
                out.add(OhlcRow(date, last, last, last, last, 0.0))
            }
        }
        return out
    }

    /**
     * Szintetikus portfólió-idősor: darabszám-súlyozott OHLC-összegek naponta.
     * A tengely a LEGKÉSŐBBI tag-első-adatnaptól indul (nincs hamis „bevezetési
     * ugrás"); tag-hiányos napokon az utolsó ismert záró ad lapos hozzájárulást.
     * Kimenet a megszokott sor-séma volume=0-val — az aggregate/presetRange/
     * computeStats lánc változtatás nélkül fogyasztja.
     */
    fun buildPortfolioSeries(items: List<Item>, dailyMap: Map<String, List<OhlcRow>>): List<OhlcRow> {
        class Member(val quantity: Double, val rows: List<OhlcRow>) {
            var idx = 0
            var last = rows.first().close
        }

        val members = items
            .map { it.quantity to (dailyMap[it.ticker] ?: emptyList()) }
            .filter { (q, rows) -> rows.isNotEmpty() && q.isFinite() && q > 0 }
            .map { (q, rows) -> Member(q, rows) }
        if (members.isEmpty()) return emptyList()

        val start = members.maxOf { it.rows.first().date }

        val dateSet = sortedSetOf<String>()
        for (m in members) {
            for (r in m.rows) if (r.date >= start) dateSet.add(r.date)
        }
        if (dateSet.isEmpty()) return emptyList()

        // Pointer-előretekerés a start elé eső sorokon — közben megjegyezzük az
        // utolsó zárót, hogy a start utáni első hiányzó napokra is legyen fill.
        for (m in members) {
            while (m.idx < m.rows.size && m.rows[m.idx].date < start) {
                m.last = m.rows[m.idx].close
                m.idx++
            }
        }

        val series = ArrayList<OhlcRow>(dateSet.size)
        for (date in dateSet) {
            var open = 0.0
            var high = 0.0
            var low = 0.0
            var close = 0.0
            for (m in members) {
                val o: Double
                val h: Double
                val l: Double
                val c: Double
                if (m.idx < m.rows.size && m.rows[m.idx].date == date) {
                    val r = m.rows[m.idx]
                    o = r.open; h = r.high; l = r.low; c = r.close
                    m.last = r.close
                    m.idx++
                } else {
                    // A tag ma nem kereskedett — lapos hozzájárulás az utolsó záróval.
                    o = m.last; h = m.last; l = m.last; c = m.last
                }
                open += m.quantity * o
                high += m.quantity * h
                low += m.quantity * l
                close += m.quantity * c
            }
            series.add(OhlcRow(date, open, high, low, close, 0.0))
        }
        return series
    }

    /**
     * Nyereség/veszteség a bekerülési árral rendelkező elemekre. Részleges
     * költségadatnál a costedCount/totalCount jelzi az arányt („N/M elem").
     */
    fun computePnL(items: List<Item>, dailyMap: Map<String, List<OhlcRow>>): PnlResult {
        var costedCount = 0
        var invested = 0.0
        var current = 0.0
        for (it in items) {
            val price = it.purchasePrice
            val rows = dailyMap[it.ticker] ?: emptyList()
            if (price == null || !price.isFinite() || !it.quantity.isFinite() || rows.isEmpty()) continue
            costedCount++
            invested += it.quantity * price
            current += it.quantity * rows.last().close
        }

        if (costedCount == 0 || invested <= 0) {
            return PnlResult(pnl = null, pnlPct = null, costedCount = costedCount, totalCount = items.size)
        }
        return PnlResult(
            pnl = current - invested,
            pnlPct = (current - invested) / invested * 100,
            costedCount = costedCount,
            totalCount = items.size,
        )
    }

    /**
     * Portfólió-összetétel az aktuális piaci érték szerint (Részletek).
     * value = darabszám × utolsó záró; a súly a teljes érték százaléka;
     * ár nélküli tag value = null. Érték szerint csökkenő sorrend.
     */
    fun computeComposition(items: List<Item>, dailyMap: Map<String, List<OhlcRow>>): Composition {
        val unsorted = items.map {
            val data = dailyMap[it.ticker] ?: emptyList()
            val price = data.lastOrNull()?.close
            val value = if (it.quantity.isFinite() && price != null) it.quantity * price else null
            CompositionRow(ticker = it.ticker, quantity = it.quantity, price = price, value = value, weight = null)
        }

        val totalValue = unsorted.sumOf { it.value ?: 0.0 }
        val weighted = unsorted.map { r ->
            if (r.value != null && totalValue > 0) r.copy(weight = r.value / totalValue * 100) else r
        }
        val rows = weighted.sortedByDescending { it.value ?: Double.NEGATIVE_INFINITY }

        return Composition(
            totalValue = totalValue,
            pricedCount = rows.count { it.value != null },
            totalCount = rows.size,
            rows = rows,
        )
    }
}
