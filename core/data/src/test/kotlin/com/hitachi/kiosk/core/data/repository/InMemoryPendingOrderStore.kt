package com.hitachi.kiosk.core.data.repository

import android.database.sqlite.SQLiteException
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PendingOrder

/**
 * A [PendingOrderStore] in memory, for repository tests on the JVM. [failWrites] makes writes fail like a broken
 * disk, and [readFailure] makes [read] throw, for example a [SQLiteException] or an [IllegalArgumentException] for a
 * row that no longer parses.
 */
internal class InMemoryPendingOrderStore(
    var stored: PendingOrder? = null,
) : PendingOrderStore {
    var failWrites = false
    var readFailure: Exception? = null
    var clears = 0
        private set

    override suspend fun read(): PendingOrder? {
        readFailure?.let { throw it }
        return stored
    }

    override suspend fun replace(order: PendingOrder) {
        if (failWrites) throw SQLiteException("disk full")
        stored = order
    }

    override suspend fun updatePayment(
        orderId: String,
        paymentId: String,
        method: PaymentMethod,
    ) {
        if (failWrites) throw SQLiteException("disk full")
        stored = stored?.takeIf { it.orderId == orderId }?.copy(paymentId = paymentId, method = method) ?: stored
    }

    override suspend fun delete(orderId: String) {
        if (failWrites) throw SQLiteException("disk full")
        if (stored?.orderId == orderId) stored = null
    }

    override suspend fun clear() {
        clears++
        stored = null
    }
}
