package com.hitachi.kiosk.core.domain.payment

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.OrderStatus
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.testing.fake.FakeCardTerminal
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Review fixes of milestone 0.5.0 for the payment machine: the EDC is never left armed (FR-018 AC3), "Confirming
 * payment" is bounded (FR-019, TD §5.3), a QR paid at the last second is not shown as expired (FR-017), the machine
 * resets when its order ends elsewhere, the amount on screen is the backend order total (TR-INT-04) and E11 keys
 * follow their reason (TD §5.3).
 */
class PaymentStateMachineHardeningTest {
    private val network = DomainError.Network()
    private val notCancellable = DomainError.Conflict("PAYMENT_NOT_CANCELLABLE")

    // The EDC is cancelled when the backend ends the payment ----------------------------------------------------------

    @Test
    fun `a backend terminal status during a card sale cancels the terminal once (FR-018 AC3)`() =
        runTest {
            val statuses = listOf(PaymentStatus.FAILED, PaymentStatus.EXPIRED, PaymentStatus.CANCELLED)
            for (status in statuses) {
                val h = PaymentHarness(this)
                h.terminal.mode = FakeCardTerminal.Mode.NO_ANSWER
                val reason = PaymentFailureReason.GATEWAY_ERROR.takeIf { status == PaymentStatus.FAILED }
                h.gateway.status.succeed(h.status(status, reason))
                h.start(PaymentMethod.CARD)

                h.pass(2.seconds)

                assertThat(h.state).isInstanceOf(PaymentUiState.Failed::class.java)
                assertThat((h.state as PaymentUiState.Failed).notCharged).isTrue()
                assertThat(h.terminal.cancelCount).isEqualTo(1)
                assertThat(h.log).containsExactly("E9:CARD", "terminal.cancel").inOrder()
            }
        }

    @Test
    fun `a backend terminal status for a QR payment never touches the terminal`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            h.start()

            h.pass(2.seconds)

            assertThat(h.terminal.cancelCount).isEqualTo(0)
        }

    @Test
    fun `an E12 answer that is a terminal status after an approval needs no second terminal cancel`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.relay.succeed(PaymentUpdate(SampleData.CARD_PAYMENT_ID, PaymentStatus.FAILED))
            h.start(PaymentMethod.CARD)

            h.pass(2.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.GENERIC, true))
            assertThat(h.terminal.cancelCount).isEqualTo(0)
        }

    @Test
    fun `E10 PAID while the card sale is still active cancels the terminal so it cannot charge again`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.NO_ANSWER
            h.gateway.status.succeed(SampleData.paymentPaid.copy(method = PaymentMethod.CARD))
            h.start(PaymentMethod.CARD)

            h.pass(3.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
            assertThat(h.terminal.cancelCount).isEqualTo(1)
        }

    // Confirming payment is bounded ------------------------------------------------------------------------------------

    @Test
    fun `an approval whose E12 failed and E10 stays PENDING fails after 60 seconds without not charged (FR-019)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.relay.fail(network)
            h.start(PaymentMethod.CARD)

            h.pass(2.seconds)
            h.pass(59.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.CARD))
            h.pass(1.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.GENERIC, false))
            val polls = h.gateway.status.callCount
            h.pass(20.seconds)
            assertThat(h.gateway.status.callCount).isEqualTo(polls)
            // Try again and Cancel order are offered again.
            assertThat(h.machine.tryAgain()).isTrue()
        }

    @Test
    fun `an approval that E10 keeps reporting as PENDING fails after 60 seconds, also without E12`() =
        runTest {
            val h = PaymentHarness(this, policy = PaymentPolicy(relayTerminalResults = false))
            h.start(PaymentMethod.CARD)

            h.pass(61.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.CARD))
            h.pass(1.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.GENERIC, false))
        }

    @Test
    fun `a QR payment that E10 keeps reporting as PROCESSING fails after 60 seconds without not charged`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.PROCESSING))
            h.start()

            h.pass(2.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.QRIS))
            h.pass(59.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.QRIS))
            h.pass(1.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.GENERIC, false))
            assertThat(h.machine.cancelOrder()).isInstanceOf(PaymentExit::class.java)
        }

    @Test
    fun `PAID just before the 60 seconds are over still wins`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.PROCESSING))
            h.start()
            h.pass(2.seconds)
            h.pass(58.seconds)
            h.gateway.status.succeed(SampleData.paymentPaid)

            h.pass(2.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
        }

    @Test
    fun `a failed E10 while Processing reports the network after the limit`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.PROCESSING))
            h.start()
            h.pass(2.seconds)
            h.gateway.status.fail(network)

            h.pass(70.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.NETWORK, false))
        }

    // A QR paid in its last seconds --------------------------------------------------------------------------------------

    @Test
    fun `PROCESSING near expiry then PAID ends as Paid, never as expired (FR-017)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = 5.seconds))
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending))
            h.gateway.status.enqueue(Outcome.Success(h.status(PaymentStatus.PROCESSING)))
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.start()

            h.pass(5.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.QRIS))
            h.pass(1.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
            assertThat(h.gateway.cancels).isEmpty()
        }

    @Test
    fun `a refused E11 at expiry whose E10 says PROCESSING keeps waiting and PAID wins`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = 5.seconds))
            h.gateway.cancel.fail(notCancellable)
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending))
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending))
            h.gateway.status.enqueue(Outcome.Success(h.status(PaymentStatus.PROCESSING)))
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.start()

            h.pass(5.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.QRIS))
            h.pass(1.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
            assertThat(h.gateway.cancels).hasSize(1)
        }

    // The terminal event at the timeout instant ----------------------------------------------------------------------

    @Test
    fun `an approval that arrived while E10 was running is handled before the terminal timeout fires`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.resultDelay = 89.seconds
            // The poll at 88 s answers only at 91 s; the approval at 89 s waits in the queue, past the 90 s deadline.
            repeat(43) { h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending)) }
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending), delay = 3.seconds)
            h.start(PaymentMethod.CARD)

            h.pass(91.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.CARD))
            assertThat(h.gateway.relays).hasSize(1)
            assertThat(h.gateway.cancels).isEmpty()
        }

    // The amount on screen is the order total ----------------------------------------------------------------------------

    @Test
    fun `a QR for another amount than the order total is cancelled and never shown (TR-INT-04)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris().copy(amount = Rupiah(100_000)))

            h.start()

            assertThat(h.log)
                .containsExactly("E9:QRIS", "E11:SWITCH_METHOD", "E8:PAYMENT_FAILED")
                .inOrder()
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.GENERIC, false))
            assertThat(h.orders.pending()).isNull()
        }

    @Test
    fun `a card payment for another amount than the order total never reaches the terminal`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startCard.succeed(SampleData.cardPayment.copy(amount = Rupiah(1)))

            h.start(PaymentMethod.CARD)

            assertThat(h.terminal.sales).isEmpty()
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.GENERIC, false))
            assertThat(h.log).containsExactly("E9:CARD", "E11:SWITCH_METHOD", "E8:PAYMENT_FAILED").inOrder()
        }

    @Test
    fun `an amount mismatch whose cancel fails keeps the order pending for recovery`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris().copy(amount = Rupiah(100_000)))
            h.gateway.cancel.fail(network)

            h.start()

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.GENERIC, false))
            assertThat(h.log.filter { it.startsWith("E8") }).isEmpty()
            assertThat(h.orders.pending()?.paymentId).isEqualTo(SampleData.QRIS_PAYMENT_ID)
        }

    @Test
    fun `the matching amount is accepted`() =
        runTest {
            val h = PaymentHarness(this)

            h.start()

            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
        }

    // E11 key per reason ---------------------------------------------------------------------------------------------------

    @Test
    fun `E11 gets a new key when the cancel reason changes, and keeps it for the same reason`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = 5.seconds))
            h.gateway.cancel.enqueue(Outcome.Failure(network))
            h.gateway.cancel.enqueue(Outcome.Failure(network))
            h.start()
            h.pass(5.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.EXPIRED, false))

            h.machine.cancelOrder()
            h.machine.cancelOrder()

            val cancels = h.gateway.cancels
            assertThat(cancels.map { it.reason }.map { it.name })
                .containsExactly("EXPIRED_ON_KIOSK", "CUSTOMER_BACK", "CUSTOMER_BACK")
                .inOrder()
            assertThat(cancels[1].key).isNotEqualTo(cancels[0].key)
            assertThat(cancels[2].key).isEqualTo(cancels[1].key)
        }

    @Test
    fun `an E8 answer that is not CANCELLED is not treated as left (FR-019)`() =
        runTest {
            val h = PaymentHarness(this)
            h.orders.cancel.succeed(CancelledOrder(SampleData.ORDER_ID, OrderStatus.AWAITING_PAYMENT))
            h.start()

            val exit = h.machine.backToOrder()

            assertThat(exit).isEqualTo(PaymentExit.NotCancelled(DomainError.Unknown))
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.GENERIC, true))
            assertThat(h.phase).isEqualTo(OrderPhase.PAYING)
        }

    // The machine resets with the order ------------------------------------------------------------------------------------

    @Test
    fun `when the order session ends mid-payment the machine goes Idle and a new payment can start`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)

            h.holder.endOrder()
            h.pass(0.milliseconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Idle)
            val polls = h.gateway.status.callCount
            h.pass(30.seconds)
            assertThat(h.gateway.status.callCount).isEqualTo(polls)
            assertThat(h.start()).isTrue()
        }

    @Test
    fun `ending the order during a card sale cancels the terminal`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.NO_ANSWER
            h.start(PaymentMethod.CARD)

            h.holder.endOrder()
            h.pass(0.milliseconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Idle)
            assertThat(h.terminal.cancelCount).isEqualTo(1)
        }

    @Test
    fun `ending the order resets a failure, but a Paid payment waits for the Complete hand-off`() =
        runTest {
            val failed = PaymentHarness(this)
            failed.gateway.status.succeed(failed.status(PaymentStatus.EXPIRED))
            failed.start()
            failed.pass(2.seconds)
            failed.holder.endOrder()
            failed.pass(0.milliseconds)
            assertThat(failed.state).isEqualTo(PaymentUiState.Idle)

            val paid = PaymentHarness(this)
            paid.gateway.status.succeed(SampleData.paymentPaid)
            paid.start()
            paid.pass(3.seconds)
            paid.holder.endOrder()
            paid.pass(0.milliseconds)
            assertThat(paid.state).isInstanceOf(PaymentUiState.Paid::class.java)
            paid.machine.acknowledgePaid()
            assertThat(paid.state).isEqualTo(PaymentUiState.Idle)
            assertThat(paid.orders.pending()).isNull()
        }

    @Test
    fun `reset forgets a stale failure and does nothing at Idle or Paid`() =
        runTest {
            val h = PaymentHarness(this)
            h.machine.reset()
            assertThat(h.state).isEqualTo(PaymentUiState.Idle)

            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            h.start()
            h.pass(2.seconds)
            assertThat(h.state).isInstanceOf(PaymentUiState.Failed::class.java)
            h.machine.reset()
            assertThat(h.state).isEqualTo(PaymentUiState.Idle)
            // The pending order is not touched: recovery owns it.
            assertThat(h.orders.pending()).isNotNull()
            assertThat(h.log.none { it.startsWith("E8") }).isTrue()
        }
}
