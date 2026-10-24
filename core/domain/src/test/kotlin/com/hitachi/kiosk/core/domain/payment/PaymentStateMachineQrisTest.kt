package com.hitachi.kiosk.core.domain.payment

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The QRIS path of architecture §5.2: FR-017, TC-074–TC-078, TC-160, NFR-017, TR-NET-10, TR-INT-03/06. */
class PaymentStateMachineQrisTest {
    private val network = DomainError.Network()
    private val serverError = DomainError.Server(code = "SERVER_ERROR", httpStatus = 503)

    // Creating and the QR ---------------------------------------------------------------------------------------

    @Test
    fun `Creating shows first, then the QR with the backend amount (TC-074, TR-INT-04)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(), delay = 1.seconds)

            assertThat(h.start()).isTrue()
            assertThat(h.state).isEqualTo(PaymentUiState.Creating(PaymentMethod.QRIS))

            h.pass(1.seconds)

            val waiting = h.state as PaymentUiState.WaitingForQr
            assertThat(waiting.amount).isEqualTo(Rupiah(172_700))
            assertThat(waiting.qrString).isEqualTo(SampleData.qrisPayment.qrString)
            assertThat(waiting.countdown.total).isEqualTo(300.seconds)
            assertThat(waiting.method).isEqualTo(PaymentMethod.QRIS)
        }

    @Test
    fun `E9 is sent for the order with the QR expiry and a new key, and the payment is saved for recovery`() =
        runTest {
            val h = PaymentHarness(this)

            h.start(qrExpirySeconds = 180)

            val start = h.gateway.starts.single()
            assertThat(start.orderId).isEqualTo(SampleData.ORDER_ID)
            assertThat(start.method).isEqualTo(PaymentMethod.QRIS)
            assertThat(start.qrExpirySeconds).isEqualTo(180)
            assertThat(start.key.value).isEqualTo("key-1")
            val pending = h.orders.pending()
            assertThat(pending?.paymentId).isEqualTo(SampleData.QRIS_PAYMENT_ID)
            assertThat(pending?.method).isEqualTo(PaymentMethod.QRIS)
        }

    @Test
    fun `a second start while a payment runs is ignored (one payment per order)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(), delay = 1.seconds)

            assertThat(h.start()).isTrue()
            assertThat(h.start()).isFalse()
            h.pass(1.seconds)
            assertThat(h.start()).isFalse()

            assertThat(h.gateway.starts).hasSize(1)
        }

    @Test
    fun `the QR payload never reaches toString (NFR-006)`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()

            assertThat(h.state.toString()).doesNotContain(SampleData.qrisPayment.qrString)
        }

    // Polling -----------------------------------------------------------------------------------------------------

    @Test
    fun `E10 is polled every 2 seconds while the payment is pending (TR-NET-10)`() =
        runTest {
            val h = PaymentHarness(this)
            h.start()

            h.pass(1_999.milliseconds)
            assertThat(h.gateway.status.callCount).isEqualTo(0)
            h.pass(1.milliseconds)
            assertThat(h.gateway.status.callCount).isEqualTo(1)
            h.pass(2.seconds)
            assertThat(h.gateway.status.callCount).isEqualTo(2)
            h.pass(10.seconds)
            assertThat(h.gateway.status.callCount).isEqualTo(7)
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
        }

    @Test
    fun `PAID shows Processing for at least 800 ms, then Paid, and polling stops (TC-075, FR-017 AC5)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending))
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.start()

            h.pass(4.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.QRIS))
            assertThat(h.phase).isEqualTo(OrderPhase.PAYING)

            h.pass(799.milliseconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.QRIS))
            h.pass(1.milliseconds)

            val paid = h.state as PaymentUiState.Paid
            assertThat(paid.orderNumber).isEqualTo("A247")
            assertThat(paid.amount).isEqualTo(Rupiah(172_700))
            assertThat(paid.method).isEqualTo(PaymentMethod.QRIS)
            assertThat(paid.issuer).isEqualTo("GoPay")
            assertThat(h.phase).isEqualTo(OrderPhase.PAID)
            val polls = h.gateway.status.callCount
            h.pass(30.seconds)
            assertThat(h.gateway.status.callCount).isEqualTo(polls)
            assertThat(h.gateway.cancels).isEmpty()
        }

    @Test
    fun `PROCESSING from the backend shows Processing and keeps polling`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.enqueue(Outcome.Success(h.status(PaymentStatus.PROCESSING)))
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.start()

            h.pass(2.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.QRIS))
            // Processing began at 2 s, so the 800 ms are already over when PAID arrives at 4 s.
            h.pass(2.seconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
        }

    @Test
    fun `the order number falls back to the pending order when E10 carries none`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(SampleData.paymentPaid.copy(paidOrder = null))
            h.start()

            h.pass(3.seconds)

            assertThat((h.state as PaymentUiState.Paid).orderNumber).isEqualTo("A247")
        }

    // Backend terminal non-paid statuses ---------------------------------------------------------------------------

    @Test
    fun `every backend terminal non-paid status fails with its copy and claims not charged (FR-019 AC1)`() =
        runTest {
            val cases =
                listOf(
                    Triple(PaymentStatus.EXPIRED, null, FailureReason.EXPIRED),
                    Triple(PaymentStatus.CANCELLED, null, FailureReason.CANCELLED),
                    Triple(PaymentStatus.FAILED, PaymentFailureReason.CANCELLED_BY_PAYER, FailureReason.CANCELLED),
                    Triple(PaymentStatus.FAILED, null, FailureReason.CANCELLED),
                    Triple(PaymentStatus.FAILED, PaymentFailureReason.GATEWAY_ERROR, FailureReason.GENERIC),
                    Triple(PaymentStatus.FAILED, PaymentFailureReason.TERMINAL_ERROR, FailureReason.GENERIC),
                    Triple(PaymentStatus.FAILED, PaymentFailureReason.INSUFFICIENT_FUNDS, FailureReason.GENERIC),
                    Triple(PaymentStatus.FAILED, PaymentFailureReason.DECLINED, FailureReason.DECLINED),
                    Triple(PaymentStatus.FAILED, PaymentFailureReason.TERMINAL_TIMEOUT, FailureReason.TERMINAL_TIMEOUT),
                )
            for ((status, reason, expected) in cases) {
                val h = PaymentHarness(this)
                h.gateway.status.succeed(h.status(status, reason))
                h.start()

                h.pass(2.seconds)

                assertThat(h.state)
                    .isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, expected, notCharged = true))
                val polls = h.gateway.status.callCount
                h.pass(20.seconds)
                assertThat(h.gateway.status.callCount).isEqualTo(polls)
            }
        }

    @Test
    fun `cancelled in the payer's app fails with the cancelled copy and no E11 (TC-076)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.FAILED, PaymentFailureReason.CANCELLED_BY_PAYER))
            h.start()

            h.pass(2.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.CANCELLED, true))
            assertThat(h.gateway.cancels).isEmpty()
            assertThat(h.phase).isEqualTo(OrderPhase.PAYING)
        }

    @Test
    fun `an E9 failure fails without a payment and without not charged`() =
        runTest {
            for ((error, expected) in listOf(network to FailureReason.NETWORK, serverError to FailureReason.GENERIC)) {
                val h = PaymentHarness(this)
                h.gateway.startQris.fail(error)

                h.start()

                assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, expected, notCharged = false))
                assertThat(h.gateway.status.callCount).isEqualTo(0)
                assertThat(h.orders.pending()?.paymentId).isNull()
            }
        }

    // QR expiry and the server clock ---------------------------------------------------------------------------------

    @Test
    fun `expiry sends E11 EXPIRED_ON_KIOSK then fails as expired, and Try again makes a new payment (TC-077)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.enqueue(Outcome.Success(h.qris(life = 20.seconds)))
            h.start()

            h.pass(19_999.milliseconds)
            assertThat(h.gateway.cancels).isEmpty()
            h.pass(1.milliseconds)

            val cancel = h.gateway.cancels.single()
            assertThat(cancel.reason).isEqualTo(CancelPaymentReason.EXPIRED_ON_KIOSK)
            assertThat(cancel.paymentId).isEqualTo(SampleData.QRIS_PAYMENT_ID)
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.EXPIRED, true))
            val polls = h.gateway.status.callCount
            h.pass(10.seconds)
            assertThat(h.gateway.status.callCount).isEqualTo(polls)

            h.gateway.startQris.succeed(h.qris(paymentId = "pay_Q2"))
            assertThat(h.machine.tryAgain()).isTrue()
            h.pass(0.milliseconds)

            assertThat(h.gateway.starts).hasSize(2)
            assertThat(h.gateway.starts[1].orderId).isEqualTo(SampleData.ORDER_ID)
            assertThat(h.gateway.starts[1].key).isNotEqualTo(h.gateway.starts[0].key)
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
            assertThat(h.orders.pending()?.paymentId).isEqualTo("pay_Q2")
        }

    @Test
    fun `a 409 at expiry re-reads E10 first and PAID wins (TD 8_4)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = 5.seconds))
            // Polls at 2 s and 4 s still see PENDING; the expiry at 5 s gets a 409 and then PAID from E10.
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending))
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending))
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.gateway.cancel.fail(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE"))
            h.start()

            h.pass(5.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Processing(PaymentMethod.QRIS))
            h.pass(800.milliseconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
            assertThat(h.phase).isEqualTo(OrderPhase.PAID)
            assertThat(h.gateway.cancels).hasSize(1)
        }

    @Test
    fun `a 409 at expiry with a terminal non-paid status behind it fails as expired, not charged`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = 5.seconds))
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending))
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending))
            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            h.gateway.cancel.fail(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE"))
            h.start()

            h.pass(5.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.EXPIRED, true))
        }

    @Test
    fun `an E11 that the backend cannot answer at expiry fails as expired without not charged`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = 5.seconds))
            h.gateway.cancel.fail(network)
            h.start()

            h.pass(5.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.EXPIRED, false))
        }

    @Test
    fun `E11 answering with PAID at expiry is read again and wins`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = 5.seconds))
            h.gateway.cancel.succeed(PaymentUpdate(SampleData.QRIS_PAYMENT_ID, PaymentStatus.PAID))
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending))
            h.gateway.status.enqueue(Outcome.Success(SampleData.paymentPending))
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.start()

            h.pass(5.seconds)
            h.pass(800.milliseconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
        }

    @Test
    fun `E11 answering with a status that is not final does not claim not charged`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = 5.seconds))
            h.gateway.cancel.succeed(PaymentUpdate(SampleData.QRIS_PAYMENT_ID, PaymentStatus.PENDING))
            h.start()

            h.pass(5.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.EXPIRED, false))
        }

    @Test
    fun `a device clock 10 minutes ahead still counts the QR down from the server time (TC-160, NFR-017)`() =
        runTest {
            val h = PaymentHarness(this)
            h.clock.skew = 10.minutes
            h.gateway.startQris.succeed(h.qris(life = 300.seconds))
            h.start()

            val countdown = (h.state as PaymentUiState.WaitingForQr).countdown
            assertThat(countdown.remaining(h.clock)).isEqualTo(300.seconds)
            h.pass(120.seconds)
            assertThat(countdown.remaining(h.clock)).isEqualTo(180.seconds)
            assertThat(h.gateway.cancels).isEmpty()

            h.pass(179.seconds)
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)
            h.pass(1.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.EXPIRED, true))
        }

    @Test
    fun `a device clock 10 minutes behind counts down from the server time too`() =
        runTest {
            val h = PaymentHarness(this)
            h.clock.skew = (-10).minutes
            h.gateway.startQris.succeed(h.qris(life = 300.seconds))
            h.start()

            assertThat((h.state as PaymentUiState.WaitingForQr).countdown.remaining(h.clock)).isEqualTo(300.seconds)
        }

    @Test
    fun `a wall clock change while the QR shows does not move the countdown (monotonic)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = 300.seconds))
            h.start()
            val countdown = (h.state as PaymentUiState.WaitingForQr).countdown

            h.pass(100.seconds)
            h.clock.skew = 3.minutes

            assertThat(countdown.remaining(h.clock)).isEqualTo(200.seconds)
        }

    @Test
    fun `without a Date header the countdown uses the E9 createdAt as the server time`() =
        runTest {
            val h = PaymentHarness(this)
            h.clock.skew = 10.minutes
            h.gateway.startQris.succeed(h.qris(life = 300.seconds, withDate = false))
            h.start()

            assertThat((h.state as PaymentUiState.WaitingForQr).countdown.remaining(h.clock)).isEqualTo(300.seconds)
        }

    @Test
    fun `a QR that is already past its expiry fails at once`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = (-5).seconds))

            h.start()

            assertThat(
                h.gateway.cancels
                    .single()
                    .reason,
            ).isEqualTo(CancelPaymentReason.EXPIRED_ON_KIOSK)
            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.EXPIRED, true))
        }

    // Unknown ---------------------------------------------------------------------------------------------------------

    @Test
    fun `a failed poll shows Checking, keeps polling and resolves to Paid (TC-085)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.enqueue(Outcome.Failure(network))
            h.gateway.status.enqueue(Outcome.Failure(network))
            h.gateway.status.succeed(SampleData.paymentPaid)
            h.start()

            h.pass(2.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Checking(PaymentMethod.QRIS))
            h.pass(2.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Checking(PaymentMethod.QRIS))
            h.pass(2.seconds)
            h.pass(800.milliseconds)

            assertThat(h.state).isInstanceOf(PaymentUiState.Paid::class.java)
        }

    @Test
    fun `PENDING after Checking returns to the same QR with the same countdown`() =
        runTest {
            val h = PaymentHarness(this)
            val waiting =
                run {
                    h.start()
                    h.state as PaymentUiState.WaitingForQr
                }
            h.gateway.status.enqueue(Outcome.Failure(network))
            h.gateway.status.succeed(SampleData.paymentPending)

            h.pass(2.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Checking(PaymentMethod.QRIS))
            h.pass(2.seconds)

            assertThat(h.state).isEqualTo(waiting)
        }

    @Test
    fun `no answer for 60 seconds fails without not charged, 59 seconds does not (A-17, TR-INT-06)`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.fail(network)
            h.start()

            // The first failed poll is at 2 s; the window runs to 62 s.
            h.pass(61.seconds)
            assertThat(h.state).isEqualTo(PaymentUiState.Checking(PaymentMethod.QRIS))
            h.pass(1.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.NETWORK, false))
            val polls = h.gateway.status.callCount
            h.pass(20.seconds)
            assertThat(h.gateway.status.callCount).isEqualTo(polls)
        }

    @Test
    fun `any backend answer resets the 60 second window`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.status.fail(network)
            h.start()
            h.pass(50.seconds)
            h.gateway.status.clearQueue()
            h.gateway.status.succeed(SampleData.paymentPending)
            h.pass(2.seconds)
            assertThat(h.state).isInstanceOf(PaymentUiState.WaitingForQr::class.java)

            h.gateway.status.fail(network)
            h.pass(60.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Checking(PaymentMethod.QRIS))
        }

    @Test
    fun `server errors and unreadable answers count as no answer`() =
        runTest {
            for (error in listOf(serverError, DomainError.Unknown, DomainError.Unauthorized)) {
                val h = PaymentHarness(this)
                h.gateway.status.fail(error)
                h.start()

                h.pass(2.seconds)

                assertThat(h.state).isEqualTo(PaymentUiState.Checking(PaymentMethod.QRIS))
            }
        }

    @Test
    fun `a QR that expires while checking cancels and fails as expired without not charged`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(h.qris(life = 10.seconds))
            h.gateway.status.fail(network)
            h.gateway.cancel.fail(network)
            h.start()

            h.pass(10.seconds)

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.EXPIRED, false))
        }
}
