package com.hitachi.kiosk.core.data.sync

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.mapper.CatalogStamp
import com.hitachi.kiosk.core.data.repository.StoredCatalog
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi.Endpoint
import com.hitachi.kiosk.core.testing.fake.FakeImagePrefetcher
import com.hitachi.kiosk.core.testing.fake.TestClock
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** DEV-022, TC-121, TC-124: ETags, atomic replace, failures and the mid-order deferral (A-29). */
class DefaultCatalogSyncRepositoryTest {
    private val backend = FakeBackendApi()
    private val store = InMemoryCatalogStore()
    private val orders = DefaultOrderSessionHolder()
    private val images = FakeImagePrefetcher()
    private val clock = TestClock()
    private val sync = DefaultCatalogSyncRepository(backend, store, orders, images, clock)

    private val newMenu =
        SampleData.menu.copy(
            menuVersion = "menu-119",
            products =
                SampleData.menu.products.map {
                    if (it.id == "c1") it.copy(price = Rupiah(40_000)) else it
                },
        )

    @Test
    fun `a first sync stores config and menu with their ETags`() =
        runTest {
            assertThat(sync.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Success(KioskMode.SINGLE))

            assertThat(backend.calls.map { it.endpoint }).containsExactly(Endpoint.E5, Endpoint.E6).inOrder()
            assertThat(backend.callsTo(Endpoint.E5).single().args).containsExactly(SampleData.MERCHANT_ID, null)
            assertThat(store.stored).isEqualTo(
                StoredCatalog(
                    SampleData.catalog,
                    CatalogStamp("\"cfg-42\"", "\"menu-118\"", clock.now().toEpochMilliseconds()),
                ),
            )
        }

    @Test
    fun `the next sync sends the ETags and a 304 keeps the cache (TC-121)`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)
            backend.config.succeed(Conditional.NotModified)
            backend.menu.succeed(Conditional.NotModified)

            assertThat(sync.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Success(KioskMode.SINGLE))

            assertThat(backend.callsTo(Endpoint.E5).last().args).containsExactly(SampleData.MERCHANT_ID, "\"cfg-42\"")
            assertThat(backend.callsTo(Endpoint.E6).last().args).containsExactly(SampleData.MERCHANT_ID, "\"menu-118\"")
            assertThat(store.writes).isEqualTo(1)
        }

    @Test
    fun `a changed menu with an unchanged config is stored with the cached config`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)
            backend.config.succeed(Conditional.NotModified)
            backend.menu.succeed(Conditional.Modified(newMenu, "\"menu-119\""))

            sync.sync(SampleData.MERCHANT_ID)

            assertThat(store.stored?.catalog?.menu).isEqualTo(newMenu)
            assertThat(store.stored?.catalog?.config).isEqualTo(SampleData.config)
            assertThat(store.stored?.stamp?.configEtag).isEqualTo("\"cfg-42\"")
            assertThat(store.stored?.stamp?.menuEtag).isEqualTo("\"menu-119\"")
        }

    @Test
    fun `a failure or a malformed answer keeps the previous catalog`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)
            val before = store.stored

            backend.menu.fail(DomainError.Unknown)
            assertThat(sync.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Failure(DomainError.Unknown))
            backend.config.fail(DomainError.Network())
            assertThat(sync.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Failure(DomainError.Network()))

            assertThat(store.stored).isEqualTo(before)
        }

    @Test
    fun `a write that fails keeps the old catalog and reports it`() =
        runTest {
            store.failWrites = true

            assertThat(sync.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Failure(DomainError.Unknown))
            assertThat(store.stored).isNull()
            assertThat(images.requests).isEmpty()
        }

    @Test
    fun `a 304 without a cached copy is a bad answer`() =
        runTest {
            backend.config.succeed(Conditional.NotModified)
            assertThat(sync.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Failure(DomainError.Unknown))

            backend.config.succeed(Conditional.Modified(SampleData.config, "\"cfg-42\""))
            backend.menu.succeed(Conditional.NotModified)
            assertThat(sync.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Failure(DomainError.Unknown))
        }

    @Test
    fun `another merchant's cache never lends its ETags`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)

            sync.sync("mer_other")

            assertThat(backend.callsTo(Endpoint.E5).last().args).containsExactly("mer_other", null)
            assertThat(backend.callsTo(Endpoint.E6).last().args).containsExactly("mer_other", null)
        }

    @Test
    fun `a sync during an order waits until the order ends (TC-124, A-29)`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)
            orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            backend.menu.succeed(Conditional.Modified(newMenu, "\"menu-119\""))

            assertThat(sync.sync(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Success(KioskMode.SINGLE))
            assertThat(store.stored?.catalog?.menu).isEqualTo(SampleData.menu)

            // Still ordering: nothing is applied.
            sync.applyPending()
            assertThat(store.stored?.catalog?.menu).isEqualTo(SampleData.menu)

            orders.endOrder()
            sync.applyPending()
            assertThat(store.stored?.catalog?.menu).isEqualTo(newMenu)

            // Applied once only.
            sync.applyPending()
            assertThat(store.writes).isEqualTo(2)
        }

    @Test
    fun `a sync during an order sends the held-back ETags`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)
            orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            backend.menu.succeed(Conditional.Modified(newMenu, "\"menu-119\""))
            sync.sync(SampleData.MERCHANT_ID)

            backend.menu.succeed(Conditional.NotModified)
            sync.sync(SampleData.MERCHANT_ID)

            assertThat(backend.callsTo(Endpoint.E6).last().args).containsExactly(SampleData.MERCHANT_ID, "\"menu-119\"")
            orders.endOrder()
            sync.applyPending()
            assertThat(store.stored?.catalog?.menu).isEqualTo(newMenu)
        }

    @Test
    fun `syncNow stores the new catalog at once even during an order (FR-016, DEV-046)`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)
            orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            backend.menu.succeed(Conditional.Modified(newMenu, "\"menu-119\""))

            assertThat(sync.syncNow(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Success(KioskMode.SINGLE))

            assertThat(store.stored?.catalog?.menu).isEqualTo(newMenu)
            assertThat(store.stored?.stamp?.menuEtag).isEqualTo("\"menu-119\"")
            assertThat(images.requests).hasSize(2)
            // Nothing is left to apply later.
            orders.endOrder()
            sync.applyPending()
            assertThat(store.writes).isEqualTo(2)
        }

    @Test
    fun `syncNow supersedes a catalog held back earlier and sends its ETags`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)
            orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            backend.menu.succeed(Conditional.Modified(newMenu, "\"menu-119\""))
            sync.sync(SampleData.MERCHANT_ID)
            assertThat(store.stored?.catalog?.menu).isEqualTo(SampleData.menu)
            val newest = newMenu.copy(menuVersion = "menu-120")
            backend.menu.succeed(Conditional.Modified(newest, "\"menu-120\""))

            sync.syncNow(SampleData.MERCHANT_ID)

            assertThat(backend.callsTo(Endpoint.E6).last().args).containsExactly(SampleData.MERCHANT_ID, "\"menu-119\"")
            assertThat(store.stored?.catalog?.menu).isEqualTo(newest)
            orders.endOrder()
            sync.applyPending()
            assertThat(store.stored?.catalog?.menu).isEqualTo(newest)
        }

    @Test
    fun `syncNow with nothing newer still applies the catalog that was held back`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)
            orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            backend.menu.succeed(Conditional.Modified(newMenu, "\"menu-119\""))
            sync.sync(SampleData.MERCHANT_ID)
            backend.config.succeed(Conditional.NotModified)
            backend.menu.succeed(Conditional.NotModified)

            assertThat(sync.syncNow(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Success(KioskMode.SINGLE))

            assertThat(store.stored?.catalog?.menu).isEqualTo(newMenu)
        }

    @Test
    fun `syncNow with nothing newer and nothing held back writes nothing`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)
            backend.config.succeed(Conditional.NotModified)
            backend.menu.succeed(Conditional.NotModified)

            sync.syncNow(SampleData.MERCHANT_ID)

            assertThat(store.writes).isEqualTo(1)
        }

    @Test
    fun `a failed syncNow keeps the catalog the order started with`() =
        runTest {
            sync.sync(SampleData.MERCHANT_ID)
            orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            backend.menu.fail(DomainError.Network())

            assertThat(sync.syncNow(SampleData.MERCHANT_ID)).isEqualTo(Outcome.Failure(DomainError.Network()))

            assertThat(store.stored?.catalog?.menu).isEqualTo(SampleData.menu)
        }

    @Test
    fun `images are prefetched after a write (NFR-013)`() =
        runTest {
            val withImages =
                SampleData.config.copy(
                    idlePromoImageUrls = listOf("https://cdn.example/idle1.jpg", null),
                )
            backend.config.succeed(Conditional.Modified(withImages, "\"cfg-43\""))
            val menu =
                SampleData.menu.copy(
                    products =
                        SampleData.menu.products.map {
                            if (it.id ==
                                "b1"
                            ) {
                                it.copy(imageUrl = "https://cdn.example/b1.jpg")
                            } else {
                                it
                            }
                        },
                )
            backend.menu.succeed(Conditional.Modified(menu, "\"menu-120\""))

            sync.sync(SampleData.MERCHANT_ID)

            assertThat(images.requests.single()).containsExactly(
                "https://cdn.example/idle1.jpg",
                SampleData.config.logoUrl,
                SampleData.config.heroPhotoUrl,
                "https://cdn.example/b1.jpg",
            )
        }

    @Test
    fun `concurrent syncs run one after the other`() =
        runTest {
            backend.config.succeed(Conditional.Modified(SampleData.config, null), delay = 1.seconds)

            val first = async { sync.sync(SampleData.MERCHANT_ID) }
            val second = async { sync.sync(SampleData.MERCHANT_ID) }

            assertThat(first.await()).isEqualTo(Outcome.Success(KioskMode.SINGLE))
            assertThat(second.await()).isEqualTo(Outcome.Success(KioskMode.SINGLE))
            assertThat(backend.calls.map { it.endpoint })
                .containsExactly(Endpoint.E5, Endpoint.E6, Endpoint.E5, Endpoint.E6)
                .inOrder()
        }
}
