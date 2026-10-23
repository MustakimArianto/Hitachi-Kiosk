package com.hitachi.kiosk.core.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeviceCredentialsTest {
    @Test
    fun `the in-memory store holds and clears credentials`() {
        val store = InMemoryDeviceCredentialsStore()
        val credentials = DeviceCredentials(deviceId = "dev_0003", deviceToken = "dvt_secret")

        assertThat(store.current()).isNull()
        store.set(credentials)
        assertThat(store.current()).isEqualTo(credentials)
        store.clear()
        assertThat(store.current()).isNull()
    }

    @Test
    fun `toString never prints the token`() {
        val text = DeviceCredentials(deviceId = "dev_0003", deviceToken = "dvt_secret").toString()

        assertThat(text).contains("dev_0003")
        assertThat(text).doesNotContain("dvt_secret")
    }

    @Test
    fun `the no-op unauthorized handler does nothing`() =
        runTest {
            UnauthorizedHandler.None.onUnauthorized()
        }
}
