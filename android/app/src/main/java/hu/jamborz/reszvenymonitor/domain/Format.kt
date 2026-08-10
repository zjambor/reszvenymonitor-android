package hu.jamborz.reszvenymonitor.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A webes format.js portja — hu-HU formázók: ár, előjeles ár, százalék,
 * volumen (E/M/Mrd), dátum. A hu-HU csoportosító a NEM TÖRŐ szóköz (U+00A0),
 * a tizedesjel a vessző — a böngésző Intl-je és a JVM CLDR-adata egyezik.
 */
object Format {

    private val HU = Locale("hu", "HU")

    /**
     * Devizajel-utótagok. FIGYELEM: a 'GBp' (kis p) a londoni penny-jegyzés —
     * NEM azonos a fonttal ('GBP'); a Yahoo így különbözteti meg őket.
     */
    private val CURRENCY_SUFFIX = mapOf("USD" to " $", "EUR" to " €", "GBP" to " £", "GBp" to " p")

    private fun currencySuffix(currency: String?): String {
        if (currency.isNullOrEmpty()) return CURRENCY_SUFFIX.getValue("USD")
        return CURRENCY_SUFFIX[currency] ?: " $currency"
    }

    /**
     * A magyar CLDR `minimumGroupingDigits` értéke 2: az ezres elválasztó csak
     * akkor jelenik meg, ha az egészrész LEGALÁBB ÖT jegyű (1464,51 — de
     * 12 564,73). A böngésző és a Node ICU-ja így viselkedik, a JDK
     * NumberFormat viszont már négy jegynél csoportosít, ezért kézzel
     * kapcsoljuk. Mérve (webes kereszt-ellenőrzés): enélkül az „Időszaki
     * változás" +1464,51% helyett +1 464,51%-ként jelenne meg.
     */
    private const val GROUPING_FROM = 10_000.0

    private fun useGrouping(v: Double, maxFrac: Int): Boolean {
        // A döntés a KEREKÍTETT értéken dől el (9999,995 → 10 000,00 már csoportosít).
        val rounded = BigDecimal(v).setScale(maxFrac, RoundingMode.HALF_UP).abs()
        return rounded >= BigDecimal(GROUPING_FROM)
    }

    /** hu-HU számformázó; a webes Intl halfExpand kerekítését a HALF_UP tükrözi. */
    private fun huNumber(minFrac: Int, maxFrac: Int, value: Double): NumberFormat =
        NumberFormat.getNumberInstance(HU).apply {
            minimumFractionDigits = minFrac
            maximumFractionDigits = maxFrac
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = useGrouping(value, maxFrac)
        }

    /** Ár tetszőleges devizában, 2 tizedessel, hu-HU számformátummal. */
    fun formatPriceIn(v: Double?, currency: String?): String {
        if (v == null || !v.isFinite()) return "—"
        return huNumber(2, 2, v).format(v) + currencySuffix(currency)
    }

    /** Előjeles árkülönbség tetszőleges devizában (pl. "+12,34 $"). */
    fun formatSignedIn(v: Double?, currency: String?): String {
        if (v == null || !v.isFinite()) return "—"
        val sign = if (v > 0) "+" else ""
        return sign + huNumber(2, 2, v).format(v) + currencySuffix(currency)
    }

    /** Ár USD-ben (kompatibilitási wrapper). */
    fun formatPrice(v: Double?): String = formatPriceIn(v, "USD")

    /** Előjeles árkülönbség USD-ben (kompatibilitási wrapper). */
    fun formatSigned(v: Double?): String = formatSignedIn(v, "USD")

    /** Előjeles százalék (pl. "+1,23%"). */
    fun formatPct(v: Double?): String {
        if (v == null || !v.isFinite()) return "—"
        val sign = if (v > 0) "+" else ""
        return sign + huNumber(2, 2, v).format(v) + "%"
    }

    /** Volumen rövidítve: E (ezer), M (millió), Mrd (milliárd). */
    fun formatVolume(v: Double?): String {
        if (v == null || !v.isFinite()) return "—"
        val abs = kotlin.math.abs(v)
        return when {
            abs >= 1e9 -> huNumber(0, 2, v / 1e9).format(v / 1e9) + " Mrd"
            abs >= 1e6 -> huNumber(0, 2, v / 1e6).format(v / 1e6) + " M"
            abs >= 1e3 -> huNumber(0, 1, v / 1e3).format(v / 1e3) + " E"
            else -> {
                val rounded = Math.round(v)
                huNumber(0, 0, rounded.toDouble()).format(rounded)
            }
        }
    }

    /** ISO dátum (YYYY-MM-DD) → magyar rövid dátum (pl. "2026. júl. 10."). */
    fun formatDateHu(iso: String?): String {
        if (iso.isNullOrEmpty()) return "—"
        return try {
            LocalDate.parse(iso).format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(HU)
            )
        } catch (e: Exception) {
            iso
        }
    }

    // -----------------------------------------------------------------------
    // Részletek-panel formázói (a webes js/ui.js pctPlain / compactNum /
    // formatStampHu párjai). Mindegyik a Node-ban MÉRT ICU-kimenethez igazodik
    // — lásd tools/xcheck-details.mjs és DetailsCrossCheckTest.
    // -----------------------------------------------------------------------

    /** Előjel NÉLKÜLI százalék (súlyok, TER) — pl. "5,23%". */
    fun formatPctPlain(v: Double?, digits: Int = 2): String {
        if (v == null || !v.isFinite()) return "—"
        return huNumber(digits, digits, v).format(v) + "%"
    }

    /**
     * Szám a JS `toLocaleString(hu-HU)` alapbeállításaival: 0–3 tizedes,
     * csoportosítás a magyar szabály szerint (9500 → "9500", 12345 → "12 345").
     */
    fun formatNumberHu(v: Double?, maxFrac: Int = 3): String {
        if (v == null || !v.isFinite()) return "—"
        return huNumber(0, maxFrac, v).format(v)
    }

    /**
     * Tömör nagyságrend (piaci kapitalizáció / AUM): "5,31 B", "657 M".
     *
     * A magyar CLDR compact-rövidítései: E (ezer), M (millió), Mrd (milliárd),
     * B (billió); az elválasztó NEM TÖRŐ SZÓKÖZ (a `formatVolume` sima szóközétől
     * eltérően — az másik formázó, a webes format.js-ben is más az alakja).
     * A kerekítés utáni ELŐLÉPTETÉS is az ICU-t követi: 999 999 → "1 M".
     */
    fun formatCompact(v: Double?): String {
        if (v == null || !v.isFinite()) return "—"
        var index = COMPACT_UNITS.indexOfLast { kotlin.math.abs(v) >= it.first }
        if (index < 0) index = 0
        // A mantissza kerekítés UTÁN léphet a következő nagyságrendbe.
        while (index < COMPACT_UNITS.size - 1 &&
            kotlin.math.abs(roundTo(v / COMPACT_UNITS[index].first, 2)) >= 1000.0
        ) {
            index++
        }
        val (divisor, suffix) = COMPACT_UNITS[index]
        val mantissa = v / divisor
        return huNumber(0, 2, mantissa).format(mantissa) + suffix
    }

    /**
     * ISO időbélyeg → magyar dátum+idő (pl. "2026. aug. 6. 13:03"), a megadott
     * időzónában. Az ÓRA MINDIG KÉTJEGYŰ: a JS `hour: '2-digit'` kérése így
     * viselkedik, a JVM lokalizált SHORT időformátuma viszont "9:03"-at adna.
     */
    fun formatStampHu(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (iso.isNullOrEmpty()) return "—"
        return try {
            OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(STAMP_FORMAT)
        } catch (e: DateTimeParseException) {
            iso
        }
    }

    /**
     * Nagyságrend-osztók a magyar compact-rövidítésekkel (növekvő sorrendben).
     * Az elválasztó NEM TÖRŐ SZÓKÖZ (U+00A0) — mérve a Node ICU-kimenetén; a
     * `formatVolume` ezzel szemben sima szóközt használ (az másik formázó, a
     * webes format.js-ben is így van).
     */
    private const val NBSP = "\u00A0"

    private val COMPACT_UNITS = listOf(
        1.0 to "",
        1e3 to "${NBSP}E",
        1e6 to "${NBSP}M",
        1e9 to "${NBSP}Mrd",
        1e12 to "${NBSP}B",
    )

    private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy. MMM d. HH:mm", HU)

    private fun roundTo(v: Double, digits: Int): Double =
        BigDecimal(v).setScale(digits, RoundingMode.HALF_UP).toDouble()
}
