package com.hitachi.kiosk.core.domain.repository

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.CreatedOrder
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PendingOrder

/**
 * Creates and cancels orders on the backend (E7, E8) and keeps the [PendingOrder] that process-death recovery reads
 * (architecture §5.3, technical-design §5.3, FR-016). Network errors, 5xx and 429 are retried with the same key
 * (1 s, 2 s, 4 s); business errors (`PRICE_CHANGED`, `ITEM_UNAVAILABLE`, conflicts) are returned as they are.
 */
interface OrderRepository {
    /**
     * E7 with [key]. On success the order is saved as the pending order before this returns, so a process death
     * right after still finds it. The caller reuses [key] for every retry of the same body and uses a new one when
     * the body changes (BR-08).
     */
    suspend fun create(
        request: OrderRequest,
        key: IdempotencyKey,
    ): Outcome<CreatedOrder>

    /** E8. When the order ends up cancelled the pending order is cleared; a failed cancel leaves it for recovery. */
    suspend fun cancel(
        orderId: String,
        reason: CancelOrderReason,
    ): Outcome<CancelledOrder>

    /** The order that has not ended yet, or `null`. */
    suspend fun pending(): PendingOrder?

    /** Saves the payment E9 created on the pending order, so recovery can query it (architecture §5.3). */
    suspend fun recordPayment(
        orderId: String,
        paymentId: String,
        method: PaymentMethod,
    )

    /** Forgets the pending order: the payment ended, or the order was handed over to Complete. */
    suspend fun clearPending()
}
