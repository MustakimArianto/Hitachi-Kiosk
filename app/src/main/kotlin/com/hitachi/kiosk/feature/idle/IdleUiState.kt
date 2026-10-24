package com.hitachi.kiosk.feature.idle

import androidx.compose.runtime.Immutable

/**
 * SCR-04 Idle (FR-006; `Idle.dc.html`, `S_Idle_issues`). The screen draws exactly this; the slideshow timing is
 * motion and lives in the screen.
 *
 * @property brandName the merchant name of the top chip, or `null` before a catalog is cached (the chip is hidden).
 * @property slides the promo slideshow, never empty: without promo images it is one tone placeholder (TC-024).
 * @property showIssueDot the pulsing red dot on the staff corner (A-31). Nothing sets it in 0.3.0; DEV-073 wires it to
 *   the hardware and network status.
 */
@Immutable
data class IdleUiState(
    val brandName: String? = null,
    val slides: List<IdleSlideUi> = listOf(IdleSlideUi(imageUrl = null)),
    val showIssueDot: Boolean = false,
)

/** One full-screen promo slide; a `null` [imageUrl] shows the tone placeholder (NFR-013). */
@Immutable
data class IdleSlideUi(
    val imageUrl: String?,
)

/** What the Idle screen can ask for. */
sealed interface IdleEvent {
    /** Idle came on screen (first time or back from an order): the order in progress ends (FR-006 AC5). */
    data object Shown : IdleEvent

    /** A tap anywhere except the staff corner (FR-006 AC2). */
    data object TapToOrder : IdleEvent

    /** The staff corner was held for 2 s (FR-006 AC3). */
    data object StaffHold : IdleEvent
}

/** One-off effects of SCR-04. */
sealed interface IdleEffect {
    /** To SCR-05 Order type. */
    data object StartOrder : IdleEffect

    /** To the staff PIN (SCR-14). */
    data object OpenStaffSettings : IdleEffect
}
