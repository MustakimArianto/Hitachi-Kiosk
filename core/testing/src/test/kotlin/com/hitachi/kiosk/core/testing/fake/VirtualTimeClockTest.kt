package com.hitachi.kiosk.core.testing.fake

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class VirtualTimeClockTest {
    @Test
    fun `delay moves both clocks, skew moves only the wall clock`() =
        runTest {
            val clock = VirtualTimeClock(testScheduler, start = Instant.parse("2026-10-22T07:15:30Z"))

            delay(90.seconds)
            assertThat(clock.elapsedRealtime()).isEqualTo(90.seconds)
            assertThat(clock.now()).isEqualTo(Instant.parse("2026-10-22T07:17:00Z"))

            clock.skew = 10.minutes
            assertThat(clock.elapsedRealtime()).isEqualTo(90.seconds)
            assertThat(clock.now()).isEqualTo(Instant.parse("2026-10-22T07:27:00Z"))
        }
}
