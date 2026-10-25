package com.hitachi.kiosk.feature.order

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DEV-037 / DEV-037a / DEV-038 / DEV-106: the `Payment` artboard with the design's sample cart, Details open, the
 * Budget Plan row (`plan`), every `S_Payment_*` board (TC-177), the `S_Payment_upsell*` strip boards with the strip
 * scrolled into view (and the meal card after its tap) and larger text (TR-MNT-08).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class YourOrderScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Payment default`() = capture("Payment", YourOrderTestStates.Default)

    @Test
    fun `Payment with Details open`() = capture("Payment_details", YourOrderTestStates.DetailsOpen)

    @Test
    fun `Payment from a Budget Plan`() = capture("Payment_plan", YourOrderTestStates.Plan)

    @Test
    fun `Payment with Card selected`() = capture("Payment_card", YourOrderTestStates.CardSelected)

    @Test
    fun `S_Payment_priceChanged`() = capture("S_Payment_priceChanged", YourOrderTestStates.PriceChanged)

    @Test
    fun `S_Payment_itemUnavailable`() = capture("S_Payment_itemUnavailable", YourOrderTestStates.ItemUnavailable)

    @Test
    fun `S_Payment_offline`() = capture("S_Payment_offline", YourOrderTestStates.Offline)

    @Test
    fun `S_Payment_noMethods`() = capture("S_Payment_noMethods", YourOrderTestStates.NoMethods)

    @Test
    fun `S_Payment_planTag`() = capture("S_Payment_planTag", YourOrderTestStates.Plan)

    @Test
    fun `S_Payment_planAdded`() = capture("S_Payment_planAdded", YourOrderTestStates.PlanAdded)

    @Test
    fun `S_Payment_emptyTrayNoPlan`() = capture("S_Payment_emptyTrayNoPlan", YourOrderTestStates.EmptyTrayNoPlan)

    @Test
    fun `S_Payment_emptyTray`() = capture("S_Payment_emptyTray", YourOrderTestStates.EmptyTray)

    @Test
    fun `S_Payment_promoNudge`() = capture("S_Payment_promoNudge", YourOrderTestStates.PromoNudge)

    @Test
    fun `S_Payment_upsell`() = capture("S_Payment_upsell", YourOrderTestStates.Suggestions, scrollToStrip = true)

    @Test
    fun `S_Payment_upsellAdded`() =
        capture("S_Payment_upsellAdded", YourOrderTestStates.SuggestionAdded, scrollToStrip = true)

    @Test
    fun `S_Payment_upsellBudget`() =
        capture("S_Payment_upsellBudget", YourOrderTestStates.SuggestionBudget, scrollToStrip = true)

    @Test
    fun `S_Payment_upsellMeal`() =
        capture("S_Payment_upsellMeal", YourOrderTestStates.SuggestionMeal, scrollToStrip = true)

    @Test
    fun `S_Payment_upsellMeal after the tap`() =
        capture("S_Payment_upsellMealOn", YourOrderTestStates.SuggestionMealOn, scrollToStrip = true)

    @Test
    fun `Payment with larger text`() = capture("Payment_largeText", YourOrderTestStates.DetailsOpen, largeText = true)

    private fun capture(
        name: String,
        state: YourOrderUiState,
        largeText: Boolean = false,
        scrollToStrip: Boolean = false,
    ) {
        composeRule.setContent {
            KioskTheme(largeText = largeText, reduceMotion = true) {
                DesignFrame { YourOrderScreen(state = state, onEvent = {}) }
            }
        }
        if (scrollToStrip) composeRule.onNodeWithTag(YOUR_ORDER_SUGGESTIONS_TAG).performScrollTo()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
