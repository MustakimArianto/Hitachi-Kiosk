package com.hitachi.kiosk.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.api.DeviceCredentials
import com.hitachi.kiosk.core.data.api.InMemoryDeviceCredentialsStore
import com.hitachi.kiosk.core.data.prefs.InMemoryTokenStore
import com.hitachi.kiosk.core.data.prefs.newDeviceStore
import com.hitachi.kiosk.core.data.sync.InMemoryCatalogStore
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val tokens = InMemoryTokenStore()
    private val deviceStore by lazy { newDeviceStore(folder.root) }
    private val credentials = InMemoryDeviceCredentialsStore()
    private val catalogStore = InMemoryCatalogStore()
    private val session by lazy { DeviceSession(tokens, deviceStore, credentials, catalogStore) }

    private val token = SampleData.activation.deviceToken

    @Test
    fun `nothing stored means not activated`() =
        runTest {
            assertThat(session.load()).isEqualTo(DeviceState.NotActivated)
            assertThat(session.deviceId).isNull()
        }

    @Test
    fun `a stored activation without a binding goes to Choose a restaurant`() =
        runTest {
            tokens.token = token
            deviceStore.saveDevice(SampleData.kioskDevice)

            assertThat(session.load()).isEqualTo(SampleData.activatedState)
            assertThat(credentials.current()).isEqualTo(DeviceCredentials(SampleData.DEVICE_ID, token))
            assertThat(session.state.value).isEqualTo(SampleData.activatedState)
        }

    @Test
    fun `a stored binding goes to Idle`() =
        runTest {
            tokens.token = token
            deviceStore.saveDevice(SampleData.kioskDevice)
            deviceStore.saveBinding(SampleData.boundMerchant, KioskMode.SINGLE)

            assertThat(session.load()).isEqualTo(SampleData.boundState)
        }

    @Test
    fun `a device record without a token is wiped`() =
        runTest {
            deviceStore.saveDevice(SampleData.kioskDevice)

            assertThat(session.load()).isEqualTo(DeviceState.NotActivated)
            assertThat(deviceStore.read()).isNull()
        }

    @Test
    fun `a token without a device record is wiped`() =
        runTest {
            tokens.token = token

            assertThat(session.load()).isEqualTo(DeviceState.NotActivated)
            assertThat(tokens.token).isNull()
        }

    @Test
    fun `unreadable storage is wiped and reads as not activated`() =
        runTest {
            tokens.token = token
            deviceStore.saveDevice(SampleData.kioskDevice)
            tokens.failReads = true

            assertThat(session.load()).isEqualTo(DeviceState.NotActivated)
            assertThat(deviceStore.read()).isNull()
            assertThat(credentials.current()).isNull()
        }

    @Test
    fun `an activation is stored and replaces an old binding`() =
        runTest {
            deviceStore.saveDevice(SampleData.kioskDevice)
            deviceStore.saveBinding(SampleData.boundMerchant, KioskMode.SINGLE)

            val state = session.saveActivation(SampleData.activation)

            assertThat(state).isEqualTo(SampleData.activatedState)
            assertThat(tokens.token).isEqualTo(token)
            assertThat(deviceStore.read()?.merchant).isNull()
            assertThat(session.deviceId).isEqualTo(SampleData.DEVICE_ID)
        }

    @Test
    fun `a binding is stored on top of the activation`() =
        runTest {
            session.saveActivation(SampleData.activation)

            val state = session.saveBinding(SampleData.boundMerchant, KioskMode.SINGLE)

            assertThat(state).isEqualTo(SampleData.boundState)
            assertThat(session.load()).isEqualTo(SampleData.boundState)
        }

    @Test
    fun `a binding without an activation is refused`() =
        runTest {
            assertThat(session.saveBinding(SampleData.boundMerchant, KioskMode.SINGLE)).isNull()
            assertThat(deviceStore.read()).isNull()
        }

    @Test
    fun `wipe forgets the token, the device, the credentials and the catalog`() =
        runTest {
            session.saveActivation(SampleData.activation)
            session.saveBinding(SampleData.boundMerchant, KioskMode.SINGLE)

            session.wipe(reason = "test")

            assertThat(session.state.value).isEqualTo(DeviceState.NotActivated)
            assertThat(tokens.token).isNull()
            assertThat(deviceStore.read()).isNull()
            assertThat(credentials.current()).isNull()
            assertThat(catalogStore.clears).isEqualTo(1)
            assertThat(session.load()).isEqualTo(DeviceState.NotActivated)
        }
}
