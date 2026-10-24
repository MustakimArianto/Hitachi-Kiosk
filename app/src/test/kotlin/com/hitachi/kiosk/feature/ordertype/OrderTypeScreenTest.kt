package com.hitachi.kiosk.feature.ordertype

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-025 / FR-007: text-only cards, the single-option note, one pick and the 950 ms advance (TC-025–TC-027, TC-174). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class OrderTypeScreenTest {
    @get:Rule(order = 0)
    val main = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val events = mutableListOf<OrderTypeEvent>()

    private fun show(state: OrderTypeUiState) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { OrderTypeScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    private fun text(value: String) = composeRule.onNodeWithText(value, useUnmergedTree = true)

    @Test
    fun `both types show as text cards with the welcome and the question`() {
        show(OrderTypeSamples.Default)

        text("Welcome to").assertExists()
        composeRule.onNodeWithContentDescription("Welcome to Urban Grill").assertExists()
        text("Where are you eating today?").assertExists()
        text("Dine in").assertExists()
        text("Served on a tray. We'll call your number.").assertExists()
        text("Takeaway").assertExists()
        text("Packed in a sealed bag, ready to go.").assertExists()
        text("Powered by Hitachi Kiosk").assertExists()
        composeRule.onNodeWithTag(ORDER_TYPE_NOTE_TAG).assertDoesNotExist()
    }

    @Test
    fun `with one type there is one card, the single question and the note`() {
        show(OrderTypeSamples.SingleOption)

        composeRule.onNodeWithTag(orderTypeCardTag(OrderType.DINE_IN)).assertExists()
        composeRule.onNodeWithTag(orderTypeCardTag(OrderType.TAKEAWAY)).assertDoesNotExist()
        text("Ready when you are").assertExists()
        text("Takeaway isn't offered at this restaurant right now.").assertExists()
    }

    @Test
    fun `a tap on a card picks its type`() {
        show(OrderTypeSamples.Default)

        composeRule.onNodeWithTag(orderTypeCardTag(OrderType.TAKEAWAY)).assertIsNotSelected().performClick()

        assertThat(events).containsExactly(OrderTypeEvent.Pick(OrderType.TAKEAWAY))
    }

    @Test
    fun `the picked card is selected and shows the check`() {
        show(OrderTypeSamples.Selected)

        composeRule.onNodeWithTag(orderTypeCardTag(OrderType.DINE_IN)).assertIsSelected()
        composeRule.onNodeWithTag(orderTypeCardTag(OrderType.TAKEAWAY)).assertIsNotSelected()
        composeRule.onNodeWithTag(ORDER_TYPE_CHECK_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a double selection keeps the first card and navigates once, 950 ms after the tap`() {
        val sessions = DefaultOrderSessionHolder()
        val viewModel = OrderTypeViewModel(FakeCatalogRepository(), sessions)
        var menuOpened = 0
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { OrderTypeRoute(onNavigateToMenu = { menuOpened++ }, viewModel = viewModel) }
            }
        }
        main.dispatcher.scheduler.runCurrent()

        composeRule.onNodeWithTag(orderTypeCardTag(OrderType.DINE_IN)).performClick()
        main.dispatcher.scheduler.advanceTimeBy(400)
        composeRule.onNodeWithTag(orderTypeCardTag(OrderType.TAKEAWAY)).performClick()
        main.dispatcher.scheduler.advanceTimeBy(549)
        main.dispatcher.scheduler.runCurrent()
        composeRule.waitForIdle()
        assertThat(menuOpened).isEqualTo(0)

        main.dispatcher.scheduler.advanceTimeBy(1)
        main.dispatcher.scheduler.runCurrent()
        composeRule.waitForIdle()
        assertThat(menuOpened).isEqualTo(1)

        main.dispatcher.scheduler.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertThat(menuOpened).isEqualTo(1)
        assertThat(
            sessions.current.value!!
                .state.value.orderType,
        ).isEqualTo(OrderType.DINE_IN)
        composeRule.onNodeWithTag(orderTypeCardTag(OrderType.DINE_IN)).assertIsSelected()
    }
}
