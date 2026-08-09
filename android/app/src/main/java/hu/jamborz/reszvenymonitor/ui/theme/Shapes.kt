package hu.jamborz.reszvenymonitor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** --radius: 14px; --radius-sm: 10px; a pill/badge teljesen kerek. */
val MonitorShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(14.dp),
)

val PillShape = RoundedCornerShape(percent = 50)
