package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.hitachi.kiosk.core.designsystem.motion.animateOnEnter
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/** The check pops in over 380 ms (`checkIn`, `cubic-bezier(.3,1.4,.5,1)`). */
private const val CHECK_IN_MILLIS = 380

/** Option rings and fills change color over 300 ms. */
private const val DOT_COLOR_MILLIS = 300

/** `checkIn` starts at 40 % scale, turned −45°. */
private const val CHECK_START_SCALE = 0.4f
private const val CHECK_START_ROTATION = -45f

/** The overshooting pop of the check (`.3, 1.4, .5, 1`). */
private val CheckPop = CubicBezierEasing(0.3f, 1.4f, 0.5f, 1f)

/**
 * The round selection indicator of option cards and check rows (`ItemDetail.dc.html`, `Combo.dc.html`): an empty
 * ring in `radioOff` when off; filled with [fill] and a white check that pops in when [selected]. Decorative: the
 * card around it carries the selected state (NFR-007).
 */
@Composable
fun SelectionDot(
    selected: Boolean,
    fill: Color,
    size: Dp,
    checkSize: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val background by animateColorAsState(
        targetValue = if (selected) fill else Color.Transparent,
        animationSpec = motionSpec(tween(DOT_COLOR_MILLIS)),
        label = "dot-fill",
    )
    val ring by animateColorAsState(
        targetValue = if (selected) fill else colors.radioOff,
        animationSpec = motionSpec(tween(DOT_COLOR_MILLIS)),
        label = "dot-ring",
    )
    Box(
        modifier =
            modifier
                .size(size)
                .background(background, CircleShape)
                .border(3.dpx, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            val pop = animateOnEnter(tween(CHECK_IN_MILLIS, easing = CheckPop))
            KioskIcon(
                icon = KioskIcons.CheckBold,
                tint = colors.surface,
                modifier =
                    Modifier.size(checkSize).graphicsLayer {
                        val p = pop.value
                        alpha = p.coerceIn(0f, 1f)
                        val scale = CHECK_START_SCALE + (1f - CHECK_START_SCALE) * p
                        scaleX = scale
                        scaleY = scale
                        rotationZ = CHECK_START_ROTATION * (1f - p)
                    },
            )
        }
    }
}

@Preview(widthDp = 300, heightDp = 120)
@Composable
private fun SelectionDotPreview() {
    KioskTheme(reduceMotion = true) {
        Row(Modifier.padding(24.dpx)) {
            SelectionDot(selected = false, fill = KioskTheme.colors.accent, size = 48.dpx, checkSize = 28.dpx)
            SelectionDot(selected = true, fill = KioskTheme.colors.accent, size = 48.dpx, checkSize = 28.dpx)
            SelectionDot(selected = true, fill = KioskTheme.colors.ink, size = 40.dpx, checkSize = 24.dpx)
        }
    }
}
