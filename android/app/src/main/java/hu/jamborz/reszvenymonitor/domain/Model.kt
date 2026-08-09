package hu.jamborz.reszvenymonitor.domain

/**
 * A domain-réteg közös sor-sémája — a webes {date, open, high, low, close, volume}
 * megfelelője. A dátum ISO string (YYYY-MM-DD), mint a weben: a rendezés és az
 * összehasonlítás lexikografikusan helyes, időzóna-kérdés fel sem merül.
 */
data class OhlcRow(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

/** Egy fx_rates-sor: 1 egység `currency` hány USD az adott napon. */
data class FxRateRow(
    val date: String,
    val currency: String,
    val usdRate: Double,
)
