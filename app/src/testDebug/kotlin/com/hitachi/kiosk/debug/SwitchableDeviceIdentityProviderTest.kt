package com.hitachi.kiosk.debug

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.testing.fake.FakeDeviceIdentityProvider
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SwitchableDeviceIdentityProviderTest {
    private val realIdentity = DeviceIdentity(serialNumber = "REAL0001", model = "D4-503", osVersion = "13")
    private val real = FakeDeviceIdentityProvider(realIdentity)
    private var settings = DeveloperSettings()
    private val provider = SwitchableDeviceIdentityProvider(real = real, settings = { settings })

    @Test
    fun `debug builds start on the fake sample serial number`() =
        runTest {
            assertThat(provider.identify()).isEqualTo(Outcome.Success(SampleData.deviceIdentity))
            assertThat(SampleData.deviceIdentity.serialNumber).isEqualTo("IMN24K0003118")
            assertThat(real.script.callCount).isEqualTo(0)
        }

    @Test
    fun `real mode reads the real provider`() =
        runTest {
            settings = DeveloperSettings(identityMode = IdentityMode.REAL)

            assertThat(provider.identify()).isEqualTo(Outcome.Success(realIdentity))
            assertThat(real.script.callCount).isEqualTo(1)
        }

    @Test
    fun `unavailable mode fails like the real reader`() =
        runTest {
            settings = DeveloperSettings(identityMode = IdentityMode.FAKE_UNAVAILABLE)

            assertThat(provider.identify())
                .isEqualTo(Outcome.Failure(DomainError.Hardware(device = "identity", reason = "serial_unavailable")))
        }

    @Test
    fun `slow mode answers after 5 seconds`() =
        runTest {
            settings = DeveloperSettings(identityMode = IdentityMode.FAKE_SLOW)

            val answer = async { provider.identify() }
            advanceTimeBy(4_999)
            runCurrent()
            assertThat(answer.isCompleted).isFalse()
            advanceTimeBy(2)
            runCurrent()
            assertThat(answer.await()).isEqualTo(Outcome.Success(SampleData.deviceIdentity))
        }

    @Test
    fun `the fake is kept while the mode stays`() {
        settings = DeveloperSettings(identityMode = IdentityMode.FAKE_UNAVAILABLE)
        val unavailable = provider.target()

        assertThat(provider.target()).isSameInstanceAs(unavailable)
        settings = DeveloperSettings(identityMode = IdentityMode.REAL)
        assertThat(provider.target()).isSameInstanceAs(real)
    }
}
