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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.time.Instant

// Payment statuses and methods are required: an unknown one fails the call (DomainError.Unknown) instead of being
// guessed, so the payment state machine keeps polling and never shows "paid" or "not charged" on a guess (A-15,
// FR-019).

/**
 * E9 → [StartedPayment]. A QRIS answer needs `qrString` and `expiresAt`, a card answer `terminalRef`. [serverTime] is
 * the response `Date` header (technical-design §5.4); only the QR countdown uses it.
 */
internal fun StartedPaymentDto.toDomain(serverTime: Instant? = null): StartedPayment {
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
                serverTime = serverTime,
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

/**
 * A `Date` response header (RFC 1123, whole seconds) as an instant, or `null` when it is missing or malformed. The
 * kiosk measures the backend clock offset with it for the QR countdown (NFR-017, technical-design §5.4).
 */
internal fun String?.httpDateToInstantOrNull(): Instant? =
    this?.let {
        try {
            Instant.fromEpochSeconds(ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond())
        } catch (_: DateTimeParseException) {
            null
        }
    }
