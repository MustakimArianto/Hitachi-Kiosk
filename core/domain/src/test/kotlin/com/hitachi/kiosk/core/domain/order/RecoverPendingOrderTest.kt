package com.hitachi.kiosk.core.domain.order

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.OrderStatus
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.PendingOrder
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fake.FakeOrderRepository
import com.hitachi.kiosk.core.testing.fake.FakePaymentGateway
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Process-death recovery (FR-016 "Process death", AC4; architecture §5.3, technical-design §8.6; DEV-052, TC-073):
 * the whole table of stored state × what the backend says, with virtual time for the `PROCESSING` wait and the
 * retries.
 */
class RecoverPendingOrderTest {
    private val gateway = FakePaymentGateway()
    private val orders = FakeOrderRepository(pending(paymentId = SampleData.QRIS_PAYMENT_ID))
    private val recover = RecoverPendingOrder(orders, gateway, FakeIdGenerator())

    private fun pending(paymentId: String?) =
        PendingOrder(
            orderId = SampleData.ORDER_ID,
            orderNumber = "A247",
            paymentId = paymentId,
            method = paymentId?.let { PaymentMethod.QRIS },
            idempotencyKey = IdempotencyKey("order-key"),
            totals = SampleData.totals,
            createdAt = SampleData.createdOrder.createdAt,
        )

    private fun payment(status: PaymentStatus): Outcome<PaymentState> =
        Outcome.Success(SampleData.paymentPending.copy(status = status))

    private val paid: Outcome<PaymentState> = Outcome.Success(SampleData.paymentPaid)

    private val network = Outcome.Failure(DomainError.Network())

    private val eightCancel = SampleData.ORDER_ID to CancelOrderReason.APP_RECOVERY

    // Nothing, order without payment -----------------------------------------------------------------------------------

    @Test
    fun `nothing pending does nothing`() =
        runTest {
            orders.clearPending()

            assertThat(recover()).isEqualTo(RecoveryOutcome.NothingPending)

            assertThat(orders.cancels).isEmpty()
            assertThat(gateway.status.callCount).isEqualTo(0)
            assertThat(gateway.cancels).isEmpty()
        }

    @Test
    fun `an order without a payment is cancelled with E8 APP_RECOVERY and forgotten`() =
        runTest {
            orders.clearPending()
            val bare = FakeOrderRepository(pending(paymentId = null))

            assertThat(RecoverPendingOrder(bare, gateway, FakeIdGenerator())()).isEqualTo(RecoveryOutcome.Cancelled)

            assertThat(bare.cancels).containsExactly(eightCancel)
            assertThat(bare.pending()).isNull()
            assertThat(gateway.status.callCount).isEqualTo(0)
            assertThat(gateway.cancels).isEmpty()
        }

    // E10 answers a final status ----------------------------------------------------------------------------------------

    @Test
    fun `PAID goes to Complete with the backend order number and clears the pending order (TC-073, FR-016 AC4)`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPaid)

            val outcome = recover()

            assertThat(outcome).isInstanceOf(RecoveryOutcome.Paid::class.java)
            val order = (outcome as RecoveryOutcome.Paid).order
            assertThat(order.orderId).isEqualTo(SampleData.ORDER_ID)
            assertThat(order.orderNumber).isEqualTo("A247")
            assertThat(order.paymentId).isEqualTo(SampleData.QRIS_PAYMENT_ID)
            assertThat(order.method).isEqualTo(PaymentMethod.QRIS)
            assertThat(order.totals).isEqualTo(SampleData.totals)
            assertThat(order.paidAt).isEqualTo(SampleData.paymentPaid.paidAt)
            assertThat(orders.pending()).isNull()
            assertThat(orders.cancels).isEmpty()
            assertThat(gateway.cancels).isEmpty()
        }

    @Test
    fun `PAID without an order number in E10 falls back to the saved one`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPaid.copy(paidOrder = null))

            val order = (recover() as RecoveryOutcome.Paid).order

            assertThat(order.orderNumber).isEqualTo("A247")
        }

    @Test
    fun `a final non-paid status cancels the order with E8 and forgets it, with no E11`() =
        runTest {
            listOf(PaymentStatus.FAILED, PaymentStatus.EXPIRED, PaymentStatus.CANCELLED).forEach { status ->
                val repo = FakeOrderRepository(pending(SampleData.QRIS_PAYMENT_ID))
                gateway.status.succeed(SampleData.paymentPending.copy(status = status))

                val outcome = RecoverPendingOrder(repo, gateway, FakeIdGenerator())()

                assertThat(outcome).isEqualTo(RecoveryOutcome.Cancelled)
                assertThat(repo.cancels).containsExactly(eightCancel)
                assertThat(repo.pending()).isNull()
            }
            assertThat(gateway.cancels).isEmpty()
        }

    // PENDING: Checkout cannot be resumed, so it is cancelled ------------------------------------------------------------

    @Test
    fun `PENDING cancels the payment with E11 EXPIRED_ON_KIOSK, then the order with E8`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending)

            assertThat(recover()).isEqualTo(RecoveryOutcome.Cancelled)

            assertThat(gateway.cancels.map { it.reason }).containsExactly(CancelPaymentReason.EXPIRED_ON_KIOSK)
            assertThat(gateway.cancels.single().paymentId).isEqualTo(SampleData.QRIS_PAYMENT_ID)
            assertThat(orders.cancels).containsExactly(eightCancel)
            assertThat(orders.pending()).isNull()
            // One E10 read, no polling.
            assertThat(gateway.status.callCount).isEqualTo(1)
        }

    @Test
    fun `an E11 that answers PAID, or a 409, re-reads E10 and PAID wins (A-15)`() =
        runTest {
            val answers: List<Outcome<PaymentUpdate>> =
                listOf(
                    Outcome.Failure(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE")),
                    Outcome.Success(PaymentUpdate(SampleData.QRIS_PAYMENT_ID, PaymentStatus.PAID)),
                )
            answers.forEach { answer ->
                val repo = FakeOrderRepository(pending(SampleData.QRIS_PAYMENT_ID))
                gateway.status.clearQueue()
                gateway.status.enqueue(payment(PaymentStatus.PENDING))
                gateway.status.enqueue(paid)
                gateway.cancel.always(answer)

                val outcome = RecoverPendingOrder(repo, gateway, FakeIdGenerator())()

                assertThat(outcome).isInstanceOf(RecoveryOutcome.Paid::class.java)
                assertThat(repo.cancels).isEmpty()
                assertThat(repo.pending()).isNull()
            }
        }

    @Test
    fun `a 409 on E11 and a non-paid final status on the re-read cancels the order`() =
        runTest {
            gateway.status.enqueue(payment(PaymentStatus.PENDING))
            gateway.status.enqueue(payment(PaymentStatus.EXPIRED))
            gateway.cancel.fail(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE"))

            assertThat(recover()).isEqualTo(RecoveryOutcome.Cancelled)

            assertThat(orders.cancels).containsExactly(eightCancel)
        }

    @Test
    fun `a 409 on E11 and a payment that is still open leaves the order pending`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending)
            gateway.cancel.fail(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE"))

            val outcome = recover()

            assertThat(outcome).isEqualTo(RecoveryOutcome.Deferred(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE")))
            assertThat(orders.cancels).isEmpty()
            assertThat(orders.pending()).isNotNull()
        }

    @Test
    fun `E11 failing on the network leaves the order pending and E8 is not called`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending)
            gateway.cancel.fail(DomainError.Network())

            assertThat(recover()).isEqualTo(RecoveryOutcome.Deferred(DomainError.Network()))

            assertThat(orders.cancels).isEmpty()
            assertThat(orders.pending()!!.orderId).isEqualTo(SampleData.ORDER_ID)
        }

    // PROCESSING: money may be in flight, so poll before anything else -------------------------------------------------

    @Test
    fun `PROCESSING is polled every 2 s and PAID ends it without any cancel`() =
        runTest {
            gateway.status.enqueue(payment(PaymentStatus.PROCESSING))
            gateway.status.enqueue(payment(PaymentStatus.PROCESSING))
            gateway.status.enqueue(payment(PaymentStatus.PROCESSING))
            gateway.status.enqueue(paid)

            val outcome = recover()

            assertThat(outcome).isInstanceOf(RecoveryOutcome.Paid::class.java)
            assertThat(currentTime).isEqualTo(6_000)
            assertThat(gateway.status.callCount).isEqualTo(4)
            assertThat(gateway.cancels).isEmpty()
            assertThat(orders.cancels).isEmpty()
            assertThat(orders.pending()).isNull()
        }

    @Test
    fun `PROCESSING that ends in a non-paid final status cancels the order only`() =
        runTest {
            gateway.status.enqueue(payment(PaymentStatus.PROCESSING))
            gateway.status.enqueue(payment(PaymentStatus.PROCESSING))
            gateway.status.enqueue(payment(PaymentStatus.FAILED))

            assertThat(recover()).isEqualTo(RecoveryOutcome.Cancelled)

            assertThat(currentTime).isEqualTo(4_000)
            assertThat(gateway.cancels).isEmpty()
            assertThat(orders.cancels).containsExactly(eightCancel)
        }

    @Test
    fun `PROCESSING that falls back to PENDING is cancelled like PENDING`() =
        runTest {
            gateway.status.enqueue(payment(PaymentStatus.PROCESSING))
            gateway.status.enqueue(payment(PaymentStatus.PENDING))

            assertThat(recover()).isEqualTo(RecoveryOutcome.Cancelled)

            assertThat(gateway.cancels.map { it.reason }).containsExactly(CancelPaymentReason.EXPIRED_ON_KIOSK)
            assertThat(orders.cancels).containsExactly(eightCancel)
        }

    @Test
    fun `PROCESSING for the whole 60 s is then cancelled like PENDING`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending.copy(status = PaymentStatus.PROCESSING))

            assertThat(recover()).isEqualTo(RecoveryOutcome.Cancelled)

            assertThat(currentTime).isEqualTo(60_000)
            // The first read plus one every 2 s for 60 s.
            assertThat(gateway.status.callCount).isEqualTo(31)
            assertThat(gateway.cancels.map { it.reason }).containsExactly(CancelPaymentReason.EXPIRED_ON_KIOSK)
            assertThat(orders.cancels).containsExactly(eightCancel)
            assertThat(orders.pending()).isNull()
        }

    @Test
    fun `PROCESSING for 60 s that refuses the cancel and is paid at the last moment goes to Complete`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending.copy(status = PaymentStatus.PROCESSING))
            gateway.cancel.fail(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE"))
            // The 31 reads of the wait answer PROCESSING; the re-read after the refused E11 answers PAID.
            repeat(31) { gateway.status.enqueue(payment(PaymentStatus.PROCESSING)) }
            gateway.status.enqueue(paid)

            val outcome = recover()

            assertThat(outcome).isInstanceOf(RecoveryOutcome.Paid::class.java)
            assertThat(orders.cancels).isEmpty()
            assertThat(orders.pending()).isNull()
        }

    @Test
    fun `PROCESSING for 60 s that cannot be cancelled stays pending`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending.copy(status = PaymentStatus.PROCESSING))
            gateway.cancel.fail(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE"))

            val outcome = recover()

            assertThat(outcome).isInstanceOf(RecoveryOutcome.Deferred::class.java)
            assertThat(orders.cancels).isEmpty()
            assertThat(orders.pending()).isNotNull()
        }

    // Network failures never drop the order ---------------------------------------------------------------------------

    @Test
    fun `E10 failing on the network is retried 1 s, 2 s, 4 s and then leaves the order pending`() =
        runTest {
            gateway.status.fail(DomainError.Network())

            assertThat(recover()).isEqualTo(RecoveryOutcome.Deferred(DomainError.Network()))

            assertThat(gateway.status.callCount).isEqualTo(4)
            assertThat(currentTime).isEqualTo(7_000)
            assertThat(orders.cancels).isEmpty()
            assertThat(gateway.cancels).isEmpty()
            assertThat(orders.pending()).isNotNull()
        }

    @Test
    fun `E10 failing once on the network is retried and the recovery goes on`() =
        runTest {
            gateway.status.enqueue(network)
            gateway.status.enqueue(paid)

            assertThat(recover()).isInstanceOf(RecoveryOutcome.Paid::class.java)
            assertThat(currentTime).isEqualTo(1_000)
        }

    @Test
    fun `the network dropping while PROCESSING is polled leaves the order pending`() =
        runTest {
            gateway.status.enqueue(payment(PaymentStatus.PROCESSING))
            gateway.status.fail(DomainError.Network())

            assertThat(recover()).isEqualTo(RecoveryOutcome.Deferred(DomainError.Network()))

            assertThat(orders.cancels).isEmpty()
            assertThat(gateway.cancels).isEmpty()
            assertThat(orders.pending()).isNotNull()
        }

    @Test
    fun `a failed E8 leaves the order pending, and the next run finishes the job`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending.copy(status = PaymentStatus.EXPIRED))
            orders.cancel.enqueue(network)

            assertThat(recover()).isEqualTo(RecoveryOutcome.Deferred(DomainError.Network()))
            assertThat(orders.pending()).isNotNull()

            assertThat(recover()).isEqualTo(RecoveryOutcome.Cancelled)
            assertThat(orders.cancels).containsExactly(eightCancel, eightCancel)
            assertThat(orders.pending()).isNull()
        }

    @Test
    fun `an order without a payment whose E8 fails stays pending for the next run`() =
        runTest {
            val bare = FakeOrderRepository(pending(paymentId = null))
            bare.cancel.enqueue(Outcome.Failure(DomainError.Server("SERVER_ERROR", 503)))
            val again = RecoverPendingOrder(bare, gateway, FakeIdGenerator())

            assertThat(again()).isInstanceOf(RecoveryOutcome.Deferred::class.java)
            assertThat(bare.pending()).isNotNull()
            assertThat(again()).isEqualTo(RecoveryOutcome.Cancelled)
            assertThat(bare.pending()).isNull()
        }

    @Test
    fun `E8 answering ORDER_ALREADY_PAID means the order was paid, so it goes to Complete`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending.copy(status = PaymentStatus.CANCELLED))
            orders.cancel.always(Outcome.Failure(DomainError.Conflict("ORDER_ALREADY_PAID")))

            val outcome = recover()

            assertThat(outcome).isInstanceOf(RecoveryOutcome.Paid::class.java)
            assertThat((outcome as RecoveryOutcome.Paid).order.orderNumber).isEqualTo("A247")
            assertThat(orders.pending()).isNull()
        }

    @Test
    fun `E8 answering a PAID order goes to Complete as well`() =
        runTest {
            val bare = FakeOrderRepository(pending(paymentId = null))
            bare.cancel.succeed(CancelledOrder(SampleData.ORDER_ID, OrderStatus.PAID))

            val outcome = RecoverPendingOrder(bare, gateway, FakeIdGenerator())()

            assertThat(outcome).isInstanceOf(RecoveryOutcome.Paid::class.java)
            assertThat(bare.pending()).isNull()
        }

    // Idempotence -----------------------------------------------------------------------------------------------------

    @Test
    fun `a second run after a finished run finds nothing and calls nothing`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPaid)
            assertThat(recover()).isInstanceOf(RecoveryOutcome.Paid::class.java)
            val statusCalls = gateway.status.callCount

            assertThat(recover()).isEqualTo(RecoveryOutcome.NothingPending)

            assertThat(gateway.status.callCount).isEqualTo(statusCalls)
            assertThat(orders.cancels).isEmpty()
        }

    @Test
    fun `a second run after a cancel finds nothing`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending)
            assertThat(recover()).isEqualTo(RecoveryOutcome.Cancelled)

            assertThat(recover()).isEqualTo(RecoveryOutcome.NothingPending)

            assertThat(orders.cancels).hasSize(1)
            assertThat(gateway.cancels).hasSize(1)
        }

    @Test
    fun `each run sends its own E11 key`() =
        runTest {
            gateway.status.succeed(SampleData.paymentPending)
            gateway.cancel.enqueue(Outcome.Failure(DomainError.Network()))

            recover()
            recover()

            assertThat(gateway.cancels.map { it.key }).containsExactly(IdempotencyKey("key-1"), IdempotencyKey("key-2"))
        }
}
