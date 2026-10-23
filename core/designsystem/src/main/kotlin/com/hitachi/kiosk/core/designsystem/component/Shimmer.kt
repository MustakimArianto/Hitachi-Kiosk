package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/**
 * The highlight band of the design's `.sk` gradient (`linear-gradient(100deg, base 30%, highlight 50%, base 70%)`
 * on a 200 % wide background): it is 0.8 block widths wide, and its centre travels from −0.5 to 1.5 block widths.
 */
private const val BAND_HALF_WIDTH = 0.4f
private const val BAND_CENTRE_STOP = 0.5f
private const val TRAVEL_START = -0.5f
private const val TRAVEL_LENGTH = 2f

/** Where the band rests when reduce motion is on: just outside the block, so the block shows its base tone. */
private const val REDUCED_PROGRESS = 1f

/**
 * The shared phase of the skeleton shimmer, 0 to 1 over [periodMillis], linear and repeating (Merchants loading,
 * `S_Merchants_skeleton`). Create it once per skeleton and pass it to every [shimmer] block so they move together.
 * With reduce motion on it stays still.
 */
@Composable
fun rememberShimmerPhase(periodMillis: Int = KioskMotion.Merchants.SHIMMER_LOOP_MILLIS): State<Float> =
    if (LocalReduceMotion.current) {
        remember { mutableFloatStateOf(REDUCED_PROGRESS) }
    } else {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmerPhase",
        )
    }

/**
 * Fills the element with a skeleton [base] tone and a moving [highlight] band driven by [phase] (from
 * [rememberShimmerPhase]). Clip it to the block's shape first. Only the draw phase reads [phase], so the shimmer
 * never relayouts.
 */
fun Modifier.shimmer(
    phase: State<Float>,
    base: Color,
    highlight: Color,
): Modifier =
    drawBehind {
        val width = size.width
        val centre = (TRAVEL_START + TRAVEL_LENGTH * phase.value) * width
        drawRect(
            brush =
                Brush.linearGradient(
                    colorStops = arrayOf(0f to base, BAND_CENTRE_STOP to highlight, 1f to base),
                    start = Offset(centre - BAND_HALF_WIDTH * width, 0f),
                    end = Offset(centre + BAND_HALF_WIDTH * width, 0f),
                ),
        )
    }

/** One skeleton block: [shape] filled by [shimmer]. Size it with [modifier]. */
@Composable
fun SkeletonBlock(
    phase: State<Float>,
    shape: Shape,
    base: Color,
    highlight: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clip(shape).shimmer(phase, base, highlight),
    )
}

@Preview(widthDp = 600, heightDp = 300)
@Composable
private fun SkeletonBlockPreview() {
    KioskTheme {
        val phase = remember { mutableFloatStateOf(0.5f) }
        Column(Modifier.padding(24.dpx), verticalArrangement = Arrangement.spacedBy(20.dpx)) {
            SkeletonBlock(
                phase = phase,
                shape = RoundedCornerShape(16.dpx),
                base = KioskTheme.colors.surfaceAlt,
                highlight = KioskTheme.colors.bg,
                modifier = Modifier.fillMaxWidth().height(52.dpx),
            )
            SkeletonBlock(
                phase = phase,
                shape = RoundedCornerShape(12.dpx),
                base = KioskTheme.colors.line,
                highlight = KioskTheme.colors.skeletonHighlight,
                modifier = Modifier.fillMaxWidth(0.5f).height(32.dpx),
            )
        }
    }
}
