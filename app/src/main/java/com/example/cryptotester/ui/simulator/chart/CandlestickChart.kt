package com.example.cryptotester.ui.simulator.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.example.cryptotester.domain.model.Candle
import com.example.cryptotester.ui.theme.ChartCrosshair
import com.example.cryptotester.ui.theme.ChartGridLine
import com.example.cryptotester.ui.theme.CryptoGreen
import com.example.cryptotester.ui.theme.CryptoRed
import com.example.cryptotester.ui.theme.DarkBackground
import com.example.cryptotester.ui.theme.TextPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

@Composable
fun CandlestickChart(
    candles: List<Candle>,
    crosshairOffset: Offset?,
    onCrosshairChange: (Offset?, Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) return

    val coroutineScope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }

    // Visible candle range controlled by pan & zoom
    var visibleCountFloat by remember { mutableFloatStateOf(60f.coerceAtMost(candles.size.toFloat())) }
    var scrollOffsetFloat by remember { mutableFloatStateOf(0f) }

    val clampedVisibleCount = visibleCountFloat.toInt().coerceIn(10, candles.size)
    val maxOffset = (candles.size - clampedVisibleCount).coerceAtLeast(0)
    val clampedOffset = scrollOffsetFloat.toInt().coerceIn(0, maxOffset)

    val visibleCandles = candles.subList(
        (candles.size - clampedVisibleCount - clampedOffset).coerceAtLeast(0),
        (candles.size - clampedOffset).coerceAtMost(candles.size)
    )

    val priceAxisWidth = 70f

    // Calculate price bounds
    val highestPrice = if (visibleCandles.isNotEmpty()) visibleCandles.maxOf { it.high } else 1.0
    val lowestPrice = if (visibleCandles.isNotEmpty()) visibleCandles.minOf { it.low } else 0.0
    val rawRange = (highestPrice - lowestPrice).let { if (it == 0.0) 1.0 else it }
    val pad = rawRange * 0.05
    val adjHigh = highestPrice + pad
    val adjLow = lowestPrice - pad
    val adjRange = adjHigh - adjLow

    // Use rememberUpdatedState so pointerInput(Unit) is stable and NEVER gets cancelled
    val currentCandles by rememberUpdatedState(candles)
    val currentAdjHigh by rememberUpdatedState(adjHigh)
    val currentAdjRange by rememberUpdatedState(adjRange)
    val currentOnCrosshairChange by rememberUpdatedState(onCrosshairChange)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    flingJob?.cancel()

                    var isNavigating = false
                    var isZooming = false
                    var accumulatedPan = Offset.Zero
                    val touchSlop = viewConfiguration.touchSlop
                    val downPosition = down.position
                    var lastPosition = down.position

                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        if (pointerCount >= 2) {
                            // Pinch-to-zoom (2 fingers)
                            isNavigating = true
                            isZooming = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            event.changes.forEach { it.consume() }

                            if (zoom != 1f) {
                                val newVisible = (visibleCountFloat / zoom).coerceIn(
                                    10f,
                                    currentCandles.size.toFloat().coerceAtLeast(10f)
                                )
                                visibleCountFloat = newVisible
                            }

                            if (pan.x != 0f) {
                                val candleWidth = (size.width - priceAxisWidth) / clampedVisibleCount
                                val delta = pan.x / candleWidth
                                val newMax = (currentCandles.size - clampedVisibleCount).coerceAtLeast(0)
                                scrollOffsetFloat = (scrollOffsetFloat + delta).coerceIn(0f, newMax.toFloat())
                            }
                        } else if (pointerCount == 1) {
                            val change = event.changes.first()
                            if (change.pressed) {
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val panDelta = change.position - lastPosition
                                lastPosition = change.position
                                accumulatedPan += panDelta

                                if (accumulatedPan.getDistance() > touchSlop || isNavigating) {
                                    isNavigating = true
                                    change.consume() // Consume to lock out parent vertical scroll!

                                    // Real exchange direction:
                                    // Dragging right pulls older history into view (increases offset)
                                    // Dragging left pulls newer history into view (decreases offset)
                                    val candleWidth = (size.width - priceAxisWidth) / clampedVisibleCount
                                    val delta = panDelta.x / candleWidth
                                    val newMax = (currentCandles.size - clampedVisibleCount).coerceAtLeast(0)
                                    scrollOffsetFloat = (scrollOffsetFloat + delta).coerceIn(0f, newMax.toFloat())
                                }
                            } else {
                                // Finger lifted
                                if (!isNavigating) {
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!isNavigating) {
                        // TAP DETECTED: place crosshair & report price
                        down.consume()
                        val chartHeight = size.height.toFloat()
                        val targetY = downPosition.y.coerceIn(0f, chartHeight)
                        val price = currentAdjHigh - (targetY / chartHeight) * currentAdjRange
                        currentOnCrosshairChange(downPosition, price)
                    } else if (!isZooming) {
                        // SWIPE DETECTED: compute fling velocity and coast smoothly
                        val velocity = velocityTracker.calculateVelocity()
                        val vx = velocity.x
                        if (abs(vx) > 150f) {
                            val candleWidth = (size.width - priceAxisWidth) / clampedVisibleCount
                            val initialCandleVelocity = vx / candleWidth

                            flingJob = coroutineScope.launch {
                                var currentVelocity = initialCandleVelocity
                                var lastTime = withFrameNanos { it }
                                while (abs(currentVelocity) > 0.5f) {
                                    withFrameNanos { now ->
                                        val dt = (now - lastTime) / 1_000_000_000f
                                        lastTime = now
                                        val delta = currentVelocity * dt
                                        val newMax = (currentCandles.size - clampedVisibleCount).coerceAtLeast(0)
                                        scrollOffsetFloat = (scrollOffsetFloat + delta).coerceIn(0f, newMax.toFloat())
                                        currentVelocity *= exp(-4.5f * dt) // Smooth natural deceleration
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (visibleCandles.isEmpty()) return@Canvas

        val chartWidth = size.width - priceAxisWidth
        val chartHeight = size.height

        // Draw grid lines
        drawPriceGrid(chartWidth, chartHeight, adjHigh, adjLow, adjRange, priceAxisWidth)

        // Draw candles
        val candleWidth = chartWidth / visibleCandles.size
        val bodyWidthRatio = 0.6f

        visibleCandles.forEachIndexed { index, candle ->
            val centerX = index * candleWidth + candleWidth / 2f
            val isGreen = candle.close >= candle.open

            val color = if (isGreen) CryptoGreen else CryptoRed

            val wickTopY = ((adjHigh - candle.high) / adjRange * chartHeight).toFloat()
            val wickBottomY = ((adjHigh - candle.low) / adjRange * chartHeight).toFloat()

            // Wick (thin line)
            drawLine(
                color = color,
                start = Offset(centerX, wickTopY),
                end = Offset(centerX, wickBottomY),
                strokeWidth = 1.5f
            )

            // Body (rectangle)
            val bodyTop = ((adjHigh - maxOf(candle.open, candle.close)) / adjRange * chartHeight).toFloat()
            val bodyBottom = ((adjHigh - minOf(candle.open, candle.close)) / adjRange * chartHeight).toFloat()
            val bodyHeight = (bodyBottom - bodyTop).coerceAtLeast(1f)
            val bodyWidth = candleWidth * bodyWidthRatio

            drawRect(
                color = color,
                topLeft = Offset(centerX - bodyWidth / 2f, bodyTop),
                size = Size(bodyWidth, bodyHeight)
            )
        }

        // Draw Crosshair if active
        if (crosshairOffset != null) {
            val targetX = crosshairOffset.x.coerceIn(0f, chartWidth)
            val targetY = crosshairOffset.y.coerceIn(0f, chartHeight)

            // Find candle index under crosshair
            val candleIndex = (targetX / candleWidth).toInt().coerceIn(0, visibleCandles.size - 1)
            val candleCenterX = candleIndex * candleWidth + candleWidth / 2f

            // Price at crosshair level
            val crosshairPrice = adjHigh - (targetY / chartHeight) * adjRange

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))

            // Vertical line through candle
            drawLine(
                color = ChartCrosshair,
                start = Offset(candleCenterX, 0f),
                end = Offset(candleCenterX, chartHeight),
                strokeWidth = 1f,
                pathEffect = dashEffect
            )

            // Horizontal line across chart
            drawLine(
                color = ChartCrosshair,
                start = Offset(0f, targetY),
                end = Offset(chartWidth, targetY),
                strokeWidth = 1f,
                pathEffect = dashEffect
            )

            // Price badge on the right price axis
            val badgeHeight = 22f
            val badgeWidth = priceAxisWidth
            drawRect(
                color = TextPrimary,
                topLeft = Offset(chartWidth, targetY - badgeHeight / 2f),
                size = Size(badgeWidth, badgeHeight)
            )

            val badgeTextPaint = android.graphics.Paint().apply {
                color = DarkBackground.hashCode()
                textSize = 24f
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.LEFT
            }

            drawContext.canvas.nativeCanvas.drawText(
                formatPrice(crosshairPrice),
                chartWidth + 4f,
                targetY + 8f,
                badgeTextPaint
            )
        }
    }
}

private fun DrawScope.drawPriceGrid(
    chartWidth: Float,
    chartHeight: Float,
    highPrice: Double,
    lowPrice: Double,
    priceRange: Double,
    priceAxisWidth: Float
) {
    val gridLines = 5
    val textPaint = android.graphics.Paint().apply {
        color = 0xFF8B949E.toInt()
        textSize = 28f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.LEFT
    }

    for (i in 0..gridLines) {
        val y = chartHeight * i / gridLines
        val price = highPrice - priceRange * i / gridLines

        drawLine(
            color = ChartGridLine,
            start = Offset(0f, y),
            end = Offset(chartWidth, y),
            strokeWidth = 0.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )

        // Price label
        drawContext.canvas.nativeCanvas.drawText(
            formatPrice(price),
            chartWidth + 8f,
            y + 10f,
            textPaint
        )
    }
}

private fun formatPrice(price: Double): String {
    return when {
        price >= 1000 -> "%.0f".format(price)
        price >= 1 -> "%.2f".format(price)
        price >= 0.01 -> "%.4f".format(price)
        else -> "%.6f".format(price)
    }
}
