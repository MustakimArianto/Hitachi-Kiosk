package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlin.time.Duration

/** A [DeviceIdentityProvider] that can succeed, be unavailable or be slow (FR-002, architecture §9). */
class FakeDeviceIdentityProvider(
    identity: DeviceIdentity = SampleData.deviceIdentity,
) : DeviceIdentityProvider {
    val script = Script<DeviceIdentity>(Outcome.Success(identity))

    override suspend fun identify(): Outcome<DeviceIdentity> = script.next()

    /** No serial number can be read ("Restart the kiosk…" on Sign in). */
    fun unavailable() = script.fail(DomainError.Hardware(device = "identity", reason = "serial_unavailable"))

    /** Answers [identity] after [delay]. */
    fun slow(
        delay: Duration,
        identity: DeviceIdentity = SampleData.deviceIdentity,
    ) = script.succeed(identity, delay)
}
