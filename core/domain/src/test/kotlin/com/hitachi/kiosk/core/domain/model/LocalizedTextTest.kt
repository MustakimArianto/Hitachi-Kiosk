package com.hitachi.kiosk.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalizedTextTest {
    @Test
    fun `English is shown when present`() {
        assertThat(LocalizedText(en = "Burgers", id = "Burger").resolve()).isEqualTo("Burgers")
    }

    @Test
    fun `Indonesian is the fallback when English is missing or blank`() {
        assertThat(LocalizedText(en = null, id = "Burger").resolve()).isEqualTo("Burger")
        assertThat(LocalizedText(en = " ", id = "Burger").resolve()).isEqualTo("Burger")
    }

    @Test
    fun `no text resolves to an empty string`() {
        assertThat(LocalizedText().resolve()).isEmpty()
    }
}
