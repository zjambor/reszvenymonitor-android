package hu.jamborz.reszvenymonitor.domain

/**
 * A webes ui.js elgépelés-javaslatának tiszta magja: Levenshtein-távolság és a
 * „Talán erre gondoltál" jelöltszűrés. A `SKR8` → `SXR8.DE` eset motiválta:
 * ilyenkor a substring-szűrő és a tőzsdei kereső is joggal üres — a tőzsdei
 * utótag nélküli alakot (SXR8.DE → „SXR8") is nézzük.
 */
object Suggest {

    /** Ticker-szimbólumnak kinéző beírás (pl. SNOW, BRK.B) — új felvételhez. */
    val SYMBOL_LIKE = Regex("^[A-Za-z][A-Za-z0-9.\\-]{0,9}$")

    /** ISIN: 2 betű országkód + 9 alfanumerikus + 1 ellenőrző számjegy. */
    val ISIN_LIKE = Regex("^[A-Z]{2}[A-Z0-9]{9}[0-9]$")

    /**
     * Levenshtein-távolság (beszúrás/törlés/csere). Néhány tucat tickerhez a
     * naiv DP bőven elég; két sorral dolgozik, nem teljes mátrixszal.
     */
    fun editDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        var prev = IntArray(n + 1) { it }
        for (i in 1..m) {
            val cur = IntArray(n + 1)
            cur[0] = i
            for (j in 1..n) {
                cur[j] = minOf(
                    prev[j] + 1, // törlés
                    cur[j - 1] + 1, // beszúrás
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1, // csere
                )
            }
            prev = cur
        }
        return prev[n]
    }

    /**
     * Közeli szimbólumok a MÁR FELVETT tickerek közül. Rövid (≤4) beírásnál egy
     * elütés fér bele, hosszabbnál kettő — ennél többre már túl sok hamis találat
     * jönne. Távolság szerint növekvő sorrend, legfeljebb [limit] elem.
     */
    fun nearSymbols(query: String, symbols: List<String>, limit: Int = 5): List<String> {
        val q = query.trim().uppercase()
        if (q.length < 3) return emptyList()
        val maxDist = if (q.length <= 4) 1 else 2

        return symbols
            .filter { it.isNotEmpty() }
            .map { sym ->
                val upper = sym.uppercase()
                val root = upper.substringBefore('.')
                sym to minOf(editDistance(q, upper), editDistance(q, root))
            }
            .filter { (_, d) -> d <= maxDist }
            .sortedBy { it.second } // stabil rendezés — azonos távolságnál az eredeti sorrend marad
            .take(limit)
            .map { it.first }
    }
}
