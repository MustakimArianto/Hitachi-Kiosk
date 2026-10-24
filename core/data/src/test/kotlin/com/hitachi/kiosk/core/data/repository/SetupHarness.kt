package com.hitachi.kiosk.core.data.repository

import com.hitachi.kiosk.core.data.api.ApiConfig
import com.hitachi.kiosk.core.data.api.InMemoryDeviceCredentialsStore
import com.hitachi.kiosk.core.data.prefs.InMemoryTokenStore
import com.hitachi.kiosk.core.data.prefs.newDeviceStore
import com.hitachi.kiosk.core.data.sync.DefaultCatalogSyncRepository
import com.hitachi.kiosk.core.data.sync.InMemoryCatalogStore
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fake.FakeImagePrefetcher
import com.hitachi.kiosk.core.testing.fake.TestClock
import java.io.File

/**
 * The setup data layer wired as in `RepositoryModule`, on a [FakeBackendApi], device storage in [folder] and the
 * catalog cache in memory.
 */
internal class SetupHarness(
    folder: File,
) {
    val backend = FakeBackendApi()
    val tokens = InMemoryTokenStore()
    val deviceStore = newDeviceStore(folder)
    val credentials = InMemoryDeviceCredentialsStore()
    val catalogStore = InMemoryCatalogStore()
    val pendingOrders = InMemoryPendingOrderStore()
    val session = DeviceSession(tokens, deviceStore, credentials, catalogStore, pendingOrders)
    val catalog =
        DefaultCatalogSyncRepository(
            backend,
            catalogStore,
            DefaultOrderSessionHolder(),
            FakeImagePrefetcher(),
            TestClock(),
        )
    val ids = FakeIdGenerator()
    val config =
        ApiConfig(
            baseUrl = "https://dev-api.hitachikiosk.example/",
            environment = "dev",
            certificatePins = emptyList(),
            appVersion = "0.2.0",
            allowCleartext = false,
        )
    val activation = DefaultActivationRepository(backend, session, config)
    val merchants = DefaultMerchantRepository(backend, session, catalog, ids)
}
