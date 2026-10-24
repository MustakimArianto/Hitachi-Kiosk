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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    /** Nothing is being recovered: no pending order, a finished one, or one waiting for the next trigger. */
    data object Settled : RecoveryState

    /** A run is asking the backend about the pending order. */
    data object Recovering : RecoveryState

    /** The order was paid: the app goes to Complete as soon as it is on Idle (FR-016 AC4). */
    data object PaidWaiting : RecoveryState
}

/**
 * Runs [RecoverPendingOrder] at startup and again until the pending order is settled (FR-016 "Process death",
 * technical-design §8.6, DEV-052), in the style of `CatalogSyncCoordinator`.
 *
 * - **Process start**: [start] (called once by `KioskApp`) loads the stored device state itself and, when the kiosk is
 *   bound, begins the recovery. It does not wait for Splash: after a process death Android may restore the saved back
 *   stack straight to a screen after Splash, so `StartupRouter` alone would never run (TC-073, FR-016 AC4).
 *   `StartupRouter` still calls [recoverAtStartup]; a second request in the same process does nothing.
 * - **Self-sufficient**: [recoverAtStartup] starts the consumers of its own run in the application scope if they are
 *   not running yet, so the gate never closes without a run to open it again (the instrumented tests run under a
 *   test application that never calls [start]).
 * - **Retry**: a run that cannot decide ([RecoveryOutcome.Deferred]: network, server, a payment still `PROCESSING`)
 *   leaves the pending order untouched and sets `retryNeeded`. While it is set, every `ONLINE` (whether or not
 *   `OFFLINE` came first, `CHECKING` sits between) and a timer of [RETRY_INTERVAL] while the kiosk stays online
 *   trigger another run. One timer per run, so it cannot spin. A run that finds nothing pending does nothing, so a
 *   trigger is always safe.
 * - **Never during an order**: a trigger waits until no customer order is in progress, so the single pending row and
 *   the E7 of a new order never meet. A row that already has a payment is never replaced by a new order either
 *   (`DefaultOrderRepository.create` refuses), so a deferred recovery shows as the customer's "couldn't reach" on Pay
 *   and Idle is not blocked.
 * - **New orders wait for a running recovery** ([blocksNewOrder]): the pending order is one row, so a new order may
 *   only create its own after a run has finished with the old one; Idle ignores the tap meanwhile. That is at most
 *   a few seconds (E10, E11, E8 with their own retries) and up to 60 s for a payment that is still `PROCESSING`.
 *   Waiting for the next trigger is not a run, so it never blocks. The gate always opens when a run ends, even when
 *   the run throws or is cancelled.
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
        private val consumersStarted = AtomicBoolean(false)
        private val startupRequested = AtomicBoolean(false)

        @Volatile
        private var retryNeeded = false

        @Volatile
        private var retryTimer: Job? = null

        val state: StateFlow<RecoveryState> = mutableState.asStateFlow()

        override val blocksNewOrder: StateFlow<Boolean> = blocking.asStateFlow()

        /**
         * The order a recovery found paid, for the Complete screen. It stays until [consumeRecoveredPaid] takes it, so
         * the 0.6.0 Complete screen can print from it (the lines of the order are not persisted).
         */
        val recoveredPaid: StateFlow<RecoveredPaidOrder?> = paid.asStateFlow()

        /**
         * The entry that runs on every process start: starts the triggers for the life of the process, loads the
         * stored device state and begins the recovery for a bound kiosk. Call once, from `KioskApp`.
         */
        fun start() {
            ensureConsumers()
            scope.launch {
                if (activation.load() is DeviceState.Bound) recoverAtStartup()
            }
        }

        /**
         * The startup run (technical-design §4.2). Marks the recovery as running at once, then runs in the background.
         * Only the first request of a process counts, so `StartupRouter` and [start] can both ask.
         */
        fun recoverAtStartup() {
            if (activation.state.value !is DeviceState.Bound) return
            if (!startupRequested.compareAndSet(false, true)) return
            ensureConsumers()
            setState(RecoveryState.Recovering)
            triggers.trySend(Unit)
        }

        /** `KioskRoot` navigated to Complete for the recovered order. */
        fun completeShown() {
            if (mutableState.value == RecoveryState.PaidWaiting) setState(RecoveryState.Settled)
        }

        /** The recovered paid order, once: for the Complete screen. */
        fun consumeRecoveredPaid(): RecoveredPaidOrder? = paid.getAndUpdate { null }

        /** Starts the run consumer and the `ONLINE` watcher, once. */
        private fun ensureConsumers() {
            if (!consumersStarted.compareAndSet(false, true)) return
            scope.launch {
                triggers.receiveAsFlow().collect {
                    // A-11: nothing is recovered under a customer; it waits for Idle.
                    sessions.current.first { it == null }
                    runRecovery()
                }
            }
            scope.launch {
                connectivity.connectivity.collect { now ->
                    if (now == Connectivity.ONLINE && retryNeeded) triggers.trySend(Unit)
                }
            }
        }

        private suspend fun runRecovery() {
            if (mutableState.value == RecoveryState.PaidWaiting) return
            if (activation.state.value !is DeviceState.Bound) {
                retryNeeded = false
                setState(RecoveryState.Settled)
                return
            }
            setState(RecoveryState.Recovering)
            try {
                finish(attempt())
            } finally {
                // A throwing or cancelled run must not leave new orders blocked.
                if (mutableState.value == RecoveryState.Recovering) setState(RecoveryState.Settled)
            }
        }

        private fun finish(outcome: RecoveryOutcome) {
            when (outcome) {
                is RecoveryOutcome.Paid -> {
                    retryNeeded = false
                    paid.value = outcome.order
                    setState(RecoveryState.PaidWaiting)
                }

                is RecoveryOutcome.Deferred -> {
                    KioskLog.w("order_recovery_deferred", "error" to outcome.error)
                    scheduleRetry()
                    setState(RecoveryState.Settled)
                }

                RecoveryOutcome.Cancelled, RecoveryOutcome.NothingPending -> {
                    retryNeeded = false
                    setState(RecoveryState.Settled)
                }
            }
        }

        /** The order stays pending: the next `ONLINE` retries it, and so does a timer while the kiosk stays online. */
        private fun scheduleRetry() {
            retryNeeded = true
            retryTimer?.cancel()
            retryTimer =
                scope.launch {
                    delay(RETRY_INTERVAL)
                    if (connectivity.connectivity.value == Connectivity.ONLINE) triggers.trySend(Unit)
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

        private companion object {
            /** The pause between retries of a deferred recovery while the kiosk is online. */
            val RETRY_INTERVAL: Duration = 30.seconds
        }
    }
