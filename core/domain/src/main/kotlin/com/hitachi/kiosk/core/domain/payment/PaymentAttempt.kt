package com.hitachi.kiosk.core.domain.payment

import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.Rupiah
import kotlin.time.Duration

/**
 * One payment attempt of an order: the E9 call and everything that follows until the payment ends. "Try again" and
 * "Pay by X instead" start a new attempt for the same [orderId] (BR-08, TD §5.3). Only the coroutine that runs the
 * attempt writes to it; an operation that takes over (back to order, cancel, switch) first stops that coroutine.
 */
internal class PaymentAttempt(
    val orderId: String,
    val method: PaymentMethod,
    val qrExpirySeconds: Int?,
) {
    var paymentId: String? = null
    var amount: Rupiah = Rupiah.Zero
    var terminalRef: String? = null
    var terminalTid: String? = null

    /** The backend reported a terminal non-paid status, or accepted the cancel: nothing was or will be charged. */
    var backendNonPaid = false

    /** The `Idempotency-Key` of E11 for this payment, kept for every retry of the cancel (TD §5.3). */
    var cancelKey: IdempotencyKey? = null

    var saleStarted = false
    var saleActive = false

    /** [com.hitachi.kiosk.core.domain.time.Clock.elapsedRealtime] readings. */
    var qrDeadline: Duration? = null
    var cardDeadline: Duration? = null
    var unknownSince: Duration? = null
    var processingSince: Duration? = null

    /** What to show again when the status is known after "Checking your payment…". */
    var resumeState: PaymentUiState? = null

    /** The payment exists and nothing says it ended, so it may still be charged or must be cancelled first. */
    val isLive: Boolean get() = paymentId != null && !backendNonPaid
}

internal val PaymentStatus.isNonPaidFinal: Boolean get() = isFinal && this != PaymentStatus.PAID

/** The failure copy for a backend terminal non-paid [state] (api-spec E10, FR-017, FR-019). */
internal fun failureReasonOf(
    state: PaymentState,
    method: PaymentMethod,
): FailureReason =
    when {
        state.status == PaymentStatus.EXPIRED -> FailureReason.EXPIRED
        state.status == PaymentStatus.CANCELLED -> FailureReason.CANCELLED
        else -> failedReasonOf(state.failureReason, method)
    }

/** The failure copy for a terminal status that carries no reason (E11 and E12 answers). */
internal fun failureReasonOf(
    status: PaymentStatus,
    method: PaymentMethod,
): FailureReason =
    when (status) {
        PaymentStatus.EXPIRED -> FailureReason.EXPIRED
        PaymentStatus.CANCELLED -> FailureReason.CANCELLED
        else -> failedReasonOf(null, method)
    }

/**
 * `FAILED` with a gateway or terminal reason. A QR payment that failed without a reason was cancelled in the payer's
 * app (FR-017); the design has no copy for insufficient funds on QRIS, so it is the generic failure there.
 */
private fun failedReasonOf(
    reason: PaymentFailureReason?,
    method: PaymentMethod,
): FailureReason =
    when (reason) {
        PaymentFailureReason.DECLINED -> {
            FailureReason.DECLINED
        }

        PaymentFailureReason.CANCELLED_BY_PAYER -> {
            FailureReason.CANCELLED
        }

        PaymentFailureReason.TERMINAL_TIMEOUT -> {
            FailureReason.TERMINAL_TIMEOUT
        }

        PaymentFailureReason.INSUFFICIENT_FUNDS -> {
            if (method == PaymentMethod.CARD) FailureReason.DECLINED else FailureReason.GENERIC
        }

        PaymentFailureReason.TERMINAL_ERROR, PaymentFailureReason.GATEWAY_ERROR -> {
            FailureReason.GENERIC
        }

        null -> {
            if (method == PaymentMethod.QRIS) FailureReason.CANCELLED else FailureReason.GENERIC
        }
    }

/** A call that did not reach the backend is [FailureReason.NETWORK]; every other error is the generic failure. */
internal fun failureReasonOf(error: DomainError): FailureReason =
    if (error is DomainError.Network) FailureReason.NETWORK else FailureReason.GENERIC
