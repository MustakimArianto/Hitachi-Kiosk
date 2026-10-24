package com.hitachi.kiosk.core.domain.payment

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The customer's buttons on Checkout: "Back to order", "Cancel order", "Try again", "Pay by X instead" and the Complete
 * hand-off (FR-016, FR-019, BR-08, TC-084, TC-086, TC-178, TC-181).
 */
class PaymentStateMachineOperationsTest {
    private val network = DomainError.Network()
    private val notCancellable = DomainError.Conflict("PAYMENT_NOT_CANCELLABLE")
    private val orderAlreadyPaid = DomainError.Conflict("ORDER_ALREADY_PAID")

    // Back to order ---------------------------------------------------------------------------------------------------

    @Test
    fun `Back to order sends E11 then E8, returns the session to review and goes Idle (TC-086)`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()

            val exit = h.machine.backToOrder()

            assertThat(exit).isEqualTo(PaymentExit.Left)
            assertThat(h.log).containsExactly("E9:QRIS", "E11:CUSTOMER_BACK", "E8:CUSTOMER_BACK").inOrder()
            assertThat(h.state).isEqualTo(PaymentUiState.Idle)
            assertThat(h.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(h.orders.pending()).isNull()
            val polls = h.gateway.status.callCount
            h.pass(30.seconds)
            assertThat(h.gateway.status.callCount).isEqualTo(polls)
        }

    @Test
    fun `after Back to order the next Pay starts a payment again, with a new key`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()
            h.machine.backToOrder()

            assertThat(h.start()).isTrue()

            assertThat(h.gateway.startKeys.distinct()).hasSize(2)
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
        }

    @Test
    fun `Back to order that meets a 409 and PAID ends as Paid, and E8 is never sent`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.cancel.fail(notCancellable)
            h.start()
            h.gateway.status.succeed(SampleData.paymentPaid)

            val exit = h.machine.backToOrder()

            assertThat(exit).isEqualTo(PaymentExit.AlreadyPaid)
            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
            assertThat(h.phase).isEqualTo(OrderPhase.PAID)
            assertThat(h.log.filter { it.startsWith("E8") }).isEmpty()
        }

    @Test
    fun `a payment that was paid while Back to order was in flight wins the race`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()
            h.gateway.cancel.fail(notCancellable, delay = 3.seconds)
            h.gateway.status.succeed(SampleData.paymentPaid)

            val exit = h.machine.backToOrder()

            assertThat(exit).isEqualTo(PaymentExit.AlreadyPaid)
            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
        }

    @Test
    fun `Back to order with an E11 that cannot be answered shows the failure and keeps the order pending`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.cancel.fail(network)
            h.start()

            val exit = h.machine.backToOrder()

            assertThat(exit).isEqualTo(PaymentExit.NotCancelled(network))
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.NETWORK, false))
            assertThat(h.log.filter { it.startsWith("E8") }).isEmpty()
            assertThat(h.orders.pending()).isNotNull()
            assertThat(h.phase).isEqualTo(OrderPhase.PAYING)
        }

    @Test
    fun `Back to order with an E8 that cannot be answered keeps not charged, E11 was accepted`() =
        runTest {
            val h = PaymentHarness(this)
            h.orders.cancel.fail(network)
            h.start()

            val exit = h.machine.backToOrder()

            assertThat(exit).isEqualTo(PaymentExit.NotCancelled(network))
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.NETWORK, true))
            assertThat(h.phase).isEqualTo(OrderPhase.PAYING)
        }

    @Test
    fun `E8 answering ORDER_ALREADY_PAID reads E10 and ends as Paid`() =
        runTest {
            val h = PaymentHarness(this)
            h.orders.cancel.fail(orderAlreadyPaid)
            h.start()
            h.gateway.status.succeed(SampleData.paymentPaid)

            val exit = h.machine.backToOrder()

            assertThat(exit).isEqualTo(PaymentExit.AlreadyPaid)
            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
        }

    @Test
    fun `E8 answering ORDER_ALREADY_PAID while E10 is silent shows the failure`() =
        runTest {
            val h = PaymentHarness(this)
            h.orders.cancel.fail(orderAlreadyPaid)
            h.start()
            h.gateway.status.fail(network)

            val exit = h.machine.backToOrder()

            assertThat(exit).isEqualTo(PaymentExit.NotCancelled(orderAlreadyPaid))
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.GENERIC, true))
        }

    @Test
    fun `Back to order is only allowed while waiting or after a failure`() =
        runTest {
            // Idle
            val idle = PaymentHarness(this)
            assertThat(idle.machine.backToOrder()).isEqualTo(PaymentExit.NotAllowed)

            // Creating
            val creating = PaymentHarness(this)
            creating.gateway.startQris.succeed(creating.qris(), delay = 5.seconds)
            creating.start()
            assertThat(creating.machine.backToOrder()).isEqualTo(PaymentExit.NotAllowed)
            assertThat(creating.state).isEqualTo(PaymentUiState.Creating(PaymentMethod.QRIS))

            // Checking
            val checking = PaymentHarness(this)
            checking.gateway.status.fail(network)
            checking.start()
            checking.pass(2.seconds)
            assertThat(checking.machine.backToOrder()).isEqualTo(PaymentExit.NotAllowed)
            assertThat(checking.state).isEqualTo(PaymentUiState.Checking(PaymentMethod.QRIS))

            // Processing
            val processing = PaymentHarness(this)
            processing.gateway.status.succeed(processing.status(PaymentStatus.PROCESSING))
            processing.start()
            processing.pass(2.seconds)
            assertThat(processing.machine.backToOrder()).isEqualTo(PaymentExit.NotAllowed)

            // Paid
            val paid = PaymentHarness(this)
            paid.gateway.status.succeed(SampleData.paymentPaid)
            paid.start()
            paid.pass(3.seconds)
            assertThat(paid.machine.backToOrder()).isEqualTo(PaymentExit.NotAllowed)

            listOf(idle, creating, checking, processing, paid).forEach {
                assertThat(it.log.none { e -> e.startsWith("E8") }).isTrue()
            }
        }

    @Test
    fun `a refused operation leaves the machine usable`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(), delay = 1.seconds)
            h.start()
            assertThat(h.machine.backToOrder()).isEqualTo(PaymentExit.NotAllowed)
            h.pass(1.seconds)

            assertThat(h.machine.backToOrder()).isEqualTo(PaymentExit.Left)
        }

    @Test
    fun `Back to order finishes even when the screen that asked for it goes away (application scope)`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()
            h.gateway.cancel.succeed(SampleData.paymentCancelled, delay = 2.seconds)

            val screen = launch(start = CoroutineStart.UNDISPATCHED) { h.machine.backToOrder() }
            h.pass(1.seconds)
            screen.cancel()
            h.pass(5.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Idle)
            assertThat(h.log).contains("E8:CUSTOMER_BACK")
            assertThat(h.phase).isEqualTo(OrderPhase.REVIEWING)
        }

    @Test
    fun `two Back to order taps cancel once`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()
            h.gateway.cancel.succeed(SampleData.paymentCancelled, delay = 1.seconds)

            val first = async { h.machine.backToOrder() }
            val second = async { h.machine.backToOrder() }

            assertThat(listOf(first.await(), second.await()))
                .containsExactly(PaymentExit.Left, PaymentExit.NotAllowed)
            assertThat(h.log.count { it.startsWith("E8") }).isEqualTo(1)
        }

    // Cancel order ----------------------------------------------------------------------------------------------------

    @Test
    fun `Cancel order after a confirmed failure sends E8 PAYMENT_FAILED only and clears the pending order`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            h.start()
            h.pass(2.seconds)

            val exit = h.machine.cancelOrder()

            assertThat(exit).isEqualTo(PaymentExit.Left)
            assertThat(h.log).containsExactly("E9:QRIS", "E8:PAYMENT_FAILED").inOrder()
            assertThat(h.state).isEqualTo(PaymentUiState.Idle)
            assertThat(h.orders.pending()).isNull()
            // The caller ends the order; the machine does not touch the session.
            assertThat(h.phase).isEqualTo(OrderPhase.PAYING)
        }

    @Test
    fun `Cancel order after an unconfirmed failure cancels the payment first`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.fail(network)
            h.start()
            h.pass(62.seconds)
            assertThat(h.state).isInstanceOf(PaymentUiState.Failed::class.java)

            val exit = h.machine.cancelOrder()

            assertThat(exit).isEqualTo(PaymentExit.Left)
            assertThat(h.log.takeLast(2)).containsExactly("E11:CUSTOMER_BACK", "E8:PAYMENT_FAILED").inOrder()
        }

    @Test
    fun `Cancel order that finds the payment paid ends as Paid`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.fail(network)
            h.start()
            h.pass(62.seconds)
            h.gateway.cancel.fail(notCancellable)
            h.gateway.status.succeed(SampleData.paymentPaid)

            val exit = h.machine.cancelOrder()

            assertThat(exit).isEqualTo(PaymentExit.AlreadyPaid)
            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
            assertThat(h.phase).isEqualTo(OrderPhase.PAID)
        }

    @Test
    fun `Cancel order after an E9 failure cancels the order only`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.fail(network)
            h.start()

            val exit = h.machine.cancelOrder()

            assertThat(exit).isEqualTo(PaymentExit.Left)
            assertThat(h.log).containsExactly("E9:QRIS", "E8:PAYMENT_FAILED").inOrder()
        }

    @Test
    fun `Cancel order that cannot reach the backend keeps the failure and the pending order`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            h.orders.cancel.fail(network)
            h.start()
            h.pass(2.seconds)

            val exit = h.machine.cancelOrder()

            assertThat(exit).isEqualTo(PaymentExit.NotCancelled(network))
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.NETWORK, true))
            assertThat(h.orders.pending()).isNotNull()
            h.orders.cancel.succeed(SampleData.cancelledOrder)
            assertThat(h.machine.cancelOrder()).isEqualTo(PaymentExit.Left)
        }

    @Test
    fun `Cancel order is not allowed while waiting`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()
            val idle = PaymentHarness(this)

            assertThat(h.machine.cancelOrder()).isEqualTo(PaymentExit.NotAllowed)
            assertThat(idle.machine.cancelOrder()).isEqualTo(PaymentExit.NotAllowed)

            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
            assertThat(h.log).containsExactly("E9:QRIS")
        }

    @Test
    fun `E11 keeps its key across a retried cancel of the same payment`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.cancel.enqueue(Outcome.Failure(network))
            h.start()
            h.machine.backToOrder()

            h.machine.cancelOrder()

            val keys = h.gateway.cancels.map { it.key }
            assertThat(keys).hasSize(2)
            assertThat(keys.distinct()).hasSize(1)
        }

    // Try again and switching -----------------------------------------------------------------------------------------

    @Test
    fun `Try again makes a fresh payment for the same order, showing Creating first (TC-178)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.FAILED, PaymentFailureReason.CANCELLED_BY_PAYER))
            h.start()
            h.pass(2.seconds)
            h.gateway.status.succeed(SampleData.paymentPending)
            h.gateway.startQris.succeed(h.qris(paymentId = "pay_Q2"), delay = 1.seconds)

            assertThat(h.machine.tryAgain()).isTrue()
            h.pass(0.milliseconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Creating(PaymentMethod.QRIS))
            h.pass(1.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
            assertThat(
                h.gateway.starts
                    .map { it.orderId }
                    .distinct(),
            ).containsExactly(SampleData.ORDER_ID)
            assertThat(h.gateway.startKeys.distinct()).hasSize(2)
            assertThat(h.log).containsExactly("E9:QRIS", "E9:QRIS").inOrder()
            assertThat(h.orders.pending()?.paymentId).isEqualTo("pay_Q2")
        }

    @Test
    fun `Try again is refused unless the machine is Failed`() =
        runTest {
            val h = PaymentHarness(this)
            assertThat(h.machine.tryAgain()).isFalse()
            h.start()
            assertThat(h.machine.tryAgain()).isFalse()

            assertThat(h.gateway.starts).hasSize(1)
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
        }

    @Test
    fun `offline disables Try again and Pay by X instead, and they return with the connection (S_Checkout_offline)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            h.start()
            h.pass(2.seconds)
            h.connectivity.set(Connectivity.OFFLINE)

            assertThat(h.machine.tryAgain()).isFalse()
            assertThat(h.machine.switchMethod(PaymentMethod.CARD)).isFalse()
            assertThat(h.state).isInstanceOf(PaymentUiState.Failed::class.java)
            assertThat(h.gateway.starts).hasSize(1)

            h.connectivity.set(Connectivity.CHECKING)
            h.gateway.status.succeed(SampleData.paymentPending)
            assertThat(h.machine.tryAgain()).isTrue()
            h.pass(0.milliseconds)
            assertThat(h.gateway.starts).hasSize(2)
        }

    @Test
    fun `Try again after the 60 second give-up cancels the possibly live payment first`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.fail(network)
            h.start()
            h.pass(62.seconds)
            h.gateway.status.succeed(SampleData.paymentPending)

            assertThat(h.machine.tryAgain()).isTrue()
            h.pass(0.milliseconds)

            assertThat(h.log.takeLast(2)).containsExactly("E11:SWITCH_METHOD", "E9:QRIS").inOrder()
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
        }

    @Test
    fun `Try again whose cancel cannot be answered stays Failed and creates no second payment`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.fail(network)
            h.start()
            h.pass(62.seconds)
            h.gateway.cancel.fail(network)

            assertThat(h.machine.tryAgain()).isTrue()
            h.pass(0.milliseconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.NETWORK, false))
            assertThat(h.gateway.starts).hasSize(1)
            // The next tap tries the cancel again, with the same key.
            h.gateway.cancel.succeed(SampleData.paymentCancelled)
            assertThat(h.machine.tryAgain()).isTrue()
            h.pass(0.milliseconds)
            assertThat(h.gateway.starts).hasSize(2)
            assertThat(
                h.gateway.cancels
                    .map { it.key }
                    .distinct(),
            ).hasSize(1)
        }

    @Test
    fun `Try again that finds the old payment paid ends as Paid`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.fail(network)
            h.start()
            h.pass(62.seconds)
            h.gateway.cancel.fail(notCancellable)
            h.gateway.status.succeed(SampleData.paymentPaid)

            assertThat(h.machine.tryAgain()).isTrue()
            h.pass(1.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
            assertThat(h.gateway.starts).hasSize(1)
        }

    @Test
    fun `Pay by card instead after a QR failure creates a card payment for the same order (TC-084)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            h.start()
            h.pass(2.seconds)
            h.gateway.status.succeed(SampleData.paymentPending)

            assertThat(h.machine.switchMethod(PaymentMethod.CARD)).isTrue()
            h.pass(0.milliseconds)

            assertThat(h.log).containsExactly("E9:QRIS", "E9:CARD").inOrder()
            assertThat(h.gateway.starts[1].orderId).isEqualTo(SampleData.ORDER_ID)
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForCard::class.java)
        }

    @Test
    fun `the method switcher while the QR shows cancels the payment and creates the card payment`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()

            assertThat(h.machine.switchMethod(PaymentMethod.CARD)).isTrue()
            h.pass(0.milliseconds)

            assertThat(h.log).containsExactly("E9:QRIS", "E11:SWITCH_METHOD", "E9:CARD").inOrder()
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForCard::class.java)
            val polls = h.gateway.status.callCount
            assertThat(polls).isEqualTo(0)
        }

    @Test
    fun `switching to the method that is already in use is refused`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()

            assertThat(h.machine.switchMethod(PaymentMethod.QRIS)).isFalse()
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
            // The refused call gave the state back: Back to order still works.
            assertThat(h.machine.backToOrder()).isEqualTo(PaymentExit.Left)
        }

    @Test
    fun `switching is not offered while Creating, Checking or Processing`() =
        runTest {
            val creating = PaymentHarness(this)
            creating.gateway.startQris.succeed(creating.qris(), delay = 5.seconds)
            creating.start()
            assertThat(creating.machine.switchMethod(PaymentMethod.CARD)).isFalse()

            val processing = PaymentHarness(this)
            processing.gateway.status.succeed(processing.status(PaymentStatus.PROCESSING))
            processing.start()
            processing.pass(2.seconds)
            assertThat(processing.machine.switchMethod(PaymentMethod.CARD)).isFalse()

            val checking = PaymentHarness(this)
            checking.gateway.status.fail(network)
            checking.start()
            checking.pass(2.seconds)
            assertThat(checking.machine.switchMethod(PaymentMethod.CARD)).isFalse()
        }

    // The Complete hand-off ---------------------------------------------------------------------------------------------

    @Test
    fun `Paid stays until the Complete screen takes over, then the pending order is cleared`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.start()
            h.pass(3.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
            assertThat(h.orders.pending()).isNotNull()
            assertThat(h.start()).isFalse()

            h.machine.acknowledgePaid()

            assertThat(h.state).isEqualTo(PaymentUiState.Idle)
            assertThat(h.orders.pending()).isNull()
            assertThat(h.log).contains("clearPending")
        }

    @Test
    fun `acknowledging does nothing unless the payment is Paid`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()

            h.machine.acknowledgePaid()

            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
            assertThat(h.orders.pending()).isNotNull()
            assertThat(h.log).doesNotContain("clearPending")
        }

    @Test
    fun `an order whose session is gone can still be paid (recovery)`() =
        runTest {
            val h = PaymentHarness(this)
            h.holder.endOrder()
            h.gateway.status.succeed(SampleData.paymentPaid)

            h.start()
            h.pass(3.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
        }
}
