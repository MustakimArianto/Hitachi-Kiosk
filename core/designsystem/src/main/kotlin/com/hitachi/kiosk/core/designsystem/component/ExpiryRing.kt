package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.reducedIf
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/** Circumference of the design's ring (`stroke-dasharray: 126`, a circle of radius 20 in a 48 unit box). */
const val EXPIRY_RING_DASH: Int = 126

private const val RING_VIEWBOX = 48f
private const val RING_RADIUS = 20f
private const val RING_STROKE = 5f
private const val RING_START_ANGLE = -90f
private const val FULL_SWEEP = 360f
private const val SECONDS_PER_MINUTE = 60
private const val SECOND_DIGITS = 2
private const val DEFAULT_RING_PX = 44

/**
 * The part of the ring that is still full: `remaining / total`, limited to `0..1`. A [totalSeconds] of zero or
 * less gives an empty ring (`Checkout.dc.html` `qrOffset`).
 */
fun expiryRingFraction(
    remainingSeconds: Int,
    totalSeconds: Int,
): Float = if (totalSeconds <= 0) 0f else (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)

/**
 * The design's `stroke-dashoffset`: `126 × (1 − remaining / total)`, so `0` is a full ring and `126` an empty one
 * (`Checkout.dc.html`).
 */
fun expiryRingDashOffset(
    remainingSeconds: Int,
    totalSeconds: Int,
): Float = EXPIRY_RING_DASH * (1f - expiryRingFraction(remainingSeconds, totalSeconds))

/** [seconds] as `m:ss` (`5:00`, `0:07`, `10:00`). Negative values show `0:00`. */
fun formatCountdown(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / SECONDS_PER_MINUTE
    val rest = (safe % SECONDS_PER_MINUTE).toString().padStart(SECOND_DIGITS, '0')
    return "$minutes:$rest"
}

/**
 * The expiry ring of the QR card (`Checkout.dc.html`): a track of `surfaceAlt` with an accent arc that starts at
 * the top and shrinks clockwise as [remainingSeconds] runs down to 0 of [totalSeconds]. The arc moves in 1 s
 * linear steps (PRD §11.4) and jumps with reduce motion. Decorative: the "Code expires in m:ss" text next to it
 * carries the meaning.
 *
 * @param size outer size of the ring; the stroke and radius scale with it.
 */
@Composable
fun ExpiryRing(
    remainingSeconds: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_RING_PX.dpx,
) {
    val colors = KioskTheme.colors
    val fraction = expiryRingFraction(remainingSeconds, totalSeconds)
    val sweep = remember { Animatable(fraction) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(fraction) {
        sweep.animateTo(
            fraction,
            tween<Float>(KioskMotion.Checkout.COUNTDOWN_STEP_MILLIS, easing = KioskEasing.Linear).reducedIf(reduce),
        )
    }
    Canvas(modifier = modifier.size(size).clearAndSetSemantics {}) {
        val unit = this.size.minDimension / RING_VIEWBOX
        val radius = RING_RADIUS * unit
        val stroke = RING_STROKE * unit
        drawCircle(color = colors.surfaceAlt, radius = radius, style = Stroke(width = stroke))
        val arc = sweep.value
        if (arc > 0f) {
            drawArc(
                color = colors.accent,
                startAngle = RING_START_ANGLE,
                sweepAngle = FULL_SWEEP * arc,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Preview(widthDp = 120, heightDp = 120)
@Composable
private fun ExpiryRingPreview() {
    KioskTheme(reduceMotion = true) { ExpiryRing(remainingSeconds = 225, totalSeconds = 300) }
}
