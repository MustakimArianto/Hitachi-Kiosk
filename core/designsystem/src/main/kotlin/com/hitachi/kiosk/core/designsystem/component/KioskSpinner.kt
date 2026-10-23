package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/** The head covers the top quarter of the ring, like the design's `border-top-color` spinner. */
private const val HEAD_SWEEP_DEGREES = 90f
private const val HEAD_START_DEGREES = -135f
private const val FULL_TURN_DEGREES = 360f

/**
 * The ring spinner of the design (`Login.dc.html` busy button, Merchants binding): a [trackColor] ring with a
 * [color] head that turns once per [periodMillis], linearly (PRD §11.4 "spinner 800 linear"). Size it with
 * [modifier]. With reduce motion on it stays still. Decorative: give the surrounding control the description.
 */
@Composable
fun KioskSpinner(
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 5.dpx,
    periodMillis: Int = KioskMotion.SignIn.SPINNER_LOOP_MILLIS,
) {
    val rotation =
        if (LocalReduceMotion.current) {
            remember { mutableFloatStateOf(0f) }
        } else {
            spinnerRotation(periodMillis)
        }
    Canvas(modifier = modifier.graphicsLayer { rotationZ = rotation.value }) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = FULL_TURN_DEGREES,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = HEAD_START_DEGREES,
            sweepAngle = HEAD_SWEEP_DEGREES,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
    }
}

@Composable
private fun spinnerRotation(periodMillis: Int): State<Float> =
    rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN_DEGREES,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "spinnerRotation",
    )

@Preview(widthDp = 200, heightDp = 200)
@Composable
private fun KioskSpinnerPreview() {
    KioskTheme(reduceMotion = true) {
        Box(Modifier.size(200.dpx).background(KioskTheme.colors.accent), contentAlignment = Alignment.Center) {
            KioskSpinner(
                color = KioskTheme.colors.surface,
                trackColor = KioskTheme.colors.surface.copy(alpha = 0.35f),
                modifier = Modifier.size(88.dpx),
                strokeWidth = 10.dpx,
            )
        }
    }
}
