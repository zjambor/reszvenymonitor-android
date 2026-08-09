package hu.jamborz.reszvenymonitor.ui.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.jamborz.reszvenymonitor.AppContainer
import hu.jamborz.reszvenymonitor.BuildConfig
import hu.jamborz.reszvenymonitor.data.ApiException
import hu.jamborz.reszvenymonitor.domain.OhlcRow
import hu.jamborz.reszvenymonitor.domain.Transform
import hu.jamborz.reszvenymonitor.ui.monitor.ChartPanel
import hu.jamborz.reszvenymonitor.ui.monitor.ChartType
import hu.jamborz.reszvenymonitor.ui.theme.LocalMonitorColors
import hu.jamborz.reszvenymonitor.ui.theme.MonitorCard
import hu.jamborz.reszvenymonitor.ui.theme.PillGroup

/**
 * 5. fázis demó: a ChartPanel valós adattal (DEFAULT_TICKER, napi, 6M ablak).
 * A 6. fázis MonitorViewModel-je váltja ki — itt csak a grafikon vizuális
 * ellenőrzése a cél (gyertya/vonal, volumen-kapcsoló, Teljes nézet, legend).
 */
@Composable
fun ChartDemoCard(container: AppContainer) {
    val palette = LocalMonitorColors.current
    var rows by remember { mutableStateOf<List<OhlcRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf(ChartType.CANDLE) }
    var volume by remember { mutableStateOf(true) }
    var fitNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            rows = container.priceRepository.getDaily(BuildConfig.DEFAULT_TICKER)
        } catch (e: ApiException) {
            error = e.message
        }
    }

    MonitorCard {
        Text(
            text = "${BuildConfig.DEFAULT_TICKER} — napi, 6M ablak (5. fázis demó)",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillGroup(
                options = listOf("Gyertya", "Vonal"),
                selected = if (type == ChartType.CANDLE) "Gyertya" else "Vonal",
                onSelect = { type = if (it == "Gyertya") ChartType.CANDLE else ChartType.LINE },
            )
            PillGroup(
                options = listOf("Volumen"),
                selected = if (volume) "Volumen" else "",
                onSelect = { volume = !volume },
            )
            Text(
                text = "Teljes nézet",
                style = MaterialTheme.typography.labelLarge,
                color = palette.textDim,
                modifier = Modifier
                    .clickable { fitNonce++ }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }

        val daily = rows
        if (error != null) {
            Text(text = error!!, color = palette.down, style = MaterialTheme.typography.bodySmall)
        }
        val windowStart = daily?.let { d ->
            Transform.presetRange(d, Transform.Preset.M6)?.let { w ->
                d.indexOfFirst { it.date >= w.from }.takeIf { it >= 0 }
            }
        }
        ChartPanel(
            rows = daily ?: emptyList(),
            chartType = type,
            showVolume = volume,
            accentColor = palette.accent,
            currency = "USD",
            loading = daily == null && error == null,
            watermark = BuildConfig.DEFAULT_TICKER,
            initialWindowStart = windowStart,
            fitNonce = fitNonce,
        )
    }
}
