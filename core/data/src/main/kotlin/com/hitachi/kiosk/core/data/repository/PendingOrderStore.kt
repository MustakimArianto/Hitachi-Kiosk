package com.hitachi.kiosk.core.data.repository

import android.database.SQLException
import com.hitachi.kiosk.core.data.api.KioskJson
import com.hitachi.kiosk.core.data.db.dao.PendingOrderDao
import com.hitachi.kiosk.core.data.db.entity.PendingOrderEntity
import com.hitachi.kiosk.core.data.dto.TotalsDto
import com.hitachi.kiosk.core.data.mapper.requiredEnum
import com.hitachi.kiosk.core.data.mapper.toDomain
import com.hitachi.kiosk.core.data.mapper.toDto
import com.hitachi.kiosk.core.data.mapper.toInstant
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PendingOrder
import javax.inject.Inject

/** The part of the pending order the device session needs: forgetting it (Log out, HTTP 401: FR-004 AC3). */
internal fun interface PendingOrderCache {
    /** Forgets the pending order. Never throws. */
    suspend fun clear()
}

/**
 * Where the [PendingOrder] lives (technical-design §6.1, TR-REL-02). Separate from [DefaultOrderRepository] so the
 * session wipe can clear it without depending on the backend, which would be a dependency cycle through the 401
 * handler. Reads and writes may throw [SQLException]; [clear] never does.
 */
internal interface PendingOrderStore : PendingOrderCache {
    /** The stored order, or `null`. Throws [IllegalArgumentException] for a row that no longer parses. */
    suspend fun read(): PendingOrder?

    /** Stores [order] as the only pending order. */
    suspend fun replace(order: PendingOrder)

    /** Records the payment on [orderId]; another order is left alone. */
    suspend fun updatePayment(
        orderId: String,
        paymentId: String,
        method: PaymentMethod,
    )

    /** Forgets [orderId] only. */
    suspend fun delete(orderId: String)
}

/** The Room `pending_order` table behind [PendingOrderStore]. */
internal class RoomPendingOrderStore
    @Inject
    constructor(
        private val dao: PendingOrderDao,
    ) : PendingOrderStore {
        override suspend fun read(): PendingOrder? = dao.read()?.toDomain()

        override suspend fun replace(order: PendingOrder) = dao.replace(order.toEntity())

        override suspend fun updatePayment(
            orderId: String,
            paymentId: String,
            method: PaymentMethod,
        ) = dao.updatePayment(orderId, paymentId, method.name)

        override suspend fun delete(orderId: String) = dao.delete(orderId)

        override suspend fun clear() {
            try {
                dao.deleteAll()
            } catch (e: SQLException) {
                KioskLog.e("pending_order_clear_failed", "type" to e.javaClass.simpleName)
            }
        }
    }

private fun PendingOrder.toEntity(): PendingOrderEntity =
    PendingOrderEntity(
        orderId = orderId,
        orderNumber = orderNumber,
        paymentId = paymentId,
        method = method?.name,
        idempotencyKey = idempotencyKey.value,
        totalsJson = KioskJson.encodeToString(TotalsDto.serializer(), totals.toDto()),
        createdAt = createdAt.toString(),
    )

private fun PendingOrderEntity.toDomain(): PendingOrder =
    PendingOrder(
        orderId = orderId,
        orderNumber = orderNumber,
        paymentId = paymentId,
        method = method?.let { requiredEnum<PaymentMethod>(it, "pending_order.method") },
        idempotencyKey = IdempotencyKey(idempotencyKey),
        totals = KioskJson.decodeFromString(TotalsDto.serializer(), totalsJson).toDomain(),
        createdAt = createdAt.toInstant(),
    )
