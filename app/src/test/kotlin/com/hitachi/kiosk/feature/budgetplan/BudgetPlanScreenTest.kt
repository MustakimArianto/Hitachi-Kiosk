package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.domain.planner.PlanTolerance
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

        // The budget and the dock both read Rp 0.
        composeRule.onAllNodesWithText("Rp 0", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("Other", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_CTA_TAG).assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_ADULTS_LESS_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(BUDGET_PLAN_CHILDREN_LESS_TAG).assertIsNotEnabled()

        assertThat(events).isEmpty()
    }

    @Test
    fun `step 1 sends chips, the keypad, the steppers and See plans`() {
        show(BudgetPlanTestStates.stepOne())

        composeRule.onNodeWithTag(budgetPlanChipTag(150_000)).assertIsSelected().performClick()
        composeRule.onNodeWithText("Rp 100k", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_OTHER_TAG).performClick()
        composeRule.onNodeWithContentDescription("More Adults").performClick()
        composeRule.onNodeWithContentDescription("Fewer Children").performClick()
        composeRule.onNodeWithText("3 people · budget", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_CTA_TAG).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_BACK_TAG).performClick()

        assertThat(events)
            .containsExactly(
                BudgetPlanEvent.PickBudget(100_000),
                BudgetPlanEvent.OpenKeypad,
                BudgetPlanEvent.IncreaseAdults,
                BudgetPlanEvent.DecreaseChildren,
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
    fun `step 2 shows both plans with the rows collapsed (TC-171)`() {
        show(BudgetPlanTestStates.stepTwo())

        composeRule.onNodeWithText("Two plans for you", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Budget Plan · Step 2 of 2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(budgetPlanSegmentTag(PlanSide.UNDER)).assertIsSelected()
        composeRule.onNodeWithText("Best fit", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Rp 400 left", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Adult 1", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Subtotal", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Add plan to order", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `step 2 sends the segment, the rows, Customize, the cart pill and Add plan to order`() {
        show(BudgetPlanTestStates.stepTwo())

        composeRule.onNodeWithTag(budgetPlanSegmentTag(PlanSide.OVER)).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_INCLUDED_TAG).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_PRICE_TAG).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_CUSTOMIZE_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_CART_TAG).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_CTA_TAG).performClick()

        assertThat(events)
            .containsExactly(
                BudgetPlanEvent.SelectPlan(PlanSide.OVER),
                BudgetPlanEvent.ToggleIncluded,
                BudgetPlanEvent.TogglePriceDetails,
                BudgetPlanEvent.OpenCustomize,
                BudgetPlanEvent.ViewOrder,
                BudgetPlanEvent.AddPlan,
            ).inOrder()
    }

    @Test
    fun `the open rows list the items per person and the price details`() {
        show(BudgetPlanTestStates.expanded())

        composeRule.onNodeWithText("Adult 1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Child 1", useUnmergedTree = true).performScrollTo()
        composeRule.onNodeWithText("Main · kid-friendly", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Restaurant tax (PB1 10%)", useUnmergedTree = true).performScrollTo()
        composeRule.onNodeWithText("Promo · Rp 10.000 off over Rp 100.000", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("− Rp 10.000", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an exact match has one hero card and no segments (TC-169)`() {
        show(BudgetPlanTestStates.exact())

        composeRule.onNodeWithText("A perfect fit", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Exact match", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Perfect fit! Rp 99.000 · Rp 0 left", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2 people · 4 items · exact match", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(budgetPlanSegmentTag(PlanSide.UNDER)).assertDoesNotExist()
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
    fun `more than enough has its own badge and note (TC-164)`() {
        show(BudgetPlanTestStates.moreThanEnough())

        composeRule.onNodeWithText("More than enough", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Your budget covers more than enough", useUnmergedTree = true).assertExists()
        composeRule
            .onNodeWithText("This is our most generous plan for your group, and it still fits.", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `the Customize sheet sends toggles, tolerance, swaps and Done (TC-170)`() {
        val state = BudgetPlanTestStates.customizeOpen()
        val target =
            checkNotNull(
                state.plan
                    ?.groups
                    ?.first()
                    ?.items
                    ?.first()
                    ?.swap,
            )
        show(state)

        composeRule.onNodeWithText("Customize your plan", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Rp 149.600 · Rp 400 left", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(BUDGET_PLAN_DESSERTS_TAG).assertIsOn().performClick()
        composeRule.onNodeWithTag(budgetPlanToleranceTag(PlanTolerance.UP_TO_CAP)).assertIsSelected()
        composeRule.onNodeWithTag(budgetPlanToleranceTag(PlanTolerance.STRICT)).performClick()
        composeRule.onNodeWithTag(budgetPlanSwapTag(target)).performScrollTo().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_RESET_TAG).assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_DONE_TAG).performClick()
        composeRule.onNodeWithTag(BUDGET_PLAN_SHEET_CLOSE_TAG).performClick()

        assertThat(events)
            .containsExactly(
                BudgetPlanEvent.SetIncludeDesserts(false),
                BudgetPlanEvent.SetTolerance(PlanTolerance.STRICT),
                BudgetPlanEvent.Swap(target),
                BudgetPlanEvent.CloseCustomize,
                BudgetPlanEvent.CloseCustomize,
            ).inOrder()
    }

    @Test
    fun `Reset to suggested is enabled after a swap`() {
        val open = BudgetPlanTestStates.customizeOpen()
        val target =
            checkNotNull(
                open.plan
                    ?.groups
                    ?.first()
                    ?.items
                    ?.first()
                    ?.swap,
            )
        show(BudgetPlanTestStates.stepTwo(BudgetPlanEvent.OpenCustomize, BudgetPlanEvent.Swap(target)))

        composeRule.onNodeWithTag(BUDGET_PLAN_RESET_TAG).assertIsEnabled().performClick()

        assertThat(events).containsExactly(BudgetPlanEvent.ResetPlan)
    }

    @Test
    fun `after adding the dock reads Added to order`() {
        show(BudgetPlanTestStates.added())

        composeRule.onNodeWithText("Added to order", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Add plan to order", useUnmergedTree = true).assertDoesNotExist()
    }
}
