package com.hitachi.kiosk.feature.menu

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
 * DEV-027 / DEV-028: the `Menu` artboard with the design's sample cart, its `S_Menu_*` boards, the `unavailable`
 * and `maxQty` scenarios, Burgers (TC-037) and larger text (TR-MNT-08).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class MenuScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Menu default Popular`() = capture("Menu", MenuTestStates.Default)

    @Test
    fun `S_Menu_emptyCategory`() = capture("S_Menu_emptyCategory", MenuTestStates.EmptyCategory)

    @Test
    fun `S_Menu_drinksTab`() = capture("S_Menu_drinksTab", MenuTestStates.DrinksTab)

    @Test
    fun `S_Menu_planOff`() = capture("S_Menu_planOff", MenuTestStates.PlanOff)

    @Test
    fun `S_Menu_offline`() = capture("S_Menu_offline", MenuTestStates.Offline)

    @Test
    fun `Menu with sold-out items`() = capture("Menu_unavailable", MenuTestStates.Unavailable)

    @Test
    fun `Menu with a line at 20`() = capture("Menu_maxQty", MenuTestStates.MaxQty)

    @Test
    fun `Menu burgers`() = capture("Menu_burgers", MenuTestStates.Burgers)

    @Test
    fun `Menu with larger text`() = capture("Menu_largeText", MenuTestStates.Default, largeText = true)

    private fun capture(
        name: String,
        state: MenuUiState,
        largeText: Boolean = false,
    ) {
        composeRule.setContent {
            KioskTheme(largeText = largeText, reduceMotion = true) {
                DesignFrame { MenuScreen(state = state, onEvent = {}) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
