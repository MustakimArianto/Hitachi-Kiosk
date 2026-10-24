package com.hitachi.kiosk.feature.idle

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-024 / FR-006: tap anywhere, the staff corner hold boundaries and the slideshow timing (TC-020–TC-022, TC-024). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class IdleScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<IdleEvent>()

    private fun show(state: IdleUiState = IdleSamples.Default) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { IdleScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    @Test
    fun `a tap in the middle starts an order`() {
        show()

        composeRule.onNodeWithTag(IDLE_TAP_TAG).assertContentDescriptionEquals("Tap to order").performClick()

        assertThat(events).containsExactly(IdleEvent.TapToOrder)
    }

    @Test
    fun `a tap on the sheet text also starts an order`() {
        show()

        composeRule.onNodeWithText("Hungry?").performClick()

        assertThat(events).containsExactly(IdleEvent.TapToOrder)
    }

    @Test
    fun `without promo images the tone slide shows and a tap still starts an order`() {
        show(IdleSamples.NoPromos)

        composeRule.onNodeWithTag(idleSlideTag(0)).assertExists()
        composeRule.onNodeWithTag(IDLE_TAP_TAG).performClick()

        assertThat(events).containsExactly(IdleEvent.TapToOrder)
    }

    @Test
    fun `holding the staff corner for 1,9 s does nothing, 2,0 s opens staff settings`() {
        show()
        composeRule.mainClock.autoAdvance = false
        val corner = composeRule.onNodeWithTag(IDLE_STAFF_CORNER_TAG)

        corner.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_900)
        assertThat(events).isEmpty()

        composeRule.mainClock.advanceTimeBy(100)
        assertThat(events).containsExactly(IdleEvent.StaffHold)

        corner.performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(100)
        assertThat(events).containsExactly(IdleEvent.StaffHold)
    }

    @Test
    fun `releasing the corner at 1,9 s cancels the hold`() {
        show()
        composeRule.mainClock.autoAdvance = false
        val corner = composeRule.onNodeWithTag(IDLE_STAFF_CORNER_TAG)

        corner.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_900)
        corner.performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertThat(events).isEmpty()
    }

    @Test
    fun `sliding out of the corner cancels the hold`() {
        show()
        composeRule.mainClock.autoAdvance = false
        val corner = composeRule.onNodeWithTag(IDLE_STAFF_CORNER_TAG)

        corner.performTouchInput {
            down(center)
            moveBy(
                androidx.compose.ui.geometry
                    .Offset(-width.toFloat(), 0f),
            )
        }
        composeRule.mainClock.advanceTimeBy(2_500)

        assertThat(events).isEmpty()
    }

    @Test
    fun `a short tap on the staff corner does not start an order`() {
        show()

        composeRule
            .onNodeWithTag(IDLE_STAFF_CORNER_TAG)
            .assertContentDescriptionEquals("Staff settings, press and hold")
            .performClick()
        composeRule.mainClock.advanceTimeBy(3_000)

        assertThat(events).isEmpty()
    }

    @Test
    fun `slides advance every 7 s`() {
        show()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(idleSlideTag(0)).assertExists()

        composeRule.mainClock.advanceTimeBy(6_900)
        composeRule.onNodeWithTag(idleSlideTag(0)).assertExists()
        composeRule.onNodeWithTag(idleSlideTag(1)).assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag(idleSlideTag(1)).assertExists()

        composeRule.mainClock.advanceTimeBy(7_000)
        composeRule.onNodeWithTag(idleSlideTag(2)).assertExists()

        composeRule.mainClock.advanceTimeBy(7_000)
        composeRule.onNodeWithTag(idleSlideTag(0)).assertExists()
    }

    @Test
    fun `the issue dot shows only when flagged`() {
        show(IdleSamples.Issues)

        composeRule
            .onNodeWithTag(IDLE_ISSUE_DOT_TAG)
            .assertContentDescriptionEquals("Staff attention needed")
    }

    @Test
    fun `no issue dot by default`() {
        show()

        composeRule.onNodeWithTag(IDLE_ISSUE_DOT_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Urban Grill").assertExists()
    }
}
