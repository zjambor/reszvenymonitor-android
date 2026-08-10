package hu.jamborz.reszvenymonitor.ui.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.jamborz.reszvenymonitor.data.dto.PortfolioDto
import hu.jamborz.reszvenymonitor.data.dto.TickerDto
import hu.jamborz.reszvenymonitor.domain.Format
import hu.jamborz.reszvenymonitor.ui.theme.BadgeKind
import hu.jamborz.reszvenymonitor.ui.theme.LocalMonitorColors
import hu.jamborz.reszvenymonitor.ui.theme.MonitorBadge
import hu.jamborz.reszvenymonitor.ui.theme.MonitorCard
import hu.jamborz.reszvenymonitor.ui.theme.auroraBackground

/**
 * Portfólió-kezelő: lista → szerkesztő. A webes modal mobil megfelelője, teljes
 * képernyőn. Írás közvetlenül PostgREST-en, owner-only RLS alatt.
 */
@Composable
fun PortfolioScreen(
    state: PortfolioViewModel.UiState,
    onOpenEditor: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDeletePortfolio: (String) -> Unit,
    onUpsertItem: (String, String, Double, Double?, String?) -> Unit,
    onDeleteItem: (String, String) -> Unit,
    onOpenPortfolioView: (PortfolioDto) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalMonitorColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground(palette)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                title = state.editing?.name ?: "Portfóliók",
                onBack = { if (state.editingId != null) onOpenEditor(null) else onBack() },
            )
            state.error?.let { ErrorRow(it) }

            val editing = state.editing
            if (editing == null) {
                PortfolioList(
                    portfolios = state.portfolios,
                    onOpen = { onOpenEditor(it.id) },
                    onView = onOpenPortfolioView,
                    onCreate = onCreate,
                )
            } else {
                PortfolioEditor(
                    portfolio = editing,
                    tickers = state.tickers,
                    busy = state.busy,
                    onRename = { onRename(editing.id, it) },
                    onDeletePortfolio = { onDeletePortfolio(editing.id) },
                    onUpsertItem = { ticker, qty, price, date ->
                        onUpsertItem(editing.id, ticker, qty, price, date)
                    },
                    onDeleteItem = { ticker -> onDeleteItem(editing.id, ticker) },
                    onView = { onOpenPortfolioView(editing) },
                )
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    val palette = LocalMonitorColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Vissza",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textDim,
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun PortfolioList(
    portfolios: List<PortfolioDto>,
    onOpen: (PortfolioDto) -> Unit,
    onView: (PortfolioDto) -> Unit,
    onCreate: (String) -> Unit,
) {
    val palette = LocalMonitorColors.current
    var newName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(portfolios, key = { it.id }) { p ->
            MonitorCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (p.items.isEmpty()) "üres portfólió" else "${p.items.size} elem",
                            fontSize = 12.5.sp,
                            color = palette.textDim,
                        )
                    }
                    SmallButton("Megnyitás") { onView(p) }
                    Box(modifier = Modifier.width(8.dp))
                    SmallButton("Szerkesztés") { onOpen(p) }
                }
            }
        }

        item {
            MonitorCard {
                Text(
                    text = "Új portfólió",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textDim,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = "Portfólió neve",
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.width(8.dp))
                    SmallButton("Létrehozás", accent = true) {
                        onCreate(newName)
                        newName = ""
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioEditor(
    portfolio: PortfolioDto,
    tickers: List<TickerDto>,
    busy: Boolean,
    onRename: (String) -> Unit,
    onDeletePortfolio: () -> Unit,
    onUpsertItem: (String, Double, Double?, String?) -> Unit,
    onDeleteItem: (String) -> Unit,
    onView: () -> Unit,
) {
    val palette = LocalMonitorColors.current
    val focusManager = LocalFocusManager.current
    var name by remember(portfolio.id) { mutableStateOf(portfolio.name) }
    var ticker by remember(portfolio.id) { mutableStateOf("") }
    var tickerFieldFocused by remember(portfolio.id) { mutableStateOf(false) }
    var quantity by remember(portfolio.id) { mutableStateOf("") }
    var price by remember(portfolio.id) { mutableStateOf("") }
    var date by remember(portfolio.id) { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            MonitorCard {
                Text("Név", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textDim)
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LabeledField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Portfólió neve",
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.width(8.dp))
                    SmallButton("Mentés") { onRename(name) }
                }
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SmallButton("Megnyitás a fő nézetben", accent = true, onClick = onView)
                    SmallButton("Portfólió törlése", danger = true) { confirmDelete = true }
                }
            }
        }

        item {
            Text(
                text = "TAGOK (${portfolio.items.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textFaint,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        items(portfolio.items, key = { it.ticker }) { item ->
            MonitorCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.ticker,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.text,
                            )
                            Box(modifier = Modifier.width(8.dp))
                            MonitorBadge("${trimNumber(item.quantity)} db", BadgeKind.Neutral)
                        }
                        val costText = item.purchasePrice?.let { p ->
                            val dateText = item.purchaseDate?.let { " · ${Format.formatDateHu(it)}" }.orEmpty()
                            "Bekerülés: ${trimNumber(p)}$dateText"
                        } ?: "Nincs bekerülési ár"
                        Text(
                            text = costText,
                            fontSize = 12.sp,
                            color = palette.textDim,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    SmallButton("Törlés", danger = true) { onDeleteItem(item.ticker) }
                }
            }
        }

        item {
            MonitorCard {
                Text(
                    text = "Tag hozzáadása / módosítása",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textDim,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LabeledField(
                    value = ticker,
                    onValueChange = { ticker = it },
                    placeholder = "Ticker — koppints a listához",
                    onFocusChanged = { tickerFieldFocused = it },
                )
                // Választólista: fókuszra nyílik, gépelésre szűkül — így nem kell
                // fejből tudni a pontos szimbólumot.
                if (tickerFieldFocused || ticker.isNotBlank()) {
                    TickerPicker(
                        tickers = tickers,
                        query = ticker,
                        memberSymbols = portfolio.items.map { it.ticker }.toSet(),
                        onPick = { picked ->
                            ticker = picked.symbol
                            tickerFieldFocused = false
                            focusManager.clearFocus()
                        },
                    )
                }
                Box(modifier = Modifier.padding(top = 8.dp))
                LabeledField(quantity, { quantity = it }, "Darabszám", numeric = true)
                Box(modifier = Modifier.padding(top = 8.dp))
                LabeledField(price, { price = it }, "Bekerülési ár (nem kötelező)", numeric = true)
                Box(modifier = Modifier.padding(top = 8.dp))
                LabeledField(date, { date = it }, "Vételi dátum: ÉÉÉÉ-HH-NN (nem kötelező)")
                Box(modifier = Modifier.padding(top = 10.dp))
                SmallButton(if (busy) "Mentés…" else "Hozzáadás", accent = true) {
                    onUpsertItem(
                        ticker,
                        quantity.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        price.replace(',', '.').toDoubleOrNull(),
                        date.trim().ifBlank { null },
                    )
                    ticker = ""; quantity = ""; price = ""; date = ""
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = palette.bgDeep,
            titleContentColor = palette.text,
            textContentColor = palette.textDim,
            title = { Text("${portfolio.name} törlése") },
            text = { Text("Biztosan törlöd a portfóliót a tételeivel együtt? A művelet nem vonható vissza.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeletePortfolio()
                }) { Text("Törlés", color = palette.down, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Mégse", color = palette.textDim) }
            },
        )
    }
}

/**
 * A felvett tickerek választólistája a beviteli mező alatt. Üres mezőnél az
 * első néhány elemet kínálja, gépelésre szimbólum ÉS név szerint szűkül.
 * A már felvett tagok jelölve vannak — rájuk koppintva a meglévő tétel
 * módosítható (a mentés (portfolio_id, ticker) kulcson upsertel).
 */
@Composable
private fun TickerPicker(
    tickers: List<TickerDto>,
    query: String,
    memberSymbols: Set<String>,
    onPick: (TickerDto) -> Unit,
) {
    val palette = LocalMonitorColors.current
    val needle = query.trim().lowercase()
    val matches = remember(tickers, needle) {
        if (needle.isEmpty()) tickers
        else tickers.filter {
            it.symbol.lowercase().contains(needle) || it.name.orEmpty().lowercase().contains(needle)
        }
    }
    // Pontos találatnál felesleges a lista — a felhasználó már kiválasztotta.
    if (matches.size == 1 && matches.first().symbol.equals(query.trim(), ignoreCase = true)) return

    val shown = matches.take(MAX_PICKER_ROWS)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0x8C0A0D1A))
            .border(1.dp, palette.border, RoundedCornerShape(11.dp)),
    ) {
        if (shown.isEmpty()) {
            Text(
                text = "Nincs ilyen felvett ticker.",
                fontSize = 12.5.sp,
                color = palette.textFaint,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
            return@Column
        }
        shown.forEach { t ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button) { onPick(t) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = t.symbol,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                )
                Text(
                    text = t.name.orEmpty(),
                    fontSize = 12.sp,
                    color = palette.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (t.isEtf) MonitorBadge("ETF", BadgeKind.Etf)
                if (t.symbol in memberSymbols) MonitorBadge("már tag", BadgeKind.Known)
            }
        }
        if (matches.size > shown.size) {
            Text(
                text = "…és még ${matches.size - shown.size} — gépelj a szűkítéshez",
                fontSize = 12.sp,
                color = palette.textFaint,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private const val MAX_PICKER_ROWS = 6

@Composable
private fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val palette = LocalMonitorColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocusChanged?.invoke(focused) }
    val shape = RoundedCornerShape(11.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
        ),
        textStyle = TextStyle(color = palette.text, fontSize = 14.sp),
        cursorBrush = SolidColor(palette.accent),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(Color(0xB80A0D1A))
                    .border(1.dp, if (focused) palette.accent else palette.border, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = palette.textFaint, fontSize = 14.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun SmallButton(
    text: String,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalMonitorColors.current
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = when {
            danger -> Color(0xFFFFD3DA)
            accent -> palette.accent
            else -> palette.textDim
        },
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (accent) palette.accentSoft else palette.surface)
            .border(
                1.dp,
                when {
                    danger -> palette.down.copy(alpha = 0.45f)
                    accent -> palette.accentRing
                    else -> palette.border
                },
                shape,
            )
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun ErrorRow(message: String) {
    val palette = LocalMonitorColors.current
    Text(
        text = message,
        fontSize = 13.sp,
        color = Color(0xFFFFD3DA),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color(0x17FB7185))
            .border(1.dp, palette.down.copy(alpha = 0.35f), MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
}

/** 0,364765521 → „0,364765521"; 3,0 → „3" — a felesleges tizedesek nélkül. */
private fun trimNumber(value: Double): String {
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return value.toString().replace('.', ',')
}
