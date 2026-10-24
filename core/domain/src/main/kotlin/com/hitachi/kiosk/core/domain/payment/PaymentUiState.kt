package com.hitachi.kiosk.core.domain.payment

import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * What the Checkout screen renders, produced by [PaymentStateMachine] (architecture §5.2, FR-017–FR-019). Each case
 * maps to a scenario of `Checkout.dc.html`:
 *
 * | State | Design scenario |
 * |---|---|
 * | [Creating] | `creating` ("Creating your payment…") |
 * | [WaitingForQr], [WaitingForCard] | `qr`, `card` |
 * | [Checking] | `checking` ("Checking your payment…") |
 * | [Processing] | the processing step ("Confirming payment") |
 * | [Failed] | `expired`, `cancelled`, `declined`, `terminalTimeout`, `offline` and the generic failure |
 */
sealed interface PaymentUiState {
    /** No payment is in progress. */
    data object Idle : PaymentUiState

    /** "Creating your payment…": E9 is running, or the previous payment is being cancelled first (FR-017, FR-019). */
    data class Creating(
        val method: PaymentMethod,
    ) : PaymentUiState

    /** The customer is expected to pay: a QR code or the card terminal prompt. */
    sealed interface Waiting : PaymentUiState {
        val method: PaymentMethod

        /** The backend order total (TR-INT-04), never the kiosk cart. */
        val amount: Rupiah
    }

    /** The QR code is on screen. [countdown] drives the "Code expires in m:ss" ring (NFR-017). */
    data class WaitingForQr(
        override val amount: Rupiah,
        val qrString: String,
        val countdown: QrCountdown,
    ) : Waiting {
        override val method: PaymentMethod get() = PaymentMethod.QRIS

        // The QR payload is a payment secret that must not reach logs (NFR-006).
        override fun toString(): String = "WaitingForQr(amount=$amount, countdown=$countdown)"
    }

    /** The EDC terminal was sent [amount]; [terminalPrompted] is true once it showed the prompt (FR-018). */
    data class WaitingForCard(
        override val amount: Rupiah,
        val terminalPrompted: Boolean = false,
    ) : Waiting {
        override val method: PaymentMethod get() = PaymentMethod.CARD
    }

    /**
     * "Checking your payment…": the backend did not answer the last poll (architecture §5.2 `Unknown`). Polling goes
     * on for 60 s. This state never shows "not charged" (FR-019, TR-INT-06).
     */
    data class Checking(
        val method: PaymentMethod,
    ) : PaymentUiState

    /** "Confirming payment": the terminal approved or the backend reported `PROCESSING` or `PAID` (FR-017). */
    data class Processing(
        val method: PaymentMethod,
    ) : PaymentUiState

    /**
     * The backend reported `PAID` (A-15) and the Processing state was shown for at least 800 ms. [orderNumber] is the
     * number the Complete screen shows (FR-020).
     */
    data class Paid(
        val method: PaymentMethod,
        val amount: Rupiah,
        val orderNumber: String?,
        val paidAt: Instant?,
        val issuer: String?,
        val reference: String?,
    ) : PaymentUiState {
        // The payment reference is truncated or dropped in logs (NFR-006): keep it out of toString too.
        override fun toString(): String = "Paid(method=$method, amount=$amount, orderNumber=$orderNumber)"
    }

    /**
     * The payment did not go through. [notCharged] is true only when the backend reported a terminal non-paid status
     * (`FAILED`, `EXPIRED`, `CANCELLED`) or accepted the cancel of the payment: "You have not been charged · your
     * order is saved". It is false for every failure reached without a backend answer (FR-019, TR-INT-06).
     */
    data class Failed(
        val method: PaymentMethod,
        val reason: FailureReason,
        val notCharged: Boolean,
    ) : PaymentUiState
}

/**
 * Why a payment failed, one value per failure copy of Checkout (FR-019 AC1). The screen picks the title and text:
 * [EXPIRED] "QR code expired", [CANCELLED] "Payment cancelled", [DECLINED] "Card declined", [TERMINAL_TIMEOUT]
 * "Terminal not responding", [GENERIC] "Payment didn't go through", and [NETWORK] "Ordering is paused" while the
 * kiosk is offline, or "Can't reach the restaurant system. Please try again." while it is online (FR-016).
 */
enum class FailureReason { EXPIRED, CANCELLED, DECLINED, TERMINAL_TIMEOUT, NETWORK, GENERIC }

/**
 * The QR countdown on the monotonic clock (technical-design §5.4): [deadline] is an [Clock.elapsedRealtime] reading,
 * already corrected for the server clock offset, and [total] is the code's whole life for the ring (FR-017).
 */
data class QrCountdown(
    val deadline: Duration,
    val total: Duration,
) {
    /** Time left, never negative. The screen ticks every second and shows `m:ss`. */
    fun remaining(clock: Clock): Duration = (deadline - clock.elapsedRealtime()).coerceAtLeast(Duration.ZERO)

    /** The part of the ring that is still full, from 1.0 to 0.0. */
    fun fractionLeft(clock: Clock): Float =
        if (total <= Duration.ZERO) 0f else (remaining(clock) / total).toFloat().coerceIn(0f, 1f)
}

/** The result of leaving Checkout: "Back to order" or "Cancel order" (FR-016, FR-019, BR-08). */
sealed interface PaymentExit {
    /** The payment and the order are cancelled on the backend and the machine is idle again. */
    data object Left : PaymentExit

    /** The backend said the order was paid in the meantime: the state is now [PaymentUiState.Paid]. */
    data object AlreadyPaid : PaymentExit

    /** The current state does not allow it (for example while processing), or another action is running. */
    data object NotAllowed : PaymentExit

    /** The backend could not cancel. The machine shows [PaymentUiState.Failed] and the order is still pending. */
    data class NotCancelled(
        val error: DomainError,
    ) : PaymentExit
}
