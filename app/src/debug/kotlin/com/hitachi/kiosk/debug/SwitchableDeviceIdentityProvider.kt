package com.hitachi.kiosk.debug

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider
import com.hitachi.kiosk.core.testing.fake.FakeDeviceIdentityProvider
import kotlin.time.Duration.Companion.seconds

/**
 * Debug only: answers each [identify] from the real serial-number reader or from a [FakeDeviceIdentityProvider],
 * following the Developer options at the time of the call (FR-002 AC3, DEV-012, technical-design §4.3).
 */
class SwitchableDeviceIdentityProvider(
    private val real: DeviceIdentityProvider,
    private val settings: () -> DeveloperSettings,
) : DeviceIdentityProvider {
    private val fakes = mutableMapOf<IdentityMode, DeviceIdentityProvider>()

    /** The implementation for the current settings. */
    @Synchronized
    fun target(): DeviceIdentityProvider {
        val mode = settings().identityMode
        if (mode == IdentityMode.REAL) return real
        return fakes.getOrPut(mode) {
            FakeDeviceIdentityProvider().apply {
                when (mode) {
                    IdentityMode.FAKE_UNAVAILABLE -> unavailable()
                    IdentityMode.FAKE_SLOW -> slow(SLOW_READ)
                    IdentityMode.FAKE, IdentityMode.REAL -> Unit
                }
            }
        }
    }

    override suspend fun identify(): Outcome<DeviceIdentity> = target().identify()

    private companion object {
        /** FR-002 AC3. */
        val SLOW_READ = 5.seconds
    }
}
