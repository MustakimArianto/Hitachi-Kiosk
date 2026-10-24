package com.hitachi.kiosk.feature.idle

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

/** DEV-024: the `Idle` artboard, `S_Idle_issues`, no promo images and larger text (TR-MNT-08). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class IdleScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Idle default`() = capture("Idle", IdleSamples.Default)

    @Test
    fun `S_Idle_issues`() = capture("S_Idle_issues", IdleSamples.Issues)

    @Test
    fun `Idle without promo images`() = capture("Idle_noPromos", IdleSamples.NoPromos)

    @Test
    fun `Idle with larger text`() = capture("Idle_largeText", IdleSamples.Default, largeText = true)

    private fun capture(
        name: String,
        state: IdleUiState,
        largeText: Boolean = false,
    ) {
        composeRule.setContent {
            KioskTheme(largeText = largeText, reduceMotion = true) {
                DesignFrame { IdleScreen(state = state, onEvent = {}) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
