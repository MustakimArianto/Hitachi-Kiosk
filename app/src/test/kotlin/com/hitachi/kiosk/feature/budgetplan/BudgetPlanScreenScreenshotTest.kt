package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DEV-043 / DEV-043a: the `BudgetPas` artboard's two steps, its `S_BudgetPas_*` boards, the sheets and larger text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class BudgetPlanScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `S_BudgetPas_step1`() = capture("S_BudgetPas_step1", BudgetPlanTestStates.stepOneEmpty())

    @Test
    fun `S_BudgetPas_step1Budget`() = capture("S_BudgetPas_step1Budget", BudgetPlanTestStates.stepOne())

    @Test
    fun `S_BudgetPas_keypadMin`() = capture("S_BudgetPas_keypadMin", BudgetPlanTestStates.keypadMin())

    @Test
    fun `BudgetPas step 2`() = capture("BudgetPas_step2", BudgetPlanTestStates.stepTwo())

    @Test
    fun `S_BudgetPas_twoPlans`() = capture("S_BudgetPas_twoPlans", BudgetPlanTestStates.stepTwo())

    @Test
    fun `S_BudgetPas_exact`() = capture("S_BudgetPas_exact", BudgetPlanTestStates.exact())

    @Test
    fun `S_BudgetPas_tooLow`() = capture("S_BudgetPas_tooLow", BudgetPlanTestStates.tooLow())

    @Test
    fun `S_BudgetPas_moreThanEnough`() = capture("S_BudgetPas_moreThanEnough", BudgetPlanTestStates.moreThanEnough())

    @Test
    fun `S_BudgetPas_group`() = capture("S_BudgetPas_group", BudgetPlanTestStates.group())

    @Test
    fun `S_BudgetPas_detailsOpen`() = capture("S_BudgetPas_detailsOpen", BudgetPlanTestStates.detailsOpen())

    @Test
    fun `S_BudgetPas_optionsOff`() = capture("S_BudgetPas_optionsOff", BudgetPlanTestStates.optionsOff())

    @Test
    fun `S_BudgetPas_swapOpen`() = capture("S_BudgetPas_swapOpen", BudgetPlanTestStates.swapOpen())

    @Test
    fun `S_BudgetPas_cartFull`() = capture("S_BudgetPas_cartFull", BudgetPlanTestStates.cartFull())

    @Test
    fun `S_BudgetPas_added`() = capture("S_BudgetPas_added", BudgetPlanTestStates.added())

    @Test
    fun `BudgetPas with the keypad open`() = capture("BudgetPas_keypad", BudgetPlanTestStates.keypadOpen())

    @Test
    fun `BudgetPas step 1 with larger text`() =
        capture("BudgetPas_step1_largeText", BudgetPlanTestStates.stepOne(), largeText = true)

    @Test
    fun `BudgetPas step 2 with larger text`() =
        capture("BudgetPas_step2_largeText", BudgetPlanTestStates.stepTwo(), largeText = true)

    private fun capture(
        name: String,
        state: BudgetPlanUiState,
        largeText: Boolean = false,
    ) {
        composeRule.setContent {
            KioskTheme(largeText = largeText, reduceMotion = true) {
                DesignFrame { BudgetPlanScreen(state = state, onEvent = {}) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
