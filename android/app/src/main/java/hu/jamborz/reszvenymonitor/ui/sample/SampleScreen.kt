package hu.jamborz.reszvenymonitor.ui.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.jamborz.reszvenymonitor.R
import hu.jamborz.reszvenymonitor.ui.theme.AccentDot
import hu.jamborz.reszvenymonitor.ui.theme.BadgeKind
import hu.jamborz.reszvenymonitor.ui.theme.ChipRow
import hu.jamborz.reszvenymonitor.ui.theme.InfoChip
import hu.jamborz.reszvenymonitor.ui.theme.LocalMonitorColors
import hu.jamborz.reszvenymonitor.ui.theme.MonitorBadge
import hu.jamborz.reszvenymonitor.ui.theme.MonitorCard
import hu.jamborz.reszvenymonitor.ui.theme.PillGroup
import hu.jamborz.reszvenymonitor.ui.theme.PillShape
import hu.jamborz.reszvenymonitor.ui.theme.StatCard
import hu.jamborz.reszvenymonitor.ui.theme.StatTone
import hu.jamborz.reszvenymonitor.ui.theme.WarnChip
import hu.jamborz.reszvenymonitor.ui.theme.auroraBackground

/**
 * 1. fázis minta-képernyő: a téma vizuális ellenőrzéséhez (aurora-háttér,
 * kártya, pill-csoportok, badge-ek, chipek, stat-rács) — a webes fő nézet
 * elrendezését idézi MINTA-adatokkal. A 6. fázisban a valódi MonitorScreen váltja.
 *
 * @param userName a fejléc user-chipjének felirata (2. fázis)
 * @param onLogout Kijelentkezés a fejlécből (2. fázis)
 */
@Composable
fun SampleScreen(
    userName: String = "",
    onLogout: () -> Unit = {},
) {
    val palette = LocalMonitorColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground(palette)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandHeader(userName = userName, onLogout = onLogout)
            IdentitySample()
            ToolbarSample()
            StatGridSample()
            BadgeShowcase()
            Text(
                text = "Minta-képernyő — 1. fázis (téma-ellenőrzés)",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textFaint,
            )
        }
    }
}

/** `.brand` + `.auth-box` — gyertya-embléma, cím, user-chip és Kijelentkezés. */
@Composable
private fun BrandHeader(userName: String, onLogout: () -> Unit) {
    val palette = LocalMonitorColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0x387C8CFF), Color(0x172DD4BF)) // 150deg indigó→türkiz
                    ),
                    RoundedCornerShape(13.dp),
                )
                .border(1.dp, palette.borderStrong, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_candles),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 13.dp)
                .weight(1f),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = palette.text,
            )
            Text(
                text = stringResource(R.string.brand_sub),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim,
            )
        }
    }
    if (userName.isNotEmpty()) {
        ChipRow {
            // .user-chip
            Text(
                text = userName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textDim,
                modifier = Modifier
                    .clip(PillShape)
                    .background(palette.surfaceStrong)
                    .border(1.dp, palette.border, PillShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
            // .btn — Kijelentkezés
            Text(
                text = stringResource(R.string.logout),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(11.dp))
                    .clickable(onClick = onLogout)
                    .padding(horizontal = 15.dp, vertical = 9.dp),
            )
        }
    }
}

/** `.identity` — szimbólum, név, badge-ek és chipek (minta-tartalommal). */
@Composable
private fun IdentitySample() {
    val palette = LocalMonitorColors.current
    MonitorCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentDot()
            Text(
                text = "NVDA",
                style = MaterialTheme.typography.headlineSmall,
                color = palette.text,
                modifier = Modifier.padding(start = 12.dp),
            )
            Text(
                text = "NVIDIA Corporation",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textDim,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f, fill = false),
            )
        }
        ChipRow(modifier = Modifier.padding(top = 10.dp)) {
            MonitorBadge("NASDAQ")
            InfoChip("IPO: 2024-03-01")
            WarnChip("Elavult lehet")
        }
        Text(
            text = "Utolsó adatnap: 2026-08-07",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textFaint,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Eszköztár-minta: preset / felbontás / deviza pill-sorok, vízszintesen görgethetően. */
@Composable
private fun ToolbarSample() {
    var preset by remember { mutableStateOf("6M") }
    var resolution by remember { mutableStateOf("Napi") }
    var currency by remember { mutableStateOf("USD") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PillGroup(
                options = listOf("1Hét", "1M", "3M", "6M", "YTD", "1É", "MIND"),
                selected = preset,
                onSelect = { preset = it },
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillGroup(
                options = listOf("Napi", "Heti", "Havi"),
                selected = resolution,
                onSelect = { resolution = it },
            )
            PillGroup(
                options = listOf("USD", "EUR", "HUF"),
                selected = currency,
                onSelect = { currency = it },
            )
        }
    }
}

/** Stat-rács: weben 6 egy sorban, mobilon 2×3 (TERV-ANDROID.md). Minta-értékek. */
@Composable
private fun StatGridSample() {
    val cells = listOf(
        Triple("Utolsó záró", "181,57 $", StatTone.Neutral),
        Triple("Napi változás", "+2,34%", StatTone.Positive),
        Triple("Időszaki változás", "−5,12%", StatTone.Negative),
        Triple("Időszak max", "195,62 $", StatTone.Neutral),
        Triple("Időszak min", "86,62 $", StatTone.Neutral),
        Triple("Átlagvolumen", "168,4 M", StatTone.Neutral),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(2).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowCells.forEach { (title, value, tone) ->
                    StatCard(
                        title = title,
                        value = value,
                        tone = tone,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Badge-változatok egy helyen — vizuális összevetéshez a webes stílussal. */
@Composable
private fun BadgeShowcase() {
    MonitorCard {
        ChipRow {
            MonitorBadge("ETF", BadgeKind.Etf)
            MonitorBadge("USD", BadgeKind.Currency)
            MonitorBadge("Portfólió", BadgeKind.Portfolio)
            MonitorBadge("már felvéve", BadgeKind.Known)
        }
    }
}
