package com.hitachi.kiosk.navigation

import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository
import com.hitachi.kiosk.di.ApplicationScope
import com.hitachi.kiosk.recovery.PendingOrderRecoveryCoordinator
import com.hitachi.kiosk.recovery.StartupRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Where Splash sends the kiosk (FR-001, PRD §5.1). */
enum class StartDestination { SIGN_IN, MERCHANTS, IDLE }

/**
 * Picks the screen after Splash (FR-001, DEV-011, technical-design §4.2). The minimum display time and the local
 * load run in parallel; routing waits for both, but never more than [initCap] for the load. A bound kiosk also
 * starts a catalog download in the background that the splash does not wait for, and hands the pending-order check
 * of architecture §5.3 to [recovery] (DEV-052): the recovery itself runs in the application scope after the splash
 * has landed on Idle, so it can never hold the splash past its cap. A kiosk that is not bound has no pending order to
 * recover.
 */
class StartupRouter(
    private val activation: ActivationRepository,
    private val catalog: CatalogSyncRepository,
    private val backgroundScope: CoroutineScope,
    private val recovery: StartupRecovery,
    private val minimumDisplay: Duration = MINIMUM_DISPLAY,
    private val initCap: Duration = INIT_CAP,
) {
    @Inject
    constructor(
        activation: ActivationRepository,
        catalog: CatalogSyncRepository,
        @ApplicationScope backgroundScope: CoroutineScope,
        recovery: PendingOrderRecoveryCoordinator,
    ) : this(activation, catalog, backgroundScope, recovery::recoverAtStartup, MINIMUM_DISPLAY, INIT_CAP)

    suspend fun route(): StartDestination =
        coroutineScope {
            val minimum = async { delay(minimumDisplay) }
            val state =
                withTimeoutOrNull(initCap) { activation.load() }
                    ?: activation.state.value.also {
                        KioskLog.w(
                            "startup_init_timeout",
                            "capMs" to initCap.inWholeMilliseconds,
                        )
                    }
            if (state is DeviceState.Bound) {
                // FR-001: start a sync when bound, but never let the splash wait on the network.
                backgroundScope.launch { catalog.sync(state.merchant.id) }
                // DEV-052: a payment that was pending when the app died is settled in the background.
                recovery.begin()
            }
            minimum.await()
            state.destination().also { KioskLog.i("startup_routed", "destination" to it) }
        }

    private fun DeviceState.destination(): StartDestination =
        when (this) {
            DeviceState.NotActivated -> StartDestination.SIGN_IN
            is DeviceState.Activated -> StartDestination.MERCHANTS
            is DeviceState.Bound -> StartDestination.IDLE
        }

    companion object {
        /** The splash animation's auto-advance (PRD §11.4). */
        val MINIMUM_DISPLAY: Duration = 3_900.milliseconds

        /** The splash never waits longer than this for initialisation (FR-001 rules). */
        val INIT_CAP: Duration = 10.seconds
    }
}
