package com.hitachi.kiosk.core.domain.model

import kotlin.time.Instant

/** Payment methods offered in 1.0 (D-12a: `COUNTER` is hidden). */
enum class PaymentMethod { QRIS, CARD }

/** Backend payment status (api-spec E10). Only [PAID] completes an order (A-15). */
enum class PaymentStatus(
    val isFinal: Boolean,
) {
    PENDING(isFinal = false),
    PROCESSING(isFinal = false),
    PAID(isFinal = true),
    FAILED(isFinal = true),
    EXPIRED(isFinal = true),
    CANCELLED(isFinal = true),
}

enum class PaymentFailureReason {
    DECLINED,
    CANCELLED_BY_PAYER,
    TERMINAL_TIMEOUT,
    TERMINAL_ERROR,
    GATEWAY_ERROR,
    INSUFFICIENT_FUNDS,
}

/** E9 result (FR-017, FR-018). */
sealed interface StartedPayment {
    val paymentId: String
    val status: PaymentStatus
    val amount: Rupiah
    val createdAt: Instant

    /**
     * [expiresAt] is authoritative for the QR countdown (NFR-017). [serverTime] is the `Date` header of the E9 answer:
     * the backend's clock when it answered. The countdown corrects the device clock with it (technical-design §5.4).
     * It is `null` when the header was missing, and the payment machine then falls back to [createdAt].
     */
    data class Qris(
        override val paymentId: String,
        override val status: PaymentStatus,
        override val amount: Rupiah,
        val qrString: String,
        val expiresAt: Instant,
        override val createdAt: Instant,
        val serverTime: Instant? = null,
    ) : StartedPayment {
        override fun toString(): String = "Qris(paymentId=$paymentId, status=$status, amount=$amount)"
    }

    /** The kiosk sends [amount] and [terminalRef] to the EDC terminal (OQ-03). */
    data class Card(
        override val paymentId: String,
        override val status: PaymentStatus,
        override val amount: Rupiah,
        val terminalRef: String,
        val terminal: TerminalInfo?,
        val timeoutSeconds: Int,
        override val createdAt: Instant,
    ) : StartedPayment
}

/** E10 result, polled every 2 s (FR-017). */
data class PaymentState(
    val paymentId: String,
    val method: PaymentMethod,
    val status: PaymentStatus,
    val amount: Rupiah,
    val expiresAt: Instant? = null,
    val paidAt: Instant? = null,
    val reference: String? = null,
    val issuer: String? = null,
    val paidOrder: PaidOrder? = null,
    val failureReason: PaymentFailureReason? = null,
    val message: LocalizedText? = null,
)

data class PaidOrder(
    val orderId: String,
    val orderNumber: String,
    val status: OrderStatus,
)

/** Why a pending payment is cancelled (E11). */
enum class CancelPaymentReason { CUSTOMER_BACK, SWITCH_METHOD, EXPIRED_ON_KIOSK, TIMEOUT }

/** E11 and E12 results. */
data class PaymentUpdate(
    val paymentId: String,
    val status: PaymentStatus,
)

/** The EDC terminal result relayed with E12 when the terminal reports to the kiosk (OQ-03). */
data class TerminalResult(
    val approved: Boolean,
    val terminalRef: String,
    val tid: String,
    val approvalCode: String?,
    val cardBrand: String?,
    val maskedPan: String?,
    val rawResponseCode: String?,
)
