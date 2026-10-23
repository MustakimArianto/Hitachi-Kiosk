package com.hitachi.kiosk.core.hardware.identity

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class SerialCacheTest {
    private val dataStore = InMemoryDataStore()
    private val cache = SerialCache(dataStore)

    @Test
    fun `stores, replaces and clears the serial number under serialNumber`() =
        runTest {
            assertThat(cache.read()).isNull()

            cache.write("IMN24K0003118")
            assertThat(cache.read()).isEqualTo("IMN24K0003118")
            assertThat(dataStore.current[SerialCache.SERIAL_NUMBER]).isEqualTo("IMN24K0003118")

            cache.write("IMN24K0003119")
            assertThat(cache.read()).isEqualTo("IMN24K0003119")

            cache.clear()
            assertThat(cache.read()).isNull()
        }

    @Test
    fun `a broken file reads as empty and writes are dropped without throwing`() =
        runTest {
            dataStore.failure = IOException("corrupt")

            assertThat(cache.read()).isNull()
            cache.write("IMN24K0003118")
            cache.clear()

            dataStore.failure = null
            assertThat(cache.read()).isNull()
        }
}
