package hu.jamborz.reszvenymonitor.domain

/**
 * A webes fx.js portja — devizaátváltás a stocks.fx_rates napi árfolyamaiból.
 *
 * MODELL: usd_rate = 1 egység `currency` hány USD;
 *   érték(hová) = érték(honnan) × usd_rate(honnan) / usd_rate(hová).
 * Az USD nincs a táblában (definíció szerint 1).
 *
 * GBp: a londoni penny-jegyzés NEM önálló deviza, hanem a GBP századrésze.
 * A /100 KIZÁRÓLAG a [toUsdFactor]-ban él — elvétése 100× hibát adna
 * (a Format.CURRENCY_SUFFIX a jelet is megkülönbözteti: " p" vs " £").
 *
 * NAPONKÉNTI átváltás, nem egyetlen spot rátával — különben a mai árfolyam
 * visszamenőleg átírná a múltat (mért példa a webes README-ben: 9,4% eltérés).
 *
 * A webes modulszintű index helyett osztály: az élettartamát a DI-konténer adja.
 */
class FxConverter {

    companion object {
        /** A választható megjelenítési devizák — szinkronban a sync-prices listájával. */
        val DISPLAY_CURRENCIES = listOf("USD", "EUR", "HUF")
    }

    private data class RatePoint(val date: String, val rate: Double)

    /** currency → dátum szerint növekvő ráta-lista. Az USD sosem szerepel benne. */
    private var index: Map<String, List<RatePoint>> = emptyMap()

    /** PostgREST-sorok → belső index. Ismételt hívás felülírja a korábbit. */
    fun setRates(rows: List<FxRateRow>) {
        index = rows
            .filter { it.currency.isNotEmpty() && it.usdRate.isFinite() && it.usdRate > 0 }
            .groupBy({ it.currency }, { RatePoint(it.date, it.usdRate) })
            .mapValues { (_, list) -> list.sortedBy { it.date } }
    }

    /** Van-e egyáltalán betöltött árfolyam? (Hiányában natív devizás megjelenítés.) */
    fun hasRates(): Boolean = index.isNotEmpty()

    /** Mely devizákra van adatunk (az USD mindig kezelhető). */
    fun knownCurrencies(): List<String> = listOf("USD") + index.keys

    /**
     * Árfolyam adott napra, FORWARD-FILL-lel: az utolsó, a keresett napnál nem
     * későbbi érték. Az FX- és a tőzsdei naptár nem esik egybe, ezért kötelező.
     * A legelső árfolyamnál korábbi dátumra a legelsőt adjuk — így egy néhány
     * napos eltolódás nem vág le sorokat a grafikon elejéről.
     */
    fun rateAsOf(currency: String, date: String): Double? {
        val list = index[currency] ?: return null
        if (list.isEmpty()) return null
        if (date >= list.last().date) return list.last().rate
        if (date <= list.first().date) return list.first().rate

        // Bináris keresés: a legnagyobb olyan elem, aminek a dátuma <= date.
        var lo = 0
        var hi = list.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (list[mid].date <= date) lo = mid else hi = mid - 1
        }
        return list[lo].rate
    }

    /**
     * 1 egység `currency` hány USD az adott napon.
     * EZ AZ EGYETLEN HELY, ahol a GBp → GBP százados váltás megjelenik.
     */
    private fun toUsdFactor(currency: String?, date: String): Double? {
        val ccy = currency?.takeIf { it.isNotEmpty() } ?: "USD"
        if (ccy == "USD") return 1.0
        if (ccy == "GBp") {
            // Londoni penny: 1 GBp = 1/100 GBP. Az fx_rates-ben csak GBP szerepel.
            val gbp = rateAsOf("GBP", date) ?: return null
            return gbp / 100
        }
        return rateAsOf(ccy, date)
    }

    /** Az adott napi szorzó `from` → `to` irányban (null, ha valamelyik ráta hiányzik). */
    private fun factorFor(from: String?, to: String?, date: String): Double? {
        if (from == to) return 1.0
        val f = toUsdFactor(from, date) ?: return null
        val t = toUsdFactor(to, date) ?: return null
        if (t <= 0) return null
        return f / t
    }

    /** Egyetlen érték átváltása adott napi árfolyamon. Hiányzó árfolyam → null. */
    fun convertValue(value: Double, from: String?, to: String?, date: String): Double? {
        if (!value.isFinite()) return null
        val k = factorFor(from ?: "USD", to ?: "USD", date) ?: return null
        return value * k
    }

    /**
     * OHLC-sorok átváltása, naponkénti árfolyammal. Azonos devizánál ugyanaz a
     * lista jön vissza (nincs másolás); a volume nem devizafüggő. Az árfolyam
     * nélküli napok sora KIMARAD.
     */
    fun convertRows(rows: List<OhlcRow>, from: String?, to: String?): List<OhlcRow> {
        val f = from ?: "USD"
        val t = to ?: "USD"
        if (f == t || rows.isEmpty()) return rows

        val out = ArrayList<OhlcRow>(rows.size)
        for (r in rows) {
            val k = factorFor(f, t, r.date) ?: continue
            out.add(
                OhlcRow(
                    date = r.date,
                    open = r.open * k,
                    high = r.high * k,
                    low = r.low * k,
                    close = r.close * k,
                    volume = r.volume,
                )
            )
        }
        return out
    }
}
