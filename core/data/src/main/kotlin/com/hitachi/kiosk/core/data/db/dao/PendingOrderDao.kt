package com.hitachi.kiosk.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hitachi.kiosk.core.data.db.entity.PendingOrderEntity

/** The `pending_order` table: zero or one row (technical-design §6.1). */
@Dao
internal abstract class PendingOrderDao {
    /** Replaces whatever is stored with [order], in one transaction: only the newest order is ever pending. */
    @Transaction
    open suspend fun replace(order: PendingOrderEntity) {
        deleteAll()
        insert(order)
    }

    @Query("SELECT * FROM pending_order LIMIT 1")
    abstract suspend fun read(): PendingOrderEntity?

    /** Records the payment E9 created on [orderId]. Changes nothing for another order. */
    @Query("UPDATE pending_order SET paymentId = :paymentId, method = :method WHERE orderId = :orderId")
    abstract suspend fun updatePayment(
        orderId: String,
        paymentId: String,
        method: String,
    )

    /** Forgets [orderId] only, so cancelling an old order never drops a newer pending one. */
    @Query("DELETE FROM pending_order WHERE orderId = :orderId")
    abstract suspend fun delete(orderId: String)

    @Query("DELETE FROM pending_order")
    abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(order: PendingOrderEntity)
}
