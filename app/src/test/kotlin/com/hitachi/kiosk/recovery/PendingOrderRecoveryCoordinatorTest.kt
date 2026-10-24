package com.hitachi.kiosk.recovery

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.model.OrderStatus
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.PendingOrder
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.RecoverPendingOrder
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.core.testing.fake.FakeActivationRepository
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fake.FakeOrderRepository
import com.hitachi.kiosk.core.testing.fake.FakePaymentGateway
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The wiring of process-death recovery (FR-016, DEV-052, TC-073): the startup run, the retry on the next `ONLINE`,
 * the wait for Idle, the unbound device and the gate that keeps a new order out while a recovery runs.
 */
class PendingOrderRecoveryCoordinatorTest {
    private val gateway = FakePaymentGateway()
    private val orders = FakeOrderRepository(pending())
    private val activation = FakeActivationRepository().apply { emit(SampleData.boundState) }
    private val connectivity = FakeConnectivityMonitor(Connectivity.ONLINE)
    private val sessions = DefaultOrderSessionHolder()

    private fun pending() =
        PendingOrder(
            orderId = SampleData.ORDER_ID,
            orderNumber = "A247",
            paymentId = SampleData.QRIS_PAYMENT_ID,
            method = PaymentMethod.QRIS,
            idempotencyKey = IdempotencyKey("order-key"),
            totals = SampleData.totals,
            createdAt = SampleData.createdOrder.createdAt,
        )

    /** The coordinator as a test application has it: nobody called `start()`, it must run by itself (DEV-052). */
    private fun TestScope.coordinator(
        repository: OrderRepository = orders,
        scope: CoroutineScope = backgroundScope,
    ): PendingOrderRecoveryCoordinator =
        PendingOrderRecoveryCoordinator(
            RecoverPendingOrder(repository, gateway, FakeIdGenerator()),
            activation,
            connectivity,
            sessions,
            scope,
        )

    /** The coordinator as `KioskApp` has it. */
    private fun TestScope.started(repository: OrderRepository = orders): PendingOrderRecoveryCoordinator =
        coordinator(repository).also {
            it.start()
            runCurrent()
        }

    /**
     * Runs everything the coordinator has scheduled, retries and the 60 s wait included. `advanceUntilIdle` would not
     * do: the coordinator runs in the test's background scope, whose tasks it does not wait for.
     */
    private fun TestScope.settle() {
        advanceTimeBy(70_000)
        runCurrent()
    }

    private fun TestScope.cycleConnectivity() {
        connectivity.set(Connectivity.OFFLINE)
        runCurrent()
        connectivity.set(Connectivity.CHECKING)
        runCurrent()
        connectivity.set(Connectivity.ONLINE)
    }

    @Test
    fun `a paid order found at startup waits for Complete and is kept for it (TC-073, FR-016 AC4)`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPaid)
            val coordinator = coordinator()

            coordinator.recoverAtStartup()
            // The gate is closed at once, before the first suspension.
            assertThat(coordinator.blocksNewOrder.value).isTrue()
            runCurrent()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.PaidWaiting)
            assertThat(coordinator.recoveredPaid.value?.orderNumber).isEqualTo("A247")
            assertThat(orders.pending()).isNull()

            coordinator.completeShown()
            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
            assertThat(coordinator.blocksNewOrder.value).isFalse()
            // The Complete screen still gets the order, once.
            assertThat(coordinator.consumeRecoveredPaid()?.orderNumber).isEqualTo("A247")
            assertThat(coordinator.consumeRecoveredPaid()).isNull()
        }

    @Test
    fun `a payment that failed is cancelled and Idle stays Idle`() =
        runTest {
            gateway.status.succeed(SampleData.paymentDeclined)
            val coordinator = started()

            coordinator.recoverAtStartup()
            runCurrent()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
            assertThat(coordinator.blocksNewOrder.value).isFalse()
            assertThat(coordinator.recoveredPaid.value).isNull()
            assertThat(orders.cancels).containsExactly(SampleData.ORDER_ID to CancelOrderReason.APP_RECOVERY)
        }

    @Test
    fun `nothing pending settles at once and calls nothing`() =
        runTest {
            orders.clearPending()
            val coordinator = started()

            coordinator.recoverAtStartup()
            runCurrent()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
            assertThat(gateway.status.callCount).isEqualTo(0)
            assertThat(orders.cancels).isEmpty()
        }

    @Test
    fun `a network failure leaves the order pending and the next ONLINE retries it`() =
        runTest {
            gateway.status.fail(DomainError.Network())
            val coordinator = started()

            coordinator.recoverAtStartup()
            settle()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
            assertThat(coordinator.blocksNewOrder.value).isFalse()
            assertThat(orders.pending()).isNotNull()
            val firstRun = gateway.status.callCount

            // The network is back and the backend answers.
            gateway.status.succeed(SampleData.paymentPaid)
            cycleConnectivity()
            settle()

            assertThat(gateway.status.callCount).isGreaterThan(firstRun)
            assertThat(coordinator.state.value).isEqualTo(RecoveryState.PaidWaiting)
            assertThat(orders.pending()).isNull()
        }

    @Test
    fun `an ONLINE with nothing deferred does nothing, and a trigger with nothing pending is a no-op`() =
        runTest {
            orders.clearPending()
            val coordinator = started()
            connectivity.set(Connectivity.CHECKING)
            connectivity.set(Connectivity.ONLINE)
            settle()
            assertThat(gateway.status.callCount).isEqualTo(0)

            cycleConnectivity()
            settle()

            assertThat(gateway.status.callCount).isEqualTo(0)
            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
        }

    @Test
    fun `a deferred recovery is retried on the next ONLINE even when OFFLINE did not come first`() =
        runTest {
            gateway.status.fail(DomainError.Network())
            val coordinator = coordinator()
            coordinator.recoverAtStartup()
            runCurrent()
            advanceTimeBy(8_000)
            runCurrent()
            assertThat(orders.pending()).isNotNull()
            val firstRun = gateway.status.callCount
            gateway.status.succeed(SampleData.paymentPaid)

            // CHECKING and back to ONLINE, never OFFLINE: a flicker of the link is enough.
            connectivity.set(Connectivity.CHECKING)
            runCurrent()
            connectivity.set(Connectivity.ONLINE)
            runCurrent()

            assertThat(gateway.status.callCount).isGreaterThan(firstRun)
            assertThat(coordinator.state.value).isEqualTo(RecoveryState.PaidWaiting)
        }

    @Test
    fun `a deferral that is not a network failure is retried every 30 s while online, and never in a hot loop`() =
        runTest {
            // PROCESSING for the whole 60 s wait, then a refused cancel: deferred although the network is fine.
            gateway.status.succeed(SampleData.paymentPending.copy(status = PaymentStatus.PROCESSING))
            gateway.cancel.fail(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE"))
            val coordinator = coordinator()
            coordinator.recoverAtStartup()

            advanceTimeBy(5 * 60_000L)
            runCurrent()

            // One run takes about 60 s of polling, so in 5 minutes there are a handful of runs, not hundreds.
            val cancels = gateway.cancels.size
            assertThat(cancels).isAtLeast(3)
            assertThat(cancels).isAtMost(5)
            assertThat(orders.pending()).isNotNull()

            // The payment resolves: the next retry settles it.
            gateway.status.succeed(SampleData.paymentPaid)
            advanceTimeBy(2 * 60_000L)
            runCurrent()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.PaidWaiting)
        }

    @Test
    fun `a deferred recovery does not retry while the kiosk is offline, and does at the next ONLINE`() =
        runTest {
            gateway.status.fail(DomainError.Network())
            val coordinator = coordinator()
            coordinator.recoverAtStartup()
            runCurrent()
            advanceTimeBy(8_000)
            connectivity.set(Connectivity.OFFLINE)
            runCurrent()
            val calls = gateway.status.callCount

            advanceTimeBy(5 * 60_000L)
            runCurrent()
            assertThat(gateway.status.callCount).isEqualTo(calls)

            gateway.status.succeed(SampleData.paymentPaid)
            connectivity.set(Connectivity.ONLINE)
            runCurrent()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.PaidWaiting)
        }

    @Test
    fun `a retry waits for the customer order to end`() =
        runTest {
            gateway.status.fail(DomainError.Network())
            val coordinator = started()
            coordinator.recoverAtStartup()
            settle()
            val deferred = gateway.status.callCount
            gateway.status.succeed(SampleData.paymentPaid)
            sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")

            cycleConnectivity()
            settle()
            assertThat(gateway.status.callCount).isEqualTo(deferred)
            assertThat(orders.pending()).isNotNull()
            // Waiting for the customer is not a run, so it does not block a new order.
            assertThat(coordinator.blocksNewOrder.value).isFalse()

            sessions.endOrder()
            settle()

            assertThat(gateway.status.callCount).isEqualTo(deferred + 1)
            assertThat(coordinator.state.value).isEqualTo(RecoveryState.PaidWaiting)
        }

    @Test
    fun `a kiosk that is not bound is never recovered`() =
        runTest {
            activation.emit(DeviceState.NotActivated)
            val coordinator = started()

            coordinator.recoverAtStartup()
            cycleConnectivity()
            settle()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
            assertThat(coordinator.blocksNewOrder.value).isFalse()
            assertThat(gateway.status.callCount).isEqualTo(0)
            assertThat(orders.pending()).isNotNull()
        }

    @Test
    fun `a device that was logged out before the retry is skipped`() =
        runTest {
            gateway.status.fail(DomainError.Network())
            val coordinator = started()
            coordinator.recoverAtStartup()
            settle()
            val deferred = gateway.status.callCount
            activation.emit(DeviceState.NotActivated)

            cycleConnectivity()
            settle()

            assertThat(gateway.status.callCount).isEqualTo(deferred)
            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
        }

    @Test
    fun `the gate stays closed for the whole 60 s wait of a PROCESSING payment`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending.copy(status = PaymentStatus.PROCESSING))
            val coordinator = started()

            coordinator.recoverAtStartup()
            runCurrent()
            assertThat(coordinator.blocksNewOrder.value).isTrue()

            settle()
            assertThat(coordinator.blocksNewOrder.value).isFalse()
            assertThat(orders.pending()).isNull()
        }

    @Test
    fun `an unexpected failure is a deferred run and does not crash or leave the gate closed`() =
        runTest {
            val exploding =
                object : OrderRepository by orders {
                    override suspend fun pending(): PendingOrder? = error("disk gone")
                }
            val coordinator = started(exploding)

            coordinator.recoverAtStartup()
            runCurrent()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
            assertThat(coordinator.blocksNewOrder.value).isFalse()
        }

    @Test
    fun `a failed cancel keeps the pending order for the next run, which finishes it`() =
        runTest {
            gateway.status.succeed(SampleData.paymentDeclined)
            orders.cancel.fail(DomainError.Network())
            val coordinator = started()

            coordinator.recoverAtStartup()
            settle()
            assertThat(orders.pending()).isNotNull()

            orders.cancel.succeed(CancelledOrder(SampleData.ORDER_ID, OrderStatus.CANCELLED))
            cycleConnectivity()
            settle()

            assertThat(orders.pending()).isNull()
            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
        }

    // Self-sufficient and process-start entry ---------------------------------------------------------------------

    @Test
    fun `without start() the startup run still settles and lifts the gate`() =
        runTest {
            orders.clearPending()
            val coordinator = coordinator()

            coordinator.recoverAtStartup()
            assertThat(coordinator.blocksNewOrder.value).isTrue()
            runCurrent()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
            assertThat(coordinator.blocksNewOrder.value).isFalse()
        }

    @Test
    fun `the gate lifts when the scope of a running recovery is cancelled`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending.copy(status = PaymentStatus.PROCESSING))
            val job = Job()
            val scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
            val coordinator = coordinator(scope = scope)

            coordinator.recoverAtStartup()
            runCurrent()
            assertThat(coordinator.blocksNewOrder.value).isTrue()

            job.cancel()
            runCurrent()

            assertThat(coordinator.blocksNewOrder.value).isFalse()
        }

    @Test
    fun `start() recovers a bound kiosk at process start without Splash (TC-073, FR-016 AC4)`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPaid)
            val coordinator = coordinator()

            coordinator.start()
            runCurrent()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.PaidWaiting)
            assertThat(coordinator.recoveredPaid.value?.orderNumber).isEqualTo("A247")
            assertThat(orders.pending()).isNull()
        }

    @Test
    fun `start() loads the stored device state itself, then Splash asking again changes nothing`() =
        runTest {
            gateway.status.fail(DomainError.Network())
            activation.stored = SampleData.boundState
            val coordinator = coordinator()

            coordinator.start()
            runCurrent()
            assertThat(activation.loadCount).isEqualTo(1)
            val firstRun = gateway.status.callCount
            assertThat(firstRun).isGreaterThan(0)

            // StartupRouter's request in the same process is the same startup run.
            coordinator.recoverAtStartup()
            runCurrent()

            assertThat(gateway.status.callCount).isEqualTo(firstRun)
        }

    @Test
    fun `start() does nothing for a kiosk that is not bound`() =
        runTest {
            activation.stored = DeviceState.NotActivated
            val coordinator = coordinator()

            coordinator.start()
            runCurrent()

            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
            assertThat(gateway.status.callCount).isEqualTo(0)
            assertThat(orders.pending()).isNotNull()
        }
}
