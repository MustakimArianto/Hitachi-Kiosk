package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/** Chips, segments and switches change color over 380 ms (`transition: background-color 380ms`). */
internal const val SELECT_COLOR_MILLIS = 380

/** Disabled buttons fade over 240 ms (`transition: opacity 240ms`). */
internal const val DISABLED_FADE_MILLIS = 240

/** A disabled control is drawn at 40 % (`opacity: .4`). */
internal const val DISABLED_ALPHA = 0.4f

/** The switch track changes over 360 ms and its knob slides over 500 ms with the spring easing. */
private const val SWITCH_TRACK_MILLIS = 360
private const val SWITCH_KNOB_MILLIS = 500

/** Bottom sheets: the scrim fades in over 360 ms, the sheet rises over 620 ms (`scrimIn`, `sheetUp`). */
private const val SCRIM_IN_MILLIS = 360

/**
 * A tappable element without ripple (the design's `.press`): scales to 0.96 while pressed. [role] and [enabled] reach
 * the semantics; the caller adds the description.
 */
@Composable
internal fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    return pressScale(scale).clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}

/** Fades a control to [DISABLED_ALPHA] (or [disabledAlpha]) when it is not [enabled]. */
@Composable
internal fun Modifier.disabledFade(
    enabled: Boolean,
    disabledAlpha: Float = DISABLED_ALPHA,
): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else disabledAlpha,
        animationSpec = motionSpec(tween(DISABLED_FADE_MILLIS)),
        label = "disabled-alpha",
    )
    return graphicsLayer { this.alpha = alpha }
}

/** The 120 × 68 dpx switch of the Customize sheet (`BudgetPas.dc.html`): green when on, its knob springs across. */
@Composable
internal fun PlanSwitch(
    checked: Boolean,
    description: String,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val track by animateColorAsState(
        targetValue = if (checked) colors.success else colors.switchOff,
        animationSpec = motionSpec(tween(SWITCH_TRACK_MILLIS, easing = KioskEasing.Emphasized)),
        label = "switch-track",
    )
    val knob by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = motionSpec(tween(SWITCH_KNOB_MILLIS, easing = KioskEasing.Spring)),
        label = "switch-knob",
    )
    val travel = 52.dpx
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            modifier
                .pressScale(scale)
                .width(120.dpx)
                .height(68.dpx)
                .clip(RoundedCornerShape(34.dpx))
                .background(track)
                .toggleable(
                    value = checked,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Switch,
                    onValueChange = onChange,
                ).semantics { contentDescription = description },
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = 8.dpx, y = 8.dpx)
                    .graphicsLayer { translationX = travel.toPx() * knob }
                    .size(52.dpx)
                    .dropShadow(
                        CircleShape,
                        Shadow(
                            radius = 6.dpx,
                            color = colors.ink.copy(alpha = KNOB_SHADOW_ALPHA),
                            offset = DpOffset(0.dpx, 2.dpx),
                        ),
                    ).background(colors.surface, CircleShape),
        )
    }
}

private const val KNOB_SHADOW_ALPHA = 0.18f

/**
 * A bottom sheet over a scrim (`BudgetPas.dc.html` Customize and keypad): the scrim fades in and closes the sheet
 * when tapped ([scrimDescription]); the sheet rises from below with the emphasized easing over [riseMillis].
 * [sheetModifier] sizes the sheet (for example a top inset) before its rounded background is drawn.
 */
@Suppress("LongParameterList")
@Composable
internal fun SheetOverlay(
    scrimDescription: String,
    onDismiss: () -> Unit,
    riseMillis: Int,
    modifier: Modifier = Modifier,
    sheetModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrim = animateEntrance(SCRIM_IN_MILLIS)
    val rise = animateEntrance(riseMillis)
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim.value }
                    .background(KioskTheme.colors.sheetScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onDismiss,
                    ).semantics { contentDescription = scrimDescription },
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(sheetModifier)
                    .graphicsLayer { translationY = size.height * (1f - rise.value) }
                    .clip(RoundedCornerShape(topStart = 56.dpx, topEnd = 56.dpx))
                    .background(KioskTheme.colors.bg)
                    // The sheet takes its own taps so they do not reach the scrim, without becoming a button.
                    .pointerInput(Unit) { detectTapGestures {} },
            content = content,
        )
    }
}

/** A 2 dpx row divider inside white cards (`border-bottom: 2px solid #F4F0EA`). */
@Composable
internal fun RowDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dpx).background(KioskTheme.colors.rowDivider))
}

/** A filled, rounded action button with centred [content] (sheet footers, keypad, group card). */
@Suppress("LongParameterList")
@Composable
internal fun SheetButton(
    onClick: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(36.dpx),
    outline: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .disabledFade(enabled)
                .pressable(onClick = onClick, enabled = enabled)
                .clip(shape)
                .background(background)
                .then(if (outline != null) Modifier.border(2.dpx, outline, shape) else Modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
