package com.hitachi.kiosk.feature.complete

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.reducedIf
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// The celebration of `Complete.dc.html`: flood, badge, check, ring waves and confetti. One linear clock runs from 0 to
// TIMELINE_END_MILLIS and every element derives its own progress from it, as the CSS keyframes do with their delays.
// Reduce motion starts the clock at its end, so the final frame shows at once (NFR-009).

/** The clock runs to the end of the last animation of the screen (the receipt prints from 2100 ms for 1800 ms). */
internal const val TIMELINE_END_MILLIS =
    (KioskMotion.Complete.RECEIPT_PRINT_DELAY_MILLIS + KioskMotion.Complete.RECEIPT_PRINT_MILLIS).toFloat()

/** Where the celebration is centred, in design px (`left: 540px; top: 400px`). */
private const val CENTER_X_PX = 540
private const val CENTER_Y_PX = 400

/** The flood grows to full size over the first 55 % of its animation and fades out over the rest. */
private const val FLOOD_GROW_FRACTION = 0.55f
private const val FLOOD_ORIGIN_Y = 0.24f

/** `circle(150%)`: 150 % of the reference radius, `sqrt((w² + h²) / 2)`. */
private const val FLOOD_RADIUS_FACTOR = 1.5f
private const val HALF = 0.5f

private const val BADGE_SIZE_PX = 240
private const val BADGE_TOP_PX = 280
private const val BADGE_PEAK_FRACTION = 0.6f
private const val BADGE_PEAK_SCALE = 1.06f
private const val BADGE_SHADOW_PX = 30
private const val BADGE_SHADOW_ALPHA = 0.3f
private const val CHECK_SIZE_PX = 128
private const val CHECK_VIEWBOX = 24f
private const val CHECK_STROKE = 2.2f
private const val CHECK_PATH = "M5 12.5l4.5 4.5L19 7.5"

/** The design draws the check with `stroke-dasharray: 60`, which is longer than the path itself. */
private const val CHECK_DASH = 60f

private const val RING_SIZE_PX = 260
private const val RING_ALPHA = 0.6f
private const val RING_GROWTH = 1.2f
private const val RING_ACCENT_STROKE_PX = 3
private const val RING_INK_STROKE_PX = 2

private const val CONFETTI_COUNT = 20
private const val CONFETTI_PEAK_FRACTION = 0.15f
private const val CONFETTI_START_SCALE = 0.3f
private const val CONFETTI_MARGIN_PX = 6

private val ConfettiEasing: Easing = CubicBezierEasing(0.15f, 0.6f, 0.3f, 1f)

/** One confetti dot: flies out along [degrees] by [distancePx], starting [delayMillis] into the screen. */
private class ConfettiBit(
    val degrees: Float,
    val sizePx: Int,
    val distancePx: Float,
    val delayMillis: Int,
    val colorIndex: Int,
)

/** The 20 dots of the design: angle `i × 18° + (i % 3) × 5°`, sizes 14/10/7, distance 220–358, five colors. */
private val ConfettiBits: List<ConfettiBit> =
    List(CONFETTI_COUNT) { i ->
        ConfettiBit(
            degrees = i * 18f + (i % 3) * 5f,
            sizePx =
                when (i % 3) {
                    0 -> 14
                    1 -> 10
                    else -> 7
                },
            distancePx = 220f + (i % 4) * 46f,
            delayMillis = KioskMotion.Complete.CONFETTI_DELAY_MILLIS + (i % 5) * 35,
            colorIndex = i % 5,
        )
    }

/** The clock of the screen's entrance, in milliseconds. Read it only in draw and layer lambdas. */
@Composable
internal fun rememberCompleteTimeline(): State<Float> {
    val reduce = LocalReduceMotion.current
    val time = remember { Animatable(if (reduce) TIMELINE_END_MILLIS else 0f) }
    LaunchedEffect(time) {
        if (time.value < TIMELINE_END_MILLIS) {
            time.animateTo(
                TIMELINE_END_MILLIS,
                tween<Float>(TIMELINE_END_MILLIS.toInt(), easing = KioskEasing.Linear).reducedIf(reduce),
            )
        }
    }
    return time.asState()
}

/** The progress of one animation that starts [delayMillis] into the clock and lasts [durationMillis], eased. */
internal fun segment(
    time: Float,
    delayMillis: Int,
    durationMillis: Int,
    easing: Easing = KioskEasing.Emphasized,
): Float = easing.transform(((time - delayMillis) / durationMillis).coerceIn(0f, 1f))

/** `flood`: an accent circle grows from 50 % / 24 % over the screen, then fades out (`Complete.dc.html`). */
@Composable
internal fun CompleteFlood(
    time: State<Float>,
    modifier: Modifier = Modifier,
) {
    val accent = KioskTheme.colors.accent
    Canvas(modifier = modifier.fillMaxSize().clearAndSetSemantics {}) {
        val raw = (time.value / KioskMotion.Complete.FLOOD_MILLIS).coerceIn(0f, 1f)
        val grow = KioskEasing.InOut.transform((raw / FLOOD_GROW_FRACTION).coerceIn(0f, 1f))
        val fade =
            KioskEasing.InOut.transform(
                ((raw - FLOOD_GROW_FRACTION) / (1f - FLOOD_GROW_FRACTION)).coerceIn(0f, 1f),
            )
        val reference = sqrt((size.width * size.width + size.height * size.height) * HALF)
        if (grow > 0f && fade < 1f) {
            drawCircle(
                color = accent.copy(alpha = 1f - fade),
                radius = FLOOD_RADIUS_FACTOR * reference * grow,
                center = Offset(size.width * HALF, size.height * FLOOD_ORIGIN_Y),
            )
        }
    }
}

/** The two expanding rings around the badge (`ringWave`), accent first and ink 250 ms later. */
@Composable
internal fun CompleteRings(
    time: State<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    Box(modifier = modifier.fillMaxSize().clearAndSetSemantics {}) {
        RingWave(time, KioskMotion.Complete.RING_WAVES_DELAY_MILLIS, colors.accent, RING_ACCENT_STROKE_PX)
        RingWave(time, KioskMotion.Complete.RING_WAVES_SECOND_DELAY_MILLIS, colors.ink, RING_INK_STROKE_PX)
    }
}

@Composable
private fun RingWave(
    time: State<Float>,
    delayMillis: Int,
    color: Color,
    strokePx: Int,
) {
    Box(
        modifier =
            Modifier
                .offset(x = (CENTER_X_PX - RING_SIZE_PX / 2).dpx, y = (CENTER_Y_PX - RING_SIZE_PX / 2).dpx)
                .size(RING_SIZE_PX.dpx)
                .graphicsLayer {
                    val p = segment(time.value, delayMillis, KioskMotion.Complete.RING_WAVES_MILLIS, KioskEasing.Press)
                    alpha = RING_ALPHA * (1f - p)
                    val grown = 1f + RING_GROWTH * p
                    scaleX = grown
                    scaleY = grown
                }.border(strokePx.dpx, color, CircleShape),
    )
}

/** The 20 confetti dots (`confetti`): each fades in, flies outwards and fades out. */
@Composable
internal fun CompleteConfetti(
    time: State<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val palette = listOf(colors.accent, colors.ink, colors.radioOff, colors.accent, colors.ink)
    val designPx = 1.dpx
    Canvas(modifier = modifier.fillMaxSize().clearAndSetSemantics {}) {
        val origin = Offset(CENTER_X_PX * designPx.toPx(), CENTER_Y_PX * designPx.toPx())
        ConfettiBits.forEach { bit ->
            val q = segment(time.value, bit.delayMillis, KioskMotion.Complete.CONFETTI_MILLIS, ConfettiEasing)
            val alpha =
                if (q <
                    CONFETTI_PEAK_FRACTION
                ) {
                    q / CONFETTI_PEAK_FRACTION
                } else {
                    1f -
                        (q - CONFETTI_PEAK_FRACTION) / (1f - CONFETTI_PEAK_FRACTION)
                }
            val unit = designPx.toPx()
            if (alpha > 0f) {
                // The dot sits at its margin offset, moves up its own axis by the distance, and the axis is rotated.
                val half = bit.sizePx * unit * HALF
                val margin = CONFETTI_MARGIN_PX * unit
                val local = Offset(half - margin, half - margin - bit.distancePx * unit * q)
                val radians = Math.toRadians(bit.degrees.toDouble())
                val rotated =
                    Offset(
                        (local.x * cos(radians) - local.y * sin(radians)).toFloat(),
                        (local.x * sin(radians) + local.y * cos(radians)).toFloat(),
                    )
                val grown = CONFETTI_START_SCALE + (1f - CONFETTI_START_SCALE) * q
                drawCircle(
                    color = palette[bit.colorIndex].copy(alpha = alpha),
                    radius = half * grown,
                    center = origin + rotated,
                )
            }
        }
    }
}

/**
 * The accent badge with the white check (`badgeIn`, `draw`): the badge pops in with a slight overshoot and the check
 * is drawn in over the first third of its animation, as the design's dash offset does.
 */
@Composable
internal fun CompleteBadge(
    time: State<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val checkPath = remember { PathParser().parsePathString(CHECK_PATH).toPath() }
    val measure = remember { PathMeasure().apply { setPath(checkPath, false) } }
    Box(
        modifier =
            modifier
                .offset(x = (CENTER_X_PX - BADGE_SIZE_PX / 2).dpx, y = BADGE_TOP_PX.dpx)
                .size(BADGE_SIZE_PX.dpx)
                .graphicsLayer {
                    val s = badgeScale(time.value)
                    scaleX = s
                    scaleY = s
                }.shadow(
                    elevation = BADGE_SHADOW_PX.dpx,
                    shape = CircleShape,
                    ambientColor = colors.accent.copy(alpha = BADGE_SHADOW_ALPHA),
                    spotColor = colors.accent.copy(alpha = BADGE_SHADOW_ALPHA),
                ).background(colors.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(CHECK_SIZE_PX.dpx).clearAndSetSemantics {}) {
            val drawn =
                segment(
                    time.value,
                    KioskMotion.Complete.CHECK_DRAW_DELAY_MILLIS,
                    KioskMotion.Complete.CHECK_DRAW_MILLIS,
                    KioskEasing.Press,
                )
            val unit = size.minDimension / CHECK_VIEWBOX
            val length = measure.length
            val visible = (CHECK_DASH * drawn).coerceAtMost(length)
            if (visible > 0f) {
                val part = Path()
                measure.getSegment(0f, visible, part, startWithMoveTo = true)
                scale(unit, unit, pivot = Offset.Zero) {
                    drawPath(
                        path = part,
                        color = colors.surface,
                        style = Stroke(width = CHECK_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

/** `badgeIn`: 0 → 1.06 at 60 % → 1, each leg on the spring curve. */
private fun badgeScale(time: Float): Float {
    val p = ((time - KioskMotion.Complete.BADGE_DELAY_MILLIS) / KioskMotion.Complete.BADGE_MILLIS).coerceIn(0f, 1f)
    return if (p < BADGE_PEAK_FRACTION) {
        BADGE_PEAK_SCALE * KioskEasing.Spring.transform(p / BADGE_PEAK_FRACTION)
    } else {
        BADGE_PEAK_SCALE -
            (BADGE_PEAK_SCALE - 1f) *
            KioskEasing.Spring.transform((p - BADGE_PEAK_FRACTION) / (1f - BADGE_PEAK_FRACTION))
    }
}
