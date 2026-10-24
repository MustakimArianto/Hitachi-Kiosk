package com.hitachi.kiosk.feature.upsell

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

/**
 * DEV-039: the `Upsell` artboard with the design's sample cart, `S_Upsell_noSuggestions` (TC-172), cards after a
 * quick add and larger text (TR-MNT-08).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class UpsellScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Upsell default`() = capture("Upsell", UpsellTestStates.Default)

    @Test
    fun `S_Upsell_noSuggestions`() = capture("S_Upsell_noSuggestions", UpsellTestStates.NoSuggestions)

    @Test
    fun `Upsell with a just-added card`() = capture("Upsell_justAdded", UpsellTestStates.JustAdded)

    @Test
    fun `Upsell with larger text`() = capture("Upsell_largeText", UpsellTestStates.Default, largeText = true)

    private fun capture(
        name: String,
        state: UpsellUiState,
        largeText: Boolean = false,
    ) {
        composeRule.setContent {
            KioskTheme(largeText = largeText, reduceMotion = true) {
                DesignFrame { UpsellScreen(state = state, onEvent = {}) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
