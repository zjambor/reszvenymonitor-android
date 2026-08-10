package hu.jamborz.reszvenymonitor.ui.monitor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CandleEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import android.view.MotionEvent
import hu.jamborz.reszvenymonitor.domain.Format
import hu.jamborz.reszvenymonitor.domain.OhlcRow
import hu.jamborz.reszvenymonitor.ui.theme.LocalMonitorColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 'candle' | 'line' — a webes setType megfelelője. */
enum class ChartType { CANDLE, LINE }

// A webes chart.js színei
private const val UP = 0xFF34D399.toInt()
private const val DOWN = 0xFFFB7185.toInt()
private const val UP_SOFT = 0x8034D399.toInt()   // rgba(52,211,153,.5)
private const val DOWN_SOFT = 0x80FB7185.toInt() // rgba(251,113,133,.5)
private const val AXIS_TEXT = 0xFF8FA0C9.toInt()
private const val GRID = 0x127C8CFF          // rgba(124,140,255,.07)
private const val AXIS_LINE = 0x387C8CFF     // rgba(124,140,255,.22)
private const val CROSSHAIR = 0x6694A3B8     // rgba(148,163,184,.4)

/**
 * A webes StockChart (chart.js) megfelelője: gyertya/vonal a fő charton,
 * volumen KÜLÖN chartban, X-viewportjuk gesztusra szinkronizálva; a legend-sor
 * a kiemelt (vagy utolsó) bar Ny/Max/Min/Z/Vol értékeit és változását mutatja.
 * Az MPAndroidChart a [ChartController] mögé van rejtve — a könyvtár később a
 * UI-réteg érintése nélkül cserélhető (TERV-ANDROID.md, Kockázatok).
 *
 * @param rows aggregált, MEGJELENÍTÉSI devizás barok
 * @param initialWindowStart az aktuális preset-ablak kezdő indexe (a teljes
 * adatsor betöltve marad — pan-nel a korábbi napok is elérhetők, mint a weben)
 * @param fitNonce növelése „Teljes nézet"-et vált ki
 */
@Composable
fun ChartPanel(
    rows: List<OhlcRow>,
    chartType: ChartType,
    showVolume: Boolean,
    accentColor: Color,
    currency: String?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    watermark: String = "",
    initialWindowStart: Int? = null,
    fitNonce: Int = 0,
) {
    val palette = LocalMonitorColors.current
    val controller = remember { ChartController() }
    var highlightIndex by remember { mutableStateOf<Int?>(null) }
    controller.onHighlight = { highlightIndex = it }

    Column(modifier = modifier) {
        // Legend-sor: kiemelt bar, különben az utolsó (webes _emitLast viselkedés).
        val bar = rows.getOrNull(highlightIndex ?: rows.lastIndex)
        val prev = rows.getOrNull((highlightIndex ?: rows.lastIndex) - 1)
        LegendRow(bar, prev, currency)

        when {
            loading -> ChartSkeleton()
            rows.isEmpty() -> ChartEmpty()
            else -> {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    // Halvány ticker-watermark a chart mögött (a háttér átlátszó).
                    if (watermark.isNotEmpty()) {
                        Text(
                            text = watermark,
                            modifier = Modifier.align(Alignment.Center),
                            color = accentColor.copy(alpha = 0.08f),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    AndroidView(
                        factory = { ctx -> controller.createMainChart(ctx) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (showVolume) {
                    AndroidView(
                        factory = { ctx -> controller.createVolumeChart(ctx) },
                        modifier = Modifier.fillMaxWidth().height(90.dp).padding(top = 2.dp),
                    )
                }
                LaunchedEffect(rows, chartType, accentColor, showVolume) {
                    controller.setData(rows, chartType, accentColor.toArgb())
                }
                LaunchedEffect(rows, initialWindowStart) {
                    if (initialWindowStart != null) controller.applyWindow(initialWindowStart)
                }
                LaunchedEffect(fitNonce) {
                    if (fitNonce > 0) controller.fitAll()
                }
            }
        }
    }
}

/** A webes .legend sor: dátum + Ny/Max/Min/Z/Vol + változás, hu-HU formázással. */
@Composable
private fun LegendRow(bar: OhlcRow?, prev: OhlcRow?, currency: String?) {
    val palette = LocalMonitorColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (bar != null) Format.formatDateHu(bar.date) else "—",
            fontSize = 11.5.sp,
            color = palette.textFaint,
        )
        LegendItem("Ny", bar?.let { Format.formatPriceIn(it.open, currency) })
        LegendItem("Max", bar?.let { Format.formatPriceIn(it.high, currency) })
        LegendItem("Min", bar?.let { Format.formatPriceIn(it.low, currency) })
        LegendItem("Z", bar?.let { Format.formatPriceIn(it.close, currency) })
        LegendItem("Vol", bar?.let { Format.formatVolume(it.volume) })
        val pct = if (bar != null && prev != null && prev.close != 0.0) {
            (bar.close - prev.close) / prev.close * 100
        } else null
        Text(
            text = pct?.let { Format.formatPct(it) } ?: "—",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                pct == null -> palette.textDim
                pct > 0 -> palette.up
                pct < 0 -> palette.down
                else -> palette.textDim
            },
        )
    }
}

@Composable
private fun LegendItem(label: String, value: String?) {
    val palette = LocalMonitorColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.textFaint)
        Text(value ?: "—", fontSize = 11.5.sp, color = palette.textDim, maxLines = 1)
    }
}

/** A webes skeleton-bar rács pulzáló megfelelője. */
@Composable
private fun ChartSkeleton() {
    val palette = LocalMonitorColors.current
    val pulse by rememberInfiniteTransition(label = "skeleton")
        .animateFloat(
            initialValue = 0.35f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
    val heights = listOf(0.45f, 0.62f, 0.38f, 0.7f, 0.55f, 0.8f, 0.5f, 0.66f, 0.42f, 0.74f, 0.58f, 0.68f)
    Row(
        modifier = Modifier.fillMaxWidth().height(300.dp).alpha(pulse),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(300.dp * h)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.surfaceStrong),
            )
        }
    }
}

@Composable
private fun ChartEmpty() {
    val palette = LocalMonitorColors.current
    Box(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Nincs megjeleníthető árfolyamadat.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textFaint,
        )
    }
}

/**
 * Vékony absztrakció az MPAndroidChart fölött: fő chart (gyertya/area-vonal
 * CombinedChartban) + volumen-BarChart, X-viewport szinkronnal. A volumen
 * chart érintése tiltott — a fő chart vezet, a volumen követi (bevált minta).
 */
private class ChartController {
    private var mainChart: CombinedChart? = null
    private var volumeChart: BarChart? = null
    private var rows: List<OhlcRow> = emptyList()
    private var lastType: ChartType = ChartType.CANDLE
    private var lastAccent: Int = 0xFF7C8CFF.toInt()
    var onHighlight: ((Int?) -> Unit)? = null

    fun createMainChart(context: Context): CombinedChart {
        val chart = CombinedChart(context)
        baseSetup(chart)
        chart.drawOrder = arrayOf(
            CombinedChart.DrawOrder.LINE,
            CombinedChart.DrawOrder.CANDLE,
        )
        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val idx = e?.x?.toInt()
                onHighlight?.invoke(idx?.takeIf { it in rows.indices })
                // A crosshair a volumen-panelen is jelenjen meg.
                if (h != null) volumeChart?.highlightValue(h.x, 0, false)
            }

            override fun onNothingSelected() {
                onHighlight?.invoke(null)
                volumeChart?.highlightValue(null)
            }
        })
        chart.onChartGestureListener = object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, mode: ChartTouchListener.ChartGesture?) {}
            override fun onChartGestureEnd(me: MotionEvent?, mode: ChartTouchListener.ChartGesture?) {
                syncViewports()
            }
            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {
                syncViewports()
            }
            override fun onChartSingleTapped(me: MotionEvent?) {}
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, vX: Float, vY: Float) {
                syncViewports()
            }
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
                syncViewports()
            }
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                syncViewports()
            }
        }
        mainChart = chart
        applyIfReady()
        return chart
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createVolumeChart(context: Context): BarChart {
        val chart = BarChart(context)
        baseSetup(chart)
        chart.setTouchEnabled(false) // a fő chart vezet
        chart.axisRight.setLabelCount(2, false)
        chart.axisRight.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = Format.formatVolume(value.toDouble())
        }
        chart.axisRight.axisMinimum = 0f
        chart.xAxis.setDrawLabels(false)
        volumeChart = chart
        applyIfReady()
        return chart
    }

    /** Közös stílus — a webes layout/grid/tengely színekkel. */
    private fun baseSetup(chart: BarLineChartBase<*>) {
        // MEGJEGYZÉS a rajzrétegről: az MPAndroidChart nagy adathalmazra a
        // szoftveres réteget (LAYER_TYPE_SOFTWARE) ajánlja. Emulátoron MÉRVE ez
        // NEM segített a MIND-preset ~900 gyertyáján (grafikon-hurcolás medián
        // képkocka-ideje 101 ms → 117 ms, tehát rosszabb), ezért marad az
        // alapértelmezett hardveres réteg.
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        chart.setDrawGridBackground(false)
        chart.setNoDataText("")
        chart.isScaleYEnabled = false
        chart.setPinchZoom(true)
        chart.isDoubleTapToZoomEnabled = true
        chart.isAutoScaleMinMaxEnabled = true // az Y a látható ablakra skálázódik, mint a weben
        chart.minOffset = 0f
        chart.setExtraOffsets(4f, 4f, 4f, 4f)

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = AXIS_TEXT
            textSize = 10f
            gridColor = GRID
            axisLineColor = AXIS_LINE
            granularity = 1f
            // Kevés, ritkított felirat — zsúfolt mobilszélességen nem csúsznak össze.
            setLabelCount(4, false)
            setAvoidFirstLastClipping(true)
        }
        chart.axisLeft.isEnabled = false
        chart.axisRight.apply {
            isEnabled = true
            textColor = AXIS_TEXT
            textSize = 10f
            gridColor = GRID
            axisLineColor = AXIS_LINE
            // Fix tengelyszélesség: a fő és a volumen chart plot-területe
            // vízszintesen fedésben marad (enélkül elcsúszna az X-szinkron).
            minWidth = 52f
            maxWidth = 52f
        }
    }

    fun setData(rows: List<OhlcRow>, type: ChartType, accent: Int) {
        this.rows = rows
        this.lastType = type
        this.lastAccent = accent
        applyIfReady()
    }

    private fun applyIfReady() {
        val main = mainChart ?: return
        if (rows.isEmpty()) {
            main.clear()
            volumeChart?.clear()
            return
        }

        val labelFormatter = makeXFormatter()

        val combined = CombinedData()
        if (lastType == ChartType.CANDLE) {
            val entries = rows.mapIndexed { i, r ->
                CandleEntry(i.toFloat(), r.high.toFloat(), r.low.toFloat(), r.open.toFloat(), r.close.toFloat())
            }
            val set = CandleDataSet(entries, "ohlc").apply {
                increasingColor = UP
                increasingPaintStyle = android.graphics.Paint.Style.FILL
                decreasingColor = DOWN
                decreasingPaintStyle = android.graphics.Paint.Style.FILL
                neutralColor = UP
                setShadowColorSameAsCandle(true)
                shadowWidth = 1f
                barSpace = 0.15f
                setDrawValues(false)
                highLightColor = CROSSHAIR
                axisDependency = YAxis.AxisDependency.RIGHT
            }
            combined.setData(CandleData(set))
            combined.setData(LineData()) // üres — a CombinedChart mindkettőt várja
        } else {
            // A webes AreaSeries: akcent-vonal + akcent 0.35→0.02 kitöltés.
            val entries = rows.mapIndexed { i, r -> Entry(i.toFloat(), r.close.toFloat()) }
            val set = LineDataSet(entries, "close").apply {
                color = lastAccent
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
                setDrawFilled(true)
                fillDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(withAlpha(lastAccent, 0.35f), withAlpha(lastAccent, 0.02f)),
                )
                highLightColor = CROSSHAIR
                axisDependency = YAxis.AxisDependency.RIGHT
            }
            combined.setData(LineData(set))
            combined.setData(CandleData())
        }
        main.xAxis.valueFormatter = labelFormatter
        main.xAxis.axisMinimum = -0.5f
        main.xAxis.axisMaximum = rows.size - 0.5f
        main.data = combined
        main.invalidate()

        volumeChart?.let { vol ->
            val bars = rows.mapIndexed { i, r -> BarEntry(i.toFloat(), r.volume.toFloat()) }
            val set = BarDataSet(bars, "volume").apply {
                colors = rows.map { if (it.close >= it.open) UP_SOFT else DOWN_SOFT }
                setDrawValues(false)
                highLightColor = CROSSHAIR
                axisDependency = YAxis.AxisDependency.RIGHT
            }
            vol.xAxis.axisMinimum = -0.5f
            vol.xAxis.axisMaximum = rows.size - 0.5f
            vol.data = BarData(set).apply { barWidth = 0.7f }
            vol.invalidate()
            syncViewports()
        }
    }

    /** Preset-ablak: a teljes sor betöltve marad, a nézet az ablakra ugrik. */
    fun applyWindow(startIndex: Int) {
        val main = mainChart ?: return
        if (rows.isEmpty()) return
        val visible = (rows.size - startIndex).coerceAtLeast(2)
        main.fitScreen()
        main.setVisibleXRangeMaximum(visible.toFloat())
        main.moveViewToX(startIndex.toFloat())
        // A korlát feloldása, hogy kifelé is lehessen zoomolni (webes viselkedés).
        main.setVisibleXRangeMaximum(rows.size.toFloat())
        syncViewports()
    }

    /** „Teljes nézet" — a webes fitContent. */
    fun fitAll() {
        mainChart?.fitScreen()
        syncViewports()
    }

    /** A volumen-viewport követi a főt (X-skála és -eltolás másolása). */
    private fun syncViewports() {
        val main = mainChart ?: return
        val vol = volumeChart ?: return
        val src = FloatArray(9)
        main.viewPortHandler.matrixTouch.getValues(src)
        val dstMatrix = Matrix()
        val dst = FloatArray(9)
        vol.viewPortHandler.matrixTouch.getValues(dst)
        dst[Matrix.MSCALE_X] = src[Matrix.MSCALE_X]
        dst[Matrix.MTRANS_X] = src[Matrix.MTRANS_X]
        dstMatrix.setValues(dst)
        vol.viewPortHandler.refresh(dstMatrix, vol, true)
    }

    private fun makeXFormatter(): ValueFormatter {
        val sameYear = rows.isNotEmpty() &&
            rows.first().date.take(4) == rows.last().date.take(4)
        val hu = Locale("hu", "HU")
        val short = DateTimeFormatter.ofPattern("MMM d.", hu)
        val long = DateTimeFormatter.ofPattern("yyyy. MMM", hu)
        val fmt = if (sameYear) short else long
        return object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                val date = rows.getOrNull(i)?.date ?: return ""
                return try {
                    LocalDate.parse(date).format(fmt)
                } catch (e: Exception) {
                    date
                }
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
}
