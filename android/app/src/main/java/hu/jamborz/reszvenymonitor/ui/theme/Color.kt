package hu.jamborz.reszvenymonitor.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A web-app styles.css `:root` design-tokenjeinek 1:1 megfelelője.
 * Az accent futásidőben a kiválasztott ticker `color` mezőjét veszi fel
 * (ahogy a weben a JS állítja a CSS-változót); a soft/ring/glow változatok
 * ugyanazokkal az alfa-értékekkel származnak belőle, mint a weben.
 */
@Immutable
data class MonitorPalette(
    // Alapszínek
    val bg: Color = Color(0xFF0B0E1A),            // --bg
    val bgDeep: Color = Color(0xFF070912),        // --bg-deep
    val surface: Color = Color(0x0D94A3CC),       // --surface: rgba(148,163,204,.05)
    val surfaceStrong: Color = Color(0x1794A3CC), // --surface-strong: rgba(148,163,204,.09)
    val border: Color = Color(0x248B99CC),        // --border: rgba(139,153,204,.14)
    val borderStrong: Color = Color(0x478B99CC),  // --border-strong: rgba(139,153,204,.28)

    // Szöveg
    val text: Color = Color(0xFFE8EBF8),          // --text
    val textDim: Color = Color(0xFF93A0C4),       // --text-dim
    val textFaint: Color = Color(0xFF5D688C),     // --text-faint

    // Akcent — alapérték #7C8CFF, futásidőben a ticker színe
    val accent: Color = Color(0xFF7C8CFF),

    // Szemantikus színek
    val up: Color = Color(0xFF34D399),            // --up
    val down: Color = Color(0xFFFB7185),          // --down
    val warn: Color = Color(0xFFFBBF24),          // --warn

    // Badge-színek (styles.css .badge-etf / .badge-ccy / .badge-portfolio)
    val etf: Color = Color(0xFF22D3EE),
    val portfolio: Color = Color(0xFFA78BFA),
) {
    val accentSoft: Color get() = accent.copy(alpha = 0.15f)  // --accent-soft
    val accentRing: Color get() = accent.copy(alpha = 0.45f)  // --accent-ring
    val accentGlow: Color get() = accent.copy(alpha = 0.30f)  // --accent-glow

    /** A .btn-primary sötét felirata (#0a0d18) — világos akcentgombon. */
    val onAccent: Color get() = Color(0xFF0A0D18)
}

val LocalMonitorColors = staticCompositionLocalOf { MonitorPalette() }
