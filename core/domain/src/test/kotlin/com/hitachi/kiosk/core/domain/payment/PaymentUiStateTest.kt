package com.hitachi.kiosk.core.domain.payment

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.testing.fake.TestClock
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PaymentUiStateTest {
    @Test
    fun `the countdown follows the monotonic clock and never goes negative (FR-017)`() {
        val clock = TestClock()
        val countdown = QrCountdown(deadline = clock.elapsedRealtime() + 300.seconds, total = 300.seconds)

        assertThat(countdown.remaining(clock)).isEqualTo(300.seconds)
        assertThat(countdown.fractionLeft(clock)).isEqualTo(1f)
        clock.advanceBy(150.seconds)
        assertThat(countdown.remaining(clock)).isEqualTo(150.seconds)
        assertThat(countdown.fractionLeft(clock)).isEqualTo(0.5f)
        clock.advanceBy(200.seconds)
        assertThat(countdown.remaining(clock)).isEqualTo(Duration.ZERO)
        assertThat(countdown.fractionLeft(clock)).isEqualTo(0f)
    }

    @Test
    fun `a countdown without a total has an empty ring`() {
        assertThat(QrCountdown(1.seconds, Duration.ZERO).fractionLeft(TestClock())).isEqualTo(0f)
    }

    @Test
    fun `states keep secrets out of toString (NFR-006)`() {
        val qr = PaymentUiState.WaitingForQr(Rupiah(1), "SECRET-QR", QrCountdown(1.seconds, 1.seconds))
        val paid = PaymentUiState.Paid(PaymentMethod.QRIS, Rupiah(1), "A247", null, "GoPay", "QR-8812-4471-0091")

        assertThat(qr.toString()).doesNotContain("SECRET-QR")
        assertThat(paid.toString()).doesNotContain("QR-8812-4471-0091")
        assertThat(paid.toString()).doesNotContain("GoPay")
    }

    @Test
    fun `failure reasons map from E10 for both methods (FR-019 AC1)`() {
        fun failed(
            status: PaymentStatus,
            reason: PaymentFailureReason? = null,
        ): PaymentState = SampleData.paymentPending.copy(status = status, failureReason = reason)

        val cases =
            listOf(
                Triple(failed(PaymentStatus.EXPIRED), PaymentMethod.CARD, FailureReason.EXPIRED),
                Triple(failed(PaymentStatus.CANCELLED), PaymentMethod.CARD, FailureReason.CANCELLED),
                Triple(failed(PaymentStatus.FAILED), PaymentMethod.CARD, FailureReason.GENERIC),
                Triple(failed(PaymentStatus.FAILED), PaymentMethod.QRIS, FailureReason.CANCELLED),
                Triple(
                    failed(PaymentStatus.FAILED, PaymentFailureReason.INSUFFICIENT_FUNDS),
                    PaymentMethod.CARD,
                    FailureReason.DECLINED,
                ),
                Triple(
                    failed(PaymentStatus.FAILED, PaymentFailureReason.INSUFFICIENT_FUNDS),
                    PaymentMethod.QRIS,
                    FailureReason.GENERIC,
                ),
            )
        for ((state, method, expected) in cases) {
            assertThat(failureReasonOf(state, method)).isEqualTo(expected)
        }
        assertThat(failureReasonOf(PaymentStatus.EXPIRED, PaymentMethod.QRIS)).isEqualTo(FailureReason.EXPIRED)
        assertThat(failureReasonOf(PaymentStatus.FAILED, PaymentMethod.CARD)).isEqualTo(FailureReason.GENERIC)
        assertThat(failureReasonOf(DomainError.Network(isTimeout = true))).isEqualTo(FailureReason.NETWORK)
        assertThat(failureReasonOf(DomainError.Unknown)).isEqualTo(FailureReason.GENERIC)
    }

    @Test
    fun `a backend answer for the other method cannot be shown and fails generically`() =
        runTest {
            val h = PaymentHarness(this)
            h.gateway.startQris.succeed(SampleData.cardPayment as StartedPayment)

            h.start()

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.GENERIC, false))
        }

    @Test
    fun `an unexpected exception fails generically instead of crashing the application scope`() =
        runTest {
            val h = PaymentHarness(this)
            h.recordPaymentFailure = IllegalStateException("database is locked")

            h.start()

            assertThat(h.state).isEqualTo(PaymentUiState.Failed(PaymentMethod.QRIS, FailureReason.GENERIC, false))
        }
}
