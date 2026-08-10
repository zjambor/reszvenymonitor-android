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

/**
 * stocks.portfolio_items sor. A `quantity` és a `purchase_price` a PostgREST-en
 * numeric — a szerializáció Double-ként kezeli (a webes Number() párja).
 */
@Serializable
data class PortfolioItemDto(
    @SerialName("portfolio_id") val portfolioId: String? = null,
    val ticker: String,
    val quantity: Double,
    @SerialName("purchase_price") val purchasePrice: Double? = null,
    /** A bekerülési ár átváltásához KELL: a vásárlás NAPI árfolyama számít (7. invariáns). */
    @SerialName("purchase_date") val purchaseDate: String? = null,
)

/** stocks.portfolios sor a beágyazott tételeivel (PostgREST resource embedding). */
@Serializable
data class PortfolioDto(
    val id: String,
    val name: String,
    /** A portfólió létrehozásakor rögzült deviza — FX-kiesésnél tartalék. */
    val currency: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("portfolio_items") val items: List<PortfolioItemDto> = emptyList(),
)

/**
 * Tőzsdei kereső-találat (sync-prices `search`). A `currency` a legfontosabb
 * megkülönböztető: ugyanaz az alap tőzsdénként MÁS devizában fut
 * (IWDA.AS EUR vs SWDA.L GBp) — rossz jegyzés csendben rossz adatsort hozna.
 */
@Serializable
data class SearchHitDto(
    val symbol: String,
    val name: String? = null,
    val exchange: String? = null,
    val quoteType: String? = null,
    val currency: String? = null,
) {
    val isEtf: Boolean get() = quoteType?.uppercase() == "ETF"
}

/** A sync-prices `search` válasza. */
@Serializable
data class SearchResponseDto(
    val ok: Boolean = false,
    val query: String? = null,
    /** A szerver JELZÉSE, hogy a kérés ISIN-ként lett feldolgozva (nem maga az ISIN). */
    val isin: Boolean = false,
    val results: List<SearchHitDto> = emptyList(),
    val code: String? = null,
    val message: String? = null,
)

/** Az `add` akció sikeres ágának adatai. */
@Serializable
data class AddedTickerDto(
    val symbol: String,
    val name: String? = null,
    val exchange: String? = null,
    val color: String? = null,
    val rows: Int? = null,
    val from: String? = null,
    val to: String? = null,
    val firstTradeDate: String? = null,
)

/**
 * A sync-prices `add` válasza. Az ÜZLETI hiba HTTP 200 + `ok:false` formában jön
 * (`invalid-symbol` | `unknown-symbol` | `already-exists`) — ezt a hívó kezeli,
 * kivétel csak valódi hálózati/szerverhibára dobódik.
 */
@Serializable
data class AddResponseDto(
    val ok: Boolean = false,
    val added: AddedTickerDto? = null,
    /** already-exists ágon a szerver ide teszi a meglévő szimbólumot. */
    val symbol: String? = null,
    val code: String? = null,
    val message: String? = null,
)

/** A `delete` akció sikeres ágának adatai. */
@Serializable
data class DeletedTickerDto(
    val symbol: String? = null,
    val name: String? = null,
    val priceRows: Int? = null,
)

/**
 * A sync-prices `delete` válasza. Üzleti hibakódok: `invalid-symbol` |
 * `unknown-symbol` | **`in-portfolio`** (utóbbinál a szerver üzenete felsorolja
 * az érintett portfóliókat).
 */
@Serializable
data class DeleteResponseDto(
    val ok: Boolean = false,
    val deleted: DeletedTickerDto? = null,
    val code: String? = null,
    val message: String? = null,
)

// ---------------------------------------------------------------------------
// asset-details (Részletek) — a függvény már NORMALIZÁLT alakot ad vissza,
// ezért a DTO-k az FMP/Yahoo/Alpaca eltéréseitől függetlenek.
//
// SZÁNDÉKOSAN SZŰK: csak azok a mezők szerepelnek, amiket a felület használ.
// A `profile` blokk ad vissza olyan mezőket is (image, price, isEtf), amiket a
// megjelenítés nem használ — ezeket nem vesszük fel, mert minden felvett mező
// egy újabb típus-feltevés a szerver felé (a `search` válasz boolean `isin`
// mezője már omlasztotta egyszer az appot).
// ---------------------------------------------------------------------------

/** Alpaca-metaadat (csak US-listás papírnál van; egyébként null). */
@Serializable
data class AlpacaAssetDto(
    val assetClass: String? = null,
    val exchange: String? = null,
    val name: String? = null,
    val tradable: Boolean? = null,
)

/** Cég-/alapprofil. A `marketCap` ETF-nél az alap vagyona (AUM). */
@Serializable
data class AssetProfileDto(
    val name: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val country: String? = null,
    val description: String? = null,
    val website: String? = null,
    val ceo: String? = null,
    val employees: Double? = null,
    val marketCap: Double? = null,
    val beta: Double? = null,
    val currency: String? = null,
    val ipoDate: String? = null,
    val alpaca: AlpacaAssetDto? = null,
)

/**
 * ETF-alapadatok. A `netAssets` SZÁNDÉKOSAN string: a Yahoo már formázva adja
 * (pl. „75.68B"), és a szerver csak akkor tölti ki, ha megbízható forrásból van
 * (lásd az asset-details megjegyzését: inkább semmi, mint rossz vagyonadat).
 */
@Serializable
data class EtfInfoDto(
    val expenseRatio: Double? = null,
    val category: String? = null,
    val family: String? = null,
    val netAssets: String? = null,
    val turnover: Double? = null,
)

/** Egy ETF-pozíció. A súly SZÁZALÉK (a szerver már ×100-zal adja). */
@Serializable
data class HoldingDto(
    val symbol: String? = null,
    val name: String? = null,
    val weight: Double? = null,
)

/** Egy szektor súlya SZÁZALÉKBAN. */
@Serializable
data class SectorWeightDto(
    val sector: String? = null,
    val weight: Double? = null,
)

/** Az asset-details `details` blokkja. */
@Serializable
data class AssetDetailsDto(
    val symbol: String,
    /** Csak akkor van kitöltve, ha a szerver TÉNYLEGESEN US-társat használt. */
    val profileSymbol: String? = null,
    val assetType: String? = null,
    val currency: String? = null,
    val profile: AssetProfileDto? = null,
    val etf: EtfInfoDto? = null,
    val holdings: List<HoldingDto>? = null,
    val sectorWeights: List<SectorWeightDto>? = null,
    /** Forrás-lánc, pl. „fmp+yahoo+alpaca" — a lábléc ebből épül. */
    val source: String? = null,
    val fetchedAt: String? = null,
) {
    val isEtf: Boolean get() = assetType == "etf"
}

/**
 * Az asset-details válasza. Üzleti hiba HTTP 200 + `ok:false` (`unknown-symbol`
 * | `no-profile-data`) — ezt a hívó kezeli; 401-nél ApiException dobódik.
 */
@Serializable
data class AssetDetailsResponseDto(
    val ok: Boolean = false,
    /** A szerveroldali 7 napos gyorsítótárból jött-e (a lábléc jelzi). */
    val cached: Boolean = false,
    val details: AssetDetailsDto? = null,
    val code: String? = null,
    val message: String? = null,
)
