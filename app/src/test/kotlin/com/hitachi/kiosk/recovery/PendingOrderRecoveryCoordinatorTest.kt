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

    private fun TestScope.started(repository: OrderRepository = orders): PendingOrderRecoveryCoordinator =
        PendingOrderRecoveryCoordinator(
            RecoverPendingOrder(repository, gateway, FakeIdGenerator()),
            activation,
            connectivity,
            sessions,
            backgroundScope,
        ).also {
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
            val coordinator = started()

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
    fun `ONLINE without a preceding OFFLINE does not run, and a trigger with nothing pending is a no-op`() =
        runTest {
            val coordinator = started()
            connectivity.set(Connectivity.CHECKING)
            connectivity.set(Connectivity.ONLINE)
            settle()
            assertThat(gateway.status.callCount).isEqualTo(0)

            orders.clearPending()
            cycleConnectivity()
            settle()

            assertThat(gateway.status.callCount).isEqualTo(0)
            assertThat(coordinator.state.value).isEqualTo(RecoveryState.Settled)
        }

    @Test
    fun `a retry waits for the customer order to end`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPaid)
            val coordinator = started()
            sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")

            cycleConnectivity()
            settle()
            assertThat(gateway.status.callCount).isEqualTo(0)
            assertThat(orders.pending()).isNotNull()

            sessions.endOrder()
            settle()

            assertThat(gateway.status.callCount).isEqualTo(1)
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
            gateway.status.succeed(SampleData.paymentPaid)
            val coordinator = started()
            activation.emit(DeviceState.NotActivated)

            cycleConnectivity()
            settle()

            assertThat(gateway.status.callCount).isEqualTo(0)
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
            orders.cancel.enqueue(Outcome.Failure(DomainError.Network()))
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
}
