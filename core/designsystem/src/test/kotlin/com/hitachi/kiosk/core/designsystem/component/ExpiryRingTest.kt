package com.hitachi.kiosk.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** DEV-048 / FR-017: the expiry ring maths of `Checkout.dc.html` (`126 × (1 − remaining / total)`) and `m:ss`. */
class ExpiryRingTest {
    @Test
    fun `a full ring has offset 0`() {
        assertThat(expiryRingFraction(300, 300)).isEqualTo(1f)
        assertThat(expiryRingDashOffset(300, 300)).isEqualTo(0f)
    }

    @Test
    fun `a half ring has half the dash as offset`() {
        assertThat(expiryRingFraction(150, 300)).isEqualTo(0.5f)
        assertThat(expiryRingDashOffset(150, 300)).isEqualTo(63f)
    }

    @Test
    fun `an expired ring has the whole dash as offset`() {
        assertThat(expiryRingDashOffset(0, 300)).isEqualTo(126f)
    }

    @Test
    fun `remaining time is clamped to the lifetime`() {
        assertThat(expiryRingFraction(400, 300)).isEqualTo(1f)
        assertThat(expiryRingFraction(-5, 300)).isEqualTo(0f)
        assertThat(expiryRingDashOffset(400, 300)).isEqualTo(0f)
        assertThat(expiryRingDashOffset(-5, 300)).isEqualTo(126f)
    }

    @Test
    fun `a lifetime of zero or less gives an empty ring`() {
        assertThat(expiryRingFraction(10, 0)).isEqualTo(0f)
        assertThat(expiryRingFraction(10, -1)).isEqualTo(0f)
        assertThat(expiryRingDashOffset(10, 0)).isEqualTo(126f)
    }

    @Test
    fun `countdown is formatted as m colon ss`() {
        assertThat(formatCountdown(300)).isEqualTo("5:00")
        assertThat(formatCountdown(299)).isEqualTo("4:59")
        assertThat(formatCountdown(61)).isEqualTo("1:01")
        assertThat(formatCountdown(7)).isEqualTo("0:07")
        assertThat(formatCountdown(0)).isEqualTo("0:00")
        assertThat(formatCountdown(600)).isEqualTo("10:00")
    }

    @Test
    fun `a negative countdown shows 0 colon 00`() {
        assertThat(formatCountdown(-3)).isEqualTo("0:00")
    }
}
