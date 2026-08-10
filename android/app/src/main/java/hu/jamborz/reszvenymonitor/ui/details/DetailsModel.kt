package hu.jamborz.reszvenymonitor.ui.details

/**
 * A Részletek-panel tartalma MEGJELENÍTÉSTŐL FÜGGETLEN modellként — a webes
 * `buildAssetDetails` / `buildPortfolioComposition` DOM-fájának megfelelője.
 *
 * MIÉRT KÜLÖN MODELL: így a „mi látszik és milyen szöveggel" kérdés tiszta
 * Kotlin, JVM-en tesztelhető — a `DetailsCrossCheckTest` pontosan ezt veti
 * össze a web-app SAJÁT építőinek kimenetével (tools/xcheck-details.mjs).
 * A Compose-réteg (DetailsSheet) már csak kirajzol.
 */
sealed class DetailsBlock {

    /** Fejléc-badge-ek: eszközosztály, tőzsde, „nem kereskedhető", „profil: X". */
    data class Chips(val items: List<String>) : DetailsBlock()

    /** Címke–érték rács (profil, alap-adatok). Üres értékű sor ide be sem kerül. */
    data class DefGrid(val rows: List<DefRow>) : DetailsBlock()

    /** Honlap-hivatkozás: a felirat séma nélküli, a cél a teljes URL. */
    data class Website(val label: String, val url: String) : DetailsBlock()

    /** Szabad szöveg (cégleírás). */
    data class Paragraph(val text: String) : DetailsBlock()

    /** Szekció-cím a saját tartalmával. */
    data class Section(val title: String, val blocks: List<DetailsBlock>) : DetailsBlock()

    /** Súly-sávok (holdingok, szektorok, portfólió-tagok). */
    data class Bars(val rows: List<BarRow>) : DetailsBlock()

    /** Záró összegző sor (portfólió-összérték). */
    data class Foot(val text: String) : DetailsBlock()
}

data class DefRow(val label: String, val value: String)

/**
 * Egy súly-sáv. A [fillPercent] a LEGNAGYOBB súlyhoz relatív (0–100), a
 * [valueText] viszont abszolút érték — a webes barRow-val egyezően: a sáv
 * arányt mutat, a szám tényt.
 */
data class BarRow(
    val label: String,
    val sub: String?,
    val valueText: String,
    val fillPercent: Double,
)
