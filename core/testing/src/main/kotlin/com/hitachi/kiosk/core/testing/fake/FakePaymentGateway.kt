package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.domain.model.TerminalResult
import com.hitachi.kiosk.core.domain.payment.PaymentGateway
import com.hitachi.kiosk.core.testing.fixture.SampleData

/**
 * A scriptable [PaymentGateway] for payment state machine tests (FR-017–FR-019). Answers default to the api-spec
 * happy path; [startKeys] records every idempotency key so tests can check that each attempt uses a new one.
 */
class FakePaymentGateway : PaymentGateway {
    val startQris = Script<StartedPayment>(Outcome.Success(SampleData.qrisPayment))
    val startCard = Script<StartedPayment>(Outcome.Success(SampleData.cardPayment))
    val status = Script<PaymentState>(Outcome.Success(SampleData.paymentPending))
    val cancel = Script<PaymentUpdate>(Outcome.Success(SampleData.paymentCancelled))
    val relay = Script<PaymentUpdate>(Outcome.Success(SampleData.terminalRelayed))

    private val keys = mutableListOf<IdempotencyKey>()

    val startKeys: List<IdempotencyKey> get() = synchronized(keys) { keys.toList() }

    override suspend fun start(
        orderId: String,
        method: PaymentMethod,
        qrExpirySeconds: Int?,
        key: IdempotencyKey,
    ): Outcome<StartedPayment> {
        synchronized(keys) { keys += key }
        return when (method) {
            PaymentMethod.QRIS -> startQris.next()
            PaymentMethod.CARD -> startCard.next()
        }
    }

    override suspend fun status(
        orderId: String,
        paymentId: String,
    ): Outcome<PaymentState> = status.next()

    override suspend fun cancel(
        orderId: String,
        paymentId: String,
        reason: CancelPaymentReason,
        key: IdempotencyKey,
    ): Outcome<PaymentUpdate> = cancel.next()

    override suspend fun relayTerminalResult(
        orderId: String,
        paymentId: String,
        result: TerminalResult,
    ): Outcome<PaymentUpdate> = relay.next()
}
