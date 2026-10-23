package com.hitachi.kiosk.core.domain.time

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The only source of time in domain code and ViewModels (NFR-017, TR-INT-10). Business timestamps come from the
 * server; [now] is for display and offsets, [elapsedRealtime] drives UI timers and countdowns.
 */
interface Clock {
    /** Wall-clock time. It can jump when the device time changes. */
    fun now(): Instant

    /** Monotonic time since boot. It never jumps, so timers use it. */
    fun elapsedRealtime(): Duration
}
