package com.hitachi.kiosk.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/** How far a title travels in a "rise" entrance, as a fraction of its own height (`translateY(105%)`, PRD §7.1). */
private const val RISE_FRACTION = 1.05f

/**
 * A one-shot progress that runs from 0 to 1 with [spec] when it enters the composition (PRD §7.1, §11.4). With
 * reduce motion on it starts at 1, so the final frame shows on the first frame (FR-001 AC3, NFR-009).
 */
@Composable
fun animateOnEnter(spec: FiniteAnimationSpec<Float>): State<Float> {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(progress) {
        if (progress.value < 1f) progress.animateTo(1f, spec.reducedIf(reduce))
    }
    return progress.asState()
}

/**
 * [animateOnEnter] with a tween of [durationMillis] after [delayMillis] (PRD §11.4). The default easing is
 * [KioskEasing.Emphasized], the entrance curve of the design.
 */
@Composable
fun animateEntrance(
    durationMillis: Int,
    delayMillis: Int = 0,
    easing: Easing = KioskEasing.Emphasized,
): State<Float> = animateOnEnter(tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing))

/** The design's `fade` keyframes: opacity follows [progress]. Drawn in a graphics layer, no relayout. */
fun Modifier.fadeIn(progress: State<Float>): Modifier = graphicsLayer { alpha = progress.value }

/**
 * The design's `up` keyframes (PRD §7.1): fades in while moving up by [distance]. Drawn in a graphics layer, no
 * relayout.
 */
fun Modifier.fadeUp(
    progress: State<Float>,
    distance: Dp,
): Modifier =
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = distance.toPx() * (1f - p)
    }

/**
 * The design's `rise` keyframes (PRD §7.1): slides up from 105 % of its own height. Put it inside a parent that
 * clips (`clipToBounds`), as the design's `overflow: hidden` wrapper does.
 */
fun Modifier.riseIn(progress: State<Float>): Modifier =
    graphicsLayer { translationY = size.height * RISE_FRACTION * (1f - progress.value) }

/**
 * The `.press` feedback of PRD §7.1: the scale to draw while [interactionSource] is pressed, animated over
 * [durationMillis] with [KioskEasing.Press]. Apply it with `graphicsLayer`. Reduce motion makes it instant.
 */
@Composable
fun animatePressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = KioskMotion.Press.BUTTON_SCALE,
    durationMillis: Int = KioskMotion.Press.BUTTON_MILLIS,
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = motionSpec(tween<Float>(durationMillis = durationMillis, easing = KioskEasing.Press)),
        label = "press",
    )
}

/** Scales the content by [scale] around its centre in a graphics layer (press feedback). */
fun Modifier.pressScale(scale: State<Float>): Modifier =
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
