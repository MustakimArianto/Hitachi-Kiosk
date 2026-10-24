package com.hitachi.kiosk.feature.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.BrandLockup
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.riseIn
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.ui.MerchantLogoImage

/** Test tag of the Checkout root; the edge-walk test finds screens by it. */
const val CHECKOUT_SCREEN_TAG = "screen:SCR-11"

/** Test tag of the total at the top. */
const val CHECKOUT_AMOUNT_TAG = "checkout-amount"

/** Test tag of "Back to order". */
const val CHECKOUT_BACK_TAG = "checkout-back"

/** Test tag of "Try again" and its disabled "Reconnecting…" look. */
const val CHECKOUT_TRY_AGAIN_TAG = "checkout-try-again"

/** Test tag of the outline "Pay by X instead" button of a failure. */
const val CHECKOUT_SWITCH_TAG = "checkout-switch"

/** Test tag of "Cancel order". */
const val CHECKOUT_CANCEL_TAG = "checkout-cancel"

/** Test tag of the title of a progress or failure panel. */
const val CHECKOUT_TITLE_TAG = "checkout-title"

/** Test tag of the text under that title. */
const val CHECKOUT_TEXT_TAG = "checkout-text"

/** Test tag of the green note of a failure. */
const val CHECKOUT_NOTE_TAG = "checkout-note"

/** Test tag of "Waiting for your payment". */
const val CHECKOUT_WAITING_TAG = "checkout-waiting"

/** Test tag of the QR code. */
const val CHECKOUT_QR_TAG = "checkout-qr"

/** Test tag of the card terminal illustration. */
const val CHECKOUT_TERMINAL_TAG = "checkout-terminal"

/** Test tag of the segment of the method switcher for [method]. */
fun checkoutMethodTag(method: PaymentMethod): String = "checkout-method-$method"

/** Top of the amount block, and of the state area below it, in design px (`Checkout.dc.html`). */
private const val AMOUNT_TOP_PX = 208
internal const val BODY_TOP_PX = 416

/** Design timings of the entrance that the motion inventory does not name. */
private const val HEADER_FADE_MILLIS = 500
private const val AMOUNT_FADE_DELAY_MILLIS = 80
private const val AMOUNT_RISE_MILLIS = 900
private const val AMOUNT_RISE_DELAY_MILLIS = 120

private const val AMOUNT_LINE_HEIGHT_EM = 1.04f

/**
 * SCR-11 Checkout (`Checkout.dc.html` and `S_Checkout_creating`, `_expired`, `_cancelled`, `_declined`,
 * `_terminalTimeout`, `_offline`; FR-016–FR-019; DEV-048–DEV-050). Draws [state] exactly: the header, the amount, and
 * the body for its stage. There is no scan line, no step list, no "Step 3 of 3" and no "Pay at the counter" button
 * (D-12a, D-30).
 *
 * @param simulateBar the debug-only "Prototype only · simulate the result" bar, drawn under "Back to order" on the
 *   waiting stages. `null` in release builds (FR-019 AC3).
 */
@Composable
fun CheckoutScreen(
    state: CheckoutUiState,
    onEvent: (CheckoutEvent) -> Unit,
    modifier: Modifier = Modifier,
    simulateBar: (@Composable (Modifier) -> Unit)? = null,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(CHECKOUT_SCREEN_TAG),
    ) {
        CheckoutHeader(brandName = state.brandName)
        CheckoutAmount(
            amount = state.amount,
            modifier = Modifier.padding(start = 72.dpx, end = 72.dpx, top = AMOUNT_TOP_PX.dpx),
        )
        when (val stage = state.stage) {
            is CheckoutStage.Creating, is CheckoutStage.Checking, is CheckoutStage.Processing -> {
                ProgressBody(stage = stage)
            }

            is CheckoutStage.WaitingForQr, is CheckoutStage.WaitingForCard -> {
                WaitingBody(state = state, stage = stage, onEvent = onEvent, simulateBar = simulateBar)
            }

            is CheckoutStage.Failed -> {
                FailedBody(failure = stage.failure, busy = state.busy, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun CheckoutHeader(brandName: String) {
    val fade = animateEntrance(HEADER_FADE_MILLIS, easing = KioskEasing.Ease)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 72.dpx, end = 72.dpx, top = 48.dpx)
                .height(96.dpx)
                .fadeIn(fade),
        contentAlignment = Alignment.CenterStart,
    ) {
        BrandLockup(name = brandName, logo = { MerchantLogoImage() })
    }
}

/** "Amount to pay", "Secure payment" and the 112 dpx total, which rises in (`rise`). */
@Composable
private fun CheckoutAmount(
    amount: String,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val labels = animateEntrance(HEADER_FADE_MILLIS, AMOUNT_FADE_DELAY_MILLIS, KioskEasing.Ease)
    val rise = animateEntrance(AMOUNT_RISE_MILLIS, AMOUNT_RISE_DELAY_MILLIS)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dpx)) {
        Row(
            modifier = Modifier.fillMaxWidth().fadeIn(labels),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            KioskText(
                text = stringResource(R.string.checkout_amount_label),
                style = KioskTheme.typography.meta,
                color = colors.muted,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dpx),
            ) {
                KioskIcon(KioskIcons.Lock, tint = colors.success, modifier = Modifier.size(30.dpx))
                KioskText(
                    text = stringResource(R.string.checkout_secure),
                    style = KioskTheme.typography.meta,
                    color = colors.success,
                )
            }
        }
        Box(modifier = Modifier.clipToBounds()) {
            KioskText(
                text = amount,
                style =
                    KioskTheme.typography.display.copy(
                        fontWeight = FontWeight.W800,
                        lineHeight = AMOUNT_LINE_HEIGHT_EM.em,
                    ),
                modifier = Modifier.riseIn(rise).testTag(CHECKOUT_AMOUNT_TAG),
                maxLines = 1,
                cssLineBox = true,
            )
        }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun CheckoutQrPreview() = CheckoutPreview(CheckoutSamples.Qr)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun CheckoutCardPreview() = CheckoutPreview(CheckoutSamples.Card)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun CheckoutCreatingPreview() = CheckoutPreview(CheckoutSamples.Creating)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun CheckoutExpiredPreview() = CheckoutPreview(CheckoutSamples.Expired)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun CheckoutOfflinePreview() = CheckoutPreview(CheckoutSamples.Offline)

@Composable
private fun CheckoutPreview(state: CheckoutUiState) {
    KioskTheme(reduceMotion = true) { DesignFrame { CheckoutScreen(state = state, onEvent = {}) } }
}
