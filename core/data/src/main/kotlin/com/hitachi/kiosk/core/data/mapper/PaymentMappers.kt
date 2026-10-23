package com.hitachi.kiosk.core.data.mapper

import com.hitachi.kiosk.core.data.dto.PaymentStateDto
import com.hitachi.kiosk.core.data.dto.PaymentUpdateDto
import com.hitachi.kiosk.core.data.dto.StartedPaymentDto
import com.hitachi.kiosk.core.data.dto.TerminalResultDto
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.OrderStatus
import com.hitachi.kiosk.core.domain.model.PaidOrder
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.domain.model.TerminalResult

// Payment statuses and methods are required: an unknown one fails the call (DomainError.Unknown) instead of being
// guessed, so the payment state machine keeps polling and never shows "paid" or "not charged" on a guess (A-15,
// FR-019).

/** E9 → [StartedPayment]. A QRIS answer needs `qrString` and `expiresAt`, a card answer `terminalRef`. */
internal fun StartedPaymentDto.toDomain(): StartedPayment {
    val status = requiredEnum<PaymentStatus>(status, "payment.status")
    return when (requiredEnum<PaymentMethod>(method, "payment.method")) {
        PaymentMethod.QRIS -> {
            StartedPayment.Qris(
                paymentId = paymentId,
                status = status,
                amount = amount.toRupiah(),
                qrString = requireNotNull(qrString) { "QRIS payment without qrString" },
                expiresAt = requireNotNull(expiresAt) { "QRIS payment without expiresAt" }.toInstant(),
                createdAt = createdAt.toInstant(),
            )
        }

        PaymentMethod.CARD -> {
            StartedPayment.Card(
                paymentId = paymentId,
                status = status,
                amount = amount.toRupiah(),
                terminalRef = requireNotNull(terminalRef) { "Card payment without terminalRef" },
                terminal = terminal?.toDomain(),
                timeoutSeconds = requireNotNull(timeoutSeconds) { "Card payment without timeoutSeconds" },
                createdAt = createdAt.toInstant(),
            )
        }
    }
}

/** E10 → [PaymentState]. An unknown failure `reason` is kept as `null`; the status still decides the outcome. */
internal fun PaymentStateDto.toDomain(): PaymentState =
    PaymentState(
        paymentId = paymentId,
        method = requiredEnum<PaymentMethod>(method, "payment.method"),
        status = requiredEnum<PaymentStatus>(status, "payment.status"),
        amount = amount.toRupiah(),
        expiresAt = expiresAt.toInstantOrNull(),
        paidAt = paidAt.toInstantOrNull(),
        reference = reference,
        issuer = issuer,
        paidOrder =
            order?.let {
                PaidOrder(
                    orderId = it.orderId,
                    orderNumber = it.orderNumber,
                    status = requiredEnum<OrderStatus>(it.status, "payment.order.status"),
                )
            },
        failureReason = reason?.let { enumOrNull<PaymentFailureReason>(it) ?: logUnknownReason() },
        message = message?.toDomain(),
    )

private fun logUnknownReason(): PaymentFailureReason? {
    KioskLog.w("api_enum_dropped", "field" to "payment.reason")
    return null
}

internal fun PaymentUpdateDto.toDomain(): PaymentUpdate =
    PaymentUpdate(paymentId = paymentId, status = requiredEnum<PaymentStatus>(status, "payment.status"))

internal fun TerminalResult.toDto(): TerminalResultDto =
    TerminalResultDto(
        approved = approved,
        terminalRef = terminalRef,
        tid = tid,
        approvalCode = approvalCode,
        cardBrand = cardBrand,
        maskedPan = maskedPan,
        rawResponseCode = rawResponseCode,
    )
