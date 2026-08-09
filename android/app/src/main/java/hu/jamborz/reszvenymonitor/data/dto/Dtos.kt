package hu.jamborz.reszvenymonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** stocks.tickers sor — a web-app fetchTickers() `select=*`-jának megfelelő mezők. */
@Serializable
data class TickerDto(
    val symbol: String,
    val name: String? = null,
    val exchange: String? = null,
    val color: String? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("first_trade_date") val firstTradeDate: String? = null,
    /** 'stock' | 'etf' — hiányzó érték részvényként kezelve (webes szabály). */
    @SerialName("asset_type") val assetType: String? = null,
    /** Jegyzési deviza (USD/EUR/GBp…); null → USD-ként kezelve (webes szabály). */
    val currency: String? = null,
) {
    val isEtf: Boolean get() = assetType == "etf"
    val nativeCurrency: String get() = currency ?: "USD"
}

/** stocks.stock_prices sor (a select szűkített oszloplistájával). */
@Serializable
data class StockPriceDto(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

/** stocks.fx_rates sor: 1 egység `currency` hány USD az adott napon. */
@Serializable
data class FxRateDto(
    val currency: String,
    val date: String,
    @SerialName("usd_rate") val usdRate: Double,
)

/** A sync-prices frissítés-válasz egy eleme. Az error === "up-to-date" NEM hiba. */
@Serializable
data class SyncResultDto(
    val symbol: String? = null,
    val rows: Int? = null,
    val source: String? = null,
    val error: String? = null,
) {
    val isUpToDate: Boolean get() = error == "up-to-date"
    val isFailure: Boolean get() = error != null && !isUpToDate
}

/** A sync-prices `POST {"symbols":[…]}` válasza. */
@Serializable
data class SyncResponseDto(
    val results: List<SyncResultDto> = emptyList(),
)
