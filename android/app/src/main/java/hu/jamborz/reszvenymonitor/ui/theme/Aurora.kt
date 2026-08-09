package hu.jamborz.reszvenymonitor.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A webes `.aurora` háttér portja: három rögzített radiális gradiens
 * (indigó, lila, türkiz) + egy halvány akcent-folt a bgDeep→bg alapon.
 * A lassú drift-animáció szándékosan kimarad (akkumulátor, TERV-ANDROID.md);
 * a statikus aurora adja a karaktert.
 */
fun Modifier.auroraBackground(palette: MonitorPalette): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val base = maxOf(w, h)

    // linear-gradient(180deg, --bg-deep, --bg 30%)
    drawRect(
        Brush.verticalGradient(
            0.0f to palette.bgDeep,
            0.3f to palette.bg,
            1.0f to palette.bg,
        )
    )

    fun glow(color: Color, cx: Float, cy: Float, radius: Float) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = Offset(cx, cy),
                radius = radius,
            )
        )
    }

    // radial-gradient(60rem 42rem at 12% -12%, rgba(99,102,241,.17), transparent 62%)
    glow(Color(0x2B6366F1), w * 0.12f, -h * 0.12f, base * 0.85f)
    // radial-gradient(52rem 38rem at 92% -4%, rgba(168,85,247,.11), transparent 60%)
    glow(Color(0x1CA855F7), w * 0.92f, -h * 0.04f, base * 0.72f)
    // radial-gradient(72rem 52rem at 50% 118%, rgba(45,212,191,.08), transparent 62%)
    glow(Color(0x142DD4BF), w * 0.50f, h * 1.18f, base * 1.0f)
    // .aurora::after — akcent-folt 30%/22%-nál, effektív alfa: 0.30 × 0.12
    glow(palette.accent.copy(alpha = 0.036f), w * 0.30f, h * 0.22f, base * 0.55f)
}
