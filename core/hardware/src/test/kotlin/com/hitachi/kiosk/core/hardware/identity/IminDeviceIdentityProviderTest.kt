package com.hitachi.kiosk.core.hardware.identity

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.log.LogLevel
import com.hitachi.kiosk.core.domain.log.LogSink
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

class IminDeviceIdentityProviderTest {
    private class ScriptedSource(
        override val name: String,
        var reading: SerialReading,
    ) : SerialSource {
        var reads = 0

        override fun read(): SerialReading = reading.also { reads++ }
    }

    private val first = ScriptedSource("first", SerialReading.Blank)
    private val second = ScriptedSource("second", SerialReading.Blank)
    private val third = ScriptedSource("third", SerialReading.Blank)
    private val dataStore = InMemoryDataStore()
    private val cache = SerialCache(dataStore)
    private val provider =
        IminDeviceIdentityProvider(
            sources = SerialSources(listOf(first, second, third)),
            cache = cache,
            build = DeviceBuild(model = "iMin Swan 2", osVersion = "14"),
        )
    private val events = mutableListOf<Pair<LogLevel, String>>()

    @Before
    fun installLog() {
        KioskLog.install(LogSink { level, event, _, _ -> events += level to event })
    }

    @After
    fun removeLog() {
        KioskLog.install(LogSink { _, _, _, _ -> })
    }

    @Test
    fun `the first plausible source wins, with model and Android version`() =
        runTest {
            first.reading = SerialReading.Value("unknown")
            second.reading = SerialReading.Value(" IMN24K0003118 ")
            third.reading = SerialReading.Value("IMN24K0009999")

            assertThat(provider.identify())
                .isEqualTo(Outcome.Success(DeviceIdentity("IMN24K0003118", model = "iMin Swan 2", osVersion = "14")))
            assertThat(third.reads).isEqualTo(0)
        }

    @Test
    fun `failing and unavailable sources are skipped`() =
        runTest {
            first.reading = SerialReading.Failed("NoSuchMethodException", null)
            second.reading = SerialReading.Unavailable(BuildSerialSource.PERMISSION_MISSING)
            third.reading = SerialReading.Value("IMN24K0003118")

            assertThat(provider.serial()).isEqualTo("IMN24K0003118")
        }

    @Test
    fun `the first value read is cached`() =
        runTest {
            second.reading = SerialReading.Value("IMN24K0003118")

            provider.identify()

            assertThat(cache.read()).isEqualTo("IMN24K0003118")
            assertThat(events).contains(LogLevel.INFO to "serial_cached")
        }

    @Test
    fun `the sources are read again at every call and a changed value replaces the cache`() =
        runTest {
            cache.write("IMN24K0000001")
            first.reading = SerialReading.Value("IMN24K0003118")

            assertThat(provider.serial()).isEqualTo("IMN24K0003118")
            assertThat(provider.serial()).isEqualTo("IMN24K0003118")

            assertThat(first.reads).isEqualTo(2)
            assertThat(cache.read()).isEqualTo("IMN24K0003118")
            assertThat(events.filter { it.second == "serial_mismatch" })
                .containsExactly(LogLevel.WARN to "serial_mismatch")
        }

    @Test
    fun `when every source fails the cached value keeps the SN stable`() =
        runTest {
            cache.write("IMN24K0003118")

            assertThat(provider.serial()).isEqualTo("IMN24K0003118")
            assertThat(events).contains(LogLevel.WARN to "serial_from_cache")
        }

    @Test
    fun `with no source and no cache the identity is unavailable`() =
        runTest {
            assertThat(provider.identify())
                .isEqualTo(Outcome.Failure(DomainError.Hardware(device = "identity", reason = "serial_unavailable")))
            assertThat(events).contains(LogLevel.WARN to "serial_unavailable")
        }

    @Test
    fun `a broken cache file does not stop a live read`() =
        runTest {
            dataStore.failure = IOException("corrupt")
            first.reading = SerialReading.Value("IMN24K0003118")

            assertThat(provider.serial()).isEqualTo("IMN24K0003118")
        }

    private suspend fun IminDeviceIdentityProvider.serial(): String? =
        (identify() as? Outcome.Success)?.value?.serialNumber
}
