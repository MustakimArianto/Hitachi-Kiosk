package com.hitachi.kiosk.feature.item

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.designsystem.component.RollDirection
import com.hitachi.kiosk.core.domain.model.Level

/**
 * SCR-08 Item detail (FR-010; `ItemDetail.dc.html` and its `S_ItemDetail_*` boards). The screen draws exactly this;
 * the ViewModel computes every flag and amount.
 *
 * @property loaded false until the cached catalog has been read; the screen then shows only its background.
 * @property product the info block, or `null` when the menu no longer has the product (shown as sold out).
 * @property soldOut "No longer available": banner, dimmed hero and a disabled CTA (`S_ItemDetail_soldOut`).
 * @property sections the modifier groups in screen order (see [ItemSectionUi]).
 * @property quantityRoll which way the quantity last changed, so it rolls the right way.
 * @property total `unit price × quantity`, formatted ("Rp 81.000").
 * @property dock the editing dock, or the Added dock after "Add to order".
 * @property showMaxToast "Maximum 20 per item" is on screen.
 * @property cartCount units in the order, for the "My order" badge.
 * @property cartBumps how many adds happened here; each one bumps the badge.
 */
@Immutable
data class ItemDetailUiState(
    val loaded: Boolean = false,
    val product: ItemProductUi? = null,
    val soldOut: Boolean = false,
    val sections: List<ItemSectionUi> = emptyList(),
    val quantity: Int = 1,
    val quantityRoll: RollDirection = RollDirection.UP,
    val total: String = "",
    val cta: ItemCta = ItemCta.Add,
    val dock: ItemDock = ItemDock.EDITING,
    val showMaxToast: Boolean = false,
    val cartCount: Int = 0,
    val cartBumps: Int = 0,
) {
    /** "−" is drawn disabled at 1 (FR-010 AC2). */
    val canDecrease: Boolean get() = quantity > MIN_QUANTITY

    /** "+" is drawn faded at 20; a tap shows the toast instead (FR-010 AC2). */
    val atMaxQuantity: Boolean get() = quantity >= MAX_QUANTITY

    companion object {
        const val MIN_QUANTITY: Int = 1
        const val MAX_QUANTITY: Int = 20
    }
}

/** The info block: chips, name, price and description. */
@Immutable
data class ItemProductUi(
    val name: String,
    val price: String,
    val description: String?,
    val badge: String?,
    val kcal: Int?,
    val tags: String?,
    val imageUrl: String?,
    val tone: String?,
)

/** What the dock's main button says and whether it adds (FR-010). */
@Immutable
sealed interface ItemCta {
    /** "Add to order · Rp X". */
    data object Add : ItemCta

    /**
     * A required group has no choice: disabled. [prompt] true means the group name already asks ("Choose a size"), so
     * the button reads "{name} to continue"; otherwise "Choose {name} to continue", and [groupName] is already in
     * lower case ("Choose spice level to continue").
     */
    data class ChooseFirst(
        val groupName: String,
        val prompt: Boolean,
    ) : ItemCta

    /** "No longer available": disabled (`S_ItemDetail_soldOut`). */
    data object Unavailable : ItemCta
}

/** The two docks of SCR-08. */
enum class ItemDock { EDITING, ADDED }

/** One modifier group, rendered generically (FR-010 data model). */
@Immutable
sealed interface ItemSectionUi {
    val groupId: String

    /**
     * A `SINGLE` group drawn as option cards. A group that needs a choice (required, no default) carries the
     * [requiredTag]; other single groups have none.
     */
    data class Choice(
        override val groupId: String,
        val title: String,
        val options: List<ChoiceOptionUi>,
        val requiredTag: RequiredTag?,
    ) : ItemSectionUi

    /**
     * A two-option `SINGLE` group whose default is free, drawn as one switch row ("Make it a meal"). On selects
     * [onOptionId]; off selects the default [offOptionId].
     */
    data class Switch(
        override val groupId: String,
        val title: String,
        val description: String?,
        val priceDelta: String?,
        val imageUrl: String?,
        val checked: Boolean,
        val onOptionId: String,
        val offOptionId: String,
    ) : ItemSectionUi

    /** A `LEVEL` group: a collapsible row with a None / Regular / Extra control per option ("Customize"). */
    data class Levels(
        override val groupId: String,
        val title: String,
        val expanded: Boolean,
        val changes: List<LevelChangeUi>,
        val rows: List<LevelRowUi>,
    ) : ItemSectionUi

    /** A `MULTI` group: a collapsible multi-select list ("Add extras"). */
    data class Extras(
        override val groupId: String,
        val title: String,
        val expanded: Boolean,
        val chosenNames: List<String>,
        val chosenTotal: String?,
        val options: List<ExtraOptionUi>,
    ) : ItemSectionUi
}

/** The tag of a group that needs a choice: "Required" (highlighted) or "Required · done". */
enum class RequiredTag { MISSING, DONE }

/** A card of a [ItemSectionUi.Choice] group: name, description and price ("+Rp 6.000"). */
@Immutable
data class ChoiceOptionUi(
    val id: String,
    val name: String,
    val description: String?,
    val priceDelta: String?,
    val selected: Boolean,
)

/** An ingredient moved off its default, for the closed summary ("No onion", "Extra smoked cheddar"). */
@Immutable
data class LevelChangeUi(
    val name: String,
    val level: Level,
)

/** One ingredient row of a [ItemSectionUi.Levels] group. */
@Immutable
data class LevelRowUi(
    val optionId: String,
    val name: String,
    val note: String?,
    val level: Level,
)

/** One row of a [ItemSectionUi.Extras] group; [enabled] is false when the group is at its `max`. */
@Immutable
data class ExtraOptionUi(
    val id: String,
    val name: String,
    val price: String,
    val imageUrl: String?,
    val selected: Boolean,
    val enabled: Boolean,
)

/** What the Item screen can ask for. */
sealed interface ItemDetailEvent {
    /** Picks an option of a `SINGLE` group, or toggles one of a `MULTI` group. */
    data class SelectOption(
        val groupId: String,
        val optionId: String,
    ) : ItemDetailEvent

    /** Sets an ingredient of a `LEVEL` group. */
    data class SetLevel(
        val groupId: String,
        val optionId: String,
        val level: Level,
    ) : ItemDetailEvent

    /** Opens or closes a collapsible row. */
    data class ToggleExpanded(
        val groupId: String,
    ) : ItemDetailEvent

    data object IncreaseQuantity : ItemDetailEvent

    data object DecreaseQuantity : ItemDetailEvent

    data object AddToOrder : ItemDetailEvent

    /** "Menu" back pill, "Back to menu" and "Keep ordering". */
    data object BackToMenu : ItemDetailEvent

    /** "My order" pill and "View order". */
    data object ViewOrder : ItemDetailEvent
}

/** One-off effects of SCR-08. */
sealed interface ItemDetailEffect {
    data object NavigateToMenu : ItemDetailEffect

    data object NavigateToYourOrder : ItemDetailEffect
}
