package com.hitachi.kiosk.core.data.repository

import com.hitachi.kiosk.core.data.api.ApiConfig
import com.hitachi.kiosk.core.data.api.InMemoryDeviceCredentialsStore
import com.hitachi.kiosk.core.data.prefs.InMemoryTokenStore
import com.hitachi.kiosk.core.data.prefs.newDeviceStore
import com.hitachi.kiosk.core.data.sync.DefaultCatalogSyncRepository
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import java.io.File

/** The 0.2.0 data layer wired as in `RepositoryModule`, on a [FakeBackendApi] and storage in [folder]. */
internal class SetupHarness(
    folder: File,
) {
    val backend = FakeBackendApi()
    val tokens = InMemoryTokenStore()
    val deviceStore = newDeviceStore(folder)
    val credentials = InMemoryDeviceCredentialsStore()
    val session = DeviceSession(tokens, deviceStore, credentials)
    val catalog = DefaultCatalogSyncRepository(backend)
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
