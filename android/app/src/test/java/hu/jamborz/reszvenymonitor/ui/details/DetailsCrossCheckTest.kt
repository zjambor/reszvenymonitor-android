package hu.jamborz.reszvenymonitor.ui.details

import hu.jamborz.reszvenymonitor.data.dto.AssetDetailsDto
import hu.jamborz.reszvenymonitor.data.dto.AssetDetailsResponseDto
import hu.jamborz.reszvenymonitor.domain.Fixtures
import hu.jamborz.reszvenymonitor.domain.Format
import hu.jamborz.reszvenymonitor.domain.FxConverter
import hu.jamborz.reszvenymonitor.domain.OhlcRow
import hu.jamborz.reszvenymonitor.domain.PortfolioCalc
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Részletek-panel tartalmának KERESZT-ELLENŐRZÉSE a web-appal.
 *
 * A várt szövegeket a `tools/xcheck-details.mjs` állítja elő úgy, hogy a web-app
 * EREDETI js/ui.js modal-építőit (`buildAssetDetails`, `buildPortfolioComposition`
 * és segédeik: pctPlain, compactNum, formatStampHu, defRow, barRow) futtatja
 * Node-ban, egy minimális DOM-csonkkal — a forrásszöveg onnan olvasódik ki,
 * nincs újragépelt másolat. Itt UGYANAZOKRA a bemenetekre a [DetailsPresenter]
 * fut, és a kimenetnek karakterre azonosnak kell lennie.
 *
 * MIT FOG MEG (a puszta számformátumon túl): MELYIK SOR MARAD KI. A VWCE.DE-nél
 * van „Nettó eszközérték" sor, az SXR8.DE-nél nincs — a szerver ott nem ad
 * megbízható AUM-ot, és a terv szerint inkább nem mutatunk vagyont, mint rosszat.
 */
class DetailsCrossCheckTest {

    // --- A generált várt értékek --------------------------------------------

    @Serializable
    private data class Expected(
        val timeZone: String = "UTC",
        val assetCases: List<AssetCase>,
        val stampCases: List<StampCase>,
        val compositionCases: List<CompositionCase>,
    )

    @Serializable
    private data class AssetCase(
        val fixture: String,
        val symbol: String,
        val cached: Boolean = false,
        /** Szintetikus eseteknél a BEMENET is itt van — így nincs kettőzött adat. */
        val input: JsonElement? = null,
        val lines: List<String>,
        val footer: String? = null,
    )

    @Serializable private data class StampCase(val iso: String, val text: String)

    @Serializable
    private data class CompositionCase(
        val portfolioId: String,
        val portfolioName: String,
        val displayCurrency: String,
        val lines: List<String>,
    )

    // --- A bemenetek (ugyanazok a fixture-ök, amikből a web dolgozott) -------

    @Serializable
    private data class PortfolioFixture(
        val id: String,
        val name: String,
        val portfolio_items: List<ItemFixture> = emptyList(),
    )

    @Serializable private data class ItemFixture(val ticker: String, val quantity: Double)

    @Serializable
    private data class TickerFixture(
        val symbol: String,
        val name: String? = null,
        val currency: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun text(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "Hiányzó fixture: $name — futtasd: node tools/xcheck-details.mjs"
        }.bufferedReader().readText().removePrefix("﻿")

    private val expected: Expected by lazy { json.decodeFromString(text("xcheck_details_expected.json")) }

    /** A tool ebben a zónában generált; a JVM alapzónája gépenként más lehet. */
    private val zone: ZoneId by lazy { ZoneId.of(expected.timeZone) }

    private fun detailsOf(case: AssetCase): AssetDetailsDto =
        case.input?.let { json.decodeFromJsonElement(AssetDetailsDto.serializer(), it) }
            ?: checkNotNull(json.decodeFromString<AssetDetailsResponseDto>(text(case.fixture)).details) {
                "Hiányzó details: ${case.fixture}"
            }

    // -----------------------------------------------------------------------
    // Profil / ETF
    // -----------------------------------------------------------------------

    @Test
    fun `profil-blokkok karakterre egyeznek a webes epitovel`() {
        assertTrue("Négy valós + három szintetikus esetet várunk", expected.assetCases.size >= 7)

        for (case in expected.assetCases) {
            val blocks = DetailsPresenter.assetBlocks(detailsOf(case))
            assertEquals("Blokkok eltérése: ${case.fixture}", stripFills(case.lines), renderLines(blocks))
            assertArrayEquals("Sávszélességek eltérése: ${case.fixture}", expectedFills(case.lines), fillsOf(blocks), 1e-9)
        }
    }

    /**
     * A terv 9. fázisának két nevesített próbája, kiemelve: a VWCE.DE AUM-ja
     * megjelenik (és a webes értékkel egyezik), a hiányzó AUM-ú ETF-nél viszont
     * a sor egyszerűen nincs ott.
     */
    @Test
    fun `AUM-sor csak megbizhato adatnal jelenik meg`() {
        val vwce = renderLines(DetailsPresenter.assetBlocks(detailsOf(caseFor("details_vwce_de.json"))))
        assertTrue(
            "A VWCE.DE-nél kell AUM-sor: $vwce",
            vwce.contains("DEF: Nettó eszközérték = 75.68B"),
        )

        val sxr8 = renderLines(DetailsPresenter.assetBlocks(detailsOf(caseFor("details_sxr8_de.json"))))
        assertTrue(
            "Az SXR8.DE-nél NEM lehet AUM-sor: $sxr8",
            sxr8.none { it.startsWith("DEF: Nettó eszközérték") },
        )
        // …de a többi alap-adat ott van, tehát nem az egész szekció esett ki.
        assertTrue("Az alap-adatok szekció megmarad", sxr8.contains("DEF: Költséghányad (TER) = 0,07%"))
    }

    @Test
    fun `forras-lablec karakterre egyezik (nevleszogezes, idobelyeg, cache-jelzes)`() {
        for (case in expected.assetCases) {
            assertEquals(
                "Lábléc eltérése: ${case.fixture}",
                case.footer,
                DetailsPresenter.sourceFooter(detailsOf(case), case.cached, zone),
            )
        }
    }

    @Test
    fun `idobelyeg-formazas egyezik (az ora ketjegyu)`() {
        assertTrue(expected.stampCases.isNotEmpty())
        for (case in expected.stampCases) {
            assertEquals(case.iso, case.text, Format.formatStampHu(case.iso, zone))
        }
    }

    // -----------------------------------------------------------------------
    // Portfólió-összetétel
    // -----------------------------------------------------------------------

    @Test
    fun `portfolio-osszetetel karakterre egyezik mindharom devizaban`() {
        val portfolios = json.decodeFromString<List<PortfolioFixture>>(text("xcheck_portfolios.json"))
        val tickers = json.decodeFromString<List<TickerFixture>>(text("xcheck_tickers.json"))
        val prices = json.decodeFromString<Map<String, List<Fixtures.PriceRow>>>(text("xcheck_pf_prices.json"))
            .mapValues { (_, rows) -> rows.map { OhlcRow(it.date, it.open, it.high, it.low, it.close, it.volume) } }
        val fx = FxConverter().apply { setRates(Fixtures.fxRows("xcheck_fx.json")) }
        val currencyOf = tickers.associate { it.symbol to (it.currency ?: "USD") }
        val names = tickers.associate { it.symbol to it.name }

        assertTrue("18 esetet várunk (6 portfólió × 3 deviza)", expected.compositionCases.size >= 18)

        for (case in expected.compositionCases) {
            val portfolio = portfolios.first { it.id == case.portfolioId }
            val label = "${case.portfolioName}/${case.displayCurrency}"

            // A webes openPortfolioDetails sorrendje: NYERS sorok → átváltás a
            // megjelenítési devizára → összetétel. Itt nincs közös tengely és
            // előre-töltés (az a chart-idősoré) — minden tag a saját utolsó
            // zárójával szerepel.
            val convertedMap = portfolio.portfolio_items.associate { item ->
                val rows: List<OhlcRow> = prices[item.ticker].orEmpty()
                item.ticker to fx.convertRows(rows, currencyOf[item.ticker] ?: "USD", case.displayCurrency)
            }
            val composition = PortfolioCalc.computeComposition(
                portfolio.portfolio_items.map { PortfolioCalc.Item(it.ticker, it.quantity) },
                convertedMap,
            )
            val blocks = DetailsPresenter.compositionBlocks(composition, names, case.displayCurrency)

            assertEquals("Összetétel eltérése: $label", stripFills(case.lines), renderLines(blocks))
            assertArrayEquals("Sávszélességek eltérése: $label", expectedFills(case.lines), fillsOf(blocks), 1e-9)
        }
    }

    // -----------------------------------------------------------------------
    // A blokkok ugyanabba a sor-alakba lapítása, amit a Node-eszköz készít
    // -----------------------------------------------------------------------

    private fun renderLines(blocks: List<DetailsBlock>): List<String> {
        val out = mutableListOf<String>()
        for (block in blocks) {
            when (block) {
                is DetailsBlock.Chips -> out += "CHIPS: ${block.items.joinToString(" | ")}"
                is DetailsBlock.DefGrid -> block.rows.forEach { out += "DEF: ${it.label} = ${it.value}" }
                is DetailsBlock.Website -> out += "WEB: ${block.label} -> ${block.url}"
                is DetailsBlock.Paragraph -> out += "DESC: ${block.text}"
                is DetailsBlock.Section -> {
                    out += "SECTION: ${block.title}"
                    out += renderLines(block.blocks)
                }
                is DetailsBlock.Bars -> block.rows.forEach { row ->
                    out += "BAR: ${row.label}${row.sub?.let { " ($it)" }.orEmpty()} = ${row.valueText}"
                }
                is DetailsBlock.Foot -> out += "FOOT: ${block.text}"
            }
        }
        return out
    }

    /**
     * A sávszélességet KÜLÖN, számként vetjük össze: a web a nyers százalékot
     * írja a CSS-be, és a JS/JVM lebegőpontos SZÖVEGES alakja eltérhet — a
     * SZÁM viszont bitre azonos kell legyen.
     */
    private fun fillsOf(blocks: List<DetailsBlock>): DoubleArray {
        val out = mutableListOf<Double>()
        fun walk(list: List<DetailsBlock>) {
            for (b in list) when (b) {
                is DetailsBlock.Bars -> b.rows.forEach { out += it.fillPercent }
                is DetailsBlock.Section -> walk(b.blocks)
                else -> Unit
            }
        }
        walk(blocks)
        return out.toDoubleArray()
    }

    private fun expectedFills(lines: List<String>): DoubleArray =
        lines.filter { it.startsWith("BAR: ") }
            .map { it.substringAfterLast(" [").removeSuffix("%]").toDouble() }
            .toDoubleArray()

    private fun stripFills(lines: List<String>): List<String> =
        lines.map { if (it.startsWith("BAR: ")) it.substringBeforeLast(" [") else it }

    private fun caseFor(fixture: String): AssetCase =
        expected.assetCases.first { it.fixture == fixture }
}
