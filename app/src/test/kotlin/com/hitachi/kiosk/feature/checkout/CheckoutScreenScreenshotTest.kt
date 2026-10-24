package com.hitachi.kiosk.feature.checkout

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DEV-048–DEV-050: the `Checkout` artboard in its QR and card views, "Checking your payment…", "Confirming payment" and
 * every `S_Checkout_*` board (TC-074, TC-178). The release screen has no simulate bar, so none is drawn here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class CheckoutScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Checkout QR`() = capture("Checkout", CheckoutSamples.Qr)

    @Test
    fun `Checkout card`() = capture("Checkout_card", CheckoutSamples.Card)

    @Test
    fun `Checkout checking`() = capture("Checkout_checking", CheckoutSamples.Checking)

    @Test
    fun `Checkout processing`() = capture("Checkout_processing", CheckoutSamples.Processing)

    @Test
    fun `Checkout processing card`() = capture("Checkout_processingCard", CheckoutSamples.ProcessingCard)

    @Test
    fun `S_Checkout_creating`() = capture("S_Checkout_creating", CheckoutSamples.Creating)

    @Test
    fun `S_Checkout_expired`() = capture("S_Checkout_expired", CheckoutSamples.Expired)

    @Test
    fun `S_Checkout_cancelled`() = capture("S_Checkout_cancelled", CheckoutSamples.Cancelled)

    @Test
    fun `S_Checkout_declined`() = capture("S_Checkout_declined", CheckoutSamples.Declined)

    @Test
    fun `S_Checkout_terminalTimeout`() = capture("S_Checkout_terminalTimeout", CheckoutSamples.TerminalTimeout)

    @Test
    fun `S_Checkout_offline`() = capture("S_Checkout_offline", CheckoutSamples.Offline)

    @Test
    fun `Checkout cancelled on the terminal`() = capture("Checkout_cancelledCard", CheckoutSamples.CancelledOnTerminal)

    private fun capture(
        name: String,
        state: CheckoutUiState,
    ) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { CheckoutScreen(state = state, onEvent = {}) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
