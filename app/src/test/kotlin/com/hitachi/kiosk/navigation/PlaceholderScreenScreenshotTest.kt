package com.hitachi.kiosk.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * TC-141 for the 0.1.0 skeleton: a placeholder screen at 1080 × 1920 px for 160, 240 and 320 dpi has the same layout,
 * the widest supported screen (1200 × 1920) fills the window, and a taller one (1080 × 2340) is letterboxed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceholderScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w1080dp-h1920dp-mdpi")
    fun `your order placeholder at 160 dpi`() = capture("placeholder_your_order_1080x1920_160dpi")

    @Test
    @Config(qualifiers = "w720dp-h1280dp-hdpi")
    fun `your order placeholder at 240 dpi`() = capture("placeholder_your_order_1080x1920_240dpi")

    @Test
    @Config(qualifiers = "w540dp-h960dp-xhdpi")
    fun `your order placeholder at 320 dpi`() = capture("placeholder_your_order_1080x1920_320dpi")

    @Test
    @Config(qualifiers = "w600dp-h960dp-xhdpi")
    fun `your order placeholder fills a 1200 x 1920 screen`() = capture("placeholder_your_order_1200x1920")

    @Test
    @Config(qualifiers = "w540dp-h1170dp-xhdpi")
    fun `your order placeholder is letterboxed on a 1080 x 2340 screen`() =
        capture("placeholder_your_order_1080x2340_letterbox")

    private fun capture(name: String) {
        compose.setContent {
            KioskTheme {
                DesignFrame {
                    PlaceholderScreen(
                        screenId = "SCR-10",
                        title = R.string.screen_your_order,
                        actions =
                            listOf(
                                PlaceholderAction(R.string.nav_add_more) {},
                                PlaceholderAction(R.string.nav_pay) {},
                                PlaceholderAction(R.string.nav_simulate_inactivity) {},
                            ),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
