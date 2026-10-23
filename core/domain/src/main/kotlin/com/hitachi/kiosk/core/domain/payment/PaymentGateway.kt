package com.hitachi.kiosk.core.domain.payment

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.domain.model.TerminalResult

/**
 * The company payment gateway as the kiosk sees it: always through the backend, E9–E12 (architecture §2, NFR-012).
 * The `PaymentStateMachine` (DEV-047) depends on this port only, so its tests script payments without a backend.
 */
interface PaymentGateway {
    /** Starts a payment for [orderId]. [key] is new for every attempt, including "Try again" (technical-design §5.3). */
    suspend fun start(
        orderId: String,
        method: PaymentMethod,
        qrExpirySeconds: Int?,
        key: IdempotencyKey,
    ): Outcome<StartedPayment>

    /** Reads the backend status. Only `PAID` here completes an order (A-15). */
    suspend fun status(
        orderId: String,
        paymentId: String,
    ): Outcome<PaymentState>

    suspend fun cancel(
        orderId: String,
        paymentId: String,
        reason: CancelPaymentReason,
        key: IdempotencyKey,
    ): Outcome<PaymentUpdate>

    /** Relays a terminal result when the EDC reports to the kiosk (OQ-03). The payment still waits for `PAID`. */
    suspend fun relayTerminalResult(
        orderId: String,
        paymentId: String,
        result: TerminalResult,
    ): Outcome<PaymentUpdate>
}
