package hu.jamborz.reszvenymonitor.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A 8. fázis ellenőrzése: a portfólió-összérték és a P/L **számjegyre egyezik**
 * a web-appal, mindhárom megjelenítési devizában.
 *
 * A várt értékeket a web-app EREDETI js/portfolio.js + js/fx.js moduljai
 * állítják elő Node-ban (`node tools/xcheck-portfolio.mjs`), valós portfóliókra
 * és valós tagsorokra. Ez a teszt UGYANAZT a láncot futtatja a domain-porttal,
 * a MonitorViewModel.recomputePortfolio sorrendjével:
 *
 *   közös tengely → előre-töltés NATÍV devizában → átváltás → összegzés
 *
 * A sorrend nem stílus kérdése (5. invariáns): fordítva egy elavult árú tag a
 * régi napi árfolyamon ragadna, és a súlyok devizafüggővé válnának.
 */
class PortfolioCrossCheckTest {

    // --- A generált várt értékek --------------------------------------------

    @Serializable private data class Expected(val cases: List<Case>)

    @Serializable
    private data class Case(
        val portfolioId: String,
        val portfolioName: String,
        val displayCurrency: String,
        val itemCount: Int,
        val seriesLength: Int,
        val firstDate: String? = null,
        val lastDate: String? = null,
        val totalValue: Double,
        val periodChangePct: Double? = null,
        val periodHigh: Double,
        val periodLow: Double,
        val pnl: ExpectedPnl,
        val formatted: ExpectedFormatted,
        val composition: ExpectedComposition,
    )

    @Serializable
    private data class ExpectedPnl(
        val pnl: Double? = null,
        val pnlPct: Double? = null,
        val costedCount: Int,
        val totalCount: Int,
    )

    @Serializable private data class ExpectedFormatted(val totalValue: String, val pnl: String)

    @Serializable
    private data class ExpectedComposition(
        val totalValue: Double,
        val pricedCount: Int,
        val rows: List<ExpectedCompositionRow>,
    )

    @Serializable
    private data class ExpectedCompositionRow(
        val ticker: String,
        val value: Double? = null,
        val weight: Double? = null,
    )

    // --- A bemenetek (ugyanazok a fixture-ök, amikből a web dolgozott) -------

    @Serializable
    private data class PortfolioFixture(
        val id: String,
        val name: String,
        val portfolio_items: List<ItemFixture> = emptyList(),
    )

    @Serializable
    private data class ItemFixture(
        val ticker: String,
        val quantity: Double,
        val purchase_price: Double? = null,
        val purchase_date: String? = null,
    )

    @Serializable
    private data class TickerFixture(val symbol: String, val currency: String? = null)

    private val json = Json { ignoreUnknownKeys = true }

    private fun text(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "Hiányzó fixture: $name — futtasd: node tools/xcheck-portfolio.mjs"
        }.bufferedReader().readText().removePrefix("﻿")

    private val expected: Expected by lazy { json.decodeFromString(text("xcheck_portfolio_expected.json")) }
    private val portfolios: List<PortfolioFixture> by lazy { json.decodeFromString(text("xcheck_portfolios.json")) }
    private val currencyOf: Map<String, String> by lazy {
        json.decodeFromString<List<TickerFixture>>(text("xcheck_tickers.json"))
            .associate { it.symbol to (it.currency ?: "USD") }
    }
    private val pricesOf: Map<String, List<OhlcRow>> by lazy {
        json.decodeFromString<Map<String, List<Fixtures.PriceRow>>>(text("xcheck_pf_prices.json"))
            .mapValues { (_, rows) -> rows.map { OhlcRow(it.date, it.open, it.high, it.low, it.close, it.volume) } }
    }
    private val fx: FxConverter by lazy {
        FxConverter().apply { setRates(Fixtures.fxRows("xcheck_fx.json")) }
    }

    /** A ViewModel számítási lánca, tisztán domain-hívásokból. */
    private data class Computed(
        val series: List<OhlcRow>,
        val pnl: PortfolioCalc.PnlResult,
        val composition: PortfolioCalc.Composition,
    )

    private fun compute(pf: PortfolioFixture, display: String): Computed {
        val members = pf.portfolio_items.filter { pricesOf[it.ticker]?.isNotEmpty() == true }
        val axis = PortfolioCalc.commonAxis(members.map { pricesOf.getValue(it.ticker) })

        // A SORREND: fillForward NATÍV devizában, és csak utána convertRows.
        val dailyMap = members.associate { item ->
            val from = currencyOf[item.ticker] ?: "USD"
            val filled = PortfolioCalc.fillForward(pricesOf.getValue(item.ticker), axis)
            item.ticker to fx.convertRows(filled, from, display)
        }

        val calcItems = members.map { PortfolioCalc.Item(it.ticker, it.quantity, it.purchase_price) }
        val costed = members.map { item ->
            val from = currencyOf[item.ticker] ?: "USD"
            val price = item.purchase_price?.let { p ->
                if (from == display) p
                else fx.convertValue(p, from, display, item.purchase_date ?: Transform.todayISO())
            }
            PortfolioCalc.Item(item.ticker, item.quantity, price)
        }

        return Computed(
            series = PortfolioCalc.buildPortfolioSeries(calcItems, dailyMap),
            pnl = PortfolioCalc.computePnL(costed, dailyMap),
            composition = PortfolioCalc.computeComposition(calcItems, dailyMap),
        )
    }

    private fun assertClose(label: String, expectedValue: Double?, actual: Double?) {
        if (expectedValue == null) {
            assertTrue("$label: a web null-t ad, az Android $actual-t", actual == null)
            return
        }
        checkNotNull(actual) { "$label: a web $expectedValue-t ad, az Android null-t" }
        assertEquals(label, expectedValue, actual, kotlin.math.abs(expectedValue) * 1e-12 + 1e-12)
    }

    @Test
    fun `portfolio-osszertek es P per L szamjegyre egyezik a webbel mindharom devizaban`() {
        assertTrue("Legalább 3 portfólió × 3 deviza esetet várunk", expected.cases.size >= 9)

        for (c in expected.cases) {
            val pf = portfolios.first { it.id == c.portfolioId }
            val label = "${c.portfolioName}/${c.displayCurrency}"
            val computed = compute(pf, c.displayCurrency)

            assertEquals("$label: idősor hossza", c.seriesLength, computed.series.size)
            assertEquals("$label: első nap", c.firstDate, computed.series.firstOrNull()?.date)
            assertEquals("$label: utolsó nap", c.lastDate, computed.series.lastOrNull()?.date)

            val range = checkNotNull(Transform.presetRange(computed.series, Transform.Preset.MIND))
            val stats = checkNotNull(Transform.computeStats(computed.series, range.from, range.to))
            assertClose("$label: összérték", c.totalValue, stats.lastClose)
            assertClose("$label: időszaki változás", c.periodChangePct, stats.periodChangePct)
            assertClose("$label: időszak max", c.periodHigh, stats.periodHigh)
            assertClose("$label: időszak min", c.periodLow, stats.periodLow)

            assertEquals("$label: költséges elemek", c.pnl.costedCount, computed.pnl.costedCount)
            assertEquals("$label: összes elem", c.pnl.totalCount, computed.pnl.totalCount)
            assertClose("$label: P/L", c.pnl.pnl, computed.pnl.pnl)
            assertClose("$label: P/L %", c.pnl.pnlPct, computed.pnl.pnlPct)

            // A formázott szöveg is karakterre egyezzen (ez kerül a stat-kártyára).
            assertEquals(
                "$label: összérték formázva",
                c.formatted.totalValue,
                Format.formatPriceIn(stats.lastClose, c.displayCurrency),
            )
            val pnlText = computed.pnl.pnl?.let { p ->
                "${Format.formatSignedIn(p, c.displayCurrency)} (${Format.formatPct(computed.pnl.pnlPct)})"
            } ?: "—"
            assertEquals("$label: P/L formázva", c.formatted.pnl, pnlText)
        }
    }

    @Test
    fun `portfolio-osszetetel (suly es ertek) egyezik a webbel`() {
        for (c in expected.cases) {
            val pf = portfolios.first { it.id == c.portfolioId }
            val label = "${c.portfolioName}/${c.displayCurrency}"
            val comp = compute(pf, c.displayCurrency).composition

            assertClose("$label: összetétel-összérték", c.composition.totalValue, comp.totalValue)
            assertEquals("$label: árazott tagok", c.composition.pricedCount, comp.pricedCount)
            assertEquals(
                "$label: sorrend (érték szerint csökkenő)",
                c.composition.rows.map { it.ticker },
                comp.rows.map { it.ticker },
            )
            for ((exp, act) in c.composition.rows.zip(comp.rows)) {
                assertClose("$label/${exp.ticker}: érték", exp.value, act.value)
                assertClose("$label/${exp.ticker}: súly", exp.weight, act.weight)
            }
        }
    }

    /**
     * A súlyok devizafüggetlensége VALÓS portfóliókon: ugyanaz a tag ugyanakkora
     * arányt képvisel USD-ben, EUR-ban és HUF-ban is. Ez a helyes sorrend
     * következménye — ezért itt a teljes láncot ellenőrizzük, nem szintetikus adaton.
     */
    @Test
    fun `a sulyok devizafuggetlenek minden valos portfolion`() {
        val byPortfolio = expected.cases.groupBy { it.portfolioId }
        assertTrue(byPortfolio.isNotEmpty())

        for ((id, cases) in byPortfolio) {
            val pf = portfolios.first { it.id == id }
            val weightsPerCurrency = cases.associate { c ->
                c.displayCurrency to compute(pf, c.displayCurrency).composition.rows
                    .associate { it.ticker to it.weight }
            }
            val reference = weightsPerCurrency.getValue("USD")
            for ((currency, weights) in weightsPerCurrency) {
                for ((ticker, weight) in weights) {
                    assertClose("${pf.name}/$currency/$ticker: súly", reference[ticker], weight)
                }
            }
        }
    }
}
