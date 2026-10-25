package com.hitachi.kiosk.feature.menu

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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

/** DEV-027 / DEV-028: what each Menu control sends, and the disabled semantics offline (FR-009, TC-175). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class MenuScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<MenuEvent>()

    private val disabled = SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)

    private fun show(state: MenuUiState) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { MenuScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    @Test
    fun `plus quick-adds and the card opens the item`() {
        show(MenuTestStates.Default)

        composeRule
            .onNodeWithTag(menuAddTag("c1"))
            .assertContentDescriptionEquals("Add Crispy Chicken (2 pc)")
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(menuProductTag("c1")).performClick()

        assertThat(events).containsExactly(MenuEvent.QuickAdd("c1"), MenuEvent.OpenProduct("c1")).inOrder()
    }

    @Test
    fun `Choose opens the item and the card has no plus (TC-039)`() {
        show(MenuTestStates.Default)

        composeRule.onNodeWithTag(menuAddTag("b2")).assertDoesNotExist()
        composeRule
            .onNodeWithTag(menuChooseTag("b2"))
            .assertContentDescriptionEquals("Choose options for Smoky Double Burger")
            .performClick()

        assertThat(events).containsExactly(MenuEvent.OpenProduct("b2"))
    }

    @Test
    fun `header, pills, budget row and dock send their events`() {
        show(MenuTestStates.Default)

        composeRule.onNodeWithTag(MENU_CHANGE_TAG).assertContentDescriptionEquals("Dine in · Change").performClick()
        composeRule.onNodeWithTag(MENU_CANCEL_TAG).performClick()
        composeRule.onNodeWithTag(menuCategoryPillTag("cat_chicken")).performClick()
        composeRule.onNodeWithTag(MENU_MEALS_PILL_TAG).performClick()
        composeRule.onNodeWithTag(MENU_BUDGET_PLAN_TAG).assertContentDescriptionEquals("Plan by budget").performClick()
        composeRule.onNodeWithTag(MENU_BUDGET_PILL_TAG).assertContentDescriptionEquals("Plan by budget").performClick()
        composeRule.onNodeWithTag(MENU_VIEW_ORDER_TAG).performClick()

        assertThat(events)
            .containsExactly(
                MenuEvent.ChangeOrderType,
                MenuEvent.Cancel,
                MenuEvent.Select(MenuSelection.Category("cat_chicken")),
                MenuEvent.OpenCombos,
                MenuEvent.OpenBudgetPlan,
                MenuEvent.OpenBudgetPlan,
                MenuEvent.ViewOrder,
            ).inOrder()
    }

    @Test
    fun `the header pill shows on every tab and the slim row only on Popular (TC-202)`() {
        show(MenuTestStates.DrinksTab)

        composeRule.onNodeWithTag(MENU_BUDGET_PILL_TAG).assertExists()
        composeRule.onNodeWithTag(MENU_BUDGET_PLAN_TAG).assertDoesNotExist()
    }

    @Test
    fun `with the planner off neither the pill nor the row shows (AC7)`() {
        show(MenuTestStates.PlanOff)

        composeRule.onNodeWithTag(MENU_BUDGET_PILL_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(MENU_BUDGET_PLAN_TAG).assertDoesNotExist()
    }

    @Test
    fun `the entry row carries the new copy (D-35)`() {
        show(MenuTestStates.Default)

        composeRule.onNodeWithText("Eating with a group?", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Tell us your budget and we pick the meal", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the dock shows the item count and total`() {
        show(MenuTestStates.Default)

        composeRule
            .onNodeWithTag(MENU_DOCK_SUMMARY_TAG)
            .assert(hasText("4 items"))
            .assert(hasText("Rp 92.000"))
        composeRule.onNodeWithText("Popular now").assertExists()
    }

    @Test
    fun `offline shows the banner and announces adds and View order as disabled (TC-175 b)`() {
        show(MenuTestStates.Offline)

        composeRule.onNodeWithText("Ordering is paused — reconnecting…", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(menuAddTag("c1")).assert(disabled).performClick()
        composeRule.onNodeWithTag(menuChooseTag("b2")).assert(disabled).performClick()
        composeRule.onNodeWithTag(MENU_VIEW_ORDER_TAG).assert(disabled).performClick()

        assertThat(events).isEmpty()

        // Browsing still works.
        composeRule.onNodeWithTag(menuProductTag("c1")).performClick()
        assertThat(events).containsExactly(MenuEvent.OpenProduct("c1"))
    }

    @Test
    fun `an add at 20 stays tappable so it can show the toast`() {
        show(MenuTestStates.MaxQty)

        composeRule.onNodeWithText("Maximum 20 per item", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(menuAddTag("d1")).assertIsEnabled().performClick()

        assertThat(events).containsExactly(MenuEvent.QuickAdd("d1"))
    }

    @Test
    fun `an empty category offers See popular items (TC-175 a)`() {
        show(MenuTestStates.EmptyCategory)

        composeRule.onNodeWithText("Nothing here right now").assertExists()
        composeRule.onNodeWithTag(MENU_SEE_POPULAR_TAG).performScrollTo().performClick()

        assertThat(events).containsExactly(MenuEvent.Select(MenuSelection.Popular))
    }
}
