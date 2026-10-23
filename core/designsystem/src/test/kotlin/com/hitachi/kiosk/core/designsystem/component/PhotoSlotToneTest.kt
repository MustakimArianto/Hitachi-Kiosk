package com.hitachi.kiosk.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskPalette
import org.junit.Test

/** NFR-013: the empty photo slot keeps a light tone, as `Photo.dc.html` computes it. */
class PhotoSlotToneTest {
    @Test
    fun `a light tone is used as is`() {
        assertThat(photoPlaceholderTone(KioskPalette.PhotoTone)).isEqualTo(KioskPalette.PhotoTone)
        assertThat(photoPlaceholderTone(KioskPalette.Surface)).isEqualTo(KioskPalette.Surface)
    }

    @Test
    fun `a dark tone falls back to the default placeholder tone`() {
        assertThat(photoPlaceholderTone(KioskPalette.Ink)).isEqualTo(KioskPalette.PhotoToneFallback)
        assertThat(photoPlaceholderTone(KioskPalette.AccentPaprika)).isEqualTo(KioskPalette.PhotoToneFallback)
    }
}
