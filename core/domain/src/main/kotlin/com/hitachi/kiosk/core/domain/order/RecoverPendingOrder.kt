package com.hitachi.kiosk.core.domain.order

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdGenerator
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.OrderStatus
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.PendingOrder
import com.hitachi.kiosk.core.domain.model.Totals
import com.hitachi.kiosk.core.domain.payment.PaymentGateway
import com.hitachi.kiosk.core.domain.payment.isNonPaidFinal
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.core.domain.retryWithBackoff
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * An order that the backend reports `PAID` after the app was killed during its payment (FR-016 AC4, A-11). The pending
 * order is already forgotten when this is handed over, so this is everything the Complete screen gets: the 0.6.0
 * Complete screen prints from it. The order lines are not persisted (architecture §5.3), so a receipt printed from a
 * recovery has the totals and the payment details but not the line list.
 *
 * @property orderNumber the number from E10, or the one saved with the pending order when E10 does not carry it.
 */
data class RecoveredPaidOrder(
    val orderId: String,
    val orderNumber: String,
    val paymentId: String?,
    val method: PaymentMethod?,
    val totals: Totals,
    val paidAt: Instant?,
    val reference: String?,
    val issuer: String?,
)

/** What [RecoverPendingOrder] ended in. */
sealed interface RecoveryOutcome {
    /** There was no pending order. */
    data object NothingPending : RecoveryOutcome

    /** The order was cancelled on the backend (E8) and forgotten: the kiosk can be Idle. */
    data object Cancelled : RecoveryOutcome

    /** The backend says the order is paid: go to Complete with [order]. The pending order is already cleared. */
    data class Paid(
        val order: RecoveredPaidOrder,
    ) : RecoveryOutcome

    /**
     * The backend could not be asked (network, server) or did not give a final answer ([error]). The pending order is
     * left untouched so the next run can decide: it is never silently dropped (technical-design §8.6).
     */
    data class Deferred(
        val error: DomainError,
    ) : RecoveryOutcome
}

/**
 * Settles the order that survived a process death (FR-016 "Process death", architecture §5.3, technical-design §8.6,
 * DEV-052, TR-REL-02). The pending order is read from [OrderRepository] and decided from what the backend says; the
 * backend is only reached through [OrderRepository] (E8) and [PaymentGateway] (E10, E11).
 *
 * | Stored state | Backend says | Action |
 * |---|---|---|
 * | nothing | | nothing ([RecoveryOutcome.NothingPending]) |
 * | order, no payment | | E8 `APP_RECOVERY`, forget, Idle |
 * | payment | E10 `PAID` | forget, Complete with the backend order number |
 * | payment | E10 `FAILED`, `EXPIRED`, `CANCELLED` | E8, forget, Idle |
 * | payment | E10 `PENDING` | E11 `EXPIRED_ON_KIOSK`, then E8, forget, Idle |
 * | payment | E10 `PROCESSING` | poll E10 every [pollInterval] for up to [processingWait]; `PAID` as above, a non-paid
 *   final status as above, still unresolved after the wait: treated like `PENDING` |
 *
 * **Decision for the PO (architecture §5.3 says `PENDING` resumes Checkout):** the QR string is deliberately not
 * persisted (TR-SEC, NFR-006), so Checkout cannot be redrawn after a restart. A payment that is still `PENDING` is
 * therefore cancelled (E11, then E8) instead of resumed, and the customer starts again. `PROCESSING` is never
 * cancelled at once, because money may be in flight; it is polled first. Whatever E11 answers, a 409 means the payment
 * moved on, so E10 is read again before anything else, exactly like `PaymentStateMachine` does: it may be `PAID`.
 * A payment that stays `PROCESSING` after [processingWait] and cannot be cancelled is left pending
 * ([RecoveryOutcome.Deferred]).
 *
 * **Failures never drop the order.** Any call that fails (after its own retries: E10 here with 1 s, 2 s, 4 s backoff,
 * E8 and E11 inside their repositories) ends the run as [RecoveryOutcome.Deferred] with the pending order untouched;
 * the caller shows Idle and runs this again on the next `ONLINE` transition. A second run after a finished run finds
 * nothing and does nothing, so recovery is idempotent.
 *
 * Callers run it one at a time and while no new order can be started: [OrderRepository.clearPending] and the E7 of a
 * new order share the single pending row.
 */
class RecoverPendingOrder(
    private val orders: OrderRepository,
    private val gateway: PaymentGateway,
    private val ids: IdGenerator,
    private val pollInterval: Duration = 2.seconds,
    private val processingWait: Duration = 60.seconds,
) {
    suspend operator fun invoke(): RecoveryOutcome {
        val pending = orders.pending() ?: return RecoveryOutcome.NothingPending
        val paymentId = pending.paymentId
        val outcome = if (paymentId == null) cancelOrder(pending) else recoverPayment(pending, paymentId)
        KioskLog.i("order_recovery", "outcome" to outcome::class.simpleName, "withPayment" to (paymentId != null))
        return outcome
    }

    private suspend fun recoverPayment(
        pending: PendingOrder,
        paymentId: String,
    ): RecoveryOutcome =
        when (val read = readStatus(pending, paymentId)) {
            is Outcome.Failure -> RecoveryOutcome.Deferred(read.error)
            is Outcome.Success -> decide(pending, paymentId, read.value) ?: waitForProcessing(pending, paymentId)
        }

    /** What [state] means for the pending order, or `null` while the payment is `PROCESSING` and nothing is decided. */
    private suspend fun decide(
        pending: PendingOrder,
        paymentId: String,
        state: PaymentState,
    ): RecoveryOutcome? =
        when (state.status) {
            PaymentStatus.PAID -> paid(pending, state)
            PaymentStatus.FAILED, PaymentStatus.EXPIRED, PaymentStatus.CANCELLED -> cancelOrder(pending)
            PaymentStatus.PENDING -> cancelPayment(pending, paymentId)
            PaymentStatus.PROCESSING -> null
        }

    /**
     * `PROCESSING`: the gateway is still deciding, so ask again every [pollInterval] up to [processingWait]. A payment
     * that is still `PROCESSING` after the wait is treated like `PENDING`.
     */
    private suspend fun waitForProcessing(
        pending: PendingOrder,
        paymentId: String,
    ): RecoveryOutcome {
        var waited = Duration.ZERO
        while (waited < processingWait) {
            delay(pollInterval)
            waited += pollInterval
            val decided =
                when (val read = readStatus(pending, paymentId)) {
                    is Outcome.Failure -> RecoveryOutcome.Deferred(read.error)
                    is Outcome.Success -> decide(pending, paymentId, read.value)
                }
            if (decided != null) return decided
        }
        return cancelPayment(pending, paymentId)
    }

    /** E11, then E8. A 409 or an answer that is not a cancel sends us back to E10 first (api-spec E11). */
    private suspend fun cancelPayment(
        pending: PendingOrder,
        paymentId: String,
    ): RecoveryOutcome {
        val key = ids.newIdempotencyKey()
        val cancel = gateway.cancel(pending.orderId, paymentId, CancelPaymentReason.EXPIRED_ON_KIOSK, key)
        return when {
            cancel is Outcome.Success && cancel.value.status.isNonPaidFinal -> cancelOrder(pending)
            cancel is Outcome.Failure && cancel.error !is DomainError.Conflict -> RecoveryOutcome.Deferred(cancel.error)
            else -> readAfterRefusedCancel(pending, paymentId, cancel)
        }
    }

    /** The payment moved on while E11 ran (409), so the backend's own status decides, and `PAID` wins (A-15). */
    private suspend fun readAfterRefusedCancel(
        pending: PendingOrder,
        paymentId: String,
        cancel: Outcome<*>,
    ): RecoveryOutcome {
        val state =
            when (val read = readStatus(pending, paymentId)) {
                is Outcome.Failure -> return RecoveryOutcome.Deferred(read.error)
                is Outcome.Success -> read.value
            }
        return when {
            state.status == PaymentStatus.PAID -> paid(pending, state)
            state.status.isNonPaidFinal -> cancelOrder(pending)
            else -> RecoveryOutcome.Deferred((cancel as? Outcome.Failure)?.error ?: DomainError.Unknown)
        }
    }

    /** E8 `APP_RECOVERY`. `ORDER_ALREADY_PAID` means the order was paid after all (api-spec E8): Complete. */
    private suspend fun cancelOrder(pending: PendingOrder): RecoveryOutcome =
        when (val cancel = orders.cancel(pending.orderId, CancelOrderReason.APP_RECOVERY)) {
            is Outcome.Success -> {
                when (cancel.value.status) {
                    OrderStatus.CANCELLED -> RecoveryOutcome.Cancelled
                    OrderStatus.PAID -> paid(pending, state = null)
                    OrderStatus.AWAITING_PAYMENT -> RecoveryOutcome.Deferred(DomainError.Unknown)
                }
            }

            is Outcome.Failure -> {
                val error = cancel.error
                if (error is DomainError.Conflict && error.code == ORDER_ALREADY_PAID) {
                    paid(pending, state = null)
                } else {
                    RecoveryOutcome.Deferred(error)
                }
            }
        }

    /** E10 with the backoff of technical-design §5.3: the gateway does not retry a status read itself. */
    private suspend fun readStatus(
        pending: PendingOrder,
        paymentId: String,
    ): Outcome<PaymentState> = retryWithBackoff { gateway.status(pending.orderId, paymentId) }

    /** The order is paid: forget it and hand it to Complete. Only here is a recovery allowed to clear the row. */
    private suspend fun paid(
        pending: PendingOrder,
        state: PaymentState?,
    ): RecoveryOutcome {
        orders.clearPending()
        return RecoveryOutcome.Paid(
            RecoveredPaidOrder(
                orderId = pending.orderId,
                orderNumber = state?.paidOrder?.orderNumber ?: pending.orderNumber,
                paymentId = pending.paymentId,
                method = state?.method ?: pending.method,
                totals = pending.totals,
                paidAt = state?.paidAt,
                reference = state?.reference,
                issuer = state?.issuer,
            ),
        )
    }

    private companion object {
        const val ORDER_ALREADY_PAID = "ORDER_ALREADY_PAID"
    }
}
