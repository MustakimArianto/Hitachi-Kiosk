package com.hitachi.kiosk.core.domain.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** NFR-017, TR-INT-03, technical-design §5.4: `offset = serverDate − clock.now()`, device expiry `expiresAt − offset`. */
class ServerClockOffsetTest {
    private val server = Instant.parse("2026-10-22T07:15:31Z")

    @Test
    fun `a device clock 10 minutes ahead gives an offset of minus 10 minutes (TC-160)`() {
        val deviceNow = server + 10.minutes

        val offset = ServerClockOffset.measure(server, deviceNow)

        assertThat(offset.offset).isEqualTo((-10).minutes)
        val expiresAt = server + 300.seconds
        // The QR expires 300 s from now on the server clock, whatever the device clock says.
        assertThat(offset.toDeviceTime(expiresAt) - deviceNow).isEqualTo(300.seconds)
    }

    @Test
    fun `a device clock behind gives a positive offset`() {
        val deviceNow = server - 3.minutes

        val offset = ServerClockOffset.measure(server, deviceNow)

        assertThat(offset.offset).isEqualTo(3.minutes)
        assertThat(offset.toServerTime(deviceNow)).isEqualTo(server)
    }

    @Test
    fun `no offset leaves times unchanged`() {
        assertThat(ServerClockOffset.None.toDeviceTime(server)).isEqualTo(server)
        assertThat(ServerClockOffset.None.toServerTime(server)).isEqualTo(server)
    }
}
