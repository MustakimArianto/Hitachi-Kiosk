package com.hitachi.kiosk.core.domain.time

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * How far the backend clock is ahead of the device clock: `offset = serverTime − deviceNow`, measured on the `Date`
 * header of the E9 answer (technical-design §5.4, NFR-017, TR-INT-03). A QR code expires at the server's `expiresAt`,
 * which on the device is `expiresAt − offset`, so a device clock that is minutes off still counts down correctly.
 */
@JvmInline
value class ServerClockOffset(
    val offset: Duration,
) {
    /** The device wall-clock instant that corresponds to the server instant [server]. */
    fun toDeviceTime(server: Instant): Instant = server - offset

    /** The server instant that corresponds to the device wall-clock instant [device]. */
    fun toServerTime(device: Instant): Instant = device + offset

    companion object {
        /** No correction: used when the server time is unknown. */
        val None = ServerClockOffset(Duration.ZERO)

        /** The offset between [serverTime] and the device clock reading [deviceNow] taken when it arrived. */
        fun measure(
            serverTime: Instant,
            deviceNow: Instant,
        ): ServerClockOffset = ServerClockOffset(serverTime - deviceNow)
    }
}
