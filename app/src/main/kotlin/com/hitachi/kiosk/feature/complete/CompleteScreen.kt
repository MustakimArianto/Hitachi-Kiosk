package com.hitachi.kiosk.feature.complete

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.ExpiryRing
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentMethod

/** Test tag of the Complete root; the edge-walk test finds screens by it. */
const val COMPLETE_SCREEN_TAG = "screen:SCR-12"

/** Test tag of the order number; its text is the whole number ("A247"). */
const val COMPLETE_ORDER_NUMBER_TAG = "complete-order-number"

/** Test tag of "Rp X paid with QRIS". */
const val COMPLETE_PAID_TAG = "complete-paid"

/** Test tag of the order-type instruction. */
const val COMPLETE_INFO_TAG = "complete-info"

/** Test tag of "Back to the start in Ns". */
const val COMPLETE_COUNTDOWN_TAG = "complete-countdown"

/** Test tag of "Done". */
const val COMPLETE_DONE_TAG = "complete-done"

// Layout of Complete.dc.html in design px; the screen is a fixed 1080 × 1920 composition.
private const val SUCCESS_TOP_PX = 120
private const val SIDE_PX = 72
private const val TITLE_TOP_PX = 580
private const val TITLE_GAP_PX = 16
private const val TICKET_TOP_PX = 860
private const val TICKET_PAD_H_PX = 56
private const val TICKET_PAD_V_PX = 48
private const val TICKET_GAP_PX = 8
private const val BORDER_PX = 2
private const val DIGIT_GAP_PX = 4
private const val DASH_FACTOR = 3f
private const val DIVIDER_MARGIN_PX = 18
private const val INFO_GAP_PX = 20
private const val INFO_TILE_PX = 72
private const val INFO_TILE_RADIUS_PX = 22
private const val INFO_ICON_PX = 40
private const val DOCK_BOTTOM_PX = 48
private const val DOCK_HEIGHT_PX = 168
private const val DOCK_PAD_START_PX = 40
private const val DOCK_PAD_END_PX = 12
private const val DOCK_GAP_PX = 24
private const val RING_PX = 56
private const val RING_TEXT_GAP_PX = 18
private const val DONE_HEIGHT_PX = 144
private const val DONE_PAD_H_PX = 96
private const val DONE_RADIUS_PX = 36

// Entrance timings of Complete.dc.html that the motion inventory does not name.
private const val SUCCESS_RISE_MILLIS = 700
private const val SUCCESS_RISE_DELAY_MILLIS = 1100
private const val TITLE_RISE_MILLIS = 900
private const val TITLE_RISE_DELAY_MILLIS = 1300
private const val SUBLINE_UP_MILLIS = 700
private const val SUBLINE_UP_DELAY_MILLIS = 1420
private const val SUBLINE_UP_PX = 32
private const val TICKET_IN_MILLIS = 900
private const val TICKET_IN_DELAY_MILLIS = 1500
private const val TICKET_IN_PX = 70
private const val DOCK_UP_MILLIS = 700
private const val DOCK_UP_DELAY_MILLIS = 2000
private const val DIGIT_RISE_FRACTION = 0.5f

private const val RISE_FRACTION = 1.05f
private const val TITLE_LINE_HEIGHT_EM = 1.04f
private const val TICKET_SHADOW_ALPHA = 0.06f
private const val DIGIT_SIZE_PX = 240
private const val DIGIT_SPACING_EM = -0.04f

/**
 * SCR-12 Complete (`Complete.dc.html`, `S_Complete_takeaway`; FR-020; DEV-060). Draws [state] exactly: the accent
 * flood, "Payment successful" with the badge, the drawn check, ring waves and confetti, "Thank you!", the order number
 * with its first character in accent, the order-type instruction, the decorative receipt-printer illustration, and the
 * countdown with "Done". The printer is only a picture: printing and the print-failed card are not part of 0.6.0.
 */
@Composable
fun CompleteScreen(
    state: CompleteUiState,
    onEvent: (CompleteEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = rememberCompleteTimeline()
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(COMPLETE_SCREEN_TAG),
    ) {
        CompleteFlood(time)
        SuccessLine(time)
        CompleteRings(time)
        CompleteConfetti(time)
        CompleteBadge(time)
        ThankYou(state = state, time = time)
        Ticket(state = state, time = time)
        CompleteReceiptPrinter(time)
        ReturnDock(state = state, time = time, onDone = { onEvent(CompleteEvent.Done) })
    }
}

/** "Payment successful": rises into view out of a clipped line (`rise`). */
@Composable
private fun SuccessLine(time: State<Float>) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = SIDE_PX.dpx, end = SIDE_PX.dpx, top = SUCCESS_TOP_PX.dpx),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(modifier = Modifier.clipToBounds()) {
            KioskText(
                text = stringResource(R.string.complete_success),
                style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W600),
                color = KioskTheme.colors.success,
                modifier = Modifier.riseWith(time, SUCCESS_RISE_DELAY_MILLIS, SUCCESS_RISE_MILLIS),
            )
        }
    }
}

/** "Thank you!" and "Rp X paid with QRIS". */
@Composable
private fun ThankYou(
    state: CompleteUiState,
    time: State<Float>,
) {
    val colors = KioskTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = SIDE_PX.dpx, end = SIDE_PX.dpx, top = TITLE_TOP_PX.dpx),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TITLE_GAP_PX.dpx),
    ) {
        Box(modifier = Modifier.clipToBounds()) {
            KioskText(
                text = stringResource(R.string.complete_thanks),
                style =
                    KioskTheme.typography.display.copy(
                        fontWeight = FontWeight.W800,
                        lineHeight = TITLE_LINE_HEIGHT_EM.em,
                    ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                cssLineBox = true,
                modifier = Modifier.riseWith(time, TITLE_RISE_DELAY_MILLIS, TITLE_RISE_MILLIS),
            )
        }
        KioskText(
            text = paidText(state),
            style = KioskTheme.typography.body,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fadeUpWith(time, SUBLINE_UP_DELAY_MILLIS, SUBLINE_UP_MILLIS, SUBLINE_UP_PX)
                    .testTag(COMPLETE_PAID_TAG),
        )
    }
}

@Composable
private fun paidText(state: CompleteUiState): String =
    when (state.method) {
        PaymentMethod.QRIS -> {
            stringResource(
                R.string.complete_paid_with,
                state.amount,
                stringResource(R.string.complete_method_qris),
            )
        }

        PaymentMethod.CARD -> {
            stringResource(
                R.string.complete_paid_with,
                state.amount,
                stringResource(R.string.complete_method_card),
            )
        }

        null -> {
            stringResource(R.string.complete_paid, state.amount)
        }
    }

/** The white ticket card: "Your order number", the 240 dpx digits and the order-type instruction (`ticketIn`). */
@Composable
private fun Ticket(
    state: CompleteUiState,
    time: State<Float>,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.dock
    val shadow = KioskTheme.elevation.floating.let { it.copy(color = colors.ink.copy(alpha = TICKET_SHADOW_ALPHA)) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = SIDE_PX.dpx, end = SIDE_PX.dpx, top = TICKET_TOP_PX.dpx)
                .fadeUpWith(time, TICKET_IN_DELAY_MILLIS, TICKET_IN_MILLIS, TICKET_IN_PX)
                .kioskShadow(shape, shadow)
                .background(colors.surface, shape)
                .border(BORDER_PX.dpx, colors.cardBorder, shape)
                .padding(horizontal = TICKET_PAD_H_PX.dpx, vertical = TICKET_PAD_V_PX.dpx),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TICKET_GAP_PX.dpx),
    ) {
        KioskText(
            text = stringResource(R.string.complete_order_number_label),
            style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W600),
            color = colors.muted,
        )
        OrderNumber(number = state.orderNumber, time = time)
        DashedDivider(modifier = Modifier.padding(vertical = DIVIDER_MARGIN_PX.dpx))
        Instruction(orderType = state.orderType)
    }
}

/** The order number, one character at a time (`digit`), the first in accent. Its semantics are the whole number. */
@Composable
private fun OrderNumber(
    number: String,
    time: State<Float>,
) {
    val colors = KioskTheme.colors
    val style =
        KioskTheme.typography.display.copy(
            fontSize = DIGIT_SIZE_PX.spx,
            fontWeight = FontWeight.W800,
            letterSpacing = DIGIT_SPACING_EM.em,
            lineHeight = 1f.em,
        )
    Row(
        modifier =
            Modifier.clearAndSetSemantics {
                testTag = COMPLETE_ORDER_NUMBER_TAG
                text = AnnotatedString(number)
            },
        horizontalArrangement = Arrangement.spacedBy(DIGIT_GAP_PX.dpx),
    ) {
        number.forEachIndexed { index, char ->
            val delay =
                KioskMotion.Complete.NUMBER_DIGITS_DELAY_MILLIS +
                    index * KioskMotion.Complete.NUMBER_DIGITS_STAGGER_MILLIS
            KioskText(
                text = char.toString(),
                style = style,
                color = if (index == 0) colors.accent else colors.ink,
                maxLines = 1,
                modifier =
                    Modifier.graphicsLayer {
                        val p = segment(time.value, delay, KioskMotion.Complete.NUMBER_DIGITS_MILLIS)
                        alpha = p
                        translationY = size.height * DIGIT_RISE_FRACTION * (1f - p)
                    },
            )
        }
    }
}

/** The dashed rule of the ticket (`border-top: 2px dashed`). */
@Composable
private fun DashedDivider(modifier: Modifier = Modifier) {
    val color = KioskTheme.colors.line
    val strokePx = BORDER_PX.dpx
    Canvas(modifier = modifier.fillMaxWidth().height(strokePx).clearAndSetSemantics {}) {
        val stroke = strokePx.toPx()
        drawLine(
            color = color,
            start = Offset(0f, stroke / 2),
            end = Offset(size.width, stroke / 2),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_FACTOR * stroke, DASH_FACTOR * stroke)),
        )
    }
}

/** The order-type line with its icon tile (FR-020, A-18, A-19). The order type of a recovered order is unknown. */
@Composable
private fun Instruction(orderType: OrderType?) {
    val colors = KioskTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(INFO_GAP_PX.dpx),
    ) {
        Box(
            modifier =
                Modifier
                    .size(INFO_TILE_PX.dpx)
                    .background(colors.surfaceAlt, RoundedCornerShape(INFO_TILE_RADIUS_PX.dpx)),
            contentAlignment = Alignment.Center,
        ) {
            val icon = if (orderType == OrderType.TAKEAWAY) KioskIcons.Bag else KioskIcons.DineIn
            KioskIcon(icon = icon, tint = colors.ink, modifier = Modifier.size(INFO_ICON_PX.dpx))
        }
        KioskText(
            text = instructionOf(orderType),
            style = KioskTheme.typography.body,
            modifier = Modifier.weight(1f).testTag(COMPLETE_INFO_TAG),
        )
    }
}

@Composable
private fun instructionOf(orderType: OrderType?): String =
    when (orderType) {
        OrderType.DINE_IN -> stringResource(R.string.complete_info_dine_in)
        OrderType.TAKEAWAY -> stringResource(R.string.complete_info_takeaway)
        null -> stringResource(R.string.complete_info_neutral)
    }

/** The bottom dock: the 20 s ring with "Back to the start in Ns" and the "Done" button (`up`). */
@Composable
private fun ReturnDock(
    state: CompleteUiState,
    time: State<Float>,
    onDone: () -> Unit,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.dock
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = SIDE_PX.dpx, end = SIDE_PX.dpx, bottom = DOCK_BOTTOM_PX.dpx)
                    .height(DOCK_HEIGHT_PX.dpx)
                    .fadeUpWith(time, DOCK_UP_DELAY_MILLIS, DOCK_UP_MILLIS, SUBLINE_UP_PX)
                    .background(colors.surface, shape)
                    .border(BORDER_PX.dpx, colors.cardBorder, shape)
                    .padding(start = DOCK_PAD_START_PX.dpx, end = DOCK_PAD_END_PX.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DOCK_GAP_PX.dpx),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RING_TEXT_GAP_PX.dpx),
            ) {
                ExpiryRing(
                    remainingSeconds = state.secondsLeft,
                    totalSeconds = CompleteUiState.RETURN_SECONDS,
                    size = RING_PX.dpx,
                )
                KioskText(
                    text = stringResource(R.string.complete_countdown, state.secondsLeft),
                    style = KioskTheme.typography.body.copy(fontSize = 28.spx, fontWeight = FontWeight.W500),
                    color = colors.muted,
                    modifier = Modifier.testTag(COMPLETE_COUNTDOWN_TAG),
                )
            }
            DoneButton(onClick = onDone)
        }
    }
}

@Composable
private fun DoneButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = animatePressScale(interaction)
    val shape = RoundedCornerShape(DONE_RADIUS_PX.dpx)
    Box(
        modifier =
            Modifier
                .height(DONE_HEIGHT_PX.dpx)
                .pressScale(scale)
                .clip(shape)
                .background(KioskTheme.colors.accent)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = DONE_PAD_H_PX.dpx)
                .testTag(COMPLETE_DONE_TAG),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = stringResource(R.string.complete_done),
            style = KioskTheme.typography.button.copy(fontSize = 36.spx),
            color = KioskTheme.colors.surface,
            maxLines = 1,
        )
    }
}

/** `rise` on the shared clock: slides up from 105 % of its own height. Put it in a parent that clips. */
private fun Modifier.riseWith(
    time: State<Float>,
    delayMillis: Int,
    durationMillis: Int,
): Modifier =
    graphicsLayer {
        val p = segment(time.value, delayMillis, durationMillis)
        translationY = size.height * RISE_FRACTION * (1f - p)
    }

/** `up`/`ticketIn` on the shared clock: fades in while moving up by [distancePx] design px. */
@Composable
internal fun Modifier.fadeUpWith(
    time: State<Float>,
    delayMillis: Int,
    durationMillis: Int,
    distancePx: Int,
): Modifier {
    val distance = distancePx.dpx
    return graphicsLayer {
        val p = segment(time.value, delayMillis, durationMillis)
        alpha = p
        translationY = distance.toPx() * (1f - p)
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun CompleteDineInPreview() = CompletePreview(CompleteSamples.DineIn)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun CompleteTakeawayPreview() = CompletePreview(CompleteSamples.Takeaway)

@Composable
private fun CompletePreview(state: CompleteUiState) {
    KioskTheme(reduceMotion = true) { DesignFrame { CompleteScreen(state = state, onEvent = {}) } }
}
