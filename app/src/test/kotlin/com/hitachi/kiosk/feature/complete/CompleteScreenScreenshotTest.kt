package com.hitachi.kiosk.feature.complete

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

/** DEV-060: the `Complete` artboard and `S_Complete_takeaway` in their final frame (TC-088, TC-090). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class CompleteScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Complete`() = capture("Complete", CompleteSamples.DineIn)

    @Test
    fun `S_Complete_takeaway`() = capture("S_Complete_takeaway", CompleteSamples.Takeaway)

    @Test
    fun `Complete card`() = capture("Complete_card", CompleteSamples.Card)

    @Test
    fun `Complete recovered`() = capture("Complete_recovered", CompleteSamples.Recovered)

    private fun capture(
        name: String,
        state: CompleteUiState,
    ) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) { DesignFrame { CompleteScreen(state = state, onEvent = {}) } }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
