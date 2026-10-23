package com.hitachi.kiosk.ui

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Merchant colors from config and menu tones (A-38, NFR-013). */
class HexColorTest {
    @Test
    fun `a #RRGGBB value is an opaque color`() {
        assertThat(parseHexColor("#C8552B")).isEqualTo(Color(0xFFC8552B))
        assertThat(parseHexColor(" e9ded0 ")).isEqualTo(Color(0xFFE9DED0))
    }

    @Test
    fun `missing or malformed values fall back to the token`() {
        assertThat(parseHexColor(null)).isNull()
        assertThat(parseHexColor("")).isNull()
        assertThat(parseHexColor("#C855")).isNull()
        assertThat(parseHexColor("#GGGGGG")).isNull()
        assertThat(parseHexColor("#80C8552B")).isNull()
    }
}
