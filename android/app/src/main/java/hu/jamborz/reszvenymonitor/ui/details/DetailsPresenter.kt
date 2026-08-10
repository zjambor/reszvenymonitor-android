package hu.jamborz.reszvenymonitor.ui.details

import hu.jamborz.reszvenymonitor.data.dto.AssetDetailsDto
import hu.jamborz.reszvenymonitor.domain.Format
import hu.jamborz.reszvenymonitor.domain.PortfolioCalc
import java.time.ZoneId

/**
 * A Részletek-panel tartalmának összeállítása — a webes js/ui.js
 * `buildAssetDetails`, `buildPortfolioComposition` és `renderAssetDetails`
 * lábléc-logikájának portja, tiszta függvényekként.
 *
 * A VEZÉRELV (a fázis feladatkiírásából): a `null`/üres mezők elegánsan
 * kimaradnak — „jobb nem mutatni vagyont, mint rosszat". Ezért nincs egyetlen
 * „—" helykitöltő sor sem: ami nincs, az nem is látszik.
 */
object DetailsPresenter {

    /** Részvény/ETF részletek blokkjai. */
    fun assetBlocks(details: AssetDetailsDto): List<DetailsBlock> {
        val blocks = mutableListOf<DetailsBlock>()
        val profile = details.profile
        val alpaca = profile?.alpaca

        // --- Fejléc-chipek ---
        val chips = buildList {
            add(if (details.isEtf) "ETF" else "Részvény")
            alpaca?.exchange?.takeIf { it.isNotBlank() }?.let { add(it) }
            alpaca?.assetClass?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (alpaca?.tradable == false) add("nem kereskedhető")
            // A profil-szimbólum csak akkor érdekes, ha ELTÉR a jegyzésitől
            // (pl. WTI2.DE profilja a US-listás WTAI-ból jön).
            details.profileSymbol
                ?.takeIf { it.isNotBlank() && it != details.symbol }
                ?.let { add("profil: $it") }
        }
        if (chips.isNotEmpty()) blocks += DetailsBlock.Chips(chips)

        // --- Profil-rács ---
        val profileRows = listOfNotNull(
            defRow("Szektor", profile?.sector),
            defRow("Iparág", profile?.industry),
            defRow("Ország", profile?.country),
            defRow(
                "Piaci kapitalizáció",
                profile?.marketCap?.takeIf { it.isFinite() }?.let {
                    "${Format.formatCompact(it)} ${profile.currency ?: "USD"}"
                },
            ),
            defRow("Béta", profile?.beta?.takeIf { it.isFinite() }?.let { Format.formatNumberHu(it, maxFrac = 2) }),
            defRow("Alkalmazottak", profile?.employees?.takeIf { it.isFinite() }?.let { Format.formatNumberHu(it) }),
            defRow("Vezérigazgató", profile?.ceo),
            defRow("Bevezetés", profile?.ipoDate?.takeIf { it.isNotBlank() }?.let { Format.formatDateHu(it) }),
        )
        if (profileRows.isNotEmpty()) blocks += DetailsBlock.DefGrid(profileRows)

        // --- Honlap + leírás ---
        profile?.website?.takeIf { it.isNotBlank() }?.let { url ->
            blocks += DetailsBlock.Website(label = url.replace(HTTP_PREFIX, ""), url = url)
        }
        profile?.description?.takeIf { it.isNotBlank() }?.let { text ->
            blocks += DetailsBlock.Section("Leírás", listOf(DetailsBlock.Paragraph(text)))
        }

        // --- ETF-összetétel ---
        if (details.isEtf) {
            val etf = details.etf
            val etfRows = listOfNotNull(
                defRow(
                    "Költséghányad (TER)",
                    etf?.expenseRatio?.takeIf { it.isFinite() }?.let { Format.formatPctPlain(it * 100) },
                ),
                defRow("Kategória", etf?.category),
                defRow("Alapkezelő", etf?.family),
                // Az AUM a szervertől MÁR FORMÁZOTTAN jön (pl. „75.68B"), és csak
                // megbízható forrásból — hiánynál a sor kimarad.
                defRow("Nettó eszközérték", etf?.netAssets),
                defRow(
                    "Fordulási ráta",
                    etf?.turnover?.takeIf { it.isFinite() }?.let { Format.formatPctPlain(it * 100) },
                ),
            )
            if (etfRows.isNotEmpty()) {
                blocks += DetailsBlock.Section("Alap-adatok", listOf(DetailsBlock.DefGrid(etfRows)))
            }

            val holdings = details.holdings.orEmpty()
            if (holdings.isNotEmpty()) {
                val maxWeight = holdings.maxOf { it.weight ?: 0.0 }
                val rows = holdings.map { h ->
                    barRow(
                        label = h.name ?: h.symbol,
                        sub = if (!h.symbol.isNullOrBlank() && !h.name.isNullOrBlank()) h.symbol else null,
                        weight = h.weight,
                        maxWeight = maxWeight,
                    )
                }
                blocks += DetailsBlock.Section(
                    "Legnagyobb pozíciók (top ${holdings.size})",
                    listOf(DetailsBlock.Bars(rows)),
                )
            }

            val sectors = details.sectorWeights.orEmpty()
            if (sectors.isNotEmpty()) {
                val maxWeight = sectors.maxOf { it.weight ?: 0.0 }
                val rows = sectors.map { s -> barRow(s.sector, null, s.weight, maxWeight) }
                blocks += DetailsBlock.Section("Szektormegoszlás", listOf(DetailsBlock.Bars(rows)))
            }
        }

        return blocks
    }

    /**
     * A modal forrás-lábléce: „Forrás: FMP + Alpaca · frissítve: … · gyorsítótárból".
     * Az ismeretlen forráskulcs (pl. `yahoo-profile`) NYERSEN jelenik meg — a
     * webes viselkedés, és őszintébb is, mint elhallgatni.
     */
    fun sourceFooter(
        details: AssetDetailsDto,
        cached: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        val sources = details.source.orEmpty()
            .split("+")
            .filter { it.isNotEmpty() }
            .map { SOURCE_NAMES[it] ?: it }
        val parts = buildList {
            if (sources.isNotEmpty()) add("Forrás: ${sources.joinToString(" + ")}")
            details.fetchedAt?.takeIf { it.isNotBlank() }
                ?.let { add("frissítve: ${Format.formatStampHu(it, zone)}") }
            if (cached) add("gyorsítótárból")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /**
     * Portfólió-összetétel — KÜLSŐ HÍVÁS NÉLKÜL, a már betöltött árakból.
     * A sáv a súlyt mutatja, az érték a piaci értéket és a súlyt együtt.
     */
    fun compositionBlocks(
        composition: PortfolioCalc.Composition,
        names: Map<String, String?>,
        currency: String,
    ): List<DetailsBlock> {
        val rows = composition.rows
        val maxWeight = rows.maxOfOrNull { it.weight ?: 0.0 } ?: 0.0
        val bars = rows.map { r ->
            val valueText = if (r.value != null) {
                "${Format.formatPriceIn(r.value, currency)} · ${Format.formatPctPlain(r.weight)}"
            } else {
                "nincs áradat"
            }
            barRow(r.ticker, names[r.ticker]?.takeIf { it.isNotBlank() }, r.weight, maxWeight)
                .copy(valueText = valueText)
        }
        val total = Format.formatPriceIn(composition.totalValue, currency)
        val foot = if (composition.pricedCount < composition.totalCount) {
            "Összérték: $total · ${composition.pricedCount}/${composition.totalCount} elem árazva"
        } else {
            "Összérték: $total · ${composition.totalCount} elem"
        }
        return listOf(DetailsBlock.Bars(bars), DetailsBlock.Foot(foot))
    }

    // -----------------------------------------------------------------------

    private val HTTP_PREFIX = Regex("^https?://")

    private val SOURCE_NAMES = mapOf("fmp" to "FMP", "yahoo" to "Yahoo", "alpaca" to "Alpaca")

    /** Üres/hiányzó érték → nincs sor (webes defRow). */
    private fun defRow(label: String, value: String?): DefRow? {
        if (value.isNullOrBlank() || value == "—") return null
        return DefRow(label, value)
    }

    /**
     * A sáv kitöltése a legnagyobb súlyhoz képest, MINIMUM 2% — hogy a
     * legkisebb tétel is látható maradjon (webes barRow).
     */
    private fun barRow(label: String?, sub: String?, weight: Double?, maxWeight: Double): BarRow {
        val fill = if (weight != null && weight.isFinite() && maxWeight > 0) {
            maxOf(2.0, weight / maxWeight * 100)
        } else {
            0.0
        }
        return BarRow(
            label = label?.takeIf { it.isNotBlank() } ?: "—",
            sub = sub,
            valueText = Format.formatPctPlain(weight),
            fillPercent = fill,
        )
    }
}
