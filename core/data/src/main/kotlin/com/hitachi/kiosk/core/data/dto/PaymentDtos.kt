package com.hitachi.kiosk.core.data.dto

import kotlinx.serialization.Serializable

/** E9 request. `qrExpirySeconds` only for QRIS. */
@Serializable
internal data class StartPaymentRequestDto(
    val method: String,
    val qrExpirySeconds: Int? = null,
)

/** E9 response, QRIS (`qrString`, `expiresAt`) or card (`terminalRef`, `terminal`, `timeoutSeconds`). */
@Serializable
internal data class StartedPaymentDto(
    val paymentId: String,
    val method: String,
    val status: String,
    val amount: Long,
    val qrString: String? = null,
    val expiresAt: String? = null,
    val terminalRef: String? = null,
    val terminal: TerminalDto? = null,
    val timeoutSeconds: Int? = null,
    val createdAt: String,
) {
    override fun toString(): String = "StartedPaymentDto(method=$method, status=$status)"
}

/** E10 response. */
@Serializable
internal data class PaymentStateDto(
    val paymentId: String,
    val method: String,
    val status: String,
    val amount: Long,
    val expiresAt: String? = null,
    val paidAt: String? = null,
    val reference: String? = null,
    val issuer: String? = null,
    val order: PaidOrderDto? = null,
    val reason: String? = null,
    val message: LocalizedTextDto? = null,
) {
    override fun toString(): String = "PaymentStateDto(method=$method, status=$status)"
}

@Serializable
internal data class PaidOrderDto(
    val orderId: String,
    val orderNumber: String,
    val status: String,
)

/** E11 request. */
@Serializable
internal data class CancelPaymentRequestDto(
    val reason: String,
)

/** E11 and E12 response. */
@Serializable
internal data class PaymentUpdateDto(
    val paymentId: String,
    val status: String,
)

/** E12 request. */
@Serializable
internal data class TerminalResultDto(
    val approved: Boolean,
    val terminalRef: String,
    val tid: String,
    val approvalCode: String? = null,
    val cardBrand: String? = null,
    val maskedPan: String? = null,
    val rawResponseCode: String? = null,
) {
    override fun toString(): String = "TerminalResultDto(approved=$approved)"
}
