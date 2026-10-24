package com.hitachi.kiosk.feature.checkout

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.PaymentMethod

private const val PROGRESS_IN_MILLIS = 560
private const val PANEL_IN_MILLIS = 600
private const val UP_RISE_PX = 24

private const val TITLE_TRACKING_EM = -0.03f
private const val PROGRESS_TITLE_LINE_HEIGHT_EM = 1f
private const val FAILED_TITLE_LINE_HEIGHT_EM = 1.02f
private const val BODY_LINE_HEIGHT_EM = 1.4f

private const val RING_OUT_MILLIS = 1_200
private const val RING_OUT_DELAY_MILLIS = 300
private const val RING_START_ALPHA = 0.5f
private const val RING_GROWTH = 0.6f
private const val POP_FROM = 0.4f
private const val POP_ALPHA_SPAN = 1.43f
private const val SOFT_ALPHA = 0.12f
private const val FULL_TURN = 360f

/** The shake of the failure badge: (progress, px) at each keyframe of `@keyframes shake`. */
private val ShakeKeys = listOf(0f to 0f, 0.2f to -12f, 0.4f to 9f, 0.6f to -5f, 0.8f to 2f, 1f to 0f)

/**
 * "Creating your payment…", "Checking your payment…" and "Confirming payment" share one panel: the turning mark, a
 * title and a line of text (`Checkout.dc.html` `isProcessing`, `S_Checkout_creating`). Checking never claims "not
 * charged" (FR-019 AC2).
 */
@Composable
internal fun ProgressBody(stage: CheckoutStage) {
    val enter = animateEntrance(PROGRESS_IN_MILLIS)
    val (title, text) = progressCopy(stage)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 72.dpx, end = 72.dpx, top = BODY_TOP_PX.dpx, bottom = 120.dpx)
                .fadeUp(enter, UP_RISE_PX.dpx)
                .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(48.dpx, Alignment.CenterVertically),
    ) {
        ProcessingMark()
        PanelTitle(stringResource(title), PROGRESS_TITLE_LINE_HEIGHT_EM, Modifier)
        PanelText(stringResource(text), maxWidth = 780)
    }
}

/** The title and text resources of a progress [stage], by method (FR-017, FR-018). */
@Composable
private fun progressCopy(stage: CheckoutStage): Pair<Int, Int> {
    val card = stage.method == PaymentMethod.CARD
    return when (stage) {
        is CheckoutStage.Creating -> {
            R.string.checkout_creating_title to
                if (card) R.string.checkout_creating_text_card else R.string.checkout_creating_text_qr
        }

        is CheckoutStage.Checking -> {
            R.string.checkout_checking_title to R.string.checkout_checking_text
        }

        else -> {
            R.string.checkout_processing_title to
                if (card) R.string.checkout_processing_text_card else R.string.checkout_processing_text_qr
        }
    }
}

/**
 * The failure panel (`expired`, `cancelled`, `declined`, `terminalTimeout`, `offline`; FR-019): the badge, the title
 * and reason, the green note, and the actions: "Pay by X instead" or "Back to order", "Cancel order", and the full
 * width "Try again" (disabled and "Reconnecting…" while offline). There is no "Pay at the counter" (D-12a).
 */
@Composable
internal fun FailedBody(
    failure: CheckoutFailure,
    busy: Boolean,
    onEvent: (CheckoutEvent) -> Unit,
) {
    val enter = animateEntrance(PANEL_IN_MILLIS)
    val copy = failureCopy(failure)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 72.dpx, end = 72.dpx, top = BODY_TOP_PX.dpx, bottom = 48.dpx)
                .fadeUp(enter, UP_RISE_PX.dpx),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FailureBadge(paused = failure.paused)
        PanelTitle(
            text = stringResource(copy.title),
            lineHeightEm = FAILED_TITLE_LINE_HEIGHT_EM,
            modifier = Modifier.padding(top = 64.dpx, bottom = 20.dpx),
        )
        PanelText(stringResource(copy.text), maxWidth = 820)
        copy.note?.let { FailureNote(stringResource(it)) }
        Spacer(Modifier.weight(1f))
        FailureActions(failure = failure, busy = busy, onEvent = onEvent)
    }
}

/** The copy of a failure: title, reason and note (FR-019 AC1; each reason of the design has its own). */
private data class FailureCopy(
    @StringRes val title: Int,
    @StringRes val text: Int,
    @StringRes val note: Int?,
)

private fun failureCopy(failure: CheckoutFailure): FailureCopy {
    val (title, text) = failureTitleAndText(failure)
    val note =
        when {
            failure.paused -> R.string.checkout_failed_saved
            failure.notCharged -> R.string.checkout_failed_not_charged
            else -> null
        }
    return FailureCopy(title, text, note)
}

private fun failureTitleAndText(failure: CheckoutFailure): Pair<Int, Int> =
    when {
        failure.paused -> {
            R.string.checkout_failed_paused_title to R.string.checkout_failed_paused_text
        }

        failure.kind == FailureKind.EXPIRED -> {
            R.string.checkout_failed_expired_title to R.string.checkout_failed_expired_text
        }

        failure.kind == FailureKind.CANCELLED -> {
            R.string.checkout_failed_cancelled_title to
                if (failure.method == PaymentMethod.CARD) {
                    R.string.checkout_failed_cancelled_text_card
                } else {
                    R.string.checkout_failed_cancelled_text_qr
                }
        }

        failure.kind == FailureKind.DECLINED -> {
            R.string.checkout_failed_declined_title to R.string.checkout_failed_declined_text
        }

        failure.kind == FailureKind.TERMINAL_TIMEOUT -> {
            R.string.checkout_failed_timeout_title to R.string.checkout_failed_timeout_text
        }

        failure.kind == FailureKind.UNREACHABLE -> {
            R.string.checkout_failed_generic_title to R.string.checkout_failed_unreachable_text
        }

        else -> {
            R.string.checkout_failed_generic_title to R.string.checkout_failed_generic_text
        }
    }

@Composable
private fun PanelTitle(
    text: String,
    lineHeightEm: Float,
    modifier: Modifier,
) {
    KioskText(
        text = text,
        style = KioskTheme.typography.title.copy(letterSpacing = TITLE_TRACKING_EM.em, lineHeight = lineHeightEm.em),
        textAlign = TextAlign.Center,
        modifier = modifier.testTag(CHECKOUT_TITLE_TAG),
    )
}

@Composable
private fun PanelText(
    text: String,
    maxWidth: Int,
) {
    KioskText(
        text = text,
        style = KioskTheme.typography.body.copy(lineHeight = BODY_LINE_HEIGHT_EM.em),
        color = KioskTheme.colors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = maxWidth.dpx).testTag(CHECKOUT_TEXT_TAG),
    )
}

/** The green reassurance pill (`failNote`). */
@Composable
private fun FailureNote(text: String) {
    val colors = KioskTheme.colors
    KioskText(
        text = text,
        style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W600),
        color = colors.success,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .padding(top = 32.dpx)
                .background(colors.successBgAlt, KioskTheme.shapes.pill)
                .padding(horizontal = 32.dpx, vertical = 16.dpx)
                .testTag(CHECKOUT_NOTE_TAG),
    )
}

@Composable
private fun FailureActions(
    failure: CheckoutFailure,
    busy: Boolean,
    onEvent: (CheckoutEvent) -> Unit,
) {
    val other = failure.switchTo.takeUnless { failure.paused }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dpx)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dpx)) {
            if (other != null) {
                OutlineButton(
                    text =
                        stringResource(
                            if (other ==
                                PaymentMethod.CARD
                            ) {
                                R.string.checkout_pay_by_card
                            } else {
                                R.string.checkout_pay_by_qris
                            },
                        ),
                    tag = CHECKOUT_SWITCH_TAG,
                    onClick = { onEvent(CheckoutEvent.PayWith(other)) },
                    enabled = !busy && !failure.offline,
                    modifier = Modifier.weight(1f),
                )
            } else {
                OutlineButton(
                    text = stringResource(R.string.checkout_back_to_order),
                    tag = CHECKOUT_BACK_TAG,
                    onClick = { onEvent(CheckoutEvent.BackToOrder) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            SoftButton(
                text = stringResource(R.string.checkout_cancel_order),
                tag = CHECKOUT_CANCEL_TAG,
                onClick = { onEvent(CheckoutEvent.CancelOrder) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
        }
        RetryButton(
            text = stringResource(if (failure.offline) R.string.checkout_reconnecting else R.string.checkout_try_again),
            tag = CHECKOUT_TRY_AGAIN_TAG,
            onClick = { onEvent(CheckoutEvent.TryAgain) },
            enabled = !failure.offline && !busy,
        )
    }
}

/**
 * The failure badge (`Checkout.dc.html`): a 220 dpx circle that pops in, a ring that leaves it, and a soft disc that
 * shakes with the ✕ on it. While [paused] it is grey with a turning ring instead of the ✕ (`S_Checkout_offline`).
 */
@Composable
private fun FailureBadge(paused: Boolean) {
    val colors = KioskTheme.colors
    val pop = animateEntrance(KioskMotion.Checkout.FAIL_POP_MILLIS, easing = KioskEasing.Spring)
    val ring = animateEntrance(RING_OUT_MILLIS, RING_OUT_DELAY_MILLIS)
    val shake =
        animateEntrance(KioskMotion.Checkout.SHAKE_MILLIS, KioskMotion.Checkout.SHAKE_DELAY_MILLIS, KioskEasing.Linear)
    Box(
        modifier =
            Modifier
                .padding(top = 64.dpx)
                .size(220.dpx)
                .graphicsLayer {
                    val p = pop.value
                    val scale = POP_FROM + (1f - POP_FROM) * p
                    scaleX = scale
                    scaleY = scale
                    alpha = (p * POP_ALPHA_SPAN).coerceIn(0f, 1f)
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = ring.value
                        alpha = RING_START_ALPHA * (1f - p)
                        scaleX = 1f + RING_GROWTH * p
                        scaleY = 1f + RING_GROWTH * p
                    }.border(3.dpx, colors.accent, CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = shakeOffset(shake.value) * density }
                    .background(if (paused) colors.surfaceAlt else colors.accent.copy(alpha = SOFT_ALPHA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(132.dpx).background(if (paused) colors.line else colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (paused) {
                    TurningRing()
                } else {
                    KioskIcon(KioskIcons.Cross, tint = colors.surface, modifier = Modifier.size(64.dpx))
                }
            }
        }
    }
}

/** The grey open ring of the offline badge, turning once per 1.1 s (`spin`); still with reduce motion. */
@Composable
private fun TurningRing() {
    val reduce = LocalReduceMotion.current
    val turn by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec =
            infiniteRepeatable(
                tween(KioskMotion.Checkout.PROCESSING_SPINNER_LOOP_MILLIS, easing = KioskEasing.Linear),
            ),
        label = "turn",
    )
    KioskIcon(
        icon = KioskIcons.SpinnerArc,
        tint = KioskTheme.colors.muted,
        modifier = Modifier.size(72.dpx).graphicsLayer { rotationZ = if (reduce) 0f else turn },
    )
}

/** The sideways offset in px of the shake at [progress] (`@keyframes shake`, linear between keyframes). */
internal fun shakeOffset(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val index = ShakeKeys.indexOfLast { it.first <= p }.coerceAtMost(ShakeKeys.size - 2)
    val (from, fromPx) = ShakeKeys[index]
    val (to, toPx) = ShakeKeys[index + 1]
    return fromPx + (toPx - fromPx) * ((p - from) / (to - from))
}
