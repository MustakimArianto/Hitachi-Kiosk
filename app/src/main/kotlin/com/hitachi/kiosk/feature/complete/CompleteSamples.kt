package com.hitachi.kiosk.feature.complete

import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentMethod

/** Sample states for previews and the Complete screenshot boards, after the design's sample order (A247, Rp 173.000). */
internal object CompleteSamples {
    /** `Complete.dc.html` with the dine-in scenario. */
    val DineIn =
        CompleteUiState(
            orderNumber = "A247",
            amount = "Rp 173.000",
            method = PaymentMethod.QRIS,
            orderType = OrderType.DINE_IN,
        )

    /** `S_Complete_takeaway`. */
    val Takeaway = DineIn.copy(orderType = OrderType.TAKEAWAY)

    /** A card payment. */
    val Card = DineIn.copy(method = PaymentMethod.CARD)

    /** An order that a recovery found paid: the order type is not known (A-47). */
    val Recovered = DineIn.copy(orderType = null)
}
