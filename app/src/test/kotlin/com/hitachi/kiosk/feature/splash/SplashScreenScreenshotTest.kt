package com.hitachi.kiosk.feature.splash

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DEV-010 / FR-001: the Splash artboard (`Main.dc.html`) at the first frame, after 4 s, and with reduce motion. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class SplashScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `splash at t0 and after 4 s matches the goldens`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { KioskTheme { DesignFrame { SplashScreen(onMarkClick = {}) } } }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/Main_t0.png")

        composeRule.mainClock.advanceTimeBy(SPLASH_SETTLED_MILLIS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/Main.png")
    }

    @Test
    fun `with reduce motion the first frame is the final frame`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { KioskTheme(reduceMotion = true) { DesignFrame { SplashScreen(onMarkClick = {}) } } }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/Main_reduceMotion.png")
    }

    private companion object {
        /** Past the last animation (bar fill ends at 3.8 s). */
        const val SPLASH_SETTLED_MILLIS = 4_000L

        init {
            check(KioskMotion.Splash.BAR_FILL_DELAY_MILLIS + KioskMotion.Splash.BAR_FILL_MILLIS < SPLASH_SETTLED_MILLIS)
        }
    }
}
