package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.time.Clock
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * A [Clock] that follows the virtual time of a test dispatcher, so `delay` and the clock agree in `runTest`: the
 * monotonic reading is the scheduler's current time, and the wall clock is [start] plus that time plus [skew].
 * Code that waits with `delay` and measures with [elapsedRealtime] (countdowns, the 60 s unknown window) can then be
 * tested without moving a [TestClock] by hand. [skew] moves only the wall clock, as when staff change the device
 * time (NFR-017 clock-skew test).
 */
class VirtualTimeClock(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant = Instant.parse("2026-10-22T07:15:30Z"),
    var skew: Duration = Duration.ZERO,
) : Clock {
    override fun now(): Instant = start + elapsedRealtime() + skew

    override fun elapsedRealtime(): Duration = scheduler.currentTime.milliseconds
}
