package com.hitachi.kiosk.feature.ordertype

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.domain.model.OrderType

/**
 * SCR-05 Order type (FR-007; `OrderType.dc.html`, `S_OrderType_singleOption`). The screen draws exactly this.
 *
 * @property brandName the merchant name of the header and the "Welcome to" heading; empty before a catalog is cached.
 * @property options the cards, Dine in first. Empty only before a catalog is cached.
 * @property unavailable the type that is not offered when only one is, for the note under the single card
 *   (A-05); `null` with two cards.
 * @property picked the card the customer tapped; set once, then every tap is ignored until the screen shows again
 *   (FR-007 AC2).
 */
@Immutable
data class OrderTypeUiState(
    val brandName: String = "",
    val options: List<OrderType> = emptyList(),
    val unavailable: OrderType? = null,
    val picked: OrderType? = null,
)

/** What the Order type screen can ask for. */
sealed interface OrderTypeEvent {
    /** The screen came on screen (first time, or back from Menu "Change"): nothing is picked. */
    data object Shown : OrderTypeEvent

    /** A tap on a card. */
    data class Pick(
        val orderType: OrderType,
    ) : OrderTypeEvent
}

/** One-off effects of SCR-05. */
sealed interface OrderTypeEffect {
    /** To SCR-06 Menu, [com.hitachi.kiosk.core.designsystem.motion.KioskMotion.OrderType.ADVANCE_MILLIS] after a pick. */
    data object NavigateToMenu : OrderTypeEffect
}
