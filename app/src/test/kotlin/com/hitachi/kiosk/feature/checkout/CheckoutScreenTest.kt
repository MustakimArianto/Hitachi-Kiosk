package com.hitachi.kiosk.feature.checkout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-048–DEV-050: what each Checkout control sends and shows (FR-017–FR-019; TC-074, TC-087, TC-178). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class CheckoutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<CheckoutEvent>()

    private var state by mutableStateOf(CheckoutUiState())

    private fun show(initial: CheckoutUiState) {
        state = initial
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame {
                    CheckoutScreen(
                        state = state,
                        onEvent = { events += it },
                        simulateBar = null,
                    )
                }
            }
        }
    }

    private fun text(value: String) = composeRule.onNodeWithText(value)

    @Test
    fun `the QR view has the amount, the QRIS card and the expiry, and none of the removed parts (TC-074)`() {
        show(CheckoutSamples.Qr)

        composeRule.onNodeWithTag(CHECKOUT_AMOUNT_TAG).assertTextEquals("Rp 173.000")
        text("Amount to pay").assertExists()
        text("Secure payment").assertExists()
        text("Waiting for your payment").assertExists()
        text("Urban Grill · Kiosk 3").assertExists()
        text("Code expires in 5:00").assertExists()
        composeRule.onNode(hasContentDescription("Payment QR code")).assertExists()
        // D-30: no scan line, no instruction list, no step indicator.
        listOf("Step 3 of 3", "Open any e-wallet or banking app", "Scan this code with the app").forEach {
            assertThat(composeRule.onAllNodes(hasText(it, substring = true)).fetchSemanticsNodes()).isEmpty()
        }
    }

    @Test
    fun `the card view shows the terminal instruction and no QR`() {
        show(CheckoutSamples.Card)

        text("Tap, insert or swipe\non the terminal below").assertExists()
        text("Visa, Mastercard and GPN accepted · contactless works too").assertExists()
        composeRule.onNodeWithTag(CHECKOUT_TERMINAL_TAG).assertExists()
        assertThat(composeRule.onAllNodes(hasContentDescription("Payment QR code")).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `the switcher and Back to order send their events`() {
        show(CheckoutSamples.Qr)

        composeRule.onNodeWithTag(checkoutMethodTag(PaymentMethod.CARD)).performClick()
        composeRule.onNodeWithTag(checkoutMethodTag(PaymentMethod.QRIS)).performClick()
        composeRule.onNodeWithTag(CHECKOUT_BACK_TAG).performClick()

        // The QR is already selected, so tapping QRIS sends nothing.
        assertThat(
            events,
        ).containsExactly(CheckoutEvent.PayWith(PaymentMethod.CARD), CheckoutEvent.BackToOrder).inOrder()
    }

    @Test
    fun `the switcher is hidden when only one method is enabled (FR-025)`() {
        show(CheckoutSamples.Qr.copy(methods = listOf(PaymentMethod.QRIS)))

        assertThat(composeRule.onAllNodes(hasText("Debit / credit card")).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `a failure offers the other method, Cancel order and Try again`() {
        show(CheckoutSamples.Expired)

        text("QR code expired").assertExists()
        text("The QR code expired before we received your payment.").assertExists()
        text("You have not been charged · your order is saved").assertExists()
        composeRule.onNodeWithTag(CHECKOUT_SWITCH_TAG).assertContentDescriptionEquals("Pay by card instead")
        composeRule.onNodeWithTag(CHECKOUT_SWITCH_TAG).performClick()
        composeRule.onNodeWithTag(CHECKOUT_CANCEL_TAG).performClick()
        composeRule.onNodeWithTag(CHECKOUT_TRY_AGAIN_TAG).performClick()

        assertThat(events)
            .containsExactly(
                CheckoutEvent.PayWith(PaymentMethod.CARD),
                CheckoutEvent.CancelOrder,
                CheckoutEvent.TryAgain,
            ).inOrder()
        // D-12a (TC-087, TC-178).
        assertThat(
            composeRule.onAllNodes(hasText("Pay at the counter", substring = true)).fetchSemanticsNodes(),
        ).isEmpty()
    }

    @Test
    fun `every failure has its own title and reason (FR-019 AC1)`() {
        val copy =
            mapOf(
                CheckoutSamples.Expired to
                    ("QR code expired" to "The QR code expired before we received your payment."),
                CheckoutSamples.Cancelled to
                    (
                        "Payment cancelled" to
                            "The payment was cancelled in your app. Try again with a fresh code, " +
                            "or choose another method."
                    ),
                CheckoutSamples.CancelledOnTerminal to
                    (
                        "Payment cancelled" to
                            "The payment was cancelled on the terminal. Try again or choose another method."
                    ),
                CheckoutSamples.Declined to
                    ("Card declined" to "Your bank declined the card. Try again or use another card."),
                CheckoutSamples.TerminalTimeout to
                    ("Terminal not responding" to "The card terminal didn’t respond. Try again or pay with QRIS."),
                CheckoutSamples.Unreachable to
                    ("Payment didn’t go through" to "Can't reach the restaurant system. Please try again."),
            )
        show(CheckoutSamples.Expired)
        copy.forEach { (sample, expected) ->
            state = sample
            composeRule.onNodeWithTag(CHECKOUT_TITLE_TAG).assertTextEquals(expected.first)
            composeRule.onNodeWithTag(CHECKOUT_TEXT_TAG).assertTextEquals(expected.second)
        }
    }

    @Test
    fun `an unreachable failure has no not-charged note (FR-019 AC2)`() {
        show(CheckoutSamples.Unreachable)

        assertThat(
            composeRule.onAllNodes(hasText("not been charged", substring = true)).fetchSemanticsNodes(),
        ).isEmpty()
        assertThat(composeRule.onAllNodes(hasText("Your order is saved")).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `offline pauses ordering, drops the switch button and disables Try again as Reconnecting (TC-178)`() {
        show(CheckoutSamples.Offline)

        text("Ordering is paused").assertExists()
        text("Ordering is paused — reconnecting…").assertExists()
        text("Your order is saved").assertExists()
        composeRule
            .onNodeWithTag(
                CHECKOUT_TRY_AGAIN_TAG,
            ).assertContentDescriptionEquals("Reconnecting…")
            .assertIsNotEnabled()
        assertThat(composeRule.onAllNodes(hasText("instead", substring = true)).fetchSemanticsNodes()).isEmpty()
        composeRule.onNodeWithTag(CHECKOUT_BACK_TAG).assertIsEnabled()

        composeRule.onNodeWithTag(CHECKOUT_TRY_AGAIN_TAG).performClick()
        assertThat(events).isEmpty()
    }

    @Test
    fun `creating, checking and processing have their own copy by method`() {
        show(CheckoutSamples.Creating)
        text("Creating your payment…").assertExists()
        text("Setting up your QR code. This only takes a moment.").assertExists()

        state = CheckoutSamples.CreatingCard
        text("Getting the card terminal ready.").assertExists()

        state = CheckoutSamples.Checking
        text("Checking your payment…").assertExists()
        text("We haven’t seen it yet. Please keep this screen open.").assertExists()

        state = CheckoutSamples.Processing
        text("Confirming payment").assertExists()
        text("Waiting for your bank to confirm. Please keep this screen open.").assertExists()

        state = CheckoutSamples.ProcessingCard
        text("Keep your card on the terminal until it beeps.").assertExists()
    }

    @Test
    fun `buttons are disabled while a leave action runs`() {
        show(CheckoutSamples.Qr.copy(busy = true))

        composeRule.onNodeWithTag(CHECKOUT_BACK_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(CHECKOUT_BACK_TAG).performClick()
        assertThat(events).isEmpty()
    }

    @Test
    fun `shake offset follows the keyframes`() {
        assertThat(shakeOffset(0f)).isEqualTo(0f)
        assertThat(shakeOffset(0.2f)).isEqualTo(-12f)
        assertThat(shakeOffset(0.4f)).isEqualTo(9f)
        assertThat(shakeOffset(1f)).isEqualTo(0f)
    }
}
