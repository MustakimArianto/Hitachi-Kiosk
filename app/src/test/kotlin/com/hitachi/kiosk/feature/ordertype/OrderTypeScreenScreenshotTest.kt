package com.hitachi.kiosk.feature.ordertype

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

/** DEV-025: the `OrderType` artboard, `S_OrderType_singleOption`, the picked state and larger text (TR-MNT-08). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class OrderTypeScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `OrderType default`() = capture("OrderType", OrderTypeSamples.Default)

    @Test
    fun `S_OrderType_singleOption`() = capture("S_OrderType_singleOption", OrderTypeSamples.SingleOption)

    @Test
    fun `OrderType after a pick`() = capture("OrderType_selected", OrderTypeSamples.Selected)

    @Test
    fun `OrderType with larger text`() = capture("OrderType_largeText", OrderTypeSamples.Default, largeText = true)

    private fun capture(
        name: String,
        state: OrderTypeUiState,
        largeText: Boolean = false,
    ) {
        composeRule.setContent {
            KioskTheme(largeText = largeText, reduceMotion = true) {
                DesignFrame { OrderTypeScreen(state = state, onEvent = {}) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
