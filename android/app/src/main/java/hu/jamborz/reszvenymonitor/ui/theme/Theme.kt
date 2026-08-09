package hu.jamborz.reszvenymonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Kizárólag sötét téma (a web-app is az). A Material 3 ColorScheme csak a
 * standard komponensek (Snackbar, dialógus…) miatt kap értékeket; a saját
 * komponensek a [LocalMonitorColors] tokenekből dolgoznak.
 *
 * @param accent a kiválasztott ticker színe — a webes `--accent` CSS-változó
 * futásidejű átállításának megfelelője.
 */
@Composable
fun MonitorTheme(
    accent: Color = MonitorPalette().accent,
    content: @Composable () -> Unit,
) {
    val palette = remember(accent) { MonitorPalette(accent = accent) }
    val colorScheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        secondary = palette.textDim,
        onSecondary = palette.bg,
        background = palette.bg,
        onBackground = palette.text,
        surface = palette.bgDeep,
        onSurface = palette.text,
        surfaceVariant = palette.surfaceStrong,
        onSurfaceVariant = palette.textDim,
        outline = palette.borderStrong,
        outlineVariant = palette.border,
        error = palette.down,
        onError = palette.onAccent,
    )
    CompositionLocalProvider(LocalMonitorColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MonitorTypography,
            shapes = MonitorShapes,
            content = content,
        )
    }
}
