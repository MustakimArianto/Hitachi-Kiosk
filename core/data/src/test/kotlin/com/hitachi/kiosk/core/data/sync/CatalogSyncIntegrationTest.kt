package com.hitachi.kiosk.core.data.sync

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.BackendHarness
import com.hitachi.kiosk.core.data.fixtureResponse
import com.hitachi.kiosk.core.data.takeRequestNow
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.testing.fake.FakeImagePrefetcher
import com.hitachi.kiosk.core.testing.fake.TestClock
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test

/** TC-121 over HTTP: the second sync sends the stored ETags, the 304s keep the cache (FR-028, TR-NET-09). */
class CatalogSyncIntegrationTest {
    @get:Rule
    val rule = MockWebServerRule()

    private val server get() = rule.server
    private val store = InMemoryCatalogStore()
    private val sync by lazy {
        DefaultCatalogSyncRepository(
            BackendHarness(server).api,
            store,
            DefaultOrderSessionHolder(),
            FakeImagePrefetcher(),
            TestClock(),
        )
    }

    @Test
    fun `a second sync is conditional and a 304 changes nothing`() =
        runTest {
            server.enqueue(fixtureResponse("e5_response", 200, "ETag" to "\"cfg-42\""))
            server.enqueue(fixtureResponse("e6_response", 200, "ETag" to "\"menu-118\""))
            assertThat(sync.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Success(KioskMode.SINGLE))
            val first = store.stored
            assertThat(first?.catalog).isEqualTo(SampleData.catalog)
            assertThat(server.takeRequestNow().headers["If-None-Match"]).isNull()
            assertThat(server.takeRequestNow().headers["If-None-Match"]).isNull()

            server.enqueue(MockResponse.Builder().code(304).build())
            server.enqueue(MockResponse.Builder().code(304).build())
            assertThat(sync.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Success(KioskMode.SINGLE))

            assertThat(server.takeRequestNow().headers["If-None-Match"]).isEqualTo("\"cfg-42\"")
            assertThat(server.takeRequestNow().headers["If-None-Match"]).isEqualTo("\"menu-118\"")
            assertThat(store.stored).isEqualTo(first)
            assertThat(store.writes).isEqualTo(1)
        }
}
