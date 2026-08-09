package hu.jamborz.reszvenymonitor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * A web 15px-es alapmérete és súlyai. Betűtípus: rendszer-alap (Roboto) —
 * a webes font-stack (Segoe UI, …, Roboto) maga is erre esik vissza más platformon.
 */
val MonitorTypography = Typography(
    // body: 15px / 1.5 sorköz
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        lineHeight = 22.5.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    // képernyőcím (fejléc h1 mobil-mérete)
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.01.em,
    ),
    // identitás-sáv szimbóluma
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    ),
    // pill / gomb-felirat: 13px, 600-as súly
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // stat-kártya címke: 11px, 600, ritkított, kiskapitális hatás (uppercase a hívónál)
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.09.em,
    ),
)
