package com.hitachi.kiosk.feature.merchant

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

/** DEV-015: the `Merchants` artboard, its `S_Merchants_*` boards and scenario states, plus larger text (TR-MNT-08). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class MerchantsScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Merchants default list`() = capture("Merchants", MerchantsSamples.Default)

    @Test
    fun `S_Merchants_skeleton`() = capture("S_Merchants_skeleton", MerchantsUiState(MerchantsContent.Loading))

    @Test
    fun `S_Merchants_empty`() = capture("S_Merchants_empty", MerchantsUiState(MerchantsContent.Empty))

    @Test
    fun `S_Merchants_bindFailed`() = capture("S_Merchants_bindFailed", MerchantsSamples.BindFailed)

    @Test
    fun `S_Merchants_closed`() = capture("S_Merchants_closed", MerchantsSamples.Closed)

    @Test
    fun `Merchants menu download failed`() =
        capture("Merchants_menuDownloadFailed", MerchantsSamples.MenuDownloadFailed)

    @Test
    fun `Merchants while binding`() = capture("Merchants_binding", MerchantsSamples.Binding)

    @Test
    fun `Merchants with larger text`() = capture("Merchants_largeText", MerchantsSamples.Default, largeText = true)

    private fun capture(
        name: String,
        state: MerchantsUiState,
        largeText: Boolean = false,
    ) {
        composeRule.setContent {
            KioskTheme(largeText = largeText, reduceMotion = true) {
                DesignFrame { MerchantsScreen(state = state, onEvent = {}) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
