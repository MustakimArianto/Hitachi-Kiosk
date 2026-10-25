package com.hitachi.kiosk.feature.complete

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentMethod

/**
 * SCR-12 Complete (FR-020; `Complete.dc.html`, `S_Complete_takeaway`). The screen draws exactly this; the flood, badge,
 * check, rings, confetti and digit cascade are motion and live in the composables.
 *
 * @property orderNumber the backend order number, shown large with its first character in accent (D-21, FR-020 AC1).
 * @property amount the formatted amount that was paid, e.g. "Rp 173.000".
 * @property method how it was paid; `null` only for a recovered order whose payment method is unknown.
 * @property orderType the order type of the customer's order; `null` for an order that a recovery found paid, because
 *   the order type is not stored (A-47). The instruction line is neutral then.
 * @property secondsLeft seconds until the screen returns to Idle by itself, 20 down to 0 (FR-020 AC2).
 */
@Immutable
data class CompleteUiState(
    val orderNumber: String = "",
    val amount: String = "",
    val method: PaymentMethod? = null,
    val orderType: OrderType? = null,
    val secondsLeft: Int = RETURN_SECONDS,
) {
    companion object {
        /** The countdown "Back to the start in Ns" starts here (FR-020). */
        const val RETURN_SECONDS: Int = 20
    }
}

/** What the customer can do on Complete. */
sealed interface CompleteEvent {
    /** The screen is on display: the paid payment is handed over and the pending order forgotten (technical-design §8.6). */
    data object Shown : CompleteEvent

    /** "Done": back to Idle at once (FR-020 AC3). */
    data object Done : CompleteEvent
}

/** One-off effects of SCR-12. */
sealed interface CompleteEffect {
    /** Back to SCR-04: "Done", the 20 s timer, or nothing to show. Idle ends the order (FR-006 AC5). */
    data object ToIdle : CompleteEffect
}
