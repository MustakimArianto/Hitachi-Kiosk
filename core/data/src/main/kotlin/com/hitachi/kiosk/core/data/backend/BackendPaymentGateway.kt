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
import javax.inject.Inject

/**
 * The [PaymentGateway] port over the backend, E9–E12 (architecture §2, NFR-012): the kiosk never talks to the
 * gateway directly. Delegates to whichever [BackendApi] `:app` binds (real, or a debug wrapper).
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
        ): Outcome<StartedPayment> = backend.startPayment(orderId, method, qrExpirySeconds, key)

        override suspend fun status(
            orderId: String,
            paymentId: String,
        ): Outcome<PaymentState> = backend.paymentStatus(orderId, paymentId)

        override suspend fun cancel(
            orderId: String,
            paymentId: String,
            reason: CancelPaymentReason,
            key: IdempotencyKey,
        ): Outcome<PaymentUpdate> = backend.cancelPayment(orderId, paymentId, reason, key)

        override suspend fun relayTerminalResult(
            orderId: String,
            paymentId: String,
            result: TerminalResult,
        ): Outcome<PaymentUpdate> = backend.relayTerminalResult(orderId, paymentId, result)
    }
