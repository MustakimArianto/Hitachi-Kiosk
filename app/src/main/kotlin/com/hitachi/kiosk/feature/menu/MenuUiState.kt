package com.hitachi.kiosk.feature.menu

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.model.BadgeType
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.time.DayPart

/**
 * SCR-06 Menu (FR-009; `Menu.dc.html`, `S_Menu_emptyCategory`, `S_Menu_offline`). The screen draws exactly this; the
 * ViewModel computes every list, flag and amount.
 *
 * @property loaded false until the first state arrives; the grid area stays blank instead of flashing the empty state.
 * @property brandName the merchant brand of the header lockup (config `brandName`).
 * @property selection the active category pill; its products fill the grid.
 * @property showBudgetPlan the slim "Plan by budget" row: Popular only, and only when the planner is enabled (FR-032).
 * @property sectionTitle the heading: `null` on Popular ("Popular now"), otherwise the category name.
 * @property products the visible (available) products of [selection], in menu order. Empty shows "Nothing here right
 *   now" (A-33).
 * @property offline the kiosk is offline: the banner shows, and adds and "View order" are disabled (FR-009 AC3).
 * @property showMaxToast "Maximum 20 per item" is on screen (2.4 s after a refused add).
 * @property addSerial counts successful quick adds. Each change flies [lastAddedProductId]'s thumbnail to the dock and
 *   bumps the dock (DEV-028).
 */
@Immutable
data class MenuUiState(
    val loaded: Boolean = false,
    val brandName: String = "",
    val orderType: OrderType = OrderType.DINE_IN,
    val dayPart: DayPart = DayPart.AFTERNOON,
    val pills: List<MenuPillUi> = listOf(MenuPillUi.Popular(active = true)),
    val selection: MenuSelection = MenuSelection.Popular,
    val showBudgetPlan: Boolean = false,
    val sectionTitle: String? = null,
    val products: List<MenuProductUi> = emptyList(),
    val dock: CartDockUi = CartDockUi(),
    val offline: Boolean = false,
    val showMaxToast: Boolean = false,
    val addSerial: Int = 0,
    val lastAddedProductId: String? = null,
) {
    /** Add buttons and "Choose ›" pills take taps (FR-009 AC3). */
    val addEnabled: Boolean get() = !offline

    /** "View order" takes taps; with an empty cart it still opens Your order (FR-009 rule). */
    val viewOrderEnabled: Boolean get() = !offline
}

/** Which list the grid shows. */
@Immutable
sealed interface MenuSelection {
    /** The featured list, under the "Popular" pill. */
    data object Popular : MenuSelection

    /** One backend category. */
    data class Category(
        val id: String,
    ) : MenuSelection
}

/** One pill of the horizontal category bar, in display order. */
@Immutable
sealed interface MenuPillUi {
    /** "Popular", always first. */
    data class Popular(
        val active: Boolean,
    ) : MenuPillUi

    data class Category(
        val id: String,
        val name: String,
        val active: Boolean,
    ) : MenuPillUi

    /** The "Meals ↗" link to the Combo screen, placed by `combosCategory.sort`. */
    data class Meals(
        val name: String,
    ) : MenuPillUi
}

/**
 * One grid card. Tapping the card opens Item detail.
 *
 * @property price the formatted price ("Rp 52.000").
 * @property tone the category placeholder tone (`#RRGGBB`).
 * @property needsChoice a required group has no default: the card shows "Choose ›" instead of "+" (A-08).
 * @property justAdded the "+" shows the accent check for 1.4 s after a quick add.
 * @property atMax the quick-add line already holds 20, so the "+" is dimmed (FR-009 `maxQty`).
 */
@Immutable
data class MenuProductUi(
    val id: String,
    val name: String,
    val price: String,
    val imageUrl: String?,
    val tone: String?,
    val badge: MenuBadgeUi? = null,
    val needsChoice: Boolean = false,
    val justAdded: Boolean = false,
    val atMax: Boolean = false,
)

/**
 * A card badge. "New" and "Best seller" are drawn in accent, the others in ink (FR-009 AC5).
 *
 * @property label the backend label; `null` uses the type's own string.
 */
@Immutable
data class MenuBadgeUi(
    val type: BadgeType,
    val label: String?,
) {
    val isAccent: Boolean get() = type == BadgeType.NEW || type == BadgeType.BEST_SELLER
}

/**
 * The cart dock.
 *
 * @property total the formatted cart subtotal ("Rp 92.000").
 * @property thumbnails the last three lines, most recent first.
 */
@Immutable
data class CartDockUi(
    val itemCount: Int = 0,
    val total: String = IdrFormatter.format(Rupiah.Zero),
    val thumbnails: List<DockThumbnailUi> = emptyList(),
)

/** A dock thumbnail of one cart line. */
@Immutable
data class DockThumbnailUi(
    val lineId: String,
    val imageUrl: String?,
    val tone: String?,
)

/** What the Menu can ask for. */
sealed interface MenuEvent {
    /** A category pill, or "See popular items" in the empty state. */
    data class Select(
        val selection: MenuSelection,
    ) : MenuEvent

    /** The round "+" on a card: one unit with the default options (DEV-028). */
    data class QuickAdd(
        val productId: String,
    ) : MenuEvent

    /** A card tap or its "Choose ›" pill. */
    data class OpenProduct(
        val productId: String,
    ) : MenuEvent

    /** The "Meals ↗" pill. */
    data object OpenCombos : MenuEvent

    /** The "Plan by budget" row. */
    data object OpenBudgetPlan : MenuEvent

    /** "Dine in · Change". */
    data object ChangeOrderType : MenuEvent

    /** "Cancel": back to Idle, which ends the order. */
    data object Cancel : MenuEvent

    /** "View order →". */
    data object ViewOrder : MenuEvent
}

/** One-off navigation of SCR-06 (PRD §5.1). */
sealed interface MenuEffect {
    data class OpenProduct(
        val productId: String,
    ) : MenuEffect

    data object OpenCombos : MenuEffect

    data object OpenBudgetPlan : MenuEffect

    data object ChangeOrderType : MenuEffect

    data object Cancel : MenuEffect

    /** Straight to SCR-10 Your order. */
    data object ViewOrder : MenuEffect

    /** To SCR-09 Upsell first (FR-014: once per order, when the toggle is on). */
    data object OfferUpsell : MenuEffect
}
