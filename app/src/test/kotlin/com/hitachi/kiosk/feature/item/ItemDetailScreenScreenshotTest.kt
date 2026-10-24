package com.hitachi.kiosk.feature.item

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.domain.model.Level
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DEV-029: the `ItemDetail` artboard, its `S_ItemDetail_*` boards and scenario states, plus larger text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class ItemDetailScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `ItemDetail default`() = capture("ItemDetail", ItemDetailTestStates.default())

    @Test
    fun `S_ItemDetail_requiredMissing`() =
        capture("S_ItemDetail_requiredMissing", ItemDetailTestStates.requiredMissing())

    @Test
    fun `S_ItemDetail_soldOut`() = capture("S_ItemDetail_soldOut", ItemDetailTestStates.soldOut())

    @Test
    fun `ItemDetail at the maximum quantity`() = capture("ItemDetail_maxQty", ItemDetailTestStates.maxQuantity())

    @Test
    fun `ItemDetail with Customize open`() =
        capture("ItemDetail_customizeOpen", ItemDetailTestStates.customizeOpen()) {
            composeRule.onNodeWithTag(itemLevelTag("ing_sauce", Level.REGULAR)).performScrollTo()
        }

    @Test
    fun `ItemDetail after adding`() = capture("ItemDetail_added", ItemDetailTestStates.added())

    @Test
    fun `ItemDetail with larger text`() =
        capture("ItemDetail_largeText", ItemDetailTestStates.default(), largeText = true)

    private fun capture(
        name: String,
        state: ItemDetailUiState,
        largeText: Boolean = false,
        before: () -> Unit = {},
    ) {
        composeRule.setContent {
            KioskTheme(largeText = largeText, reduceMotion = true) {
                DesignFrame { ItemDetailScreen(state = state, onEvent = {}) }
            }
        }
        before()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
