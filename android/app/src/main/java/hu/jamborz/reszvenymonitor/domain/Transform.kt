package hu.jamborz.reszvenymonitor.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * A webes transform.js portja: dátumsegédek (csak UTC), heti/havi OHLC-aggregáció,
 * preset-időablakok, statisztika. A függvénynevek szándékosan egyeznek a webes
 * forrással, hogy oda-vissza kereshetők legyenek.
 */
object Transform {

    /** Felbontás — a webes 'daily' | 'weekly' | 'monthly' megfelelője. */
    enum class Resolution { DAILY, WEEKLY, MONTHLY }

    /** Preset-időablakok — a webes PRESETS kulcsok megfelelői. */
    enum class Preset(val key: String) {
        HET1("1HET"), M1("1M"), M3("3M"), M6("6M"), YTD("YTD"), EV1("1EV"), MIND("MIND");
    }

    data class DateRange(val from: String, val to: String)

    data class Stats(
        val lastClose: Double,
        val dayChange: Double?,
        val dayChangePct: Double?,
        val periodChangePct: Double?,
        val periodHigh: Double,
        val periodLow: Double,
        val avgVolume: Double,
    )

    /** A mai nap ISO dátuma (UTC szerint). */
    fun todayISO(): String = LocalDate.now(ZoneOffset.UTC).toString()

    /**
     * ISO dátum eltolása visszafelé nappal/hónappal/évvel — a webes shiftDate
     * JS-Date-szemantikájával: hónap-/évlépésnél a hónapvégi túlcsordulás
     * ÁTGÖRDÜL a következő hónapba (2023-03-31 −1 hónap → „febr. 31." → 2023-03-03),
     * nem clampelődik, mint a java.time minusMonths. A sorrend is a webé:
     * előbb nap, aztán hónap, végül év.
     */
    fun shiftDate(dateStr: String, days: Int = 0, months: Int = 0, years: Int = 0): String {
        var d = LocalDate.parse(dateStr)
        if (days != 0) d = d.minusDays(days.toLong())
        if (months != 0) d = jsShiftMonths(d, -months)
        if (years != 0) d = jsShiftYears(d, -years)
        return d.toString()
    }

    private fun jsShiftMonths(d: LocalDate, delta: Int): LocalDate {
        val total = d.year * 12 + (d.monthValue - 1) + delta
        val y = Math.floorDiv(total, 12)
        val m = Math.floorMod(total, 12) + 1
        // A napot a hónap elsejéhez adjuk hozzá — így a túlcsordulás átgördül (JS-viselkedés).
        return LocalDate.of(y, m, 1).plusDays((d.dayOfMonth - 1).toLong())
    }

    private fun jsShiftYears(d: LocalDate, delta: Int): LocalDate =
        LocalDate.of(d.year + delta, d.monthValue, 1).plusDays((d.dayOfMonth - 1).toLong())

    /** A dátumot tartalmazó (ISO-)hét hétfőjének dátuma — heti aggregációs kulcs. */
    fun mondayKey(dateStr: String): String =
        LocalDate.parse(dateStr)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toString()

    /** Havi aggregációs kulcs: "YYYY-MM". */
    fun monthKey(dateStr: String): String = dateStr.substring(0, 7)

    /** Két ISO dátum közötti napok száma (to − from). */
    fun daysBetween(fromISO: String, toISO: String): Int =
        ChronoUnit.DAYS.between(LocalDate.parse(fromISO), LocalDate.parse(toISO)).toInt()

    /**
     * Napi sorok → napi/heti/havi OHLC-barok. A bemenet dátum szerint növekvő;
     * a bar dátuma az időszak ELSŐ kereskedési napja, open = első nyitó,
     * close = utolsó záró, high/low = időszaki szélsőérték, volume = összeg.
     */
    fun aggregate(daily: List<OhlcRow>, mode: Resolution): List<OhlcRow> {
        if (mode == Resolution.DAILY) return daily.toList()
        val keyFn: (String) -> String =
            if (mode == Resolution.WEEKLY) ::mondayKey else ::monthKey
        val bars = mutableListOf<OhlcRow>()
        var currentKey: String? = null
        var bar: OhlcRow? = null
        for (row in daily) {
            val key = keyFn(row.date)
            val b = bar
            if (key != currentKey || b == null) {
                b?.let(bars::add)
                currentKey = key
                bar = row.copy()
            } else {
                bar = b.copy(
                    high = maxOf(b.high, row.high),
                    low = minOf(b.low, row.low),
                    close = row.close,
                    volume = b.volume + row.volume,
                )
            }
        }
        bar?.let(bars::add)
        return bars
    }

    /**
     * Preset → {from, to} ISO dátumablak a napi adatsor alapján;
     * a from az első elérhető adatnapra clampelve. Üres sorra null.
     */
    fun presetRange(daily: List<OhlcRow>, preset: Preset): DateRange? {
        if (daily.isEmpty()) return null
        val first = daily.first().date
        val last = daily.last().date
        var from = when (preset) {
            Preset.HET1 -> shiftDate(last, days = 7)
            Preset.M1 -> shiftDate(last, months = 1)
            Preset.M3 -> shiftDate(last, months = 3)
            Preset.M6 -> shiftDate(last, months = 6)
            Preset.YTD -> "${last.substring(0, 4)}-01-01"
            Preset.EV1 -> shiftDate(last, years = 1)
            Preset.MIND -> first
        }
        if (from < first) from = first
        return DateRange(from, last)
    }

    /**
     * Statisztikák — mindig a NAPI adatokból, a [from, to] ablakra: utolsó záró,
     * napi változás (érték, %), időszaki változás (%), időszak max/min, átlagvolumen.
     * A napi változás a TELJES sor utolsó két napjából számol (mint a weben);
     * üresre szűrt ablaknál a teljes sor a tartalék. Nullosztás ellen védve.
     */
    fun computeStats(daily: List<OhlcRow>, from: String, to: String): Stats? {
        if (daily.isEmpty()) return null
        val windowRows = daily.filter { it.date >= from && it.date <= to }
        val rows = windowRows.ifEmpty { daily }

        val last = daily.last()
        val prev = if (daily.size > 1) daily[daily.size - 2] else null
        val dayChange = prev?.let { last.close - it.close }
        val dayChangePct = prev?.takeIf { it.close != 0.0 }
            ?.let { (last.close - it.close) / it.close * 100 }

        val firstRow = rows.first()
        val periodChangePct = firstRow.close.takeIf { it != 0.0 }
            ?.let { (last.close - it) / it * 100 }

        var high = Double.NEGATIVE_INFINITY
        var low = Double.POSITIVE_INFINITY
        var volSum = 0.0
        for (r in rows) {
            if (r.high > high) high = r.high
            if (r.low < low) low = r.low
            volSum += r.volume
        }

        return Stats(
            lastClose = last.close,
            dayChange = dayChange,
            dayChangePct = dayChangePct,
            periodChangePct = periodChangePct,
            periodHigh = high,
            periodLow = low,
            avgVolume = volSum / rows.size,
        )
    }
}
