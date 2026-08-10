package hu.jamborz.reszvenymonitor.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.jamborz.reszvenymonitor.ui.theme.BadgeKind
import hu.jamborz.reszvenymonitor.ui.theme.ChipRow
import hu.jamborz.reszvenymonitor.ui.theme.LocalMonitorColors
import hu.jamborz.reszvenymonitor.ui.theme.MonitorBadge

/**
 * A Részletek-panel — a webes modal mobil megfelelője: alulról felúszó lap.
 * A tartalmat a [DetailsPresenter] állítja elő, itt már csak kirajzolás van.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(state: DetailsViewModel.UiState, onDismiss: () -> Unit) {
    if (!state.visible) return
    val palette = LocalMonitorColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.bgDeep,
        contentColor = palette.text,
        scrimColor = Color(0xB3050710),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                color = palette.text,
            )

            when {
                state.loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(20.dp))
                    Text("Részletek betöltése…", color = palette.textDim, style = MaterialTheme.typography.bodyMedium)
                }

                state.error != null -> Text(
                    text = state.error,
                    color = Color(0xFFFFD3DA),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(Color(0x17FB7185))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )

                else -> state.blocks.forEach { DetailsBlockView(it) }
            }

            state.footer?.let { footer ->
                Text(
                    text = footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailsBlockView(block: DetailsBlock) {
    val palette = LocalMonitorColors.current
    when (block) {
        is DetailsBlock.Chips -> ChipRow {
            block.items.forEachIndexed { index, chip ->
                // Az első chip az eszközosztály (ETF/Részvény) — kiemelt színnel,
                // ahogy a weben; a többi semleges.
                MonitorBadge(chip, if (index == 0) BadgeKind.Etf else BadgeKind.Neutral)
            }
        }

        is DetailsBlock.DefGrid -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            block.rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textFaint,
                        modifier = Modifier.weight(0.42f),
                    )
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.text,
                        modifier = Modifier.weight(0.58f),
                    )
                }
            }
        }

        is DetailsBlock.Website -> {
            val uriHandler = LocalUriHandler.current
            Text(
                text = block.label,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.accent,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { uriHandler.openUri(block.url) },
            )
        }

        is DetailsBlock.Paragraph -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim,
            lineHeight = 19.sp,
        )

        is DetailsBlock.Section -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = block.title,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textFaint,
                modifier = Modifier.padding(top = 6.dp),
            )
            block.blocks.forEach { DetailsBlockView(it) }
        }

        is DetailsBlock.Bars -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            block.rows.forEach { BarRowView(it) }
        }

        is DetailsBlock.Foot -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Egy súly-sáv: felirat (+ alfelirat), érték, és a legnagyobbhoz mért kitöltés. */
@Composable
private fun BarRowView(row: BarRow) {
    val palette = LocalMonitorColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Felirat + alfelirat EGY szövegként: így szűk helyen a MÁSODLAGOS
            // rész vágódik le, nem az azonosító (a webes .d-bar-label/.d-bar-sub
            // párost a CSS ugyanígy csonkolja).
            Text(
                text = buildAnnotatedString {
                    append(row.label)
                    row.sub?.let {
                        append("  ")
                        withStyle(SpanStyle(color = palette.textFaint, fontSize = 11.5.sp)) { append(it) }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.valueText,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.surfaceStrong),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((row.fillPercent / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.accent),
            )
        }
    }
}
