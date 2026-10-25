package com.hitachi.kiosk.feature.complete

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-060: what Complete shows and sends (FR-020; TC-088–TC-090). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class CompleteScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<CompleteEvent>()

    private fun show(state: CompleteUiState) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { CompleteScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    @Test
    fun `dine in shows the number, the amount and the receipt copy (TC-088)`() {
        show(CompleteSamples.DineIn)

        composeRule.onNodeWithTag(COMPLETE_ORDER_NUMBER_TAG).assertTextEquals("A247")
        composeRule.onNodeWithTag(COMPLETE_PAID_TAG).assertTextEquals("Rp 173.000 paid with QRIS")
        composeRule
            .onNodeWithTag(COMPLETE_INFO_TAG)
            .assertTextEquals(
                "Dine in · Take your receipt and watch the screen. We’ll call your number when your tray is ready.",
            )
        composeRule.onNodeWithTag(COMPLETE_COUNTDOWN_TAG).assertTextEquals("Back to the start in 20s")
    }

    @Test
    fun `takeaway shows the pick up copy (TC-090)`() {
        show(CompleteSamples.Takeaway)

        composeRule
            .onNodeWithTag(COMPLETE_INFO_TAG)
            .assertTextEquals("Takeaway · Pick up at the counter when your number is called.")
    }

    @Test
    fun `a card payment says paid with Card`() {
        show(CompleteSamples.Card)

        composeRule.onNodeWithTag(COMPLETE_PAID_TAG).assertTextEquals("Rp 173.000 paid with Card")
    }

    @Test
    fun `the decorative receipt printer is shown without any print action`() {
        show(CompleteSamples.DineIn)

        composeRule.onNodeWithTag(COMPLETE_PRINTER_TAG).assertExists()
    }

    @Test
    fun `Done sends the Done event (TC-089)`() {
        show(CompleteSamples.DineIn)

        composeRule.onNodeWithTag(COMPLETE_DONE_TAG).performClick()

        assertThat(events).containsExactly(CompleteEvent.Done)
    }

    @Test
    fun `there is no print failure card because printing is not part of 0_6_0`() {
        show(CompleteSamples.DineIn)

        assertThat(composeRule.onAllNodes(hasText("Receipt couldn’t print")).fetchSemanticsNodes()).isEmpty()
    }
}
