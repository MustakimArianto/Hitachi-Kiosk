package com.hitachi.kiosk.core.domain.payment

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.device.CardTerminal
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.TerminalEvent
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.PendingOrder
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.core.testing.fake.FakeCardTerminal
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fake.FakeOrderRepository
import com.hitachi.kiosk.core.testing.fake.FakePaymentGateway
import com.hitachi.kiosk.core.testing.fake.VirtualTimeClock
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The [PaymentStateMachine] with every port faked and the clock bound to the virtual time of [testScope]. The machine
 * runs in the test's background scope, so a test never has to stop its polling.
 */
internal class PaymentHarness(
    private val testScope: TestScope,
    policy: PaymentPolicy = PaymentPolicy(),
    initialConnectivity: Connectivity = Connectivity.ONLINE,
    private val terminalOverride: ((FakeCardTerminal) -> CardTerminal)? = null,
) {
    val clock = VirtualTimeClock(testScope.testScheduler)
    val gateway = FakePaymentGateway()
    val terminal = FakeCardTerminal(resultDelay = 2.seconds)
    val connectivity = FakeConnectivityMonitor(initialConnectivity)
    val ids = FakeIdGenerator()
    val holder = DefaultOrderSessionHolder()
    val orders =
        FakeOrderRepository(
            PendingOrder(
                orderId = SampleData.ORDER_ID,
                orderNumber = "A247",
                paymentId = null,
                method = null,
                idempotencyKey = IdempotencyKey("order-key"),
                totals = SampleData.totals,
                createdAt = SampleData.createdOrder.createdAt,
            ),
        )
    val session: OrderSession = payingSession()

    /** The order of the calls that change something outside the machine, for example `terminal.cancel`, `E11`, `E8`. */
    val log = mutableListOf<String>()

    val machine =
        PaymentStateMachine(
            scope = testScope.backgroundScope,
            clock = clock,
            ids = ids,
            gateway = loggingGateway(),
            terminal = loggingTerminal(),
            orders = loggingOrders(),
            connectivity = connectivity,
            sessions = holder,
            policy = policy,
        )

    val state: PaymentUiState get() = machine.state.value

    val phase: OrderPhase get() = session.state.value.phase

    /** The backend's clock: the true time, whatever the device clock says. */
    val serverNow: Instant get() = clock.now() - clock.skew

    /** Advances virtual time by [duration] and runs everything that became due, including at the boundary. */
    fun pass(duration: Duration) {
        testScope.advanceTimeBy(duration)
        testScope.runCurrent()
    }

    fun start(
        method: PaymentMethod = PaymentMethod.QRIS,
        qrExpirySeconds: Int? = 300,
    ): Boolean = machine.start(SampleData.ORDER_ID, method, qrExpirySeconds).also { testScope.runCurrent() }

    /** A QRIS E9 answer created now by the backend, valid for [life]. [withDate] false leaves out the `Date` header. */
    fun qris(
        life: Duration = 300.seconds,
        withDate: Boolean = true,
        paymentId: String = SampleData.QRIS_PAYMENT_ID,
    ): StartedPayment.Qris {
        val now = serverNow
        return SampleData.qrisPayment.copy(
            paymentId = paymentId,
            createdAt = now,
            expiresAt = now + life,
            serverTime = now.takeIf { withDate },
        )
    }

    /** E10 with [status], for either method. */
    fun status(
        status: PaymentStatus,
        reason: PaymentFailureReason? = null,
    ): PaymentState = SampleData.paymentPending.copy(status = status, failureReason = reason)

    fun approve(reference: String = SampleData.cardPayment.terminalRef): TerminalEvent.Approved =
        TerminalEvent.Approved(reference = reference, tid = "TID 0003 1182", approvalCode = "123456")

    private fun loggingGateway(): PaymentGateway =
        object : PaymentGateway by gateway {
            override suspend fun start(
                orderId: String,
                method: PaymentMethod,
                qrExpirySeconds: Int?,
                key: IdempotencyKey,
            ): Outcome<StartedPayment> {
                log += "E9:$method"
                return gateway.start(orderId, method, qrExpirySeconds, key)
            }

            override suspend fun cancel(
                orderId: String,
                paymentId: String,
                reason: CancelPaymentReason,
                key: IdempotencyKey,
            ): Outcome<PaymentUpdate> {
                log += "E11:$reason"
                return gateway.cancel(orderId, paymentId, reason, key)
            }
        }

    private fun loggingTerminal(): CardTerminal {
        val base = terminalOverride?.invoke(terminal) ?: terminal
        return object : CardTerminal by base {
            override suspend fun cancel() {
                log += "terminal.cancel"
                base.cancel()
            }
        }
    }

    private fun loggingOrders(): OrderRepository =
        object : OrderRepository by orders {
            override suspend fun cancel(
                orderId: String,
                reason: CancelOrderReason,
            ): Outcome<CancelledOrder> {
                log += "E8:$reason"
                return orders.cancel(orderId, reason)
            }

            override suspend fun clearPending() {
                log += "clearPending"
                orders.clearPending()
            }
        }

    private fun payingSession(): OrderSession {
        val session = holder.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
        session.startBuilding()
        session.add(CartItem.ProductItem("c1", emptyList()), 1, Rupiah(38_000), emptyList())
        session.startReviewing()
        session.submit()
        session.orderCreated()
        return session
    }
}
