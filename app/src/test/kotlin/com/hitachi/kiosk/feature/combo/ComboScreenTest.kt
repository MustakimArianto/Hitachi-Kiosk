package com.hitachi.kiosk.feature.combo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-030 / FR-011: what each control sends, the CTA states and More / Show fewer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class ComboScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ComboEvent>()

    private fun show(state: ComboUiState) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { ComboScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    @Test
    fun `TC-046 the defaults are shown and Add combo adds`() {
        show(ComboTestStates.default())

        composeRule.onNodeWithTag(comboCardTag("combo_burger")).assertIsSelected()
        composeRule.onNodeWithText("Save Rp 14.000", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("More sides (1)", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Cheeseburger Meal · Fries · Cola · Regular", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(COMBO_ADD_TAG).assertIsEnabled().performClick()

        assertThat(events).containsExactly(ComboEvent.AddCombo)
    }

    @Test
    fun `TC-176 c missing choices disable the CTA and show Required`() {
        show(ComboTestStates.requiredMissing())

        composeRule.onNodeWithText("Pick side & drink", useUnmergedTree = true).assertExists()
        composeRule
            .onNodeWithText("Cheeseburger Meal · pick a side & drink", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithTag(COMBO_ADD_TAG).assertIsNotEnabled().performClick()

        assertThat(events).isEmpty()
    }

    @Test
    fun `a missing side alone reads Choose a side`() {
        show(ComboSamples.Default.copy(cta = ComboCta.CHOOSE_SIDE))

        composeRule.onNodeWithText("Choose a side", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `TC-176 d a sold-out option is disabled and labelled Sold out`() {
        show(ComboTestStates.soldOut())

        composeRule
            .onNodeWithTag(comboSlotTag("cs_cheese"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            .performClick()
        // Cheese fries and Iced latte.
        composeRule.onAllNodesWithText("Sold out", useUnmergedTree = true).assertCountEquals(2)

        assertThat(events).isEmpty()
    }

    @Test
    fun `options, the combo list, the size control and More send their events`() {
        show(ComboTestStates.default())

        composeRule.onNodeWithTag(comboCardTag("combo_feast")).performClick()
        composeRule.onNodeWithTag(comboSlotTag("cs_nuggets")).performScrollTo().performClick()
        composeRule.onNodeWithTag(COMBO_MORE_SIDES_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(COMBO_LARGE_TAG).performScrollTo().performClick()

        assertThat(events)
            .containsExactly(
                ComboEvent.SelectCombo("combo_feast"),
                ComboEvent.SelectSide("cs_nuggets"),
                ComboEvent.ToggleMoreSides,
                ComboEvent.SelectLarge(true),
            ).inOrder()
    }

    @Test
    fun `Show fewer keeps the selected fourth side visible`() {
        val state =
            ComboTestStates.build(
                ComboEvent.ToggleMoreSides,
                ComboEvent.SelectSide("cs_corn"),
                ComboEvent.ToggleMoreSides,
            )
        show(state)

        composeRule.onNodeWithText("More sides (1)", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(comboSlotTag("cs_corn")).performScrollTo().assertIsSelected()
    }

    @Test
    fun `More sides reveals the fourth side and reads Show fewer`() {
        show(ComboTestStates.build(ComboEvent.ToggleMoreSides))

        composeRule.onNodeWithTag(comboSlotTag("cs_corn")).performScrollTo().assertExists()
        composeRule.onNodeWithText("Show fewer", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `TC-048 Feast labels show the multiplied amounts`() {
        show(ComboTestStates.feast())

        composeRule.onNodeWithText("Large +Rp 12.000", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Rp 129.000", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `TC-049 the Added state reads Added`() {
        show(ComboTestStates.added())

        composeRule.onNodeWithText("Added", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Your order, 5 items").assertExists()
    }

    @Test
    fun `the header pills navigate`() {
        show(ComboTestStates.default())

        composeRule.onNodeWithTag(COMBO_BACK_TAG).performClick()
        composeRule.onNodeWithContentDescription("Your order, 4 items").performClick()

        assertThat(events).containsExactly(ComboEvent.BackToMenu, ComboEvent.ViewOrder).inOrder()
    }
}
