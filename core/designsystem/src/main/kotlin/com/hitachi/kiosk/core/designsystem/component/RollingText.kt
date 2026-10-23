package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.spx

/** A rolled-in value starts 60 % of its height away (`rollA` keyframes, PRD §7.1). */
private const val ROLL_FRACTION = 0.6f

/** Which way a rolling value moves: [UP] for increases (and every price), [DOWN] for a quantity going down. */
enum class RollDirection { UP, DOWN }

/**
 * Rolling number text of PRD §7.1 and §11.3: when [text] changes, the new value rolls in from 60 % below (or above
 * for [RollDirection.DOWN]) while fading in, over [durationMillis] after [delayMillis] with the emphasized easing,
 * and the old value is replaced at once. The first value shows without animation; with reduce motion every change
 * is instant (NFR-009). Clipped to its own bounds, like the design's `overflow: hidden` wrapper.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    durationMillis: Int,
    modifier: Modifier = Modifier,
    color: Color = KioskTheme.colors.ink,
    delayMillis: Int = 0,
    direction: RollDirection = RollDirection.UP,
) {
    val reduce = LocalReduceMotion.current
    AnimatedContent(
        targetState = text,
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.CenterStart,
        transitionSpec = { rollTransform(reduce, direction, durationMillis, delayMillis) },
        label = "rolling-text",
    ) { value ->
        KioskText(text = value, style = style, color = color, maxLines = 1)
    }
}

private fun rollTransform(
    reduce: Boolean,
    direction: RollDirection,
    durationMillis: Int,
    delayMillis: Int,
): ContentTransform {
    if (reduce) return EnterTransition.None togetherWith ExitTransition.None
    val sign = if (direction == RollDirection.UP) 1 else -1
    val spec = tween<Float>(durationMillis, delayMillis, KioskEasing.Emphasized)
    val enter =
        slideInVertically(tween(durationMillis, delayMillis, KioskEasing.Emphasized)) { height ->
            (height * ROLL_FRACTION * sign).toInt()
        } + fadeIn(spec)
    return enter togetherWith ExitTransition.None
}

@Preview(widthDp = 400, heightDp = 160)
@Composable
private fun RollingTextPreview() {
    KioskTheme(reduceMotion = true) {
        Column {
            RollingText(
                text = "Rp 81.000",
                style = KioskTheme.typography.section.copy(fontSize = 44.spx),
                durationMillis = 420,
            )
            RollingText(
                text = "3",
                style = KioskTheme.typography.item,
                durationMillis = 340,
                direction = RollDirection.DOWN,
            )
        }
    }
}
