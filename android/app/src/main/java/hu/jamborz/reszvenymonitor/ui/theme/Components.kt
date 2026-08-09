package hu.jamborz.reszvenymonitor.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * A webes komponens-osztályok (.card, .pill-group/.pill, .badge*, .chip*,
 * .stat-card) Compose-megfelelői — ugyanazokkal a színekkel és arányokkal.
 */

/** `.card` — finom függőleges gradiens + 1px keret, 14dp rádiusz. */
@Composable
fun MonitorCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalMonitorColors.current
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.verticalGradient(
                    // rgba(148,163,204,.055) → rgba(148,163,204,.02)
                    listOf(Color(0x0E94A3CC), Color(0x0594A3CC))
                )
            )
            .border(1.dp, palette.border, MaterialTheme.shapes.medium)
            .padding(contentPadding),
        content = content,
    )
}

/** `.pill-group` + `.pill` — kapszula-gombcsoport, egy kiválasztott elemmel. */
@Composable
fun PillGroup(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalMonitorColors.current
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(Color(0x9E0A0D1A)) // rgba(10,13,26,.62)
            .border(1.dp, palette.border, PillShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            val pillModifier = Modifier
                .clip(PillShape)
                .background(if (active) palette.accentSoft else Color.Transparent)
                .then(
                    if (active) Modifier.border(1.dp, palette.accentRing, PillShape)
                    else Modifier
                )
                .selectable(
                    selected = active,
                    enabled = enabled,
                    onClick = { onSelect(option) },
                )
                .padding(horizontal = 13.dp, vertical = 7.dp)
            Text(
                text = option,
                modifier = pillModifier,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    !enabled -> palette.textFaint
                    active -> palette.text
                    else -> palette.textDim
                },
                maxLines = 1,
            )
        }
    }
}

/** `.badge` változatai — a webes színkódokkal. */
enum class BadgeKind { Neutral, Etf, Currency, Portfolio, Known }

@Composable
fun MonitorBadge(
    text: String,
    kind: BadgeKind = BadgeKind.Neutral,
    modifier: Modifier = Modifier,
) {
    val palette = LocalMonitorColors.current
    val (color, background, borderColor) = when (kind) {
        BadgeKind.Neutral -> Triple(palette.textDim, Color.Transparent, palette.borderStrong)
        // .badge-etf: cián, hogy elüssön a semleges exchange-badge-től
        BadgeKind.Etf -> Triple(palette.etf, palette.etf.copy(alpha = 0.12f), palette.etf.copy(alpha = 0.35f))
        // .badge-ccy: sárga — a tőzsdei találatok legfontosabb megkülönböztetője
        BadgeKind.Currency -> Triple(palette.warn, palette.warn.copy(alpha = 0.12f), palette.warn.copy(alpha = 0.35f))
        BadgeKind.Portfolio -> Triple(palette.portfolio, palette.portfolio.copy(alpha = 0.12f), palette.portfolio.copy(alpha = 0.35f))
        // .badge-known: halvány, mert csak tájékoztat (a szaggatott keret helyett halvány tömör)
        BadgeKind.Known -> Triple(palette.textFaint, Color.Transparent, palette.border)
    }
    val uppercase = kind == BadgeKind.Neutral || kind == BadgeKind.Etf || kind == BadgeKind.Portfolio
    Text(
        text = if (uppercase) text.uppercase() else text,
        modifier = modifier
            .clip(PillShape)
            .background(background)
            .border(1.dp, borderColor, PillShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = color,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.07.em,
        maxLines = 1,
    )
}

/** `.chip-info` (akcent) és `.chip-warn` (sárga) — pl. IPO- és „Elavult lehet" jelzés. */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    val palette = LocalMonitorColors.current
    ChipBase(text, palette.accent, palette.accentSoft, palette.accentRing, modifier)
}

@Composable
fun WarnChip(text: String, modifier: Modifier = Modifier) {
    val palette = LocalMonitorColors.current
    ChipBase(text, palette.warn, palette.warn.copy(alpha = 0.10f), palette.warn.copy(alpha = 0.32f), modifier)
}

@Composable
private fun ChipBase(
    text: String,
    color: Color,
    background: Color,
    borderColor: Color,
    modifier: Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(PillShape)
            .background(background)
            .border(1.dp, borderColor, PillShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = color,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/** `.stat-card` — címke + tabuláris számérték, pozitív/negatív színezéssel. */
enum class StatTone { Neutral, Positive, Negative }

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: StatTone = StatTone.Neutral,
) {
    val palette = LocalMonitorColors.current
    MonitorCard(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 13.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 6.dp),
            color = when (tone) {
                StatTone.Positive -> palette.up
                StatTone.Negative -> palette.down
                StatTone.Neutral -> palette.text
            },
            fontSize = 18.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** `.accent-dot` — a kiválasztott instrumentum akcent-pöttye, halvány ragyogással. */
@Composable
fun AccentDot(modifier: Modifier = Modifier) {
    val palette = LocalMonitorColors.current
    Box(
        modifier = modifier
            .size(12.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(palette.accentGlow, Color.Transparent)
                    ),
                    radius = size.minDimension, // glow a pötty körül
                )
                drawCircle(color = palette.accent, radius = size.minDimension / 2f)
            },
    )
}

/** Vízszintes elrendezési segéd chip-/badge-sorokhoz. */
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
