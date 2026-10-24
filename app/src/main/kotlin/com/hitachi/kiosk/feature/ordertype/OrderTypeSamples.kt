package com.hitachi.kiosk.feature.ordertype

import com.hitachi.kiosk.core.domain.model.OrderType

/** Sample states of `OrderType.dc.html` for previews and screenshot tests ("Urban Grill"). */
internal object OrderTypeSamples {
    val Default =
        OrderTypeUiState(
            brandName = "Urban Grill",
            options = listOf(OrderType.DINE_IN, OrderType.TAKEAWAY),
        )

    /** `S_OrderType_singleOption`: Takeaway is not offered. */
    val SingleOption =
        OrderTypeUiState(
            brandName = "Urban Grill",
            options = listOf(OrderType.DINE_IN),
            unavailable = OrderType.TAKEAWAY,
        )

    /** Dine in picked: accent fill and check, Takeaway dimmed. */
    val Selected = Default.copy(picked = OrderType.DINE_IN)
}
