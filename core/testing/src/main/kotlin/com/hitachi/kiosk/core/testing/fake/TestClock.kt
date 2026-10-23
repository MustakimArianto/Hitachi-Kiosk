package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A [Clock] that only moves when a test says so (TR-INT-10). Starts at the api-spec sample time. */
class TestClock(
    private var now: Instant = Instant.parse("2026-10-22T07:15:30Z"),
    private var elapsed: Duration = Duration.ZERO,
) : Clock {
    override fun now(): Instant = now

    override fun elapsedRealtime(): Duration = elapsed

    /** Moves wall-clock and monotonic time forward together. */
    fun advanceBy(duration: Duration) {
        require(!duration.isNegative()) { "Use setWallClock to move the wall clock backwards" }
        now += duration
        elapsed += duration
    }

    /** Changes only the wall clock, as when staff change the device time (NFR-017 clock-skew test). */
    fun setWallClock(instant: Instant) {
        now = instant
    }
}
