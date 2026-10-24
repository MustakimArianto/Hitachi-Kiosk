package com.hitachi.kiosk.core.domain.payment

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.CardTerminal
import com.hitachi.kiosk.core.domain.device.TerminalEvent
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.TerminalResult
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.testing.fake.FakeCardTerminal
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The card path of architecture §5.2: FR-018, A-15, A-16, TC-080–TC-082, TC-181, TR-INT-04/05, TR-SEC-12. */
class PaymentStateMachineCardTest {
    private val network = DomainError.Network()
    private val card = SampleData.cardPayment

    // The terminal prompt ---------------------------------------------------------------------------------------

    @Test
    fun `the terminal is sent the backend amount and reference, not the cart (TC-080, TR-INT-04)`() =
        runTest {
            val h = PaymentHarness(this)
            // The E9 amount is the order total the backend holds; an E9 for another amount is refused (TR-INT-04).
            h.gateway.startCard.succeed(card.copy(amount = SampleData.totals.total))

            h.start(PaymentMethod.CARD)

            assertThat(h.terminal.sales).containsExactly(FakeCardTerminal.Sale(Rupiah(172_700), card.terminalRef))
            assertThat(h.state)
                .isEqualTo(PaymentUiState.WaitingForCard(Rupiah(172_700), terminalPrompted = true))
            assertThat(
                h.gateway.starts
                    .single()
                    .qrExpirySeconds,
            ).isNull()
            assertThat(h.orders.pending()?.method).isEqualTo(PaymentMethod.CARD)
        }

    @Test
    fun `Creating shows before the card prompt (S_Checkout_creating)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startCard.succeed(card, delay = 1.seconds)

            h.start(PaymentMethod.CARD)
            assertThat(h.state).isEqualTo(PaymentUiState.Creating(PaymentMethod.CARD))
            h.pass(1.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForCard::class.java)
        }

    // Approved ------------------------------------------------------------------------------------------------------

    @Test
    fun `an approval alone never makes the order paid (A-15, TR-SEC-12)`() =
        runTest {
            val h = PaymentHarness(this)
            h.start(PaymentMethod.CARD)

            h.pass(2.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.CARD))
            // Confirming payment lasts at most 60 s (FR-019); until then an approval alone is never Paid.
            h.pass(59.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.CARD))
            assertThat(h.phase).isEqualTo(OrderPhase.PAYING)
            assertThat(h.gateway.status.callCount).isGreaterThan(20)
        }

    @Test
    fun `the approval is relayed with E12 and Paid follows only the backend PAID (TC-080)`() =
        runTest {
            val h = PaymentHarness(this)
            h.start(PaymentMethod.CARD)

            h.pass(2.seconds)

            val relayed = h.gateway.relays.single()
            assertThat(relayed)
                .isEqualTo(
                    TerminalResult(
                        approved = true,
                        terminalRef = card.terminalRef,
                        tid = "TID 0003 1182",
                        approvalCode = "123456",
                        cardBrand = null,
                        maskedPan = null,
                        rawResponseCode = null,
                    ),
                )
            h.gateway.status.succeed(SampleData.paymentPaid.copy(method = PaymentMethod.CARD))
            h.pass(2.seconds)

            val paid = h.state as PaymentUiState.Paid
            assertThat(paid.method).isEqualTo(PaymentMethod.CARD)
            assertThat(h.phase).isEqualTo(OrderPhase.PAID)
        }

    @Test
    fun `a terminal that reports to the gateway directly is not relayed (OQ-03)`() =
        runTest {
            val h = PaymentHarness(this, policy = PaymentPolicy(relayTerminalResults = false))
            h.start(PaymentMethod.CARD)

            h.pass(10.seconds)

            assertThat(h.gateway.relays).isEmpty()
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.CARD))
        }

    @Test
    fun `PAID that arrives with the approval still shows Processing for 800 ms`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.resultDelay = 1.seconds
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.start(PaymentMethod.CARD)

            // The terminal approves at 1 s (Processing); the first poll at 2 s sees PAID after 1 s of Processing.
            h.pass(1.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.CARD))
            h.pass(1.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
        }

    @Test
    fun `a failed E12 shows Checking, keeps polling and returns to Processing (TD 5_3)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.relay.fail(network)
            h.start(PaymentMethod.CARD)

            h.pass(2.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Checking(PaymentMethod.CARD))
            h.pass(2.seconds)

            // The poll answers PENDING: the approval was seen, so the screen is not back at "tap your card".
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.CARD))
            assertThat(h.gateway.relays).hasSize(1)
        }

    @Test
    fun `a failed E12 and no backend answer for 60 seconds fails without not charged`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.relay.fail(network)
            h.gateway.status.fail(network)
            h.start(PaymentMethod.CARD)

            h.pass(61.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Checking(PaymentMethod.CARD))
            h.pass(1.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.NETWORK, false))
        }

    @Test
    fun `E12 answering with PAID reads E10 at once`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.relay.succeed(PaymentUpdate(SampleData.CARD_PAYMENT_ID, PaymentStatus.PAID))
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.terminal.resultDelay = 1.seconds
            h.start(PaymentMethod.CARD)

            h.pass(1.seconds)
            h.pass(800.milliseconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
            // Approved at 1 s, then the immediate read: the first scheduled poll (2 s) never had to run.
            assertThat(h.gateway.status.callCount).isEqualTo(1)
        }

    @Test
    fun `E12 answering with a failed status fails and claims not charged`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.relay.succeed(PaymentUpdate(SampleData.CARD_PAYMENT_ID, PaymentStatus.FAILED))
            h.start(PaymentMethod.CARD)

            h.pass(2.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.GENERIC, true))
        }

    @Test
    fun `a repeated approval from the terminal is relayed once`() =
        runTest {
            val approved = TerminalEvent.Approved(reference = card.terminalRef, tid = "TID 0003 1182")
            val h =
                PaymentHarness(this) { fake ->
                    object : CardTerminal by fake {
                        override fun startSale(
                            amount: Rupiah,
                            reference: String,
                        ): Flow<TerminalEvent> = flowOf(TerminalEvent.Prompted, approved, approved)
                    }
                }

            h.start(PaymentMethod.CARD)
            h.pass(10.seconds)

            assertThat(h.gateway.relays).hasSize(1)
        }

    // Declined, cancelled, error --------------------------------------------------------------------------------------

    @Test
    fun `a decline is relayed, E10 is read, and the failure claims not charged only with a backend answer (TC-081)`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.DECLINE
            h.gateway.relay.succeed(PaymentUpdate(SampleData.CARD_PAYMENT_ID, PaymentStatus.FAILED))
            h.start(PaymentMethod.CARD)

            h.pass(2.seconds)

            val relayed = h.gateway.relays.single()
            assertThat(relayed.approved).isFalse()
            assertThat(relayed.rawResponseCode).isEqualTo("05")
            assertThat(relayed.tid).isEqualTo("TID 0003 1182")
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.DECLINED, true))
        }

    @Test
    fun `a decline without a backend terminal status fails as declined without not charged`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.DECLINE
            h.start(PaymentMethod.CARD)

            h.pass(2.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.DECLINED, false))
            val polls = h.gateway.status.callCount
            h.pass(20.seconds)
            assertThat(h.gateway.status.callCount).isEqualTo(polls)
        }

    @Test
    fun `a decline whose E10 says FAILED claims not charged`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.DECLINE
            h.gateway.status.succeed(SampleData.paymentDeclined)
            h.start(PaymentMethod.CARD)

            h.pass(2.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.DECLINED, true))
        }

    @Test
    fun `a decline that E10 contradicts with PAID ends as Paid`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.DECLINE
            h.terminal.resultDelay = 1.seconds
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.start(PaymentMethod.CARD)

            h.pass(1.seconds)
            h.pass(800.milliseconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
        }

    @Test
    fun `a customer cancel on the terminal fails as cancelled`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.CUSTOMER_CANCEL
            h.start(PaymentMethod.CARD)

            h.pass(2.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.CANCELLED, false))
            assertThat(h.gateway.relays).isEmpty()
        }

    @Test
    fun `a terminal error fails generically, and E10 FAILED upgrades it to not charged`() =
        runTest {
            val unconfirmed = PaymentHarness(this)
            unconfirmed.terminal.mode = FakeCardTerminal.Mode.ERROR
            unconfirmed.start(PaymentMethod.CARD)
            unconfirmed.pass(2.seconds)
            assertThat(unconfirmed.state)
                .isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.GENERIC, false))

            val confirmed = PaymentHarness(this)
            confirmed.terminal.mode = FakeCardTerminal.Mode.ERROR
            confirmed.gateway.status.succeed(confirmed.status(PaymentStatus.FAILED, PaymentFailureReason.GATEWAY_ERROR))
            confirmed.start(PaymentMethod.CARD)
            confirmed.pass(2.seconds)
            assertThat(confirmed.state)
                .isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.GENERIC, true))
        }

    @Test
    fun `an adapter that throws becomes a terminal error`() =
        runTest {
            val h =
                PaymentHarness(this) { fake ->
                    object : CardTerminal by fake {
                        override fun startSale(
                            amount: Rupiah,
                            reference: String,
                        ): Flow<TerminalEvent> = flow { error("usb unplugged") }
                    }
                }

            h.start(PaymentMethod.CARD)
            h.pass(1.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.GENERIC, false))
        }

    // 90 s timeout ----------------------------------------------------------------------------------------------------

    @Test
    fun `no terminal answer for 90 seconds cancels the sale and E11 TIMEOUT, then fails (TC-082, A-16)`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.NO_ANSWER
            h.start(PaymentMethod.CARD)

            h.pass(89_999.milliseconds)
            assertThat(h.terminal.cancelCount).isEqualTo(0)
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForCard::class.java)
            h.pass(1.milliseconds)

            assertThat(h.log).containsExactly("E9:CARD", "terminal.cancel", "E11:TIMEOUT").inOrder()
            assertThat(h.state)
                .isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.TERMINAL_TIMEOUT, true))
            val polls = h.gateway.status.callCount
            h.pass(20.seconds)
            assertThat(h.gateway.status.callCount).isEqualTo(polls)
        }

    @Test
    fun `a terminal timeout whose E11 cannot be answered fails without not charged`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.NO_ANSWER
            h.gateway.cancel.fail(network)
            h.start(PaymentMethod.CARD)

            h.pass(90.seconds)

            assertThat(h.state)
                .isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.TERMINAL_TIMEOUT, false))
        }

    @Test
    fun `a terminal timeout whose E11 gets a 409 reads E10 and PAID wins`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.NO_ANSWER
            h.gateway.cancel.fail(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE"))
            repeat(44) { h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending)) }
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.start(PaymentMethod.CARD)

            h.pass(90.seconds)
            h.pass(800.milliseconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
        }

    @Test
    fun `the 90 seconds count from the sale, an approval stops them`() =
        runTest {
            val h = PaymentHarness(this)
            h.start(PaymentMethod.CARD)

            h.pass(120.seconds)

            assertThat(h.terminal.cancelCount).isEqualTo(0)
            assertThat(h.gateway.cancels).isEmpty()
        }

    // Back to order and switching -------------------------------------------------------------------------------------

    @Test
    fun `Back to order cancels the terminal sale first, then E11 and E8 (TC-181, FR-018 AC3)`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.NO_ANSWER
            h.start(PaymentMethod.CARD)

            val exit = h.machine.backToOrder()

            assertThat(exit).isEqualTo(PaymentExit.Left)
            assertThat(h.log)
                .containsExactly("E9:CARD", "terminal.cancel", "E11:CUSTOMER_BACK", "E8:CUSTOMER_BACK")
                .inOrder()
            assertThat(h.state).isEqualTo(PaymentUiState.Idle)
            assertThat(h.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(h.orders.pending()).isNull()
        }

    @Test
    fun `Back to order is not allowed once the terminal approved`() =
        runTest {
            val h = PaymentHarness(this)
            h.start(PaymentMethod.CARD)
            h.pass(2.seconds)

            val exit = h.machine.backToOrder()

            assertThat(exit).isEqualTo(PaymentExit.NotAllowed)
            assertThat(h.terminal.cancelCount).isEqualTo(0)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.CARD))
        }

    @Test
    fun `switching from the card prompt to QRIS cancels the sale and the payment, and keeps the order (TC-084)`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.NO_ANSWER
            h.start(PaymentMethod.CARD)

            assertThat(h.machine.switchMethod(PaymentMethod.QRIS)).isTrue()
            h.pass(0.milliseconds)

            assertThat(h.log).containsExactly("E9:CARD", "terminal.cancel", "E11:SWITCH_METHOD", "E9:QRIS").inOrder()
            assertThat(h.gateway.starts.map { it.orderId }).containsExactly(SampleData.ORDER_ID, SampleData.ORDER_ID)
            assertThat(h.gateway.starts[1].qrExpirySeconds).isEqualTo(300)
            assertThat(h.gateway.starts[1].key).isNotEqualTo(h.gateway.starts[0].key)
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
            assertThat(
                h.gateway.cancels
                    .single()
                    .reason,
            ).isEqualTo(CancelPaymentReason.SWITCH_METHOD)
        }

    @Test
    fun `Pay by QRIS instead after a confirmed decline needs no E11 (TC-084)`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.DECLINE
            h.gateway.status.succeed(SampleData.paymentDeclined)
            h.start(PaymentMethod.CARD)
            h.pass(2.seconds)
            assertThat(h.state).isInstanceOf(PaymentUiState.Failed::class.java)

            assertThat(h.machine.switchMethod(PaymentMethod.QRIS)).isTrue()
            h.pass(0.milliseconds)

            assertThat(h.log.filter { it.startsWith("E11") }).isEmpty()
            assertThat(h.log.last()).isEqualTo("E9:QRIS")
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
        }

    @Test
    fun `Pay by QRIS instead after an unconfirmed decline cancels the card payment first`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.DECLINE
            h.start(PaymentMethod.CARD)
            h.pass(2.seconds)
            assertThat(h.state)
                .isEqualTo(PaymentUiState.Failed(PaymentMethod.CARD, FailureReason.DECLINED, false))

            assertThat(h.machine.switchMethod(PaymentMethod.QRIS)).isTrue()
            h.pass(0.milliseconds)

            assertThat(h.log).containsExactly("E9:CARD", "E11:SWITCH_METHOD", "E9:QRIS").inOrder()
        }

    @Test
    fun `Try again after a terminal timeout starts a fresh card payment for the same order`() =
        runTest {
            val h = PaymentHarness(this)
            h.terminal.mode = FakeCardTerminal.Mode.NO_ANSWER
            h.start(PaymentMethod.CARD)
            h.pass(90.seconds)
            h.terminal.mode = FakeCardTerminal.Mode.APPROVE

            assertThat(h.machine.tryAgain()).isTrue()
            h.pass(0.milliseconds)

            assertThat(h.gateway.starts).hasSize(2)
            assertThat(h.gateway.starts[1].method).isEqualTo(PaymentMethod.CARD)
            assertThat(h.gateway.starts[1].key).isNotEqualTo(h.gateway.starts[0].key)
            assertThat(h.terminal.sales).hasSize(2)
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForCard::class.java)
        }
}
