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
 * happy path; [startKeys] records every idempotency key so tests can check that each attempt uses a new one, and
 * [starts], [cancels] and [relays] record every call with its arguments.
 */
class FakePaymentGateway : PaymentGateway {
    /** One E9 call. */
    data class Start(
        val orderId: String,
        val method: PaymentMethod,
        val qrExpirySeconds: Int?,
        val key: IdempotencyKey,
    )

    /** One E11 call. */
    data class Cancel(
        val orderId: String,
        val paymentId: String,
        val reason: CancelPaymentReason,
        val key: IdempotencyKey,
    )

    val startQris = Script<StartedPayment>(Outcome.Success(SampleData.qrisPayment))
    val startCard = Script<StartedPayment>(Outcome.Success(SampleData.cardPayment))
    val status = Script<PaymentState>(Outcome.Success(SampleData.paymentPending))
    val cancel = Script<PaymentUpdate>(Outcome.Success(SampleData.paymentCancelled))
    val relay = Script<PaymentUpdate>(Outcome.Success(SampleData.terminalRelayed))

    private val lock = Any()
    private val recordedStarts = mutableListOf<Start>()
    private val recordedCancels = mutableListOf<Cancel>()
    private val recordedRelays = mutableListOf<TerminalResult>()

    val startKeys: List<IdempotencyKey> get() = synchronized(lock) { recordedStarts.map { it.key } }

    /** Every E9 call, in order. */
    val starts: List<Start> get() = synchronized(lock) { recordedStarts.toList() }

    /** Every E11 call, in order. */
    val cancels: List<Cancel> get() = synchronized(lock) { recordedCancels.toList() }

    /** Every E12 body, in order. */
    val relays: List<TerminalResult> get() = synchronized(lock) { recordedRelays.toList() }

    override suspend fun start(
        orderId: String,
        method: PaymentMethod,
        qrExpirySeconds: Int?,
        key: IdempotencyKey,
    ): Outcome<StartedPayment> {
        synchronized(lock) { recordedStarts += Start(orderId, method, qrExpirySeconds, key) }
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
    ): Outcome<PaymentUpdate> {
        synchronized(lock) { recordedCancels += Cancel(orderId, paymentId, reason, key) }
        return cancel.next()
    }

    override suspend fun relayTerminalResult(
        orderId: String,
        paymentId: String,
        result: TerminalResult,
    ): Outcome<PaymentUpdate> {
        synchronized(lock) { recordedRelays += result }
        return relay.next()
    }
}
