package com.hitachi.kiosk.core.domain.pricing

import com.hitachi.kiosk.core.domain.model.Combo
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.ModifierGroup
import com.hitachi.kiosk.core.domain.model.ModifierOption
import com.hitachi.kiosk.core.domain.model.ModifierType
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Selection

/**
 * Unit prices of menu items before order totals (FR-010, FR-011). Integer Rupiah only (BR-01). Order totals,
 * promo, tax and rounding are the `PricingEngine` of 0.4.0 (FR-013).
 */
object MenuPricing {
    /**
     * FR-010: `base + Σ selected option deltas`. For a `LEVEL` group every option counts at its chosen level, or at
     * its default level when [selections] does not change it (a selection carries a level only when it differs from
     * the default, api-spec E7).
     */
    fun productUnitPrice(
        product: Product,
        groups: List<ModifierGroup>,
        selections: List<Selection>,
    ): Rupiah =
        groups.fold(product.price) { total, group ->
            val chosen = selections.filter { it.groupId == group.id }
            total + groupDelta(group, chosen)
        }

    /** The price of [option] at [level], or zero when the option has no price for it (api-spec E6). */
    fun levelDelta(
        option: ModifierOption,
        level: Level,
    ): Rupiah = option.levels.firstOrNull { it.level == level }?.priceDelta ?: Rupiah.Zero

    /** The level a `LEVEL` option starts at: its `defaultLevel`, or Regular (FR-010 AC4). */
    fun defaultLevel(option: ModifierOption): Level = option.defaultLevel ?: Level.REGULAR

    /**
     * FR-011: `combo price + (side upcharge + drink upcharge + large upcharge) × serves`. A missing side or drink adds
     * nothing.
     */
    fun comboUnitPrice(
        combo: Combo,
        sideUpcharge: Rupiah,
        drinkUpcharge: Rupiah,
        size: ComboSize,
        largeUpcharge: Rupiah,
    ): Rupiah {
        val sizeUpcharge = if (size == ComboSize.LARGE) largeUpcharge else Rupiah.Zero
        return combo.price + comboUpcharge(combo, sideUpcharge + drinkUpcharge + sizeUpcharge)
    }

    /** An upcharge as the combo charges it: times `serves`, which the option labels also show (FR-011 AC2). */
    fun comboUpcharge(
        combo: Combo,
        upcharge: Rupiah,
    ): Rupiah = upcharge * combo.serves

    /** "Save Rp X": separate price minus combo price, never below zero (FR-011). */
    fun comboSaving(combo: Combo): Rupiah =
        (combo.separatePrice - combo.price).takeIf { it > Rupiah.Zero } ?: Rupiah.Zero

    private fun groupDelta(
        group: ModifierGroup,
        chosen: List<Selection>,
    ): Rupiah =
        when (group.type) {
            ModifierType.SINGLE, ModifierType.MULTI -> {
                chosen.fold(Rupiah.Zero) { sum, selection ->
                    sum + (group.options.firstOrNull { it.id == selection.optionId }?.priceDelta ?: Rupiah.Zero)
                }
            }

            ModifierType.LEVEL -> {
                group.options.fold(Rupiah.Zero) { sum, option ->
                    val level = chosen.firstOrNull { it.optionId == option.id }?.level ?: defaultLevel(option)
                    sum + levelDelta(option, level)
                }
            }
        }
}
