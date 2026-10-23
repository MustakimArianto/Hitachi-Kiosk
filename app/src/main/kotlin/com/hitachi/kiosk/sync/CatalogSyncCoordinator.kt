package com.hitachi.kiosk.sync

import com.hitachi.kiosk.core.data.sync.PeriodicCatalogSync
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository
import com.hitachi.kiosk.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app-wide catalog sync triggers of FR-028 (technical-design §7.2) that are not tied to a screen: the 15-minute
 * periodic work, a sync when the network comes back, and applying a catalog held back during an order as soon as the
 * order ends (A-29). Binding and startup trigger their own syncs (`MerchantRepository`, `StartupRouter`).
 */
@Singleton
class CatalogSyncCoordinator
    @Inject
    constructor(
        private val periodic: PeriodicCatalogSync,
        private val catalog: CatalogSyncRepository,
        private val activation: ActivationRepository,
        private val connectivity: ConnectivityMonitor,
        private val orders: OrderSessionHolder,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        /** Starts the triggers for the life of the process. Call once, from `KioskApp`. */
        fun start() {
            scope.launch { periodic.schedule() }
            scope.launch {
                orders.current
                    .map { it == null }
                    .distinctUntilChanged()
                    .filter { idle -> idle }
                    .collect { catalog.applyPending() }
            }
            scope.launch {
                connectivity.connectivity
                    .runningFold(Reconnect()) { last, now -> Reconnect(previous = last.current, current = now) }
                    .filter { it.previous == Connectivity.OFFLINE && it.current == Connectivity.ONLINE }
                    .collect { syncBoundMerchant() }
            }
        }

        private suspend fun syncBoundMerchant() {
            val bound = activation.state.value as? DeviceState.Bound ?: return
            catalog.sync(bound.merchant.id)
        }

        private data class Reconnect(
            val previous: Connectivity? = null,
            val current: Connectivity? = null,
        )
    }
