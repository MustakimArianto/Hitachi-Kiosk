package com.hitachi.kiosk.sync

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.testing.fake.FakeActivationRepository
import com.hitachi.kiosk.core.testing.fake.FakeCatalogSyncRepository
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** FR-028 triggers outside the screens: periodic work, reconnect, and applying a held-back sync at order end (A-29). */
class CatalogSyncCoordinatorTest {
    private var scheduled = 0
    private val catalog = FakeCatalogSyncRepository()
    private val activation = FakeActivationRepository()
    private val connectivity = FakeConnectivityMonitor(Connectivity.ONLINE)
    private val orders = DefaultOrderSessionHolder()

    private fun TestScope.started(): CatalogSyncCoordinator =
        CatalogSyncCoordinator({ scheduled++ }, catalog, activation, connectivity, orders, backgroundScope)
            .also {
                it.start()
                runCurrent()
            }

    @Test
    fun `the periodic sync is scheduled once at start`() =
        runTest {
            started()

            assertThat(scheduled).isEqualTo(1)
        }

    @Test
    fun `a held-back catalog is applied when the order ends`() =
        runTest {
            started()
            val idleAtStart = catalog.pendingApplied

            orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            runCurrent()
            assertThat(catalog.pendingApplied).isEqualTo(idleAtStart)

            orders.endOrder()
            runCurrent()
            assertThat(catalog.pendingApplied).isEqualTo(idleAtStart + 1)
        }

    @Test
    fun `coming back online syncs the bound merchant once`() =
        runTest {
            activation.emit(SampleData.boundState)
            started()

            connectivity.set(Connectivity.CHECKING)
            connectivity.set(Connectivity.ONLINE)
            runCurrent()
            assertThat(catalog.synced).isEmpty()

            connectivity.set(Connectivity.OFFLINE)
            runCurrent()
            connectivity.set(Connectivity.ONLINE)
            runCurrent()
            assertThat(catalog.synced).containsExactly(SampleData.MERCHANT_ID)
        }

    @Test
    fun `reconnecting without a merchant syncs nothing`() =
        runTest {
            activation.emit(SampleData.activatedState)
            started()

            connectivity.set(Connectivity.OFFLINE)
            runCurrent()
            connectivity.set(Connectivity.ONLINE)
            runCurrent()

            assertThat(catalog.synced).isEmpty()
        }
}
