package com.hitachi.kiosk.core.domain.cart

import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.ModifierGroup
import com.hitachi.kiosk.core.domain.model.ModifierOption
import com.hitachi.kiosk.core.domain.model.ModifierPurpose
import com.hitachi.kiosk.core.domain.model.ModifierType
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.UpsellConfig
import com.hitachi.kiosk.core.domain.model.groupsOf
import com.hitachi.kiosk.core.domain.model.needsChoice
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.pricing.PricingEngine
import com.hitachi.kiosk.core.domain.pricing.PricingRules

/** One card of the "Goes well with your order" strip (FR-014, DEV-106, DEV-114). */
sealed interface UpsellCard {
    /** Identifies the card across re-rankings, so a card the customer acted on keeps its place. */
    val key: String

    /** A plain add-on, added with its default options at [unitPrice]. */
    data class AddProduct(
        val product: Product,
        val unitPrice: Rupiah,
    ) : UpsellCard {
        override val key: String get() = addKey(product.id)
    }

    /** "Make it a meal": switches [product]'s meal [option] on for [priceDelta] more (DEV-114, OQ-17). */
    data class MakeMeal(
        val product: Product,
        val option: ModifierOption,
        val priceDelta: Rupiah,
    ) : UpsellCard {
        override val key: String get() = mealKey(product.id)
    }

    companion object {
        fun addKey(productId: String): String = "add:$productId"

        fun mealKey(productId: String): String = "meal:$productId"
    }
}

/** What "Make it a meal" changes: one unit of [lineId] becomes [item] (see [Cart.switchOne]). */
data class MealSwitch(
    val lineId: String,
    val item: CartItem.ProductItem,
    val unitPrice: Rupiah,
    val choices: List<LineChoice>,
)

/**
 * The "Goes well with your order" strip rules (FR-014, D-33; replaces the SCR-09 rules of DEV-039). Pure and chosen in
 * the domain layer, not in the screen.
 */
object Upsell {
    /** The strip holds at most three cards (FR-014). */
    const val MAX_SUGGESTIONS: Int = 3

    /**
     * One available add-on per gap the order has, in this priority: a drink (the order has a main, and no drink or
     * meal), a side (a main, no side or meal), a dessert (no dessert) (FR-014). A meal is a combo line or, when the
     * API marks the group, a product line with "Make it a meal" on (A-48). At most `config.maxSuggestions` and
     * [MAX_SUGGESTIONS] come back.
     *
     * Candidates are the products of the upsell categories that are available, not in the cart yet, need no choice
     * and whose default price passes [fits]. Inside a gap the order is `plannerWeight` (higher first), featured, menu
     * order. [fits] receives a candidate's unit price: Budget Plan orders pass [withinBudget] (FR-014 AC5).
     */
    fun suggestions(
        menu: Menu,
        config: UpsellConfig,
        cart: Cart,
        fits: (Rupiah) -> Boolean = { true },
    ): List<Product> = rank(menu, config, cart, fits, mealCovered = false).map { it.first }

    /**
     * The strip: [suggestions] as [UpsellCard.AddProduct]s, led by a [UpsellCard.MakeMeal] card when the API marks a
     * main's meal option, that option is off, the order has no meal yet and the upgrade passes [fits]. A meal card
     * covers the drink and side gaps, so only the dessert gap follows it (DEV-114). Lines in [mealExcludedLineIds]
     * (a Budget Plan's lines) are never upgraded.
     */
    fun cards(
        menu: Menu,
        config: UpsellConfig,
        cart: Cart,
        fits: (Rupiah) -> Boolean = { true },
        mealExcludedLineIds: Set<String> = emptySet(),
    ): List<UpsellCard> {
        val limit = config.maxSuggestions.coerceIn(0, MAX_SUGGESTIONS)
        val meal = if (hasMeal(menu, cart)) null else mealCard(menu, cart, fits, mealExcludedLineIds)
        val products =
            rank(menu, config, cart, fits, mealCovered = meal != null)
                .map { (product, price) -> UpsellCard.AddProduct(product, price) }
        return (listOfNotNull(meal) + products).take(limit)
    }

    /**
     * What Your order draws: [cards], except that a card the customer already acted on stays on screen at the slot it
     * had, so its "− 1 +" stepper does not jump away or vanish when the ranking changes (FR-014 undo). [held] maps such
     * a card's key to its slot; a held card whose product left the menu is dropped, and the other slots fill from
     * [cards] in rank order.
     */
    fun strip(
        menu: Menu,
        config: UpsellConfig,
        cart: Cart,
        held: Map<String, Int> = emptyMap(),
        fits: (Rupiah) -> Boolean = { true },
        mealExcludedLineIds: Set<String> = emptySet(),
    ): List<UpsellCard> {
        val limit = config.maxSuggestions.coerceIn(0, MAX_SUGGESTIONS)
        val pool = cards(menu, config, cart, fits, mealExcludedLineIds).filterNot { it.key in held }.toMutableList()
        val slots = arrayOfNulls<UpsellCard>(limit)
        held.entries
            .sortedBy { it.value }
            .mapNotNull { (key, slot) -> card(menu, key)?.let { slot to it } }
            .forEach { (slot, card) ->
                val free = (slot.coerceAtLeast(0) until limit).firstOrNull { slots[it] == null }
                if (free != null) slots[free] = card
            }
        for (i in slots.indices) {
            if (slots[i] == null && pool.isNotEmpty()) slots[i] = pool.removeAt(0)
        }
        return slots.filterNotNull()
    }

    /** The card for [key] (an [UpsellCard.key]) as the menu has it now, or `null` when it cannot be shown any more. */
    fun card(
        menu: Menu,
        key: String,
    ): UpsellCard? {
        val product = menu.product(key.substringAfter(':'))?.takeIf { it.available } ?: return null
        return when {
            key.startsWith("add:") -> {
                UpsellCard.AddProduct(product, ProductCustomization.defaults(product, menu).unitPrice)
            }

            key.startsWith("meal:") -> {
                menu.mealGroup(product)?.mealOption()?.let { UpsellCard.MakeMeal(product, it, it.priceDelta) }
            }

            else -> {
                null
            }
        }
    }

    /** How many units of [productId] in [cart] have "Make it a meal" on (DEV-114: the meal card's quantity). */
    fun mealUnits(
        menu: Menu,
        cart: Cart,
        productId: String,
    ): Int {
        val group = menu.product(productId)?.let { menu.mealGroup(it) } ?: return 0
        val option = group.mealOption() ?: return 0
        return cart.lines
            .filter { line ->
                val item = line.item as? CartItem.ProductItem
                item != null && item.productId == productId && item.isMeal(group, option)
            }.sumOf { it.quantity }
    }

    /**
     * The room check for a Budget Plan order (FR-014 AC5): a function that says whether adding [unitPrice] keeps the
     * order's total, with tax, promo and rounding, within [budget]. A suggestion never pushes a budget customer over
     * the budget they set.
     */
    fun withinBudget(
        budget: Rupiah,
        cart: Cart,
        rules: PricingRules,
    ): (Rupiah) -> Boolean =
        { unitPrice ->
            val extra =
                CartLine(PROBE_LINE_ID, CartItem.ProductItem(PROBE_LINE_ID, emptyList()), 1, unitPrice, emptyList())
            PricingEngine.price(cart.lines + extra, rules).total <= budget
        }

    /**
     * The change that turns one unit of a [productId] line into a meal ([on]) or back (DEV-114, TC-205), or `null`
     * when the API does not mark the product's meal group, no line is in the other state, or [on] is already true for
     * every line. The other choices of the line stay, and the price follows the options (FR-010). Lines in
     * [excludedLineIds] are skipped.
     */
    fun mealSwitch(
        menu: Menu,
        cart: Cart,
        productId: String,
        on: Boolean,
        excludedLineIds: Set<String> = emptySet(),
    ): MealSwitch? {
        val product = menu.product(productId) ?: return null
        val group = menu.mealGroup(product) ?: return null
        val onOption = group.mealOption() ?: return null
        val offOption = group.options.firstOrNull { it.isDefault } ?: return null
        val line =
            cart.lines.firstOrNull { line ->
                val item = line.item as? CartItem.ProductItem
                item?.productId == productId &&
                    line.lineId !in excludedLineIds &&
                    item.isMeal(group, onOption) != on
            } ?: return null
        val item = line.item as CartItem.ProductItem
        val custom =
            ProductCustomization
                .from(product, menu, item.selections)
                .select(group.id, if (on) onOption.id else offOption.id)
        return MealSwitch(line.lineId, custom.toCartItem(), custom.unitPrice, custom.choices())
    }

    /** Ranked candidates with their default unit price, one per gap. */
    private fun rank(
        menu: Menu,
        config: UpsellConfig,
        cart: Cart,
        fits: (Rupiah) -> Boolean,
        mealCovered: Boolean,
    ): List<Pair<Product, Rupiah>> {
        val limit = config.maxSuggestions.coerceIn(0, MAX_SUGGESTIONS)
        val roles = cart.lines.mapNotNull { menu.roleOf(it.item) }.toSet()
        val covered = mealCovered || hasMeal(menu, cart)
        val hasMain = ProductRole.MAIN in roles || cart.lines.any { it.item is CartItem.ComboItem }
        val inCart = cart.lines.mapNotNull { (it.item as? CartItem.ProductItem)?.productId }.toSet()
        val gaps =
            listOfNotNull(
                ProductRole.DRINK.takeIf { hasMain && !covered && it !in roles },
                ProductRole.SIDE.takeIf { hasMain && !covered && it !in roles },
                ProductRole.DESSERT.takeIf { it !in roles },
            )
        val featured = menu.featuredProductIds.toSet()
        val order = menu.products.withIndex().associate { it.value.id to it.index }
        return gaps
            .mapNotNull { role ->
                menu.products
                    .asSequence()
                    .filter { it.categoryId in config.categoryIds && it.role == role }
                    .filter { it.available && it.id !in inCart && !menu.needsChoice(it) }
                    .map { it to ProductCustomization.defaults(it, menu).unitPrice }
                    .filter { (_, price) -> fits(price) }
                    .sortedWith(
                        compareByDescending<Pair<Product, Rupiah>> { it.first.plannerWeight }
                            .thenByDescending { it.first.id in featured }
                            .thenBy { order.getValue(it.first.id) },
                    ).firstOrNull()
            }.take(limit)
    }

    private fun mealCard(
        menu: Menu,
        cart: Cart,
        fits: (Rupiah) -> Boolean,
        excluded: Set<String>,
    ): UpsellCard.MakeMeal? =
        cart.lines
            .mapNotNull { (it.item as? CartItem.ProductItem)?.productId }
            .distinct()
            .mapNotNull { id -> menu.product(id)?.takeIf { it.role == ProductRole.MAIN } }
            .firstNotNullOfOrNull { product ->
                val option = menu.mealGroup(product)?.mealOption() ?: return@firstNotNullOfOrNull null
                UpsellCard.MakeMeal(product, option, option.priceDelta).takeIf {
                    fits(option.priceDelta) && mealSwitch(menu, cart, product.id, on = true, excluded) != null
                }
            }

    private fun hasMeal(
        menu: Menu,
        cart: Cart,
    ): Boolean =
        cart.lines.any { line ->
            when (val item = line.item) {
                is CartItem.ComboItem -> {
                    true
                }

                is CartItem.ProductItem -> {
                    val group = menu.product(item.productId)?.let { menu.mealGroup(it) }
                    val option = group?.mealOption()
                    group != null && option != null && item.isMeal(group, option)
                }
            }
        }

    private fun Menu.roleOf(item: CartItem): ProductRole? =
        (item as? CartItem.ProductItem)?.let {
            product(it.productId)?.role
        }

    private fun CartItem.ProductItem.isMeal(
        group: ModifierGroup,
        onOption: ModifierOption,
    ): Boolean = selections.any { it.groupId == group.id && it.optionId == onOption.id }

    /** The group the API marks as "Make it a meal": `SINGLE` with a default (off) option and a non-default (on) one. */
    private fun Menu.mealGroup(product: Product): ModifierGroup? =
        groupsOf(product).firstOrNull {
            it.purpose == ModifierPurpose.MEAL_UPGRADE &&
                it.type == ModifierType.SINGLE &&
                it.mealOption() != null &&
                it.options.any { option -> option.isDefault }
        }

    private fun ModifierGroup.mealOption(): ModifierOption? = options.firstOrNull { !it.isDefault }

    private const val PROBE_LINE_ID = "upsell-probe"
}
