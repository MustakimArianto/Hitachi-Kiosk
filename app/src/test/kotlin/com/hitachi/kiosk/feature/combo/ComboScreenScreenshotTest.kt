package com.hitachi.kiosk.feature.combo

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

/** DEV-030: the `Combo` artboard, its `S_Combo_*` boards, the Feast and Added states, plus larger text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class ComboScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Combo default`() = capture("Combo", ComboTestStates.default())

    @Test
    fun `S_Combo_requiredMissing`() = capture("S_Combo_requiredMissing", ComboTestStates.requiredMissing())

    @Test
    fun `S_Combo_soldOut`() = capture("S_Combo_soldOut", ComboTestStates.soldOut())

    @Test
    fun `Combo Feast for Two with Nuggets and Large`() = capture("Combo_feast", ComboTestStates.feast())

    @Test
    fun `Combo just added`() = capture("Combo_added", ComboTestStates.added())

    @Test
    fun `Combo with larger text`() = capture("Combo_largeText", ComboTestStates.default(), largeText = true)

    private fun capture(
        name: String,
        state: ComboUiState,
        largeText: Boolean = false,
    ) {
        composeRule.setContent {
            KioskTheme(largeText = largeText, reduceMotion = true) {
                DesignFrame { ComboScreen(state = state, onEvent = {}) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
