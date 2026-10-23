package com.hitachi.kiosk.time

import com.hitachi.kiosk.core.domain.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** The device clock (TR-INT-10): wall time for display, `elapsedRealtime` for timers. */
class SystemClock : Clock {
    override fun now(): Instant =
        kotlin.time.Clock.System
            .now()

    override fun elapsedRealtime(): Duration =
        android.os.SystemClock
            .elapsedRealtime()
            .milliseconds
}
