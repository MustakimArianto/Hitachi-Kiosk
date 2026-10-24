package com.hitachi.kiosk.core.data.repository

import android.database.SQLException
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdGenerator
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.CreatedOrder
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.OrderStatus
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PendingOrder
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.core.domain.retryWithBackoff
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Creates and cancels orders (E7, E8) and keeps the [PendingOrder] for process-death recovery (FR-016, DEV-045,
 * technical-design §5.3, architecture §5.3). Both calls carry an `Idempotency-Key`, so [retryWithBackoff] resends them
 * after a network error, 5xx or 429 (1 s, 2 s, 4 s) without ever creating or cancelling twice; business errors
 * (`PRICE_CHANGED`, `ITEM_UNAVAILABLE`, conflicts) are never retried and come back as they are.
 */
internal class DefaultOrderRepository
    @Inject
    constructor(
        private val backend: BackendApi,
        private val store: PendingOrderStore,
        private val ids: IdGenerator,
    ) : OrderRepository {
        /**
         * E7 with the caller's [key] for every retry. The pending order is saved before success is returned, so a
         * process death right after still finds it (TR-REL-02). If that save fails the order cannot be recovered, so
         * this reports [DomainError.Unknown] instead of success: Pay again resends the same body with the same key,
         * the backend answers with the same order, and the save is tried again.
         */
        override suspend fun create(
            request: OrderRequest,
            key: IdempotencyKey,
        ): Outcome<CreatedOrder> {
            val outcome = retryWithBackoff { backend.createOrder(request, key) }
            val order =
                when (outcome) {
                    is Outcome.Failure -> return outcome.also { logFailure("create", it) }
                    is Outcome.Success -> outcome.value
                }
            val pending =
                PendingOrder(
                    orderId = order.orderId,
                    orderNumber = order.orderNumber,
                    paymentId = null,
                    method = null,
                    idempotencyKey = key,
                    totals = order.totals,
                    createdAt = order.createdAt,
                )
            return try {
                store.replace(pending)
                outcome
            } catch (e: SQLException) {
                KioskLog.e("pending_order_save_failed", "type" to e.javaClass.simpleName)
                Outcome.Failure(DomainError.Unknown)
            }
        }

        /**
         * E8 with one key for all of its retries. A cancelled order clears the pending order. Every failure leaves
         * it: after a network failure recovery cancels at the next launch (`APP_RECOVERY`), and after 409
         * `ORDER_ALREADY_PAID` the order was paid, so the caller goes to Complete and clears it from there. That
         * conflict is returned as it is, not mapped, so the payment layer can tell it from a network failure. The
         * api-spec names no other E8 conflict, so none gets special treatment; an order the backend already cancelled
         * is assumed to answer 200 `CANCELLED` again.
         */
        override suspend fun cancel(
            orderId: String,
            reason: CancelOrderReason,
        ): Outcome<CancelledOrder> {
            val key = ids.newIdempotencyKey()
            val outcome = retryWithBackoff { backend.cancelOrder(orderId, reason, key) }
            when (outcome) {
                is Outcome.Failure -> logFailure("cancel", outcome)
                is Outcome.Success -> if (outcome.value.status == OrderStatus.CANCELLED) forget(orderId)
            }
            return outcome
        }

        override suspend fun pending(): PendingOrder? =
            try {
                store.read()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLException) {
                unreadable(e)
            } catch (e: IllegalArgumentException) {
                // A row whose totals, method or timestamp no longer parse.
                unreadable(e)
            }

        override suspend fun recordPayment(
            orderId: String,
            paymentId: String,
            method: PaymentMethod,
        ) {
            try {
                store.updatePayment(orderId, paymentId, method)
            } catch (e: SQLException) {
                KioskLog.e("pending_order_update_failed", "type" to e.javaClass.simpleName)
            }
        }

        override suspend fun clearPending() = store.clear()

        private suspend fun forget(orderId: String) {
            try {
                store.delete(orderId)
            } catch (e: SQLException) {
                KioskLog.e("pending_order_delete_failed", "type" to e.javaClass.simpleName)
            }
        }

        private fun unreadable(e: Exception): PendingOrder? {
            KioskLog.e("pending_order_unreadable", "type" to e.javaClass.simpleName)
            return null
        }

        private fun logFailure(
            call: String,
            failure: Outcome.Failure,
        ) = KioskLog.w("order_call_failed", "call" to call, "error" to failure.error)
    }
