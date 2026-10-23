package com.hitachi.kiosk.core.domain.format

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.Rupiah
import org.junit.Test

class IdrFormatterTest {
    @Test
    fun `amounts use a dot every three digits (BR-01)`() {
        assertThat(IdrFormatter.format(Rupiah(0))).isEqualTo("Rp 0")
        assertThat(IdrFormatter.format(Rupiah(800))).isEqualTo("Rp 800")
        assertThat(IdrFormatter.format(Rupiah(52_000))).isEqualTo("Rp 52.000")
        assertThat(IdrFormatter.format(Rupiah(1_234_567))).isEqualTo("Rp 1.234.567")
        assertThat(IdrFormatter.format(Rupiah(-10_000))).isEqualTo("-Rp 10.000")
        assertThat(IdrFormatter.format(Rupiah(Long.MIN_VALUE))).isEqualTo("-Rp 9.223.372.036.854.775.808")
    }

    @Test
    fun `signed amounts show a plus for upcharges`() {
        assertThat(IdrFormatter.formatSigned(Rupiah(6_000))).isEqualTo("+Rp 6.000")
        assertThat(IdrFormatter.formatSigned(Rupiah(0))).isEqualTo("Rp 0")
        assertThat(IdrFormatter.formatSigned(Rupiah(-500))).isEqualTo("-Rp 500")
    }
}
