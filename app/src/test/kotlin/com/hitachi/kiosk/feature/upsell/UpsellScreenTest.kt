package com.hitachi.kiosk.feature.upsell

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-039: what each Upsell control sends, and the "Nothing more to suggest" state (FR-014, TC-063, TC-172). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class UpsellScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<UpsellEvent>()

    private fun show(state: UpsellUiState) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { UpsellScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    @Test
    fun `plus quick-adds the suggestion`() {
        show(UpsellTestStates.Default)

        composeRule
            .onNodeWithTag(upsellAddTag("e1"))
            .assertContentDescriptionEquals("Add Caramel Sundae")
            .performClick()

        assertThat(events).containsExactly(UpsellEvent.QuickAdd("e1"))
    }

    @Test
    fun `the heading, strip and dock show the design copy and amounts (TC-063)`() {
        show(UpsellTestStates.Default)

        composeRule.onNodeWithText("Before you pay").assertIsDisplayed()
        composeRule.onNodeWithText("Anything on the side?").assertIsDisplayed()
        composeRule.onNodeWithTag(UPSELL_STRIP_TAG).assertContentDescriptionEquals("Your order so far")
        composeRule.onNodeWithText("5 items").assertIsDisplayed()
        composeRule.onNodeWithText("Rp 167.000").assertIsDisplayed()
        composeRule.onNodeWithText("Melted cheddar · 450 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("Dessert").assertExists()
        composeRule.onNodeWithTag(UPSELL_NO_THANKS_TAG).assertContentDescriptionEquals("No thanks")
        composeRule.onNodeWithTag(UPSELL_CONTINUE_TAG).assertContentDescriptionEquals("Continue, Rp 167.000")
    }

    @Test
    fun `No thanks, Continue and Menu send their events`() {
        show(UpsellTestStates.Default)

        composeRule.onNodeWithTag(UPSELL_NO_THANKS_TAG).performClick()
        composeRule.onNodeWithTag(UPSELL_CONTINUE_TAG).performClick()
        composeRule.onNodeWithTag(UPSELL_BACK_TAG).performClick()

        assertThat(events)
            .containsExactly(UpsellEvent.NoThanks, UpsellEvent.Continue, UpsellEvent.BackToMenu)
            .inOrder()
    }

    @Test
    fun `an added card reads N added`() {
        show(UpsellTestStates.JustAdded)

        composeRule.onNodeWithText("2 added").assertIsDisplayed()
        composeRule.onNodeWithText("1 added").assertIsDisplayed()
        composeRule.onNodeWithText("8 items").assertIsDisplayed()
    }

    @Test
    fun `with nothing to suggest only Review order is offered (TC-172)`() {
        show(UpsellTestStates.NoSuggestions)

        composeRule.onNodeWithText("You’re all set").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing more to suggest").assertIsDisplayed()
        composeRule.onNodeWithTag(UPSELL_EMPTY_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(UPSELL_NO_THANKS_TAG).assertDoesNotExist()
        composeRule
            .onNodeWithTag(UPSELL_CONTINUE_TAG)
            .assertContentDescriptionEquals("Review order, Rp 270.000")
            .performClick()

        assertThat(events).containsExactly(UpsellEvent.Continue)
    }
}
