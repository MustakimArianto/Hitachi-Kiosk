package com.hitachi.kiosk.feature.order

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.RollingText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.cart.describe
import com.hitachi.kiosk.ui.MenuPhoto
import com.hitachi.kiosk.ui.rememberLineWording
import kotlin.math.roundToInt

/** Test tag of a line's row. */
fun yourOrderLineTag(lineId: String): String = "your-order-line:$lineId"

/** Test tag of a line's "−" (the trash icon at quantity 1). */
fun yourOrderDecrementTag(lineId: String): String = "your-order-dec:$lineId"

/** Test tag of a line's "+". */
fun yourOrderIncrementTag(lineId: String): String = "your-order-inc:$lineId"

/** Test tag of "Remove" on an unavailable line. */
fun yourOrderRemoveTag(lineId: String): String = "your-order-remove:$lineId"

/** Each row is 208 dpx tall with 24 dpx below it (`max-height: 232px`, `margin-bottom: 24px`). */
private const val ROW_HEIGHT_PX = 208
private const val ROW_GAP_PX = 24

/** The row entrance (`rowIn`) starts 200 ms in. */
private const val ROW_IN_DELAY_MILLIS = 200

/** A leaving row fades out over 280 ms and slides 160 dpx to the left (`.row` transitions). */
private const val LEAVE_FADE_MILLIS = 280
private const val LEAVE_SLIDE_PX = 160

/** The slide-out accelerates away (`cubic-bezier(.4,0,1,1)`). */
private val LeaveEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/** An unavailable line is drawn at 85 % (`opacity: .85`). */
private const val UNAVAILABLE_ALPHA = 0.85f

private const val NAME_LINE_HEIGHT_EM = 1.15f
private const val DETAILS_LINE_HEIGHT_EM = 1.3f
private const val NOTE_LINE_HEIGHT_EM = 1.2f
private const val TOTAL_LINE_HEIGHT_EM = 44f / 34f

/**
 * One order line of `Payment.dc.html` (FR-012): photo, name, the worded options, a price or availability note, the
 * rolling line total and the quantity pill, or "Remove" for an unavailable line. A [OrderLineUi.leaving] line
 * collapses, fades and slides out over 460 ms; with reduce motion it is gone at once.
 */
@Composable
internal fun OrderLineRow(
    line: OrderLineUi,
    index: Int,
    onEvent: (YourOrderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val entrance =
        animateEntrance(
            KioskMotion.YourOrder.ROWS_IN_MILLIS,
            ROW_IN_DELAY_MILLIS + index * KioskMotion.YourOrder.ROWS_STAGGER_MILLIS,
        )
    val target = if (line.leaving) 0f else 1f
    val height by animateFloatAsState(
        target,
        motionSpec(tween(KioskMotion.YourOrder.REMOVE_MILLIS, easing = KioskEasing.Emphasized)),
        label = "row-height",
    )
    val opacity by animateFloatAsState(
        target,
        motionSpec(tween(LEAVE_FADE_MILLIS, easing = LinearEasing)),
        label = "row-alpha",
    )
    val slide by animateFloatAsState(
        target,
        motionSpec(tween(KioskMotion.YourOrder.REMOVE_MILLIS, easing = LeaveEasing)),
        label = "row-slide",
    )
    val slidePx = LEAVE_SLIDE_PX.dpx
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .collapse { height }
                .graphicsLayer {
                    alpha = opacity
                    translationX = -slidePx.toPx() * (1f - slide)
                }.padding(bottom = ROW_GAP_PX.dpx)
                .testTag(yourOrderLineTag(line.lineId)),
    ) {
        Row(
            modifier =
                Modifier
                    .fadeUp(entrance, KioskMotion.Common.UP_DISTANCE_PX.dpx)
                    .fillMaxWidth()
                    .heightIn(min = ROW_HEIGHT_PX.dpx)
                    .height(IntrinsicSize.Min)
                    .alpha(if (line.unavailable) UNAVAILABLE_ALPHA else 1f)
                    .background(if (line.unavailable) colors.surfaceAlt else colors.surface, shape)
                    .border(2.dpx, colors.cardBorder, shape)
                    .padding(24.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dpx),
        ) {
            MenuPhoto(
                imageUrl = line.imageUrl,
                tone = line.tone,
                contentDescription = null,
                shape = KioskTheme.shapes.medium,
                modifier = Modifier.size(160.dpx),
            )
            LineText(line, Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                LineTotal(line)
                if (line.unavailable) {
                    RemoveButton(line, onClick = { onEvent(YourOrderEvent.Remove(line.lineId)) })
                } else {
                    QuantityPill(line, onEvent)
                }
            }
        }
    }
}

/** Lays the content out [fraction] of its height tall and clips it, like the row's `max-height` transition. */
private fun Modifier.collapse(fraction: () -> Float): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * fraction().coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, height) { placeable.place(0, 0) }
    }

@Composable
private fun LineText(
    line: OrderLineUi,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val wording = rememberLineWording()
    val details = remember(line.choices, wording) { describe(line.choices, wording) }
    val note =
        when {
            line.unavailable -> stringResource(R.string.your_order_line_unavailable)
            line.previousTotal != null -> stringResource(R.string.your_order_line_price_updated, line.previousTotal)
            else -> null
        }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dpx)) {
        KioskText(
            text = line.name,
            style = KioskTheme.typography.item.copy(fontSize = 34.spx, lineHeight = NAME_LINE_HEIGHT_EM.em),
            maxLines = 2,
        )
        if (details.isNotEmpty()) {
            KioskText(
                text = details,
                style = KioskTheme.typography.meta.copy(lineHeight = DETAILS_LINE_HEIGHT_EM.em),
                color = colors.muted,
            )
        }
        if (note != null) {
            KioskText(
                text = note,
                style =
                    KioskTheme.typography.meta.copy(
                        fontWeight = FontWeight.W700,
                        lineHeight = NOTE_LINE_HEIGHT_EM.em,
                    ),
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun LineTotal(line: OrderLineUi) {
    val style = KioskTheme.typography.button.copy(letterSpacing = 0.em, lineHeight = TOTAL_LINE_HEIGHT_EM.em)
    RollingText(
        text = if (line.unavailable) stringResource(R.string.your_order_line_unavailable_total) else line.total,
        style = style,
        durationMillis = LINE_ROLL_MILLIS,
        modifier = Modifier.height(44.dpx),
    )
}

/** Line totals and quantities roll over 380 ms (`roll` on `l.tick`). */
private const val LINE_ROLL_MILLIS = 380

/** The quantity pill (`role="group"`): "−" or the trash icon at 1, the rolling quantity and "+". */
@Composable
private fun QuantityPill(
    line: OrderLineUi,
    onEvent: (YourOrderEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val group = stringResource(R.string.your_order_quantity_description, line.name)
    val single = line.quantity <= 1
    Row(
        modifier =
            Modifier
                .height(96.dpx)
                .background(colors.surfaceAlt, KioskTheme.shapes.pill)
                .semantics { contentDescription = group }
                .padding(8.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dpx),
    ) {
        val decrement =
            if (single) R.string.your_order_line_remove_description else R.string.your_order_decrement_description
        StepperButton(
            description = stringResource(decrement, line.name),
            tag = yourOrderDecrementTag(line.lineId),
            light = true,
            enabled = !line.leaving,
            onClick = { onEvent(YourOrderEvent.Decrement(line.lineId)) },
        ) {
            KioskIcon(
                icon = if (single) KioskIcons.Trash else KioskIcons.Minus,
                tint = if (single) colors.accent else colors.ink,
                modifier = Modifier.size(34.dpx),
            )
        }
        Box(modifier = Modifier.width(56.dpx).height(80.dpx), contentAlignment = Alignment.Center) {
            RollingText(
                text = line.quantity.toString(),
                style = KioskTheme.typography.button.copy(letterSpacing = 0.em),
                durationMillis = LINE_ROLL_MILLIS,
            )
        }
        StepperButton(
            description = stringResource(R.string.your_order_increment_description, line.name),
            tag = yourOrderIncrementTag(line.lineId),
            light = false,
            enabled = !line.leaving,
            onClick = { onEvent(YourOrderEvent.Increment(line.lineId)) },
        ) {
            KioskIcon(icon = KioskIcons.Plus, tint = colors.surface, modifier = Modifier.size(34.dpx))
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun StepperButton(
    description: String,
    tag: String,
    light: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val colors = KioskTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            Modifier
                .size(80.dpx)
                .pressScale(scale)
                .background(if (light) colors.surface else colors.ink, CircleShape)
                .orderButton(description, tag, interactionSource, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun RemoveButton(
    line: OrderLineUi,
    onClick: () -> Unit,
) {
    val colors = KioskTheme.colors
    val label = stringResource(R.string.your_order_line_remove)
    val description = stringResource(R.string.your_order_line_remove_description, line.name)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            Modifier
                .height(80.dpx)
                .pressScale(scale)
                .background(colors.ink, KioskTheme.shapes.pill)
                .orderButton(
                    description,
                    yourOrderRemoveTag(line.lineId),
                    interactionSource,
                    enabled = !line.leaving,
                    onClick = onClick,
                ).padding(horizontal = 32.dpx),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = label,
            style = KioskTheme.typography.button.copy(fontSize = 28.spx, letterSpacing = 0.em),
            color = colors.bg,
            maxLines = 1,
        )
    }
}

/**
 * A tappable control without ripple (the design uses the `.press` scale instead), announced as one control with
 * [description] (the design's `aria-label`) and [tag]. When not [enabled] it ignores taps and is announced as
 * disabled (NFR-007).
 */
@Suppress("LongParameterList")
internal fun Modifier.orderButton(
    description: String,
    tag: String,
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    role: Role = Role.Button,
    selected: Boolean? = null,
    onClick: () -> Unit,
): Modifier =
    clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    ).clearAndSetSemantics {
        testTag = tag
        contentDescription = description
        this.role = role
        if (selected != null) this.selected = selected
        if (enabled) {
            onClick {
                onClick()
                true
            }
        } else {
            disabled()
        }
    }
