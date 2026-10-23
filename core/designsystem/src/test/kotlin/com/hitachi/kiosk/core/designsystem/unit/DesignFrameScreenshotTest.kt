package com.hitachi.kiosk.core.designsystem.unit

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskPalette
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DEV-003 / NFR-002 / TC-141: a box with 72 dpx margins inside [DesignFrame] on a 1080 × 1920 px screen renders
 * the same pixels at 160, 240 and 320 dpi. Each density compares its capture with the golden and with the
 * expected geometry; [DesignFrameGoldensTest] checks the three goldens are pixel-identical.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignFrameScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w1080dp-h1920dp-mdpi")
    fun `a 72 dpx margin box at 160 dpi fills 72 px margins`() = captureMarginBox("mdpi")

    @Test
    @Config(qualifiers = "w720dp-h1280dp-hdpi")
    fun `a 72 dpx margin box at 240 dpi fills 72 px margins`() = captureMarginBox("hdpi")

    @Test
    @Config(qualifiers = "w540dp-h960dp-xhdpi")
    fun `a 72 dpx margin box at 320 dpi fills 72 px margins`() = captureMarginBox("xhdpi")

    private fun captureMarginBox(density: String) {
        composeRule.setContent {
            KioskTheme {
                DesignFrame {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(72.dpx)
                            .background(KioskTheme.colors.ink),
                    )
                }
            }
        }

        val root = composeRule.onRoot()
        root.captureRoboImage(marginBoxGolden(density))
        assertMarginGeometry(root.captureToImage().asAndroidBitmap())
    }

    private fun assertMarginGeometry(bitmap: Bitmap) {
        val bg = KioskPalette.Bg.toArgb()
        val ink = KioskPalette.Ink.toArgb()
        val mid = 540

        assertThat(bitmap.width to bitmap.height).isEqualTo(1080 to 1920)
        assertThat(bitmap.getPixel(71, 960)).isEqualTo(bg)
        assertThat(bitmap.getPixel(72, 960)).isEqualTo(ink)
        assertThat(bitmap.getPixel(1007, 960)).isEqualTo(ink)
        assertThat(bitmap.getPixel(1008, 960)).isEqualTo(bg)
        assertThat(bitmap.getPixel(mid, 71)).isEqualTo(bg)
        assertThat(bitmap.getPixel(mid, 72)).isEqualTo(ink)
        assertThat(bitmap.getPixel(mid, 1847)).isEqualTo(ink)
        assertThat(bitmap.getPixel(mid, 1848)).isEqualTo(bg)
    }

    companion object {
        /** Golden path for one density, relative to the module directory (Roborazzi's working directory). */
        fun marginBoxGolden(density: String): String = "src/test/screenshots/DesignFrame_marginBox_$density.png"
    }
}
