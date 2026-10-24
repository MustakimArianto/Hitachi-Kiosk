package com.hitachi.kiosk.feature.checkout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.QrCard
import com.hitachi.kiosk.core.designsystem.component.QrCenterMark
import com.hitachi.kiosk.core.designsystem.component.formatCountdown
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.theme.KioskShadow
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.ui.qr.QrCode

/** The state area fades up over 600 ms after 160 ms (`stateIn`). */
private const val STATE_IN_MILLIS = 600
private const val STATE_IN_DELAY_MILLIS = 160
private const val DOCK_MILLIS = 700
private const val DOCK_DELAY_MILLIS = 300
private const val DOCK_RISE_PX = 48
private const val UP_MILLIS = 700
private const val UP_RISE_PX = 24

/** The switcher's thumb slides over 500 ms with the springy curve; the labels change colour over 300 ms. */
private const val THUMB_MILLIS = 500
private const val LABEL_COLOR_MILLIS = 300

private const val CARD_TITLE_TRACKING_EM = -0.03f
private const val CARD_TITLE_LINE_HEIGHT_EM = 1.05f
private const val THUMB_SHADOW_ALPHA = 0.06f
private const val THUMB_SHADOW_SOFT_ALPHA = 0.05f

/**
 * The waiting stages (`qr`, `card`): the method switcher, "Waiting for your payment", the QR card or the card
 * terminal view, and the dock with "Back to order" and, in debug builds, the simulate bar (FR-017, FR-018).
 */
@Composable
internal fun WaitingBody(
    state: CheckoutUiState,
    stage: CheckoutStage,
    onEvent: (CheckoutEvent) -> Unit,
    simulateBar: (@Composable (Modifier) -> Unit)?,
) {
    val enter = animateEntrance(STATE_IN_MILLIS, STATE_IN_DELAY_MILLIS)
    val dock = animateEntrance(DOCK_MILLIS, DOCK_DELAY_MILLIS)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 72.dpx, end = 72.dpx, top = BODY_TOP_PX.dpx, bottom = 48.dpx)
                .fadeUp(enter, UP_RISE_PX.dpx),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.methods.size > 1) {
            MethodSwitcher(
                selected = stage.method,
                enabled = !state.busy,
                onSelect = { onEvent(CheckoutEvent.PayWith(it)) },
            )
        }
        WaitingStatus(modifier = Modifier.padding(top = 32.dpx))
        if (stage is CheckoutStage.WaitingForQr) {
            QrBlock(stage = stage, state = state, onEncodeFailed = { onEvent(CheckoutEvent.QrEncodeFailed) })
        } else if (stage is CheckoutStage.WaitingForCard) {
            CardBlock(amount = state.amount)
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth().fadeUp(dock, DOCK_RISE_PX.dpx),
            verticalArrangement = Arrangement.spacedBy(16.dpx),
        ) {
            OutlineButton(
                text = stringResource(R.string.checkout_back_to_order),
                tag = CHECKOUT_BACK_TAG,
                onClick = { onEvent(CheckoutEvent.BackToOrder) },
                enabled = !state.busy,
                chevron = true,
                textSizePx = 32,
                modifier = Modifier.fillMaxWidth(),
            )
            simulateBar?.invoke(Modifier.fillMaxWidth())
        }
    }
}

/** QRIS / "Debit / credit card" pill with a thumb that slides to the selected segment (`role="group"`). */
@Composable
private fun MethodSwitcher(
    selected: PaymentMethod,
    enabled: Boolean,
    onSelect: (PaymentMethod) -> Unit,
) {
    val colors = KioskTheme.colors
    val description = stringResource(R.string.checkout_methods_description)
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(104.dpx)
                .semantics { contentDescription = description }
                .background(colors.surfaceAlt, KioskTheme.shapes.pill)
                .padding(8.dpx),
    ) {
        val segment = maxWidth / 2
        val thumbX by animateDpAsState(
            targetValue = if (selected == PaymentMethod.CARD) segment else 0.dpx,
            animationSpec = motionSpec(tween(THUMB_MILLIS, easing = KioskEasing.Spring)),
            label = "thumb",
        )
        val thumbShape = RoundedCornerShape(44.dpx)
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(thumbX.roundToPx(), 0) }
                    .width(segment)
                    .height(88.dpx)
                    .kioskShadow(
                        thumbShape,
                        KioskShadow(offsetY = 2.dpx, blurRadius = 6.dpx, color = colors.ink.copy(THUMB_SHADOW_ALPHA)),
                    ).kioskShadow(
                        thumbShape,
                        KioskShadow(
                            offsetY = 10.dpx,
                            blurRadius = 24.dpx,
                            color = colors.ink.copy(THUMB_SHADOW_SOFT_ALPHA),
                        ),
                    ).background(colors.surface, thumbShape),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Segment(PaymentMethod.QRIS, R.string.checkout_method_qris, selected, enabled, onSelect, Modifier.weight(1f))
            Segment(PaymentMethod.CARD, R.string.checkout_method_card, selected, enabled, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Segment(
    method: PaymentMethod,
    label: Int,
    selected: PaymentMethod,
    enabled: Boolean,
    onSelect: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val text = stringResource(label)
    val isSelected = method == selected
    val color by animateColorAsState(
        targetValue = if (isSelected) colors.ink else colors.muted,
        animationSpec = motionSpec(tween(LABEL_COLOR_MILLIS)),
        label = "label",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .height(88.dpx)
                .clip(KioskTheme.shapes.pill)
                .checkoutButton(
                    description = text,
                    tag = checkoutMethodTag(method),
                    interactionSource = interactionSource,
                    enabled = enabled,
                    role = Role.RadioButton,
                    selected = isSelected,
                    onClick = { if (!isSelected) onSelect(method) },
                ),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = text,
            style =
                KioskTheme.typography.button.copy(
                    fontSize = 30.spx,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.em,
                ),
            color = color,
            maxLines = 1,
        )
    }
}

/** The pulsing dot and "Waiting for your payment" (`role="status"`). */
@Composable
private fun WaitingStatus(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier.testTag(CHECKOUT_WAITING_TAG).semantics(mergeDescendants = true) {
                liveRegion =
                    LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dpx),
    ) {
        PulseDot()
        KioskText(
            text = stringResource(R.string.checkout_waiting),
            style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W600),
        )
    }
}

/** The QRIS card with the merchant mark and the expiry ring (FR-017; no scan line, no steps, D-30). */
@Composable
private fun QrBlock(
    stage: CheckoutStage.WaitingForQr,
    state: CheckoutUiState,
    onEncodeFailed: () -> Unit,
) {
    val caption =
        if (state.kioskName.isBlank()) {
            state.brandName
        } else {
            stringResource(R.string.checkout_qr_caption, state.brandName, state.kioskName)
        }
    QrCard(
        title = stringResource(R.string.checkout_qr_title),
        caption = caption,
        expiryText = stringResource(R.string.checkout_qr_expires_in, formatCountdown(stage.remainingSeconds)),
        remainingSeconds = stage.remainingSeconds,
        totalSeconds = stage.totalSeconds,
        modifier = Modifier.padding(top = 32.dpx),
    ) {
        QrCode(
            content = stage.qrString,
            modifier = Modifier.testTag(CHECKOUT_QR_TAG),
            contentDescription = stringResource(R.string.checkout_qr_description),
            onEncodeFailed = onEncodeFailed,
            centerMark = { QrCenterMark() },
        )
    }
}

/** "Tap, insert or swipe on the terminal below", the accepted cards, the terminal and the bobbing arrow (FR-018). */
@Composable
private fun CardBlock(amount: String) {
    val colors = KioskTheme.colors
    val title = animateEntrance(UP_MILLIS)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KioskText(
            text = stringResource(R.string.checkout_card_title),
            style =
                KioskTheme.typography.title.copy(
                    fontSize = 64.spx,
                    letterSpacing = CARD_TITLE_TRACKING_EM.em,
                    lineHeight = CARD_TITLE_LINE_HEIGHT_EM.em,
                ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 48.dpx, bottom = 12.dpx).fadeUp(title, UP_RISE_PX.dpx),
        )
        KioskText(
            text = stringResource(R.string.checkout_card_accepted),
            style = KioskTheme.typography.body.copy(fontSize = 28.spx),
            color = colors.muted,
            textAlign = TextAlign.Center,
        )
        // Decorative (`aria-hidden` in the design): the text above carries the instruction.
        TerminalIllustration(
            amount = amount,
            amountLabel = stringResource(R.string.checkout_terminal_amount),
            modifier = Modifier.padding(top = 24.dpx).testTag(CHECKOUT_TERMINAL_TAG),
        )
        BobbingArrow()
    }
}
