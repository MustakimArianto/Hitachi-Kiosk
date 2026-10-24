package com.hitachi.kiosk.feature.upsell

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah

/**
 * SCR-09 Upsell (FR-014; `Upsell.dc.html`, `S_Upsell_noSuggestions`). The screen draws exactly this; the ViewModel
 * computes the suggestions, the counts and the amounts.
 *
 * @property loaded false until the catalog and the suggestions are known; the grid area stays blank instead of
 *   flashing the "Nothing more to suggest" state.
 * @property suggestions the 2 × 2 grid, fixed when the screen opens, so a card that was just added stays in place.
 *   Empty shows the "Nothing more to suggest" state with the single "Review order" button (TC-172).
 * @property itemCount the strip's "N items".
 * @property subtotal the amount on the strip and on "Continue · Rp X": the cart subtotal, as the design and the Menu
 *   dock show it. Promo, tax and rounding first appear on Your order (FR-013).
 * @property thumbnails the strip's thumbnails: the last three lines, most recent first.
 * @property addSerial counts successful quick adds. Each change bumps the count and "Continue" and rolls the amounts.
 */
@Immutable
data class UpsellUiState(
    val loaded: Boolean = false,
    val orderType: OrderType = OrderType.DINE_IN,
    val suggestions: List<UpsellSuggestionUi> = emptyList(),
    val itemCount: Int = 0,
    val subtotal: String = IdrFormatter.format(Rupiah.Zero),
    val thumbnails: List<UpsellThumbnailUi> = emptyList(),
    val addSerial: Int = 0,
) {
    /** `S_Upsell_noSuggestions`: "You're all set", the empty panel and only "Review order". */
    val noSuggestions: Boolean get() = loaded && suggestions.isEmpty()
}

/**
 * One suggestion card.
 *
 * @property role drawn as the card's chip ("Side", "Dessert").
 * @property description the backend description, shown before the calories on the note line ("Sea salt · 320 kcal").
 * @property price the formatted unit price ("Rp 26.000").
 * @property tone the category placeholder tone (`#RRGGBB`).
 * @property addedQuantity how many of it the cart holds: above zero the card turns accent and reads "N added".
 * @property justAdded the "+" shows the accent check for 1.4 s after a quick add.
 */
@Immutable
data class UpsellSuggestionUi(
    val id: String,
    val name: String,
    val role: ProductRole,
    val description: String?,
    val kcal: Int?,
    val price: String,
    val imageUrl: String?,
    val tone: String?,
    val addedQuantity: Int = 0,
    val justAdded: Boolean = false,
) {
    val inCart: Boolean get() = addedQuantity > 0
}

/** A strip thumbnail of one cart line. */
@Immutable
data class UpsellThumbnailUi(
    val lineId: String,
    val imageUrl: String?,
    val tone: String?,
)

/** What SCR-09 can ask for. */
sealed interface UpsellEvent {
    /** The round "+" on a card: one unit with the default options (FR-014 AC3). */
    data class QuickAdd(
        val productId: String,
    ) : UpsellEvent

    /** "No thanks". */
    data object NoThanks : UpsellEvent

    /** "Continue · Rp X", or "Review order" when there is nothing to suggest. */
    data object Continue : UpsellEvent

    /** "‹ Menu". */
    data object BackToMenu : UpsellEvent
}

/** One-off navigation of SCR-09 (PRD §5.1). */
sealed interface UpsellEffect {
    /** To SCR-10 Your order. */
    data object OpenYourOrder : UpsellEffect

    /** Back to SCR-06 Menu. */
    data object BackToMenu : UpsellEffect
}
