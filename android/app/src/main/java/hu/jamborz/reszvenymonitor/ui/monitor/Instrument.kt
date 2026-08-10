package hu.jamborz.reszvenymonitor.ui.monitor

import hu.jamborz.reszvenymonitor.data.dto.PortfolioDto
import hu.jamborz.reszvenymonitor.data.dto.TickerDto

/**
 * A fő nézetben megjeleníthető instrumentum: valódi ticker VAGY portfólió.
 * A weben ez duck-typing volt (`kind: 'portfolio'` mező a pszeudo-tickeren);
 * itt zárt hierarchia, így a fordító kényszeríti ki a két ág kezelését.
 */
sealed class Instrument {

    abstract val key: String

    /** A fejlécben nagy betűvel megjelenő azonosító (szimbólum vagy portfólió-név). */
    abstract val title: String

    /** Alcím: cégnév, illetve portfóliónál az elemszám. */
    abstract val subtitle: String

    data class Stock(val ticker: TickerDto) : Instrument() {
        override val key: String get() = "t-${ticker.symbol}"
        override val title: String get() = ticker.symbol
        override val subtitle: String get() = ticker.name.orEmpty()
    }

    data class Portfolio(val portfolio: PortfolioDto) : Instrument() {
        override val key: String get() = "p-${portfolio.id}"
        override val title: String get() = portfolio.name
        override val subtitle: String
            get() = if (portfolio.items.isEmpty()) "üres portfólió" else "${portfolio.items.size} elem"
    }

    val isPortfolio: Boolean get() = this is Portfolio
    val asStock: TickerDto? get() = (this as? Stock)?.ticker
    val asPortfolio: PortfolioDto? get() = (this as? Portfolio)?.portfolio

    companion object {
        /** A portfóliók egységes akcentszíne (a webes PORTFOLIO_COLOR párja). */
        const val PORTFOLIO_COLOR = "#a78bfa"
    }
}
