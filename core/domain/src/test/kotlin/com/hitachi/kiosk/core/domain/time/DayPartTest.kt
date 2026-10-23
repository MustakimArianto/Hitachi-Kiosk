package com.hitachi.kiosk.core.domain.time

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class DayPartTest {
    @Test
    fun `the greeting follows A-07 boundaries`() {
        assertThat(DayPart.at(4)).isEqualTo(DayPart.EVENING)
        assertThat(DayPart.at(5)).isEqualTo(DayPart.MORNING)
        assertThat(DayPart.at(10)).isEqualTo(DayPart.MORNING)
        assertThat(DayPart.at(11)).isEqualTo(DayPart.AFTERNOON)
        assertThat(DayPart.at(16)).isEqualTo(DayPart.AFTERNOON)
        assertThat(DayPart.at(17)).isEqualTo(DayPart.EVENING)
        assertThat(DayPart.at(0)).isEqualTo(DayPart.EVENING)
        assertThat(DayPart.at(23)).isEqualTo(DayPart.EVENING)
    }

    @Test
    fun `an hour outside the day is a programmer error`() {
        assertThrows(IllegalArgumentException::class.java) { DayPart.at(24) }
    }
}
