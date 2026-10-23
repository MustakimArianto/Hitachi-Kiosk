package com.hitachi.kiosk.core.domain.cart

import com.hitachi.kiosk.core.domain.model.Combo
import com.hitachi.kiosk.core.domain.model.ComboSelection
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.ComboSlotOption
import com.hitachi.kiosk.core.domain.model.ComboSlots
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.availableCombos
import com.hitachi.kiosk.core.domain.pricing.MenuPricing

/** Which required combo choices are still open (FR-011, `S_Combo_requiredMissing`). */
enum class ComboMissing { SIDE, DRINK, SIDE_AND_DRINK }

/**
 * The combo being configured on the Combo screen (FR-011). Immutable: every change returns a new value. A sold-out
 * side or drink cannot be picked (`S_Combo_soldOut`).
 */
data class ComboCustomization(
    val combo: Combo,
    val slots: ComboSlots,
    val sideId: String?,
    val drinkId: String?,
    val size: ComboSize,
) {
    val side: ComboSlotOption? get() = slots.sides.firstOrNull { it.id == sideId }

    val drink: ComboSlotOption? get() = slots.drinks.firstOrNull { it.id == drinkId }

    /** What still has to be picked, or `null` when the combo can be added. */
    val missing: ComboMissing?
        get() =
            when {
                side == null && drink == null -> ComboMissing.SIDE_AND_DRINK
                side == null -> ComboMissing.SIDE
                drink == null -> ComboMissing.DRINK
                else -> null
            }

    /** `price + (side + drink + large) × serves` (FR-011 AC1, AC2). */
    val unitPrice: Rupiah
        get() =
            MenuPricing.comboUnitPrice(
                combo = combo,
                sideUpcharge = side?.upcharge ?: Rupiah.Zero,
                drinkUpcharge = drink?.upcharge ?: Rupiah.Zero,
                size = size,
                largeUpcharge = slots.sizeLargeUpcharge,
            )

    /** The "Large +Rp X" label amount: the large upcharge times `serves` (FR-011 AC2). */
    val largeUpcharge: Rupiah get() = MenuPricing.comboUpcharge(combo, slots.sizeLargeUpcharge)

    /** The amount on an option card ("+Rp 8.000" for nuggets on Feast for Two, FR-011 AC2). */
    fun upchargeOf(option: ComboSlotOption): Rupiah = MenuPricing.comboUpcharge(combo, option.upcharge)

    /** Switches the combo and keeps the side, drink and size (FR-011). */
    fun selectCombo(next: Combo): ComboCustomization = copy(combo = next)

    /** Picks a side; a sold-out or unknown one changes nothing (FR-011). */
    fun selectSide(id: String): ComboCustomization =
        if (slots.sides.any { it.id == id && it.available }) copy(sideId = id) else this

    /** Picks a drink; a sold-out or unknown one changes nothing (FR-011). */
    fun selectDrink(id: String): ComboCustomization =
        if (slots.drinks.any { it.id == id && it.available }) copy(drinkId = id) else this

    fun selectSize(next: ComboSize): ComboCustomization = copy(size = next)

    /** The cart item, or `null` while a side or drink is missing (FR-011). */
    fun toCartItem(): CartItem.ComboItem? {
        val sideId = side?.id ?: return null
        val drinkId = drink?.id ?: return null
        return CartItem.ComboItem(combo.id, ComboSelection(sideId, drinkId, size))
    }

    /** The choices for the line description: side, drink and size (FR-011 summary line). */
    fun choices(): List<LineChoice> =
        listOfNotNull(side?.let { LineChoice.Option(it.name) }, drink?.let { LineChoice.Option(it.name) }) +
            LineChoice.Size(size)

    companion object {
        /**
         * FR-011 defaults: [comboId] (or the first available combo), the first available side and drink, Regular.
         * `null` when the menu has no available combo.
         */
        fun defaults(
            menu: Menu,
            comboId: String? = null,
        ): ComboCustomization? {
            val combos = menu.availableCombos()
            val combo = combos.firstOrNull { it.id == comboId } ?: combos.firstOrNull() ?: return null
            return ComboCustomization(
                combo = combo,
                slots = menu.comboSlots,
                sideId =
                    menu.comboSlots.sides
                        .firstOrNull { it.available }
                        ?.id,
                drinkId =
                    menu.comboSlots.drinks
                        .firstOrNull { it.available }
                        ?.id,
                size = ComboSize.REGULAR,
            )
        }
    }
}
