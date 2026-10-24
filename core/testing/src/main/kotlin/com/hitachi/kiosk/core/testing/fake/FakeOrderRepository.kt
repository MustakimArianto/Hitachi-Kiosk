package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.CreatedOrder
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.OrderStatus
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PendingOrder
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData

/**
 * A scriptable [OrderRepository] (FR-016). [create] and [cancel] answer from their scripts and keep the pending order
 * the way the real repository does, so payment and recovery tests see the same state. [requests] and [createKeys]
 * record every call so tests can check bodies and idempotency keys.
 */
class FakeOrderRepository(
    initialPending: PendingOrder? = null,
) : OrderRepository {
    val create = Script<CreatedOrder>(Outcome.Success(SampleData.createdOrder))
    val cancel = Script<CancelledOrder>(Outcome.Success(CancelledOrder(SampleData.ORDER_ID, OrderStatus.CANCELLED)))

    private val lock = Any()
    private val recordedRequests = mutableListOf<OrderRequest>()
    private val recordedKeys = mutableListOf<IdempotencyKey>()
    private val recordedCancels = mutableListOf<Pair<String, CancelOrderReason>>()
    private var pendingOrder: PendingOrder? = initialPending

    val requests: List<OrderRequest> get() = synchronized(lock) { recordedRequests.toList() }
    val createKeys: List<IdempotencyKey> get() = synchronized(lock) { recordedKeys.toList() }
    val cancels: List<Pair<String, CancelOrderReason>> get() = synchronized(lock) { recordedCancels.toList() }

    /** The total of the pending order, without suspending, for fakes that must agree with it. */
    val pendingTotal: Rupiah? get() = synchronized(lock) { pendingOrder?.totals?.total }

    override suspend fun create(
        request: OrderRequest,
        key: IdempotencyKey,
    ): Outcome<CreatedOrder> {
        synchronized(lock) {
            recordedRequests += request
            recordedKeys += key
        }
        // The sample order answers with the total of the request, like the real backend with correct pricing.
        val scripted = create.next()
        val outcome =
            if (scripted is Outcome.Success && scripted.value === SampleData.createdOrder) {
                Outcome.Success(scripted.value.copy(totals = request.clientTotals))
            } else {
                scripted
            }
        if (outcome is Outcome.Success) {
            val order = outcome.value
            synchronized(lock) {
                pendingOrder =
                    PendingOrder(
                        orderId = order.orderId,
                        orderNumber = order.orderNumber,
                        paymentId = null,
                        method = null,
                        idempotencyKey = key,
                        totals = order.totals,
                        createdAt = order.createdAt,
                    )
            }
        }
        return outcome
    }

    override suspend fun cancel(
        orderId: String,
        reason: CancelOrderReason,
    ): Outcome<CancelledOrder> {
        synchronized(lock) { recordedCancels += orderId to reason }
        val outcome = cancel.next()
        if (outcome is Outcome.Success) synchronized(lock) { pendingOrder = null }
        return outcome
    }

    override suspend fun pending(): PendingOrder? = synchronized(lock) { pendingOrder }

    override suspend fun recordPayment(
        orderId: String,
        paymentId: String,
        method: PaymentMethod,
    ) {
        synchronized(lock) {
            pendingOrder = pendingOrder?.takeIf { it.orderId == orderId }?.copy(paymentId = paymentId, method = method)
                ?: pendingOrder
        }
    }

    override suspend fun clearPending() {
        synchronized(lock) { pendingOrder = null }
    }
}
