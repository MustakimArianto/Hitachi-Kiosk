package com.hitachi.kiosk.feature.order

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-037 / DEV-037a / DEV-038: what each Your order control sends and shows (FR-012, FR-015; TC-053, TC-054). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class YourOrderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<YourOrderEvent>()

    private val disabled = SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)

    private var state by mutableStateOf(YourOrderUiState())

    private fun show(initial: YourOrderUiState) {
        state = initial
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { YourOrderScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    @Test
    fun `lines show their worded options and send plus, minus and remove`() {
        show(YourOrderTestStates.Default)

        composeRule.onNodeWithText("Regular · Make it a meal · Extra smoked cheddar · No onion").assertExists()
        composeRule
            .onNodeWithTag(yourOrderDecrementTag("l2"))
            .assertContentDescriptionEquals("Remove Crispy Chicken (2 pc)")
            .performClick()
        composeRule
            .onNodeWithTag(yourOrderDecrementTag("l4"))
            .assertContentDescriptionEquals("Remove one Iced Lychee Tea")
            .performClick()
        composeRule
            .onNodeWithTag(yourOrderIncrementTag("l3"))
            .assertContentDescriptionEquals("Add one French Fries")
            .performClick()

        assertThat(events)
            .containsExactly(
                YourOrderEvent.Decrement("l2"),
                YourOrderEvent.Decrement("l4"),
                YourOrderEvent.Increment("l3"),
            ).inOrder()
    }

    @Test
    fun `header, methods and Pay send their events`() {
        show(YourOrderTestStates.Default)

        composeRule.onNodeWithTag(YOUR_ORDER_ADD_MORE_TAG).performClick()
        composeRule.onNodeWithTag(yourOrderMethodTag(PaymentMethod.QRIS)).assertIsSelected()
        composeRule.onNodeWithTag(yourOrderMethodTag(PaymentMethod.CARD)).performClick()
        composeRule
            .onNodeWithTag(YOUR_ORDER_PAY_TAG)
            .assertContentDescriptionEquals("Pay Rp 173.000")
            .assertIsEnabled()
            .performClick()

        assertThat(events)
            .containsExactly(
                YourOrderEvent.AddMore,
                YourOrderEvent.SelectMethod(PaymentMethod.CARD),
                YourOrderEvent.Pay,
            ).inOrder()
    }

    @Test
    fun `the Pay label equals the panel total (TC-066)`() {
        show(YourOrderTestStates.Default)

        composeRule.onNodeWithTag(YOUR_ORDER_TOTAL_TAG).assert(hasText("Rp 173.000"))
        composeRule.onNodeWithText("Pay Rp 173.000", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `Details shows Subtotal, Promo, the tax label and Rounding (TC-173)`() {
        show(YourOrderTestStates.Default)
        composeRule.onNodeWithText("Subtotal").assertDoesNotExist()

        composeRule
            .onNodeWithTag(YOUR_ORDER_DETAILS_TAG)
            .assertContentDescriptionEquals("Show price details")
            .performClick()
        assertThat(events).containsExactly(YourOrderEvent.ToggleDetails)

        state = YourOrderTestStates.DetailsOpen
        composeRule.onNodeWithText("Subtotal", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Rp 10.000 off over Rp 100.000", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("− Rp 10.000", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Restaurant tax (PB1 10%)", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("+ Rp 300", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Pay Rp 173.000", useUnmergedTree = true).assertExists()

        state = YourOrderTestStates.Default
        composeRule.onNodeWithText("Subtotal", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the item count pluralizes (TC-054)`() {
        show(YourOrderTestStates.Default.copy(itemCount = 1))
        composeRule.onNodeWithTag(YOUR_ORDER_COUNT_TAG).assert(hasText("1 item"))

        state = YourOrderTestStates.Default.copy(itemCount = 2)
        composeRule.onNodeWithTag(YOUR_ORDER_COUNT_TAG).assert(hasText("2 items"))
    }

    @Test
    fun `the empty tray offers Back to menu and Pay is disabled (TC-053)`() {
        show(YourOrderTestStates.EmptyTray)

        composeRule.onNodeWithText("Your tray is empty").assertExists()
        composeRule.onNodeWithText("Add something tasty to get started.").assertExists()
        composeRule.onNodeWithTag(YOUR_ORDER_PAY_TAG).assert(disabled).performClick()
        composeRule.onNodeWithTag(YOUR_ORDER_PAY_TAG).assertContentDescriptionEquals("Add an item to pay")
        composeRule.onNodeWithTag(YOUR_ORDER_BACK_TO_MENU_TAG).performScrollTo().performClick()

        assertThat(events).containsExactly(YourOrderEvent.BackToMenu)
    }

    @Test
    fun `price changed offers Review and blocks Pay with its reason (TC-177)`() {
        show(YourOrderTestStates.PriceChanged)

        composeRule.onNodeWithText("Prices have been updated. Please review your order.").assertExists()
        composeRule.onNodeWithText("Price updated · was Rp 75.000").assertExists()
        composeRule.onNodeWithTag(YOUR_ORDER_PAY_TAG).assert(disabled)
        composeRule.onNodeWithTag(YOUR_ORDER_PAY_TAG).assertContentDescriptionEquals("Review your order to continue")
        composeRule.onNodeWithTag(YOUR_ORDER_BANNER_ACTION_TAG).assertContentDescriptionEquals("Review").performClick()

        assertThat(events).containsExactly(YourOrderEvent.ReviewPrices)
    }

    @Test
    fun `an unavailable line offers Remove on the line and in the banner (TC-177)`() {
        show(YourOrderTestStates.ItemUnavailable)

        composeRule.onNodeWithText("1 item is no longer available.").assertExists()
        composeRule.onNodeWithText("No longer available").assertExists()
        composeRule.onNodeWithTag(yourOrderIncrementTag("l3")).assertDoesNotExist()
        composeRule
            .onNodeWithTag(yourOrderRemoveTag("l3"))
            .assertContentDescriptionEquals("Remove French Fries")
            .performClick()
        composeRule.onNodeWithTag(YOUR_ORDER_BANNER_ACTION_TAG).performClick()
        composeRule
            .onNodeWithTag(YOUR_ORDER_PAY_TAG)
            .assertContentDescriptionEquals("Remove the unavailable item to pay")

        assertThat(events).containsExactly(YourOrderEvent.Remove("l3"), YourOrderEvent.RemoveUnavailable).inOrder()
    }

    @Test
    fun `offline, no methods and the promo nudge show as designed`() {
        show(YourOrderTestStates.Offline)
        composeRule.onNodeWithText("Ordering is paused — reconnecting…").assertExists()
        composeRule.onNodeWithTag(YOUR_ORDER_PAY_TAG).assertContentDescriptionEquals("Pay unavailable while offline")

        state = YourOrderTestStates.NoMethods
        composeRule.onNodeWithTag(YOUR_ORDER_NO_METHODS_TAG).assertExists()
        composeRule.onNodeWithText("Please ask a team member for help.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(yourOrderMethodTag(PaymentMethod.QRIS)).assertDoesNotExist()
        composeRule.onNodeWithTag(YOUR_ORDER_PAY_TAG).assertContentDescriptionEquals("Pay unavailable")

        state = YourOrderTestStates.PromoNudge
        composeRule.onNodeWithText("Add Rp 8.000 more to get Rp 10.000 off").assertExists()
        composeRule.onNodeWithTag(YOUR_ORDER_PAY_TAG).assertIsEnabled()
    }

    @Test
    fun `a Budget Plan order shows its tag row`() {
        show(YourOrderTestStates.Plan)

        composeRule.onNodeWithTag(YOUR_ORDER_PLAN_TAG).assertExists()
        composeRule.onNodeWithText("Budget Plan", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2 adults · 1 child · Rp 150.000", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `Edit plan on the plan row sends EditPlan (TC-203)`() {
        show(YourOrderTestStates.Plan)

        composeRule.onNodeWithTag(YOUR_ORDER_EDIT_PLAN_TAG).assertContentDescriptionEquals("Edit plan").performClick()

        assertThat(events).containsExactly(YourOrderEvent.EditPlan)
    }

    @Test
    fun `the Plan added confirmation shows after a plan was added (DEV-112)`() {
        show(YourOrderTestStates.PlanAdded)

        composeRule.onNodeWithTag(YOUR_ORDER_PLAN_ADDED_TAG).assertExists()
        composeRule.onNodeWithText("Plan added", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the empty tray offers Plan by budget only when the planner is on (TC-202)`() {
        show(YourOrderTestStates.EmptyTray)

        composeRule
            .onNodeWithTag(
                YOUR_ORDER_EMPTY_PLAN_TAG,
            ).assertContentDescriptionEquals("Plan by budget")
            .performClick()

        assertThat(events).containsExactly(YourOrderEvent.OpenBudgetPlan)
    }

    @Test
    fun `without the planner the empty tray has no Plan by budget (AC16)`() {
        show(YourOrderTestStates.EmptyTrayNoPlan)

        composeRule.onNodeWithTag(YOUR_ORDER_EMPTY_PLAN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(YOUR_ORDER_BACK_TO_MENU_TAG).assertExists()
    }

    @Test
    fun `the strip shows up to three cards with a drink first and sends add (TC-194, TC-196)`() {
        show(YourOrderTestStates.Suggestions)

        composeRule.onNodeWithTag(YOUR_ORDER_SUGGESTIONS_TAG).performScrollTo()
        composeRule.onNodeWithText("Goes well with your order").assertExists()
        composeRule.onNodeWithText("Fits your budget").assertDoesNotExist()
        composeRule.onNodeWithTag(suggestionCardTag("add:d1")).assertExists()
        composeRule.onNodeWithTag(suggestionCardTag("add:s1")).assertExists()
        composeRule.onNodeWithTag(suggestionCardTag("add:e1")).assertExists()
        composeRule.onNodeWithText("Drink").assertExists()
        composeRule
            .onNodeWithTag(suggestionAddTag("add:d1"))
            .assertContentDescriptionEquals("Add Iced Lychee Tea")
            .performClick()
        composeRule.onNodeWithTag(suggestionDecrementTag("add:d1")).assertDoesNotExist()

        assertThat(events).containsExactly(YourOrderEvent.SuggestionAdd("add:d1"))
    }

    @Test
    fun `an added card shows its stepper, and minus and plus send their events (TC-196)`() {
        show(YourOrderTestStates.SuggestionAdded)

        composeRule.onNodeWithTag(YOUR_ORDER_SUGGESTIONS_TAG).performScrollTo()
        composeRule.onNodeWithTag(suggestionAddTag("add:d1")).assertDoesNotExist()
        composeRule
            .onNodeWithTag(suggestionDecrementTag("add:d1"))
            .assertContentDescriptionEquals("Remove one Iced Lychee Tea")
            .performClick()
        composeRule
            .onNodeWithTag(suggestionIncrementTag("add:d1"))
            .assertContentDescriptionEquals("Add one more Iced Lychee Tea")
            .performClick()
        // The other cards keep their "Add" button.
        composeRule.onNodeWithTag(suggestionAddTag("add:s1")).assertExists()

        assertThat(events)
            .containsExactly(YourOrderEvent.SuggestionRemove("add:d1"), YourOrderEvent.SuggestionAdd("add:d1"))
            .inOrder()
    }

    @Test
    fun `a Budget Plan strip says it fits the budget (TC-197)`() {
        show(YourOrderTestStates.SuggestionBudget)

        composeRule.onNodeWithTag(YOUR_ORDER_SUGGESTIONS_TAG).performScrollTo()
        composeRule.onNodeWithText("Fits your budget").assertExists()
    }

    @Test
    fun `the meal card leads, shows its plus price and its stepper dims plus at the limit (TC-205)`() {
        show(YourOrderTestStates.SuggestionMeal)

        composeRule.onNodeWithTag(YOUR_ORDER_SUGGESTIONS_TAG).performScrollTo()
        composeRule.onNodeWithText("Make it a meal").assertExists()
        composeRule.onNodeWithText("Adds fries & a drink").assertExists()
        composeRule.onNodeWithText("+Rp 18.000").assertExists()
        composeRule.onNodeWithText("Upgrade").assertExists()
        composeRule.onNodeWithTag(suggestionAddTag("meal:b2")).performClick()
        assertThat(events).containsExactly(YourOrderEvent.SuggestionAdd("meal:b2"))

        state = YourOrderTestStates.SuggestionMealOn
        composeRule.onNodeWithTag(suggestionAddTag("meal:b2")).assertDoesNotExist()
        composeRule.onNodeWithTag(suggestionIncrementTag("meal:b2")).assert(disabled)
        composeRule.onNodeWithTag(suggestionDecrementTag("meal:b2")).performClick()
        assertThat(events.last()).isEqualTo(YourOrderEvent.SuggestionRemove("meal:b2"))
    }

    @Test
    fun `without suggestions no strip is drawn (TC-194, AC1, AC4)`() {
        show(YourOrderTestStates.Default)
        composeRule.onNodeWithTag(YOUR_ORDER_SUGGESTIONS_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(YOUR_ORDER_PAY_TAG).assertIsEnabled()
    }
}
