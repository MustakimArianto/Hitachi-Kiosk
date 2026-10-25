package com.hitachi.kiosk.core.domain.cart

import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.ModifierGroup
import com.hitachi.kiosk.core.domain.model.ModifierType
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Selection
import com.hitachi.kiosk.core.domain.model.groupsOf
import com.hitachi.kiosk.core.domain.model.minimumChoices
import com.hitachi.kiosk.core.domain.pricing.MenuPricing

/**
 * The choices a customer is making for one product on Item detail, or the defaults quick add uses (FR-009, FR-010).
 * Immutable: every change returns a new value. Rendering is generic over the product's modifier groups.
 *
 * @property chosen the selected option IDs of each `SINGLE` and `MULTI` group.
 * @property levels the level of each `LEVEL` option, by group ID then option ID.
 */
data class ProductCustomization(
    val product: Product,
    val groups: List<ModifierGroup>,
    val chosen: Map<String, List<String>>,
    val levels: Map<String, Map<String, Level>>,
) {
    /** `base + Σ option deltas` (FR-010). */
    val unitPrice: Rupiah get() = MenuPricing.productUnitPrice(product, groups, selections())

    /** Required groups still short of their minimum, in screen order (FR-010 edge case, TC-044). */
    val missingRequiredGroups: List<ModifierGroup>
        get() =
            groups.filter { group ->
                group.type != ModifierType.LEVEL && chosen[group.id].orEmpty().size < group.minimumChoices()
            }

    /** Whether "Add to order" may run: every required group is satisfied (FR-010). */
    val isComplete: Boolean get() = missingRequiredGroups.isEmpty()

    fun isSelected(
        groupId: String,
        optionId: String,
    ): Boolean = optionId in chosen[groupId].orEmpty()

    fun levelOf(
        groupId: String,
        optionId: String,
    ): Level? = levels[groupId]?.get(optionId)

    /** Whether a `MULTI` group already holds its `max` options, so unselected ones cannot be added (api-spec E6). */
    fun isAtMax(groupId: String): Boolean {
        val group = group(groupId) ?: return false
        val max = group.max ?: return false
        return group.type == ModifierType.MULTI && chosen[groupId].orEmpty().size >= max
    }

    /**
     * Picks [optionId] in a `SINGLE` group, or toggles it in a `MULTI` group. Adding beyond a `MULTI` group's `max`,
     * or an unknown group or option, changes nothing.
     */
    fun select(
        groupId: String,
        optionId: String,
    ): ProductCustomization {
        val group = group(groupId) ?: return this
        if (group.options.none { it.id == optionId }) return this
        val current = chosen[groupId].orEmpty()
        val next =
            when (group.type) {
                ModifierType.SINGLE -> {
                    listOf(optionId)
                }

                ModifierType.MULTI -> {
                    when {
                        optionId in current -> current - optionId
                        isAtMax(groupId) -> return this
                        else -> group.options.map { it.id }.filter { it in current || it == optionId }
                    }
                }

                ModifierType.LEVEL -> {
                    return this
                }
            }
        return copy(chosen = chosen + (groupId to next))
    }

    /** Sets a `LEVEL` option to [level] (None / Regular / Extra, FR-010). */
    fun setLevel(
        groupId: String,
        optionId: String,
        level: Level,
    ): ProductCustomization {
        val group = group(groupId)?.takeIf { it.type == ModifierType.LEVEL } ?: return this
        if (group.options.none { it.id == optionId }) return this
        return copy(levels = levels + (groupId to (levels[groupId].orEmpty() + (optionId to level))))
    }

    /**
     * The choices as order selections, in group and option order. A `LEVEL` option appears only when it differs from
     * its default (api-spec E7), so two identical customizations always give equal lists (FR-010 merge rule).
     */
    fun selections(): List<Selection> =
        groups.flatMap { group ->
            when (group.type) {
                ModifierType.SINGLE, ModifierType.MULTI -> {
                    group.options
                        .filter { it.id in chosen[group.id].orEmpty() }
                        .map { Selection(group.id, it.id) }
                }

                ModifierType.LEVEL -> {
                    group.options.mapNotNull { option ->
                        val level = levelOf(group.id, option.id) ?: return@mapNotNull null
                        Selection(group.id, option.id, level).takeIf { level != MenuPricing.defaultLevel(option) }
                    }
                }
            }
        }

    /** What the cart stores for these choices (FR-010). */
    fun toCartItem(): CartItem.ProductItem = CartItem.ProductItem(product.id, selections())

    /**
     * The choices that differ from the defaults, for the line description (FR-010 AC3). The UI words them, for
     * example "Extra smoked cheddar" or "No onion".
     */
    fun choices(): List<LineChoice> =
        groups.flatMap { group ->
            when (group.type) {
                ModifierType.SINGLE, ModifierType.MULTI -> {
                    group.options
                        .filter { it.id in chosen[group.id].orEmpty() && !it.isDefault }
                        .map { LineChoice.Option(it.name) }
                }

                ModifierType.LEVEL -> {
                    group.options.mapNotNull { option ->
                        val level = levelOf(group.id, option.id) ?: return@mapNotNull null
                        LineChoice.LevelChange(option.name, level).takeIf { level != MenuPricing.defaultLevel(option) }
                    }
                }
            }
        }

    private fun group(id: String): ModifierGroup? = groups.firstOrNull { it.id == id }

    companion object {
        /**
         * [product] with every default selected: default options, and each `LEVEL` option at its default level
         * (FR-010 AC4). Quick add uses exactly this (FR-009).
         */
        fun defaults(
            product: Product,
            menu: Menu,
        ): ProductCustomization {
            val groups = menu.groupsOf(product)
            return ProductCustomization(
                product = product,
                groups = groups,
                chosen =
                    groups
                        .filter { it.type != ModifierType.LEVEL }
                        .associate { group -> group.id to group.defaultOptionIds() },
                levels =
                    groups
                        .filter { it.type == ModifierType.LEVEL }
                        .associate { group ->
                            group.id to group.options.associate { it.id to MenuPricing.defaultLevel(it) }
                        },
            )
        }

        /**
         * The customization that [selections] describe, the inverse of [selections]: a `SINGLE` or `MULTI` group holds
         * exactly the options listed for it, and a `LEVEL` option is at its default level unless a selection moves it
         * (DEV-114: the meal card changes one option of a line that is already in the cart).
         */
        fun from(
            product: Product,
            menu: Menu,
            selections: List<Selection>,
        ): ProductCustomization {
            val base = defaults(product, menu)
            return base.copy(
                chosen =
                    base.groups
                        .filter { it.type != ModifierType.LEVEL }
                        .associate { group ->
                            group.id to
                                group.options
                                    .map { it.id }
                                    .filter { id -> selections.any { it.groupId == group.id && it.optionId == id } }
                        },
                levels =
                    base.levels.mapValues { (groupId, byOption) ->
                        byOption +
                            selections
                                .filter { it.groupId == groupId && it.level != null }
                                .associate { it.optionId to checkNotNull(it.level) }
                    },
            )
        }

        /** A `SINGLE` group keeps only its first default; a `MULTI` group keeps up to `max` of them. */
        private fun ModifierGroup.defaultOptionIds(): List<String> {
            val defaults = options.filter { it.isDefault }.map { it.id }
            return when (type) {
                ModifierType.SINGLE -> defaults.take(1)
                else -> max?.let { defaults.take(it) } ?: defaults
            }
        }
    }
}
