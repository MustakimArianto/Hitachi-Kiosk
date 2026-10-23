package com.hitachi.kiosk.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class RupiahTest {
    @Test
    fun `arithmetic works on whole rupiah`() {
        assertThat(Rupiah(52_000) + Rupiah(18_000)).isEqualTo(Rupiah(70_000))
        assertThat(Rupiah(172_700) - Rupiah(10_000)).isEqualTo(Rupiah(162_700))
        assertThat(Rupiah(16_000) * 2).isEqualTo(Rupiah(32_000))
        assertThat(Rupiah.Zero.value).isEqualTo(0)
    }

    @Test
    fun `amounts compare by value`() {
        assertThat(Rupiah(1) < Rupiah(2)).isTrue()
        assertThat(listOf(Rupiah(3), Rupiah(1), Rupiah(2)).sorted()).containsExactly(Rupiah(1), Rupiah(2), Rupiah(3))
    }

    @Test
    fun `overflow throws instead of wrapping`() {
        assertThrows(ArithmeticException::class.java) { Rupiah(Long.MAX_VALUE) + Rupiah(1) }
        assertThrows(ArithmeticException::class.java) { Rupiah(Long.MIN_VALUE) - Rupiah(1) }
        assertThrows(ArithmeticException::class.java) { Rupiah(Long.MAX_VALUE) * 2 }
    }
}
