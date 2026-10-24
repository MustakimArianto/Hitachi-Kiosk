package com.hitachi.kiosk.feature.ordertype

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.outerRing
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.OrderType
import kotlin.math.hypot
import kotlin.math.sqrt
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.OrderType as Motion

/** Test tag of an order type card ("order-type:DINE_IN"). */
fun orderTypeCardTag(orderType: OrderType): String = "order-type:${orderType.name}"

/** Test tag of the check badge on the picked card. */
const val ORDER_TYPE_CHECK_TAG = "order-type-check"

/** The other card dims to 45 % once one is picked, over 300 ms (CSS `ease`). */
private const val DIMMED_ALPHA = 0.45f
private const val DIM_MILLIS = 300

/** `.press:active { transform: scale(.97) }`. */
private const val CARD_PRESS_SCALE = 0.97f

/** `cardIn`: up from 72 px. */
private const val CARD_IN_DISTANCE_PX = 72

/** `fill`: `circle(0% → 160% at 20% 30%)`; a CSS circle percentage is of `hypot(w, h) / √2`. */
private const val FILL_CENTER_X = 0.2f
private const val FILL_CENTER_Y = 0.3f
private const val FILL_RADIUS = 1.6f

/** `lift`: up 10 px at 40 %, back at 100 %. */
private const val LIFT_PX = 10
private const val LIFT_PEAK_AT = 0.4f

/** `popIn`: from .4 scale and −30° to 1.1 at 70 %, then 1. */
private const val POP_FROM_SCALE = 0.4f
private const val POP_OVERSHOOT_SCALE = 1.1f
private const val POP_FROM_DEGREES = -30f
private const val POP_PEAK_AT = 0.7f

/** `ring`: opacity .7 → 0 while growing to 1.7. */
private const val RING_ALPHA = 0.7f
private const val RING_GROWTH = 0.7f

/** Selected text and icon tile on the accent fill: `rgba(255,255,255,.9)` and `.2`. */
private const val SELECTED_DESCRIPTION_ALPHA = 0.9f
private const val SELECTED_TILE_ALPHA = 0.2f
private const val TITLE_EM = -0.025f
private const val DESCRIPTION_LINE_HEIGHT_EM = 1.35f

/**
 * One full-width order type card (`OrderType.dc.html`): the icon tile, title and one-line description on white with
 * a 2 dpx `line` ring. When [selected] the accent fills it from the icon side (600 ms circular reveal), the icon lifts
 * and a check pops in with a ring; when another card is picked ([dimmed]) it fades to 45 %. A radio button for
 * accessibility; the check shows the selection without relying on color (NFR-007).
 */
@Composable
internal fun OrderTypeCard(
    orderType: OrderType,
    selected: Boolean,
    dimmed: Boolean,
    entrance: State<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val interactionSource = remember { MutableInteractionSource() }
    val press = animatePressScale(interactionSource, pressedScale = CARD_PRESS_SCALE)
    val dim by animateFloatAsState(
        targetValue = if (dimmed) DIMMED_ALPHA else 1f,
        animationSpec = motionSpec(tween(DIM_MILLIS, easing = KioskEasing.Ease)),
        label = "orderTypeDim",
    )
    val fill = if (selected) animateEntrance(Motion.SELECTION_FILL_MILLIS) else null
    val accent = colors.accent
    val distance = CARD_IN_DISTANCE_PX.dpx
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(360.dpx)
                .graphicsLayer {
                    val p = entrance.value
                    alpha = p * dim
                    translationY = distance.toPx() * (1f - p)
                    scaleX = press.value
                    scaleY = press.value
                }.outerRing(2.dpx, colors.line, shape)
                .clip(shape)
                .background(colors.surface)
                .drawBehind {
                    val reveal = fill?.value ?: return@drawBehind
                    val radius = FILL_RADIUS * hypot(size.width, size.height) / sqrt(2f) * reveal
                    drawCircle(
                        color = accent,
                        radius = radius,
                        center = Offset(size.width * FILL_CENTER_X, size.height * FILL_CENTER_Y),
                    )
                }.selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).testTag(orderTypeCardTag(orderType)),
    ) {
        CardContent(orderType = orderType, selected = selected)
        if (selected) {
            CheckBadge(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dpx, end = 40.dpx),
            )
        }
    }
}

@Composable
private fun CardContent(
    orderType: OrderType,
    selected: Boolean,
) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val foreground = if (selected) colors.surface else colors.ink
    val tile by animateColorAsState(
        targetValue = if (selected) colors.surface.copy(alpha = SELECTED_TILE_ALPHA) else colors.surfaceAlt,
        animationSpec = motionSpec(tween(DIM_MILLIS, easing = KioskEasing.Ease)),
        label = "orderTypeTile",
    )
    val lift =
        if (selected) {
            animateEntrance(
                Motion.ICON_LIFT_MILLIS,
                Motion.ICON_LIFT_DELAY_MILLIS,
                KioskEasing.Linear,
            )
        } else {
            null
        }
    val liftDistance = LIFT_PX.dpx
    val (title, description) =
        when (orderType) {
            OrderType.DINE_IN -> R.string.order_type_dine_in to R.string.order_type_dine_in_description
            OrderType.TAKEAWAY -> R.string.order_type_takeaway to R.string.order_type_takeaway_description
        }
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(48.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dpx),
    ) {
        Box(
            modifier =
                Modifier
                    .size(144.dpx)
                    .graphicsLayer { translationY = -liftDistance.toPx() * liftCurve(lift?.value ?: 0f) }
                    .background(tile, KioskTheme.shapes.card),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(
                icon = if (orderType == OrderType.DINE_IN) KioskIcons.DineIn else KioskIcons.Bag,
                tint = foreground,
                modifier = Modifier.size(80.dpx),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dpx)) {
            KioskText(
                text = stringResource(title),
                style = typography.title.copy(fontSize = 48.spx, letterSpacing = TITLE_EM.em, lineHeight = 1.em),
                color = foreground,
                cssLineBox = true,
            )
            KioskText(
                text = stringResource(description),
                style =
                    typography.body.copy(
                        fontSize = 28.spx,
                        fontWeight = FontWeight.W400,
                        lineHeight = DESCRIPTION_LINE_HEIGHT_EM.em,
                    ),
                color = if (selected) colors.surface.copy(alpha = SELECTED_DESCRIPTION_ALPHA) else colors.muted,
            )
        }
    }
}

/** The `lift` keyframes over linear progress [p]: up to the peak at 40 %, back down by 100 %, each eased. */
private fun liftCurve(p: Float): Float =
    if (p < LIFT_PEAK_AT) {
        KioskEasing.Emphasized.transform(p / LIFT_PEAK_AT)
    } else {
        1f - KioskEasing.Emphasized.transform((p - LIFT_PEAK_AT) / (1f - LIFT_PEAK_AT))
    }

/** The 88 dpx accent check with the ring that grows out of it (`popIn` 500 @150, `ring` 800 @200). */
@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    val colors = KioskTheme.colors
    val pop = animateEntrance(Motion.CHECK_POP_MILLIS, Motion.CHECK_POP_DELAY_MILLIS, KioskEasing.Linear)
    val ring = animateEntrance(Motion.RING_MILLIS, Motion.RING_DELAY_MILLIS, KioskEasing.Press)
    Box(modifier.size(88.dpx).testTag(ORDER_TYPE_CHECK_TAG)) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val r = ring.value
                        alpha = RING_ALPHA * (1f - r)
                        scaleX = 1f + RING_GROWTH * r
                        scaleY = 1f + RING_GROWTH * r
                    }.border(3.dpx, colors.accent, CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = pop.value
                        if (p < POP_PEAK_AT) {
                            val q = KioskEasing.Spring.transform(p / POP_PEAK_AT)
                            alpha = q.coerceIn(0f, 1f)
                            val scale = POP_FROM_SCALE + (POP_OVERSHOOT_SCALE - POP_FROM_SCALE) * q
                            scaleX = scale
                            scaleY = scale
                            rotationZ = POP_FROM_DEGREES * (1f - q)
                        } else {
                            val q = KioskEasing.Spring.transform((p - POP_PEAK_AT) / (1f - POP_PEAK_AT))
                            val scale = POP_OVERSHOOT_SCALE + (1f - POP_OVERSHOOT_SCALE) * q
                            scaleX = scale
                            scaleY = scale
                        }
                    }.background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.Check, tint = colors.surface, modifier = Modifier.size(44.dpx))
        }
    }
}
