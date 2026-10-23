package com.hitachi.kiosk.core.designsystem.unit

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/** DEV-003 / TC-141: dpx and spx conversion and the TR-DSP-02 frame. */
class DesignUnitTest {
    @Test
    fun `the design scale is the frame width over 1080`() {
        assertThat(designScale(1080f)).isEqualTo(1f)
        assertThat(designScale(720f)).isWithin(1e-6f).of(2f / 3f)
        assertThat(designScale(540f)).isEqualTo(0.5f)
        assertThat(designScale(1200f)).isWithin(1e-6f).of(1200f / 1080f)
    }

    @Test
    fun `72 dpx is 72 px at 160 240 and 320 dpi on a 1080 px wide screen`() {
        // A 1080 px wide screen is 1080 dp at mdpi, 720 dp at hdpi and 540 dp at xhdpi.
        val densities = mapOf(1080f to 1f, 720f to 1.5f, 540f to 2f)

        densities.forEach { (widthDp, density) ->
            val px = designPxToDp(72, designScale(widthDp)).value * density
            assertThat(px).isWithin(1e-3f).of(72f)
        }
    }

    @Test
    fun `dpx converts with the scale`() {
        assertThat(designPxToDp(72, 1f)).isEqualTo(72.dp)
        assertThat(designPxToDp(72, 0.5f)).isEqualTo(36.dp)
        assertThat(designPxToDp(0, 0.5f)).isEqualTo(0.dp)
    }

    @Test
    fun `spx converts with the scale and multiplies by 1_15 for larger text`() {
        assertThat(designPxToSp(30, 1f, largeText = false).value).isEqualTo(30f)
        assertThat(designPxToSp(30, 0.5f, largeText = false).value).isEqualTo(15f)
        assertThat(designPxToSp(100, 1f, largeText = true).value).isWithin(1e-4f).of(115f)
        assertThat(designPxToSp(100, 0.5f, largeText = true).value).isWithin(1e-4f).of(57.5f)
        assertThat(textMultiplier(largeText = false)).isEqualTo(1f)
        assertThat(textMultiplier(largeText = true)).isEqualTo(1.15f)
    }

    @Test
    fun `a 1080 by 1920 window fills the frame at scale 1`() {
        val frame = designFrameSize(1080f, 1920f)

        assertThat(frame).isEqualTo(DesignFrameSize(1080f, 1920f))
        assertThat(frame.scale).isEqualTo(1f)
    }

    @Test
    fun `a 1080 px wide screen gives the same frame in px at every density`() {
        listOf(1080f to 1920f, 720f to 1280f, 540f to 960f).forEach { (w, h) ->
            assertThat(designFrameSize(w, h)).isEqualTo(DesignFrameSize(w, h))
        }
    }

    @Test
    fun `a 1200 by 1920 window is the widest that still fills the frame`() {
        val frame = designFrameSize(1200f, 1920f)

        assertThat(frame).isEqualTo(DesignFrameSize(1200f, 1920f))
        assertThat(frame.scale).isWithin(1e-6f).of(1200f / 1080f)
    }

    @Test
    fun `a 1080 by 2340 window is letterboxed vertically to 1080 by 1920`() {
        val frame = designFrameSize(1080f, 2340f)

        assertThat(frame.widthDp).isEqualTo(1080f)
        assertThat(frame.heightDp).isWithin(1e-3f).of(1920f)
        assertThat(frame.scale).isEqualTo(1f)
    }

    @Test
    fun `a window wider than 10 by 16 is pillarboxed to 1200 by 1920`() {
        val frame = designFrameSize(1440f, 1920f)

        assertThat(frame.widthDp).isWithin(1e-3f).of(1200f)
        assertThat(frame.heightDp).isEqualTo(1920f)
        assertThat(frame.scale).isWithin(1e-6f).of(1200f / 1080f)
    }

    @Test
    fun `a landscape window is pillarboxed to a centred 10 by 16 frame`() {
        val frame = designFrameSize(1920f, 1080f)

        assertThat(frame.widthDp).isWithin(1e-3f).of(675f)
        assertThat(frame.heightDp).isEqualTo(1080f)
    }

    @Test
    fun `an unbounded height is clamped by the width`() {
        val frame = designFrameSize(540f, Float.POSITIVE_INFINITY)

        assertThat(frame.heightDp).isWithin(1e-3f).of(960f)
    }

    @Test
    fun `a non positive window size is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { designFrameSize(0f, 1920f) }
        assertThrows(IllegalArgumentException::class.java) {
            designFrameSize(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        }
    }
}
