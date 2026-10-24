package com.hitachi.kiosk.core.data.repository

import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.PendingOrder
import com.hitachi.kiosk.core.testing.fixture.SampleData

/** The pending order E7 leaves behind for the api-spec sample order, created with `key-1` (architecture §5.3). */
internal val SamplePendingOrder =
    PendingOrder(
        orderId = SampleData.ORDER_ID,
        orderNumber = SampleData.createdOrder.orderNumber,
        paymentId = null,
        method = null,
        idempotencyKey = IdempotencyKey("key-1"),
        totals = SampleData.createdOrder.totals,
        createdAt = SampleData.createdOrder.createdAt,
    )
