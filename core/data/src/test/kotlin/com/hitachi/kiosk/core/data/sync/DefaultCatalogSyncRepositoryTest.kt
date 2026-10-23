package com.hitachi.kiosk.core.data.sync

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi.Endpoint
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class DefaultCatalogSyncRepositoryTest {
    private val backend = FakeBackendApi()
    private val catalog = DefaultCatalogSyncRepository(backend)

    @Test
    fun `a sync downloads config then menu and keeps both`() =
        runTest {
            assertThat(catalog.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Success(KioskMode.SINGLE))

            assertThat(backend.calls.map { it.endpoint }).containsExactly(Endpoint.E5, Endpoint.E6).inOrder()
            assertThat(backend.callsTo(Endpoint.E5).single().args).containsExactly(SampleData.MERCHANT_ID, null)
            assertThat(catalog.latest).isEqualTo(CatalogSnapshot(SampleData.config, SampleData.menu))
        }

    @Test
    fun `a config failure skips the menu`() =
        runTest {
            backend.config.fail(DomainError.Server("SERVER_ERROR", 503))

            assertThat(catalog.sync(SampleData.MERCHANT_ID)).isInstanceOf(Outcome.Failure::class.java)
            assertThat(backend.callsTo(Endpoint.E6)).isEmpty()
            assertThat(catalog.latest).isNull()
        }

    @Test
    fun `a menu failure keeps the previous catalog`() =
        runTest {
            catalog.sync(SampleData.MERCHANT_ID)
            backend.menu.fail(DomainError.Network())

            assertThat(catalog.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Failure(DomainError.Network()))
            assertThat(catalog.latest?.menu).isEqualTo(SampleData.menu)
        }

    @Test
    fun `a 304 without a cached copy is a failure, with one it keeps the copy`() =
        runTest {
            backend.config.succeed(Conditional.NotModified)
            assertThat(catalog.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Failure(DomainError.Unknown))

            backend.config.succeed(Conditional.Modified(SampleData.config, "\"cfg-42\""))
            catalog.sync(SampleData.MERCHANT_ID)
            backend.config.succeed(Conditional.NotModified)
            backend.menu.succeed(Conditional.NotModified)
            assertThat(catalog.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Success(KioskMode.SINGLE))
            backend.menu.succeed(Conditional.NotModified)
        }

    @Test
    fun `concurrent syncs run one after the other`() =
        runTest {
            backend.config.succeed(Conditional.Modified(SampleData.config, null), delay = 1.seconds)

            val first = async { catalog.sync(SampleData.MERCHANT_ID) }
            val second = async { catalog.sync(SampleData.MERCHANT_ID) }

            assertThat(first.await()).isEqualTo(Outcome.Success(KioskMode.SINGLE))
            assertThat(second.await()).isEqualTo(Outcome.Success(KioskMode.SINGLE))
            assertThat(backend.calls.map { it.endpoint })
                .containsExactly(Endpoint.E5, Endpoint.E6, Endpoint.E5, Endpoint.E6)
                .inOrder()
        }
}
