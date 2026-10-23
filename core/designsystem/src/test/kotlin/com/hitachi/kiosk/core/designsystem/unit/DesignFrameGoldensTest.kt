package com.hitachi.kiosk.core.designsystem.unit

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * NFR-002 / TR-DSP-03: the recorded 160, 240 and 320 dpi goldens of [DesignFrameScreenshotTest] are
 * pixel-identical, so the layout does not depend on density.
 */
class DesignFrameGoldensTest {
    @Test
    fun `the 72 dpx margin goldens are pixel identical at 160 240 and 320 dpi`() {
        val images =
            listOf("mdpi", "hdpi", "xhdpi").associateWith { density ->
                val file = File(DesignFrameScreenshotTest.marginBoxGolden(density))
                assertWithMessage("missing golden $file, run recordRoborazziDebug").that(file.exists()).isTrue()
                ImageIO.read(file).pixels()
            }

        val reference = images.getValue("mdpi")
        assertThat(reference.size).isEqualTo(1080 * 1920)
        images.forEach { (density, pixels) ->
            assertWithMessage("$density differs from mdpi").that(pixels.contentEquals(reference)).isTrue()
        }
    }

    private fun BufferedImage.pixels(): IntArray = getRGB(0, 0, width, height, null, 0, width)
}
