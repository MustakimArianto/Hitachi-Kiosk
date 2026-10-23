package com.hitachi.kiosk.core.data.prefs

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { newDeviceStore(folder.root) }

    @Test
    fun `an empty store has no device`() =
        runTest {
            assertThat(store.read()).isNull()
        }

    @Test
    fun `a saved device reads back without a binding`() =
        runTest {
            store.saveDevice(SampleData.kioskDevice)

            assertThat(store.read()).isEqualTo(DeviceRecord(SampleData.kioskDevice, merchant = null, mode = null))
        }

    @Test
    fun `a binding is stored with the operating mode`() =
        runTest {
            store.saveDevice(SampleData.kioskDevice)
            store.saveBinding(SampleData.boundMerchant, KioskMode.SINGLE)

            assertThat(store.read())
                .isEqualTo(DeviceRecord(SampleData.kioskDevice, SampleData.boundMerchant, KioskMode.SINGLE))
        }

    @Test
    fun `activating again removes the old binding`() =
        runTest {
            store.saveDevice(SampleData.kioskDevice)
            store.saveBinding(SampleData.boundMerchant, KioskMode.SINGLE)

            store.saveDevice(SampleData.kioskDevice.copy(kioskName = "Kiosk 4"))

            assertThat(store.read()?.merchant).isNull()
            assertThat(store.read()?.device?.kioskName).isEqualTo("Kiosk 4")
        }

    @Test
    fun `clear removes everything`() =
        runTest {
            store.saveDevice(SampleData.kioskDevice)
            store.saveBinding(SampleData.boundMerchant, KioskMode.SINGLE)

            store.clear()

            assertThat(store.read()).isNull()
        }
}
