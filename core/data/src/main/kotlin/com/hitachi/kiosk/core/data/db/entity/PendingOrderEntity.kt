package com.hitachi.kiosk.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The order and payment that survive a process death (technical-design §6.1, architecture §5.3, TR-REL-02): written
 * when E7 succeeds, updated when E9 starts a payment, deleted when the order ends. At most one row exists. Unlike the
 * catalog cache this table never migrates destructively (TR-DAT-04). Nothing here is customer data or a payment
 * secret (NFR-006).
 *
 * @property totalsJson the order totals in their E7 wire form (`TotalsDto`).
 * @property createdAt the E7 `createdAt`, ISO-8601 UTC, so no precision is lost.
 */
@Entity(tableName = "pending_order")
internal data class PendingOrderEntity(
    @PrimaryKey val orderId: String,
    val orderNumber: String,
    val paymentId: String?,
    val method: String?,
    val idempotencyKey: String,
    val totalsJson: String,
    val createdAt: String,
)
