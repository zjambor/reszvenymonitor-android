package hu.jamborz.reszvenymonitor.ui.monitor

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.jamborz.reszvenymonitor.BuildConfig
import hu.jamborz.reszvenymonitor.R
import hu.jamborz.reszvenymonitor.data.dto.TickerDto
import hu.jamborz.reszvenymonitor.domain.Format
import hu.jamborz.reszvenymonitor.domain.Transform
import hu.jamborz.reszvenymonitor.ui.theme.AccentDot
import hu.jamborz.reszvenymonitor.ui.theme.BadgeKind
import hu.jamborz.reszvenymonitor.ui.theme.ChipRow
import hu.jamborz.reszvenymonitor.ui.theme.InfoChip
import hu.jamborz.reszvenymonitor.ui.theme.LocalMonitorColors
import hu.jamborz.reszvenymonitor.ui.theme.MonitorBadge
import hu.jamborz.reszvenymonitor.ui.theme.MonitorCard
import hu.jamborz.reszvenymonitor.ui.theme.MonitorPalette
import hu.jamborz.reszvenymonitor.ui.theme.MonitorTheme
import hu.jamborz.reszvenymonitor.ui.theme.PillGroup
import hu.jamborz.reszvenymonitor.ui.theme.PillShape
import hu.jamborz.reszvenymonitor.ui.theme.StatCard
import hu.jamborz.reszvenymonitor.ui.theme.StatTone
import hu.jamborz.reszvenymonitor.ui.theme.WarnChip
import hu.jamborz.reszvenymonitor.ui.theme.auroraBackground

/**
 * A fő nézet — a webes elrendezés mobil adaptációja (TERV-ANDROID.md 2. képernyő):
 * márka-fejléc, identitás, eszköztár (görgethető chip-sorok), grafikon, stat-rács.
 *
 * A ticker-választó chip-sor IDEIGLENES: a 7. fázisban a fejléc keresőmezője
 * váltja ki, ami a kereső-képernyőre visz.
 */
@Composable
fun MonitorScreen(
    state: MonitorViewModel.UiState,
    userName: String,
    onPreset: (Transform.Preset) -> Unit,
    onResolution: (Transform.Resolution) -> Unit,
    onChartType: (ChartType) -> Unit,
    onToggleVolume: () -> Unit,
    onCurrency: (String) -> Unit,
    onFit: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPortfolios: () -> Unit,
    onOpenDetails: () -> Unit,
    onDeleteTicker: (TickerDto) -> Unit,
    detailsSheet: @Composable () -> Unit = {},
) {
    // A kiválasztott instrumentum színe az EGÉSZ felület akcentje — ugyanaz,
    // amit a weben a JS a `--accent` CSS-változó átírásával ér el.
    MonitorTheme(accent = accentOf(state.selected, MonitorPalette())) {
        MonitorContent(
            state = state,
            userName = userName,
            onPreset = onPreset,
            onResolution = onResolution,
            onChartType = onChartType,
            onToggleVolume = onToggleVolume,
            onCurrency = onCurrency,
            onFit = onFit,
            onRefresh = onRefresh,
            onRetry = onRetry,
            onLogout = onLogout,
            onOpenSearch = onOpenSearch,
            onOpenPortfolios = onOpenPortfolios,
            onOpenDetails = onOpenDetails,
            onDeleteTicker = onDeleteTicker,
        )
        // A lap a témán BELÜL jelenik meg, hogy az akcentszínt is örökölje.
        detailsSheet()
    }
}

@Composable
private fun MonitorContent(
    state: MonitorViewModel.UiState,
    userName: String,
    onPreset: (Transform.Preset) -> Unit,
    onResolution: (Transform.Resolution) -> Unit,
    onChartType: (ChartType) -> Unit,
    onToggleVolume: () -> Unit,
    onCurrency: (String) -> Unit,
    onFit: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPortfolios: () -> Unit,
    onOpenDetails: () -> Unit,
    onDeleteTicker: (TickerDto) -> Unit,
) {
    val palette = LocalMonitorColors.current
    var confirmDelete by remember { mutableStateOf<TickerDto?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground(palette),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandHeader(userName = userName, onLogout = onLogout)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) { SearchBar(onOpenSearch) }
                ToolbarButton("Portfóliók", onClick = onOpenPortfolios)
            }
            state.error?.let { ErrorCard(it, onRetry) }
            IdentityHeader(state, onOpenDetails = onOpenDetails, onDelete = { confirmDelete = it })
            Toolbar(state, onPreset, onResolution, onChartType, onToggleVolume, onCurrency, onFit, onRefresh)

            MonitorCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                ChartPanel(
                    rows = state.bars,
                    chartType = state.chartType,
                    showVolume = state.showVolume,
                    accentColor = palette.accent,
                    currency = state.effectiveCurrency,
                    loading = state.loading,
                    watermark = state.selected?.title.orEmpty(),
                    initialWindowStart = state.windowStartIndex,
                    fitNonce = state.fitNonce,
                )
            }

            StatGrid(state)

            state.status?.let { status ->
                Text(
                    text = status.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status.tone) {
                        MonitorViewModel.StatusMessage.Tone.SUCCESS -> palette.up
                        MonitorViewModel.StatusMessage.Tone.ERROR -> palette.down
                        MonitorViewModel.StatusMessage.Tone.NEUTRAL -> palette.textFaint
                    },
                )
            }
        }

        // Kétlépcsős törlés-megerősítés (a webes „SYM törlése — biztos?" natív párja).
        confirmDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                containerColor = palette.bgDeep,
                titleContentColor = palette.text,
                textContentColor = palette.textDim,
                title = { Text("${target.symbol} törlése") },
                text = {
                    Text(
                        "Biztosan törlöd a(z) ${target.symbol} tickert a teljes " +
                            "árfolyam-adatsorával együtt? A művelet nem vonható vissza."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = null
                        onDeleteTicker(target)
                    }) { Text("Törlés", color = palette.down, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = null }) {
                        Text("Mégse", color = palette.textDim)
                    }
                },
            )
        }
    }
}

/** A fejléc keresőmezője — a kereső-képernyőre visz (webes combobox helyett). */
@Composable
private fun SearchBar(onClick: () -> Unit) {
    val palette = LocalMonitorColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xB80A0D1A))
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(text = "🔍", fontSize = 13.sp)
        Text(
            text = "Ticker, cégnév vagy ISIN keresése…",
            fontSize = 14.sp,
            color = palette.textFaint,
        )
    }
}

/**
 * Az instrumentum akcentszíne: tickernél a `color` mezője, portfóliónál az
 * egységes lila — hibás/hiányzó értéknél az alap indigó.
 */
private fun accentOf(instrument: Instrument?, palette: MonitorPalette): Color {
    val raw = when (instrument) {
        is Instrument.Stock -> instrument.ticker.color
        is Instrument.Portfolio -> Instrument.PORTFOLIO_COLOR
        null -> null
    }
    val hex = raw?.removePrefix("#")?.takeIf { it.length == 6 } ?: return palette.accent
    return try {
        Color(hex.toLong(16) or 0xFF000000L)
    } catch (e: NumberFormatException) {
        palette.accent
    }
}

@Composable
private fun BrandHeader(userName: String, onLogout: () -> Unit) {
    val palette = LocalMonitorColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0x387C8CFF), Color(0x172DD4BF))),
                    RoundedCornerShape(12.dp),
                )
                .border(1.dp, palette.borderStrong, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_candles),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = palette.text,
            modifier = Modifier
                .padding(start = 11.dp)
                .weight(1f),
        )
        if (userName.isNotEmpty()) {
            Text(
                text = userName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textDim,
                modifier = Modifier
                    .clip(PillShape)
                    .background(palette.surfaceStrong)
                    .border(1.dp, palette.border, PillShape)
                    .clickable(onClick = onLogout)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}

/** A webes .error-bar megfelelője, „Újra" gombbal. */
@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    val palette = LocalMonitorColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(Color(0x17FB7185))
            .border(1.dp, palette.down.copy(alpha = 0.35f), MaterialTheme.shapes.small)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(
            text = message,
            color = Color(0xFFFFD3DA),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Újra",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFFD3DA),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, palette.down.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** Identitás-sáv: szimbólum/név, badge-ek, utolsó adatnap, IPO/elavult chip, Részletek, Törlés. */
@Composable
private fun IdentityHeader(
    state: MonitorViewModel.UiState,
    onOpenDetails: () -> Unit,
    onDelete: (TickerDto) -> Unit,
) {
    val palette = LocalMonitorColors.current
    val instrument = state.selected ?: return
    val ticker = instrument.asStock
    MonitorCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentDot()
            // MINDKETTŐ súlyozott: így a hosszú cégnév nem szoríthatja ki a
            // szimbólumot (mérve: az „iShares Core S&P 500 UCITS ETF USD (Acc)"
            // mellől az SXR8.DE puszta „…"-ra zsugorodott).
            Text(
                text = instrument.title,
                style = MaterialTheme.typography.headlineSmall,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f, fill = false),
            )
            if (instrument.subtitle.isNotEmpty()) {
                Text(
                    text = instrument.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f, fill = false),
                )
            }
        }
        ChipRow(modifier = Modifier.padding(top = 10.dp)) {
            if (instrument.isPortfolio) {
                MonitorBadge("Portfólió", BadgeKind.Portfolio)
                // A tagok TÉNYLEGES devizái — vegyes portfóliónál ez a lényeges infó.
                state.portfolioCurrencies
                    .filter { it != state.effectiveCurrency }
                    .forEach { MonitorBadge(it, BadgeKind.Currency) }
            } else if (ticker != null) {
                ticker.exchange?.let { MonitorBadge(it) }
                if (ticker.isEtf) MonitorBadge("ETF", BadgeKind.Etf)
                // A jegyzési deviza akkor érdekes, ha eltér a megjelenítésitől.
                if (ticker.nativeCurrency != state.effectiveCurrency) {
                    MonitorBadge(ticker.nativeCurrency, BadgeKind.Currency)
                }
            }
        }
        // Friss tőzsdei bevezetés: az első kereskedési nap a START_DATE utánra esik.
        val ipo = ticker?.firstTradeDate
        if (ipo != null && ipo > BuildConfig.START_DATE) {
            ChipRow(modifier = Modifier.padding(top = 8.dp)) {
                InfoChip("Bevezetés: ${Format.formatDateHu(ipo)}")
            }
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Utolsó adatnap: ${state.lastDate?.let { Format.formatDateHu(it) } ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textFaint,
                modifier = Modifier.weight(1f),
            )
            if (state.stale) WarnChip("Elavult lehet")
            // Részletek: tickernél profil/ETF-összetétel a szervertől, portfóliónál
            // helyben számolt összetétel — mindkét instrumentumtípusnál látszik.
            ToolbarButton("Részletek", onClick = onOpenDetails)
            // .btn-ghost — a törlés visszafogott, de elérhető (megerősítés követi).
            // Portfóliót a saját szerkesztőjéből kell törölni, nem innen.
            if (ticker != null) {
                Text(
                    text = "Törlés",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD3DA),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, palette.down.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable { onDelete(ticker) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun Toolbar(
    state: MonitorViewModel.UiState,
    onPreset: (Transform.Preset) -> Unit,
    onResolution: (Transform.Resolution) -> Unit,
    onChartType: (ChartType) -> Unit,
    onToggleVolume: () -> Unit,
    onCurrency: (String) -> Unit,
    onFit: () -> Unit,
    onRefresh: () -> Unit,
) {
    val palette = LocalMonitorColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PillGroup(
                options = PRESET_LABELS.values.toList(),
                selected = PRESET_LABELS.getValue(state.preset),
                onSelect = { label ->
                    PRESET_LABELS.entries.firstOrNull { it.value == label }?.let { onPreset(it.key) }
                },
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillGroup(
                options = RESOLUTION_LABELS.values.toList(),
                selected = RESOLUTION_LABELS.getValue(state.resolution),
                onSelect = { label ->
                    RESOLUTION_LABELS.entries.firstOrNull { it.value == label }?.let { onResolution(it.key) }
                },
            )
            PillGroup(
                options = listOf("Gyertya", "Vonal"),
                selected = if (state.chartType == ChartType.CANDLE) "Gyertya" else "Vonal",
                onSelect = { onChartType(if (it == "Gyertya") ChartType.CANDLE else ChartType.LINE) },
            )
            // FX-kiesésnél tiltott a kapcsoló (11. invariáns).
            PillGroup(
                options = listOf("USD", "EUR", "HUF"),
                selected = state.effectiveCurrency,
                onSelect = onCurrency,
                enabled = state.currencyEnabled,
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Portfóliónál a volumen értelmetlen (6. invariáns) — a gomb tiltott.
            ToolbarButton(
                text = "Volumen",
                active = state.showVolume,
                enabled = state.volumeEnabled,
                onClick = onToggleVolume,
            )
            ToolbarButton("Teljes nézet", onClick = onFit)
            ToolbarButton(
                text = if (state.refreshing) "Frissítés…" else "Frissítés",
                enabled = !state.refreshing,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    text: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalMonitorColors.current
    val shape = RoundedCornerShape(11.dp)
    Text(
        text = text,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = when {
            !enabled -> palette.textFaint
            active -> palette.text
            else -> palette.textDim
        },
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (active) palette.accentSoft else palette.surface)
            .border(1.dp, if (active) palette.accentRing else palette.border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
    )
}

/** Stat-kártyák: weben 6 egy sorban, mobilon 2×3 (TERV-ANDROID.md). */
@Composable
private fun StatGrid(state: MonitorViewModel.UiState) {
    val s = state.stats
    val ccy = state.effectiveCurrency
    val dayText = s?.dayChange?.let { d ->
        "${Format.formatSignedIn(d, ccy)} (${Format.formatPct(s.dayChangePct)})"
    } ?: "—"

    // Portfólió-nézetben az utolsó kártya P/L-t mutat, „(N/M elem)" jelzéssel,
    // ha nem minden taghoz van bekerülési ár (7. invariáns).
    val lastCell = if (state.isPortfolioView) {
        val pnl = state.pnl
        val label = if (pnl != null && pnl.costedCount < pnl.totalCount) {
            "Nyereség/veszteség (${pnl.costedCount}/${pnl.totalCount} elem)"
        } else {
            "Nyereség/veszteség"
        }
        val value = pnl?.pnl?.let { p ->
            "${Format.formatSignedIn(p, ccy)} (${Format.formatPct(pnl.pnlPct)})"
        } ?: "—"
        Triple(label, value, toneOf(pnl?.pnl))
    } else {
        Triple("Átlagvolumen", s?.let { Format.formatVolume(it.avgVolume) } ?: "—", StatTone.Neutral)
    }

    val cells = listOf(
        Triple(if (state.isPortfolioView) "Összérték" else "Utolsó záró",
            s?.let { Format.formatPriceIn(it.lastClose, ccy) } ?: "—", StatTone.Neutral),
        Triple("Napi változás", dayText, toneOf(s?.dayChange)),
        Triple("Időszaki változás", s?.periodChangePct?.let { Format.formatPct(it) } ?: "—", toneOf(s?.periodChangePct)),
        Triple("Időszak max", s?.let { Format.formatPriceIn(it.periodHigh, ccy) } ?: "—", StatTone.Neutral),
        Triple("Időszak min", s?.let { Format.formatPriceIn(it.periodLow, ccy) } ?: "—", StatTone.Neutral),
        lastCell,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(2).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowCells.forEach { (title, value, tone) ->
                    StatCard(title = title, value = value, tone = tone, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun toneOf(value: Double?): StatTone = when {
    value == null || value.isNaN() -> StatTone.Neutral
    value > 0 -> StatTone.Positive
    value < 0 -> StatTone.Negative
    else -> StatTone.Neutral
}

private val PRESET_LABELS = linkedMapOf(
    Transform.Preset.HET1 to "1Hét",
    Transform.Preset.M1 to "1M",
    Transform.Preset.M3 to "3M",
    Transform.Preset.M6 to "6M",
    Transform.Preset.YTD to "YTD",
    Transform.Preset.EV1 to "1É",
    Transform.Preset.MIND to "MIND",
)

private val RESOLUTION_LABELS = linkedMapOf(
    Transform.Resolution.DAILY to "Napi",
    Transform.Resolution.WEEKLY to "Heti",
    Transform.Resolution.MONTHLY to "Havi",
)
