package com.hitachi.kiosk.core.data.backend

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.domain.model.TerminalResult
import com.hitachi.kiosk.core.domain.payment.PaymentGateway
import com.hitachi.kiosk.core.domain.retryWithBackoff
import javax.inject.Inject

/**
 * The [PaymentGateway] port over the backend, E9–E12 (architecture §2, NFR-012): the kiosk never talks to the
 * gateway directly. Delegates to whichever [BackendApi] `:app` binds (real, or a debug wrapper).
 *
 * Retries follow technical-design §5.3: E9 and E11 carry an `Idempotency-Key`, so they are retried with the same key
 * after a network error, a 5xx or a 429 (1 s, 2 s, 4 s). E10 is not retried here, because the payment state machine
 * polls it every 2 s and treats a failed poll as "no answer yet". E12 has no key, so it is never retried (the machine
 * keeps polling E10 instead).
 */
class BackendPaymentGateway
    @Inject
    constructor(
        private val backend: BackendApi,
    ) : PaymentGateway {
        override suspend fun start(
            orderId: String,
            method: PaymentMethod,
            qrExpirySeconds: Int?,
            key: IdempotencyKey,
        ): Outcome<StartedPayment> = retryWithBackoff { backend.startPayment(orderId, method, qrExpirySeconds, key) }

        override suspend fun status(
            orderId: String,
            paymentId: String,
        ): Outcome<PaymentState> = backend.paymentStatus(orderId, paymentId)

        override suspend fun cancel(
            orderId: String,
            paymentId: String,
            reason: CancelPaymentReason,
            key: IdempotencyKey,
        ): Outcome<PaymentUpdate> = retryWithBackoff { backend.cancelPayment(orderId, paymentId, reason, key) }

        override suspend fun relayTerminalResult(
            orderId: String,
            paymentId: String,
            result: TerminalResult,
        ): Outcome<PaymentUpdate> = backend.relayTerminalResult(orderId, paymentId, result)
    }
