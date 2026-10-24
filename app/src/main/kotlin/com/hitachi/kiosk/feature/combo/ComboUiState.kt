package com.hitachi.kiosk.feature.combo

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.domain.cart.ComboMissing

/**
 * SCR-07 Combo (FR-011; `Combo.dc.html` and its `S_Combo_*` boards). The screen draws exactly this; the ViewModel
 * computes every amount and flag.
 *
 * @property loaded false until the cached catalog has been read.
 * @property combos the available combos; empty when the menu has none (only the header shows).
 * @property large the Large size is chosen.
 * @property largeUpcharge "+Rp 6.000", the large upcharge times `serves` (FR-011 AC2).
 * @property summary what the dock summary line names.
 * @property total the combo unit price, formatted ("Rp 56.000").
 * @property cta what the dock button says and whether it adds.
 * @property cartCount units in the order, for the "My order" badge.
 * @property cartBumps how many combos were added here; each one bumps the badge.
 */
@Immutable
data class ComboUiState(
    val loaded: Boolean = false,
    val combos: List<ComboCardUi> = emptyList(),
    val sides: SlotSectionUi = SlotSectionUi(),
    val drinks: SlotSectionUi = SlotSectionUi(),
    val large: Boolean = false,
    val largeUpcharge: String = "",
    val summary: ComboSummaryUi? = null,
    val total: String = "",
    val cta: ComboCta = ComboCta.ADD,
    val cartCount: Int = 0,
    val cartBumps: Int = 0,
)

/** A combo of the radio list: photo, name, includes, price and "Save Rp X" (FR-011). */
@Immutable
data class ComboCardUi(
    val id: String,
    val name: String,
    val includes: String?,
    val price: String,
    val saving: String?,
    val imageUrl: String?,
    val tone: String?,
    val selected: Boolean,
)

/**
 * The Side or Drink section. [visible] are the cards shown: the first three, every option when [expanded], and the
 * selected option even when it would be hidden. [hiddenCount] is the "More sides (n)" count; 0 hides the button.
 */
@Immutable
data class SlotSectionUi(
    val visible: List<SlotOptionUi> = emptyList(),
    val hiddenCount: Int = 0,
    val expanded: Boolean = false,
    val missing: Boolean = false,
    val tone: String? = null,
)

/** One side or drink card. [upcharge] `null` means "Included"; an unavailable option shows "Sold out". */
@Immutable
data class SlotOptionUi(
    val id: String,
    val name: String,
    val upcharge: String?,
    val imageUrl: String?,
    val available: Boolean,
    val selected: Boolean,
)

/** The dock summary: "{shortName} · {side} · {drink} · {size}", or what is still missing. */
@Immutable
data class ComboSummaryUi(
    val shortName: String,
    val side: String?,
    val drink: String?,
    val large: Boolean,
    val missing: ComboMissing?,
)

/** The dock button (FR-011, TC-176 c). */
enum class ComboCta {
    /** "Add combo". */
    ADD,

    /** "✓ Added" for 1.8 s; taps add nothing (TC-049). */
    ADDED,

    /** Disabled "Pick side & drink". */
    PICK_SIDE_AND_DRINK,

    /** Disabled "Choose a side". */
    CHOOSE_SIDE,

    /** Disabled "Choose a drink". */
    CHOOSE_DRINK,
}

/** What the Combo screen can ask for. */
sealed interface ComboEvent {
    data class SelectCombo(
        val comboId: String,
    ) : ComboEvent

    data class SelectSide(
        val sideId: String,
    ) : ComboEvent

    data class SelectDrink(
        val drinkId: String,
    ) : ComboEvent

    data class SelectLarge(
        val large: Boolean,
    ) : ComboEvent

    /** "More sides (n)" / "Show fewer". */
    data object ToggleMoreSides : ComboEvent

    /** "More drinks (n)" / "Show fewer". */
    data object ToggleMoreDrinks : ComboEvent

    data object AddCombo : ComboEvent

    /** "Menu" back pill. */
    data object BackToMenu : ComboEvent

    /** "My order" pill. */
    data object ViewOrder : ComboEvent
}

/** One-off effects of SCR-07. */
sealed interface ComboEffect {
    data object NavigateToMenu : ComboEffect

    data object NavigateToYourOrder : ComboEffect
}
