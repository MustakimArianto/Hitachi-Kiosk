package com.hitachi.kiosk.inactivity

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.feature.menu.MenuScreen
import com.hitachi.kiosk.feature.menu.MenuTestStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DEV-040: the `StillThere` artboard over the Menu, and its `countdownLow` scenario (`StillThere.dc.html`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class StillThereOverlayScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `StillThere default`() = capture("StillThere", StillThereState.Showing(StillThereState.COUNTDOWN_SECONDS))

    @Test
    fun `StillThere countdownLow`() =
        capture("StillThere_countdownLow", StillThereState.Showing(StillThereState.COUNTDOWN_LOW_SECONDS))

    private fun capture(
        name: String,
        state: StillThereState,
    ) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame {
                    StillThereHost(state = state, onContinue = {}, onStartOver = {}) {
                        MenuScreen(state = MenuTestStates.Default, onEvent = {})
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
