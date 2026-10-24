package com.hitachi.kiosk.recovery

import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.order.RecoverPendingOrder
import com.hitachi.kiosk.core.domain.order.RecoveredPaidOrder
import com.hitachi.kiosk.core.domain.order.RecoveryOutcome
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** What `StartupRouter` calls once the kiosk is known to be bound, so a pending order is looked at (DEV-052). */
fun interface StartupRecovery {
    /** Starts the recovery in the background and returns at once: the splash never waits for it (FR-001). */
    fun begin()
}

/** Whether a recovery is in the way of a new order. Idle reads it before it starts one. */
interface RecoveryGate {
    /** `true` from the moment the startup recovery begins until it has settled. Set synchronously. */
    val blocksNewOrder: StateFlow<Boolean>
}

/** Where the recovery of the pending order is. */
sealed interface RecoveryState {
    /** Nothing is being recovered: no pending order, a finished one, or one waiting for the next `ONLINE`. */
    data object Settled : RecoveryState

    /** A run is asking the backend about the pending order. */
    data object Recovering : RecoveryState

    /** The order was paid: the app goes to Complete as soon as it is on Idle (FR-016 AC4). */
    data object PaidWaiting : RecoveryState
}

/**
 * Runs [RecoverPendingOrder] at startup and again on the next `ONLINE` after the kiosk was `OFFLINE` (FR-016 "Process
 * death", technical-design §8.6, DEV-052), in the style of `CatalogSyncCoordinator`.
 *
 * - **Startup**: `StartupRouter` calls [recoverAtStartup] when the device is bound. Recovery runs in the application
 *   scope after the splash has landed on Idle; the splash is never held for it. The first `PAID` result is kept in
 *   [recoveredPaid] and [state] becomes [RecoveryState.PaidWaiting], which `KioskRoot` turns into one navigation to
 *   Complete when the app is on Idle.
 * - **Retry**: a run that cannot decide (network, server) leaves the pending order untouched. The next `ONLINE` after an
 *   `OFFLINE` triggers another run (`CHECKING` sits between the two, so it is the first `ONLINE`, not a direct
 *   change), and a run that finds nothing pending does nothing, so a trigger is always safe.
 * - **Never during an order**: a trigger waits until no customer order is in progress, so the single pending row and
 *   the E7 of a new order never meet.
 * - **New orders wait for a running recovery** ([blocksNewOrder]): the pending order is one row, so a new order may
 *   only create its own after recovery has finished with the old one; Idle ignores the tap meanwhile. That is at most
 *   a few seconds (E10, E11, E8 with their own retries) and up to 60 s for a payment that is still `PROCESSING`.
 *   Waiting for the next `ONLINE` is not a run, so an offline kiosk is not blocked.
 * - Never runs for a device that is not bound.
 */
@Singleton
class PendingOrderRecoveryCoordinator
    @Inject
    constructor(
        private val recover: RecoverPendingOrder,
        private val activation: ActivationRepository,
        private val connectivity: ConnectivityMonitor,
        private val sessions: OrderSessionHolder,
        @ApplicationScope private val scope: CoroutineScope,
    ) : RecoveryGate {
        private val triggers = Channel<Unit>(Channel.CONFLATED)
        private val mutableState = MutableStateFlow<RecoveryState>(RecoveryState.Settled)
        private val blocking = MutableStateFlow(false)
        private val paid = MutableStateFlow<RecoveredPaidOrder?>(null)

        val state: StateFlow<RecoveryState> = mutableState.asStateFlow()

        override val blocksNewOrder: StateFlow<Boolean> = blocking.asStateFlow()

        /**
         * The order a recovery found paid, for the Complete screen. It stays until [consumeRecoveredPaid] takes it, so
         * the 0.6.0 Complete screen can print from it (the lines of the order are not persisted).
         */
        val recoveredPaid: StateFlow<RecoveredPaidOrder?> = paid.asStateFlow()

        /** Starts the retry triggers for the life of the process. Call once, from `KioskApp`. */
        fun start() {
            scope.launch {
                triggers.receiveAsFlow().collect {
                    // A-11: nothing is recovered under a customer; it waits for Idle.
                    sessions.current.first { it == null }
                    runRecovery()
                }
            }
            scope.launch {
                var wasOffline = false
                connectivity.connectivity.collect { now ->
                    when (now) {
                        Connectivity.OFFLINE -> {
                            wasOffline = true
                        }

                        Connectivity.ONLINE -> {
                            if (wasOffline) triggers.trySend(Unit)
                            wasOffline = false
                        }

                        Connectivity.CHECKING -> {
                            Unit
                        }
                    }
                }
            }
        }

        /** The startup run (technical-design §4.2). Marks the recovery as running at once, then runs in the background. */
        fun recoverAtStartup() {
            if (activation.state.value !is DeviceState.Bound) return
            setState(RecoveryState.Recovering)
            triggers.trySend(Unit)
        }

        /** `KioskRoot` navigated to Complete for the recovered order. */
        fun completeShown() {
            if (mutableState.value == RecoveryState.PaidWaiting) setState(RecoveryState.Settled)
        }

        /** The recovered paid order, once: for the Complete screen. */
        fun consumeRecoveredPaid(): RecoveredPaidOrder? = paid.getAndUpdate { null }

        private suspend fun runRecovery() {
            if (mutableState.value == RecoveryState.PaidWaiting) return
            if (activation.state.value !is DeviceState.Bound) {
                setState(RecoveryState.Settled)
                return
            }
            setState(RecoveryState.Recovering)
            when (val outcome = attempt()) {
                is RecoveryOutcome.Paid -> {
                    paid.value = outcome.order
                    setState(RecoveryState.PaidWaiting)
                }

                is RecoveryOutcome.Deferred -> {
                    KioskLog.w("order_recovery_deferred", "error" to outcome.error)
                    setState(RecoveryState.Settled)
                }

                RecoveryOutcome.Cancelled, RecoveryOutcome.NothingPending -> {
                    setState(RecoveryState.Settled)
                }
            }
        }

        /** Whatever goes wrong is a [RecoveryOutcome.Deferred], so the pending order stays for the next run. */
        @Suppress("TooGenericExceptionCaught") // A failing disk or parse must not take the app scope down.
        private suspend fun attempt(): RecoveryOutcome =
            try {
                recover()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KioskLog.e("order_recovery_failed", "type" to e.javaClass.simpleName)
                RecoveryOutcome.Deferred(DomainError.Unknown)
            }

        private fun setState(next: RecoveryState) {
            mutableState.value = next
            blocking.value = next != RecoveryState.Settled
        }
    }
