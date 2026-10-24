package com.hitachi.kiosk.feature.item

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.domain.model.Level
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-029 / FR-010: what each control sends, the stepper and CTA states, and the collapsible summaries. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class ItemDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ItemDetailEvent>()

    private fun show(state: ItemDetailUiState) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { ItemDetailScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    @Test
    fun `TC-041 the decrease button is disabled at 1 and increase still sends`() {
        show(ItemDetailSamples.Default)

        composeRule.onNodeWithTag(ITEM_DECREASE_TAG).assertIsNotEnabled().performClick()
        composeRule.onNodeWithContentDescription("Increase quantity").assertIsEnabled().performClick()

        assertThat(events).containsExactly(ItemDetailEvent.IncreaseQuantity)
    }

    @Test
    fun `TC-044 a missing required choice disables the CTA and names the group`() {
        show(ItemDetailSamples.RequiredMissing)

        composeRule.onNodeWithText("Required", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Choose a size to continue", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(ITEM_ADD_TAG).assertIsNotEnabled().performClick()

        assertThat(events).isEmpty()
    }

    @Test
    fun `a group name that does not ask is worded with Choose`() {
        show(ItemDetailSamples.Default.copy(cta = ItemCta.ChooseFirst("spice level", prompt = false)))

        composeRule.onNodeWithText("Choose spice level to continue", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `Add to order shows the rolling total and adds`() {
        show(ItemDetailSamples.Default)

        composeRule.onNodeWithText("Required · done", useUnmergedTree = true).assertExists()
        composeRule
            .onNodeWithTag(ITEM_ADD_TAG)
            .assertTextEquals("Add to order", "Rp 52.000", includeEditableText = false)
            .assertIsEnabled()
            .performClick()

        assertThat(events).containsExactly(ItemDetailEvent.AddToOrder)
    }

    @Test
    fun `options, the meal switch and the collapsible rows send their events`() {
        show(ItemDetailSamples.Default)

        composeRule.onNodeWithTag(itemOptionTag("mg_b2_size", "opt_b2_regular")).assertIsSelected()
        composeRule.onNodeWithTag(itemOptionTag("mg_b2_size", "opt_b2_large")).performClick()
        composeRule.onNodeWithTag(itemSwitchTag("mg_meal_upgrade")).assertIsOff().performClick()
        composeRule.onNodeWithTag(itemCollapsibleTag("mg_b2_ingredients")).performScrollTo().performClick()

        assertThat(events)
            .containsExactly(
                ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_large"),
                ItemDetailEvent.SelectOption("mg_meal_upgrade", "opt_make_meal"),
                ItemDetailEvent.ToggleExpanded("mg_b2_ingredients"),
            ).inOrder()
    }

    @Test
    fun `TC-040 TC-042 closed rows summarise the choices, open rows show the controls`() {
        val sections =
            ItemDetailSamples.Default.sections.map { section ->
                when (section) {
                    is ItemSectionUi.Levels -> {
                        section.copy(
                            changes =
                                listOf(
                                    LevelChangeUi("smoked cheddar", Level.EXTRA),
                                    LevelChangeUi("onion", Level.NONE),
                                ),
                        )
                    }

                    is ItemSectionUi.Extras -> {
                        section.copy(chosenNames = listOf("Fried egg"), chosenTotal = "+Rp 6.000")
                    }

                    else -> {
                        section
                    }
                }
            }
        show(ItemDetailSamples.Default.copy(sections = sections))

        composeRule.onNodeWithText("Extra smoked cheddar · No onion", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Fried egg · +Rp 6.000", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(itemLevelTag("ing_onion", Level.NONE)).assertDoesNotExist()
    }

    @Test
    fun `closed rows say No changes and None added by default`() {
        show(ItemDetailSamples.Default)

        composeRule.onNodeWithText("No changes", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("None added", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an open Customize row sets levels`() {
        val open =
            ItemDetailSamples.Default.sections.map {
                if (it is ItemSectionUi.Levels) it.copy(expanded = true) else it
            }
        show(ItemDetailSamples.Default.copy(sections = open))

        composeRule.onNodeWithContentDescription("Onion amount").assertExists()
        composeRule.onNodeWithTag(itemLevelTag("ing_onion", Level.NONE)).performScrollTo().performClick()

        assertThat(events).containsExactly(ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_onion", Level.NONE))
    }

    @Test
    fun `the header pills navigate and the cart pill is described with its count`() {
        show(ItemDetailSamples.Default)

        composeRule.onNodeWithTag(ITEM_BACK_TAG).performClick()
        composeRule.onNodeWithContentDescription("Your order, 4 items").performClick()

        assertThat(events).containsExactly(ItemDetailEvent.BackToMenu, ItemDetailEvent.ViewOrder).inOrder()
    }

    @Test
    fun `TC-043 the Added dock offers Keep ordering and View order`() {
        show(ItemDetailSamples.Default.copy(dock = ItemDock.ADDED))

        composeRule.onNodeWithTag(ITEM_ADD_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(ITEM_KEEP_ORDERING_TAG).performClick()
        composeRule.onNodeWithTag(ITEM_VIEW_ORDER_TAG).performClick()

        assertThat(events).containsExactly(ItemDetailEvent.BackToMenu, ItemDetailEvent.ViewOrder).inOrder()
    }

    @Test
    fun `TC-176 b sold out shows the banner and a disabled CTA`() {
        show(ItemDetailSamples.SoldOut)

        composeRule
            .onNodeWithText(
                "This item just sold out. Pick something else from the menu.",
                useUnmergedTree = true,
            ).assertExists()
        composeRule.onNodeWithTag(ITEM_ADD_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(ITEM_SOLD_OUT_BACK_TAG).performClick()

        assertThat(events).containsExactly(ItemDetailEvent.BackToMenu)
    }

    @Test
    fun `the maximum toast is shown when asked`() {
        show(ItemDetailSamples.Default.copy(quantity = 20, showMaxToast = true))

        composeRule.onNodeWithText("Maximum 20 per item", useUnmergedTree = true).assertExists()
    }
}
