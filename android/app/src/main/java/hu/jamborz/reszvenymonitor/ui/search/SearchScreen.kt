package hu.jamborz.reszvenymonitor.ui.search

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.jamborz.reszvenymonitor.data.dto.PortfolioDto
import hu.jamborz.reszvenymonitor.data.dto.SearchHitDto
import hu.jamborz.reszvenymonitor.data.dto.TickerDto
import hu.jamborz.reszvenymonitor.ui.theme.BadgeKind
import hu.jamborz.reszvenymonitor.ui.theme.LocalMonitorColors
import hu.jamborz.reszvenymonitor.ui.theme.MonitorBadge
import hu.jamborz.reszvenymonitor.ui.theme.auroraBackground

/**
 * Teljes képernyős kereső — a webes ARIA-combobox mobil megfelelője.
 * Az elemek sorrendje a webes renderOptions szerinti (lásd SearchViewModel).
 */
@Composable
fun SearchScreen(
    state: SearchViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onPickTicker: (TickerDto) -> Unit,
    onPickPortfolio: (PortfolioDto) -> Unit,
    onPickHit: (SearchHitDto) -> Unit,
    onAdd: (String) -> Unit,
    onSearchExchanges: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalMonitorColors.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground(palette)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onSubmit = { if (state.canSearchExchanges) onSearchExchanges() },
                onBack = onBack,
                focusRequester = focusRequester,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            state.busyMessage?.let { BusyRow(it) }
            state.error?.let { ErrorRow(it) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val results = state.searchResults

                // --- Tőzsdei keresés állapotai -----------------------------
                if (state.searching) {
                    item { InfoRow("Keresés a tőzsdéken: ${quoted(state.query.trim())}…") }
                }
                state.searchError?.let { item { InfoRow(it, isError = true) } }

                if (results != null) {
                    item {
                        GroupHeader(
                            if (results.isNotEmpty()) "Tőzsdei találatok: ${quoted(state.query.trim())}"
                            else "Nincs tőzsdei találat: ${quoted(state.query.trim())}"
                        )
                    }
                    if (results.isEmpty()) {
                        item {
                            InfoRow(
                                "Ellenőrizd a szimbólumot, vagy keress a nevével " +
                                    "(pl. ${quoted("iShares Core S&P 500")}) vagy ISIN-nel."
                            )
                        }
                    }
                    val known = state.tickers.map { it.symbol.uppercase() }.toSet()
                    items(results, key = { "hit-${it.symbol}" }) { hit ->
                        SearchHitRow(
                            hit = hit,
                            alreadyKnown = hit.symbol.uppercase() in known,
                            onClick = { onPickHit(hit) },
                        )
                    }
                }

                // --- Helyi találatok csoportosítva --------------------------
                if (results == null) {
                    val stocks = state.localHits.filterNot { it.isEtf }
                    val etfs = state.localHits.filter { it.isEtf }

                    if (state.portfolioHits.isNotEmpty()) {
                        item { GroupHeader("Portfóliók") }
                        items(state.portfolioHits, key = { "p-${it.id}" }) { p ->
                            PortfolioRow(p) { onPickPortfolio(p) }
                        }
                    }
                    if (stocks.isNotEmpty()) {
                        item { GroupHeader("Részvények") }
                        items(stocks, key = { "s-${it.symbol}" }) { t ->
                            TickerRow(t) { onPickTicker(t) }
                        }
                    }
                    if (etfs.isNotEmpty()) {
                        item { GroupHeader("ETF-ek") }
                        items(etfs, key = { "e-${it.symbol}" }) { t ->
                            TickerRow(t) { onPickTicker(t) }
                        }
                    }

                    // Elgépelés-javaslat — csak helyi találat híján.
                    if (state.suggestions.isNotEmpty()) {
                        item { GroupHeader("Talán erre gondoltál") }
                        items(state.suggestions, key = { "n-${it.symbol}" }) { t ->
                            TickerRow(t) { onPickTicker(t) }
                        }
                    }

                    // Felvétel-opció szimbólumszerű, ismeretlen beírásra.
                    state.addCandidate?.let { candidate ->
                        item {
                            ActionRow(
                                text = "${quoted(candidate)} felvétele új tickerként",
                                onClick = { onAdd(candidate) },
                            )
                        }
                    }

                    // Tőzsdei keresés — helyi találat MELLETT is (más jegyzés kellhet).
                    if (state.canSearchExchanges) {
                        item {
                            ActionRow(
                                text = if (state.isIsinQuery) {
                                    "ISIN keresése a tőzsdéken: ${quoted(state.query.trim())}"
                                } else {
                                    "${quoted(state.query.trim())} keresése a tőzsdéken"
                                },
                                onClick = onSearchExchanges,
                            )
                        }
                    }

                    if (state.localHits.isEmpty() && state.suggestions.isEmpty() &&
                        state.addCandidate == null && !state.canSearchExchanges
                    ) {
                        item { InfoRow("Nincs találat") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val palette = LocalMonitorColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            textStyle = TextStyle(color = palette.text, fontSize = 15.sp),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(Color(0xB80A0D1A))
                        .border(1.dp, if (focused.value) palette.accent else palette.border, shape)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Ticker, cégnév vagy ISIN…",
                            color = palette.textFaint,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    val palette = LocalMonitorColors.current
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textFaint,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

/** Egy felvett ticker sora: színpötty, szimbólum, név, tőzsde- és ETF-badge. */
@Composable
private fun TickerRow(ticker: TickerDto, onClick: () -> Unit) {
    val palette = LocalMonitorColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(parseColor(ticker.color) ?: palette.accent),
        )
        Text(
            text = ticker.symbol,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
        )
        Text(
            text = ticker.name.orEmpty(),
            fontSize = 13.sp,
            color = palette.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ticker.exchange?.let { MonitorBadge(it) }
        if (ticker.isEtf) MonitorBadge("ETF", BadgeKind.Etf)
    }
}

/** Portfólió sora a keresőben — lila PORTFÓLIÓ-badge-dzsel, mint a weben. */
@Composable
private fun PortfolioRow(portfolio: PortfolioDto, onClick: () -> Unit) {
    val palette = LocalMonitorColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(palette.portfolio),
        )
        Text(
            text = portfolio.name,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = if (portfolio.items.isEmpty()) "üres" else "${portfolio.items.size} elem",
            fontSize = 13.sp,
            color = palette.textDim,
            modifier = Modifier.weight(1f),
        )
        MonitorBadge("Portfólió", BadgeKind.Portfolio)
    }
}

/**
 * Tőzsdei találat sora. A DEVIZA-badge itt a legfontosabb: ugyanaz az alap
 * tőzsdénként más devizában fut (IWDA.AS EUR vs SWDA.L GBp).
 */
@Composable
private fun SearchHitRow(hit: SearchHitDto, alreadyKnown: Boolean, onClick: () -> Unit) {
    val palette = LocalMonitorColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = hit.symbol,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Text(
                text = hit.name.orEmpty(),
                fontSize = 13.sp,
                color = palette.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            hit.exchange?.let { MonitorBadge(it) }
            hit.currency?.let { MonitorBadge(it, BadgeKind.Currency) }
            if (hit.isEtf) MonitorBadge("ETF", BadgeKind.Etf)
            if (alreadyKnown) MonitorBadge("már felvéve", BadgeKind.Known)
        }
    }
}

/** Akció-sor (felvétel / tőzsdei keresés) — akcent-kerettel kiemelve. */
@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    val palette = LocalMonitorColors.current
    val shape = MaterialTheme.shapes.small
    Text(
        text = text,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.accent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(palette.accentSoft)
            .border(1.dp, palette.accentRing, shape)
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun InfoRow(text: String, isError: Boolean = false) {
    val palette = LocalMonitorColors.current
    Text(
        text = text,
        fontSize = 13.sp,
        color = if (isError) palette.down else palette.textFaint,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
    )
}

@Composable
private fun BusyRow(text: String) {
    val palette = LocalMonitorColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = palette.accent, strokeWidth = 2.dp)
        Text(text = text, fontSize = 13.sp, color = palette.textDim)
    }
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

/**
 * Magyar idézőjelek közé zárt szöveg. Escape-elt unicode, mert a nyers „ és "
 * karakterek forrásban könnyen összekeverednek a string-határolóval.
 */
private fun quoted(text: String): String = "\u201E$text\u201D"

/** `#rrggbb` → Color; hibás/hiányzó értéknél null (a hívó akcentre esik vissza). */
private fun parseColor(hex: String?): Color? {
    val clean = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    return try {
        Color(clean.toLong(16) or 0xFF000000L)
    } catch (e: NumberFormatException) {
        null
    }
}
