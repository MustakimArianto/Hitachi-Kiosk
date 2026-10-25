package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-043 / DEV-043a, FR-032: what each control sends, the disabled states and the copy of each state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class BudgetPlanScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<BudgetPlanEvent>()

    private fun show(state: BudgetPlanUiState) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { BudgetPlanScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    @Test
    fun `without a budget See plans is disabled (TC-166)`() {
        show(BudgetPlanTestStates.build())

        // The budget reads Rp 0 and the dock asks for one (D-35).
        composeRule.onAllNodesWithText("Rp 0", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithText("Choose a budget to continue", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2 people", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_PER_PERSON_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Other", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_CTA_TAG).assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_CHILDREN_LESS_TAG).assertIsNotEnabled()

        assertThat(events).isEmpty()
    }

    @Test
    fun `step 1 sends chips, the keypad, the steppers and See plans`() {
        show(BudgetPlanTestStates.stepOne())

        composeRule.onNodeWithText("About Rp 75.000 per person", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(budgetPlanChipTag(150_000)).assertIsSelected().performClick()
        composeRule.onNodeWithText("Rp 100k", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_OTHER_TAG).performClick()
        composeRule.onNodeWithContentDescription("More Adults").performClick()
        composeRule.onNodeWithContentDescription("More Children").performClick()
        composeRule.onNodeWithText("2 people · budget", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_CTA_TAG).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_BACK_TAG).performClick()

        assertThat(events)
            .containsExactly(
                BudgetPlanEvent.PickBudget(100_000),
                BudgetPlanEvent.OpenKeypad,
                BudgetPlanEvent.IncreaseAdults,
                BudgetPlanEvent.IncreaseChildren,
                BudgetPlanEvent.SeePlans,
                BudgetPlanEvent.Back,
            ).inOrder()
    }

    @Test
    fun `a group over 10 shows the group card and a disabled Reduce group (TC-166, AC11)`() {
        show(BudgetPlanTestStates.group())

        composeRule.onNodeWithText("Please order in smaller groups", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("12 people · max 10", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_CTA_TAG).assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_FIT_GROUP_TAG).performScrollTo().performClick()

        assertThat(events).containsExactly(BudgetPlanEvent.FitGroup)
    }

    @Test
    fun `the keypad sends keys, and Use this budget waits for Rp 1_000 (TC-171)`() {
        show(BudgetPlanTestStates.build(BudgetPlanEvent.OpenKeypad))

        composeRule.onNodeWithTag(BUDGET_PLAN_KEYPAD_USE_TAG).assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag(budgetPlanKeyTag(KeypadKey.ONE)).performClick()
        composeRule.onNodeWithTag(budgetPlanKeyTag(KeypadKey.THOUSAND)).performClick()
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_KEYPAD_CANCEL_TAG).performClick()

        assertThat(events)
            .containsExactly(
                BudgetPlanEvent.PressKey(KeypadKey.ONE),
                BudgetPlanEvent.PressKey(KeypadKey.THOUSAND),
                BudgetPlanEvent.PressKey(KeypadKey.DELETE),
                BudgetPlanEvent.CloseKeypad,
            ).inOrder()
    }

    @Test
    fun `a typed amount can be used`() {
        show(BudgetPlanTestStates.keypadOpen())

        composeRule.onNodeWithTag(BUDGET_PLAN_KEYPAD_AMOUNT_TAG).assertExists()
        composeRule.onNodeWithText("Rp 123.456", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_KEYPAD_USE_TAG).assertIsEnabled().performClick()

        assertThat(events).containsExactly(BudgetPlanEvent.UseKeypadBudget)
    }

    @Test
    fun `the keypad shows Minimum Rp 1_000 for 500 and keeps Use this budget disabled (TC-198)`() {
        show(BudgetPlanTestStates.keypadMin())

        composeRule.onNodeWithTag(BUDGET_PLAN_KEYPAD_ERROR_TAG).assertExists()
        composeRule.onAllNodesWithText("Minimum Rp 1.000", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("Rp 500", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_KEYPAD_USE_TAG).assertIsNotEnabled()
    }

    @Test
    fun `step 2 shows two plan cards and What's included open (TC-199)`() {
        show(BudgetPlanTestStates.stepTwo())

        composeRule.onNodeWithText("Two plans for you", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Budget Plan · Step 2 of 2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(budgetPlanCardTag(PlanSide.UNDER)).assertIsSelected()
        composeRule.onNodeWithTag(budgetPlanCardTag(PlanSide.OVER)).assertIsNotSelected()
        composeRule.onNodeWithText("Within your budget", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("A little extra", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Rp 400 left", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("+Rp 700 over", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Adult 1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Subtotal", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Add plan to order", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `step 2 sends the cards, the options, the rows, a swap, the cart pill and Add plan to order`() {
        val state = BudgetPlanTestStates.stepTwo()
        show(state)
        val target = SwapTarget(0)

        composeRule.onNodeWithTag(budgetPlanCardTag(PlanSide.OVER)).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_SIDES_TAG).assertIsOn().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_DESSERTS_TAG).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_OVER_TAG).assertIsOn().performClick()
        composeRule.onNodeWithTag(budgetPlanSwapTag(target)).performScrollTo().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_INCLUDED_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_PRICE_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_CART_TAG).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_CTA_TAG).performClick()

        assertThat(events)
            .containsExactly(
                BudgetPlanEvent.SelectPlan(PlanSide.OVER),
                BudgetPlanEvent.SetIncludeSides(false),
                BudgetPlanEvent.SetIncludeDesserts(false),
                BudgetPlanEvent.SetAllowOver(false),
                BudgetPlanEvent.Swap(target),
                BudgetPlanEvent.ToggleIncluded,
                BudgetPlanEvent.TogglePriceDetails,
                BudgetPlanEvent.ViewOrder,
                BudgetPlanEvent.AddPlan,
            ).inOrder()
    }

    @Test
    fun `with the options off one plan shows, with its note and Reset (AC10)`() {
        show(BudgetPlanTestStates.optionsOff())

        composeRule.onNodeWithTag(budgetPlanCardTag(PlanSide.UNDER)).assertDoesNotExist()
        composeRule.onNodeWithTag(BUDGET_PLAN_HERO_TAG).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_SIDES_TAG).assertIsOff()
        composeRule.onNodeWithTag(BUDGET_PLAN_OVER_TAG).assertIsOff()
        composeRule
            .onNodeWithText(
                "A second plan is hidden while \"Allow a little over budget\" is off.",
                useUnmergedTree = true,
            ).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_RESET_TAG).performScrollTo().performClick()

        assertThat(events).containsExactly(BudgetPlanEvent.ResetPlan)
    }

    @Test
    fun `the rows with their prices and the price details list the combo savings`() {
        show(BudgetPlanTestStates.stepTwo(BudgetPlanEvent.TogglePriceDetails))

        composeRule.onNodeWithText("Adult 1", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("Main + drink", useUnmergedTree = true).onFirst().assertExists()
        composeRule.onNodeWithText("Restaurant tax (PB1 10%)", useUnmergedTree = true).performScrollTo()
        composeRule.onNodeWithText("Promo · Rp 10.000 off over Rp 100.000", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("− Rp 10.000", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the details-open board has the rows closed and the price open`() {
        show(BudgetPlanTestStates.detailsOpen())

        composeRule.onNodeWithText("Subtotal", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Adult 1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an exact match has one hero card and no plan cards (TC-169)`() {
        show(BudgetPlanTestStates.exact())

        composeRule.onNodeWithText("A perfect fit", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Exact match", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Perfect fit! Rp 99.000 · Rp 0 left", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(budgetPlanCardTag(PlanSide.UNDER)).assertDoesNotExist()
    }

    @Test
    fun `a budget too low shows what is still needed (TC-163)`() {
        show(BudgetPlanTestStates.tooLow())

        composeRule.onNodeWithText("Need Rp 16.600 more", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Rp 16.600 more needed", useUnmergedTree = true).assertExists()
        composeRule
            .onNodeWithText(
                "This is the lowest-priced complete meal for your group. Raise the budget or adjust the group.",
                useUnmergedTree = true,
            ).assertExists()
    }

    @Test
    fun `more than enough says where the menu tops out and still fits (TC-164, AC13)`() {
        show(BudgetPlanTestStates.moreThanEnough())

        composeRule.onNodeWithText("More than enough", useUnmergedTree = true).assertExists()
        composeRule
            .onNodeWithText("Our menu tops out at", substring = true, useUnmergedTree = true)
            .assertExists()
        composeRule
            .onAllNodesWithText("under your budget", substring = true, useUnmergedTree = true)
            .onFirst()
            .assertExists()
        composeRule
            .onNodeWithText("This is our most generous plan for your group, and it still fits.", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `the swap picker lists the current choice and sends a row, Done and close (TC-200)`() {
        show(BudgetPlanTestStates.swapOpen())

        composeRule.onNodeWithTag(BUDGET_PLAN_SWAP_SHEET_TAG).assertExists()
        composeRule.onNodeWithText("Swap Adult 1's meal", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Plan total Rp 149.600", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Current", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Same price", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(budgetPlanSwapRowTag(1)).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_SWAP_DONE_TAG).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_SWAP_CLOSE_TAG).performClick()

        assertThat(events)
            .containsExactly(
                BudgetPlanEvent.PickAlternative(1),
                BudgetPlanEvent.CloseSwap,
                BudgetPlanEvent.CloseSwap,
            ).inOrder()
    }

    @Test
    fun `a plan that does not fit shows the message and Review order (TC-201)`() {
        show(BudgetPlanTestStates.cartFull())

        composeRule
            .onNodeWithText(
                "Your order has too many items to add this plan",
                useUnmergedTree = true,
            ).assertExists()
        composeRule.onNodeWithText("Review order", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Add plan to order", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(BUDGET_PLAN_CTA_TAG).performClick()

        assertThat(events).containsExactly(BudgetPlanEvent.ViewOrder)
    }

    @Test
    fun `after adding the dock reads Added to order`() {
        show(BudgetPlanTestStates.added())

        composeRule.onNodeWithText("Added to order", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Add plan to order", useUnmergedTree = true).assertDoesNotExist()
    }
}
