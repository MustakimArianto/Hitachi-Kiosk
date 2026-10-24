package com.hitachi.kiosk.core.domain.planner

import com.hitachi.kiosk.core.domain.cart.ComboCustomization
import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.model.BudgetPlannerConfig
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Totals
import com.hitachi.kiosk.core.domain.model.availableCombos
import com.hitachi.kiosk.core.domain.model.toneOf
import com.hitachi.kiosk.core.domain.pricing.PricingEngine
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import java.util.BitSet

/**
 * The Budget Plan group meal planner (FR-032, BR-13–BR-15, technical-design §8.3, TD-06). Pure and deterministic:
 * the same menu, config and request always give the same plans. Build one per catalog; it precomputes the items a
 * plan may use.
 *
 * How it plans:
 * 1. **Slots.** Each person is a slot: an adult picks a (main, drink) pair or a one-person combo, a child picks a
 *    (kid-friendly main, kid-friendly drink) pair, and each two-person combo is one slot for two adults. Then one
 *    optional side or dessert per person, shared by the table. Sold-out items and products with `servesAdults` > 1
 *    are left out. Items keep their default options, or the first option of a group without one (A-42).
 * 2. **Reachable subtotals.** A dynamic program over the slots, from the last to the first, records which subtotals
 *    the remaining slots can make (in units of the greatest common divisor of the prices).
 * 3. **Final totals.** Every reachable subtotal goes through the [PricingEngine] (promo, tax, rounding), so the
 *    promo threshold and the rounding are exact (BR-15).
 * 4. **Selection.** The closest total at or under the budget (an exact total wins, AC2, AC8), and the closest total
 *    over it within `overCapPercent` (D-27). A tie on the total goes to the higher subtotal (more food), then to
 *    fewer shared meals. The edge cases of FR-032 follow.
 * 5. **Build.** The chosen subtotal is rebuilt slot by slot, preferring a main or extra not used yet (variety), then
 *    the merchant's `plannerWeight`, then featured items, then menu order.
 *
 * A Swap keeps everyone's main and drink and re-picks only the shared sides and desserts (step 2 over the extra
 * slots), so the plan lands back on its side of the budget (AC9).
 */
class BudgetPlanner(
    menu: Menu,
    private val config: BudgetPlannerConfig,
    private val rules: PricingRules,
) {
    private val products: List<Product> = preferred(menu)
    private val adultMains =
        products
            .filter {
                it.role == ProductRole.MAIN && it.servesAdults == 1
            }.items(menu, PlanRole.MAIN)
    private val kidMainsTagged = adultMains.filter { item -> product(item).kidFriendly }

    /** BR-14, D-28: without a kid-friendly main, children get the cheapest main. */
    val kidsMenuUnavailable: Boolean = kidMainsTagged.isEmpty()

    private val kidMains = kidMainsTagged.ifEmpty { adultMains.sortedBy { it.unitPrice }.take(1) }
    private val drinks = products.filter { it.role == ProductRole.DRINK }.items(menu, PlanRole.DRINK)
    private val kidDrinks = drinks.filter { item -> product(item).kidFriendly }.ifEmpty { drinks }
    private val sides = products.filter { it.role == ProductRole.SIDE }.items(menu, PlanRole.SIDE)
    private val desserts = products.filter { it.role == ProductRole.DESSERT }.items(menu, PlanRole.DESSERT)
    private val combos =
        menu.availableCombos().mapNotNull { combo ->
            comboItem(menu, combo.id)?.let {
                combo.serves to
                    it
            }
        }
    private val meals = combos.filter { it.first == 1 }.map { it.second }
    private val sharedMeals = combos.filter { it.first == 2 }.map { it.second }

    private val unit: Long = priceUnit()
    private val totalsCache = HashMap<Long, Totals>()

    /** Whether any plan can be built from this menu (see [canPlan]). */
    val isAvailable: Boolean get() = adultMains.isNotEmpty() && drinks.isNotEmpty()

    /** Step 2 of SCR-19 for [request] (FR-032). */
    fun plan(request: PlanRequest): PlanResult {
        require(request.adults >= 1) { "A plan needs at least one adult" }
        require(request.children >= 0) { "Children cannot be negative" }
        require(request.budget > Rupiah.Zero) { "A plan needs a budget" }
        if (request.people > config.maxGroupSize) return PlanResult.GroupTooLarge(config.maxGroupSize)
        if (!isAvailable) return PlanResult.NoPlan

        val budget = request.budget.value
        val cap = cap(request)
        var under: Candidate? = null
        var over: Candidate? = null
        var cheapest: Candidate? = null
        var top: Candidate? = null
        val maxShared = if (sharedMeals.isEmpty()) 0 else request.adults / 2
        for (shared in 0..maxShared) {
            val slots = slots(request, shared)
            val reach = reach(slots)
            reach[0].forEachSetBit { units ->
                val subtotal = units * unit
                val candidate = Candidate(slots, reach, subtotal, totals(subtotal).total.value)
                if (candidate.total <= budget && candidate.beats(under)) under = candidate
                if (candidate.total in (budget + 1)..cap && candidate.closerOverThan(over)) over = candidate
                if (cheapest == null || candidate.total < cheapest!!.total) cheapest = candidate
                if (candidate.beats(top)) top = candidate
            }
        }

        val bestUnder = under
        val plans =
            when {
                bestUnder == null -> listOf(build(checkNotNull(cheapest), PlanKind.TOO_LOW))

                bestUnder.total == budget -> listOf(build(bestUnder, PlanKind.EXACT))

                over == null &&
                    bestUnder.sameAs(
                        checkNotNull(top),
                    )
                -> listOf(build(bestUnder, PlanKind.MORE_THAN_ENOUGH))

                else -> listOfNotNull(build(bestUnder, PlanKind.UNDER), over?.let { build(it, PlanKind.OVER) })
            }
        return PlanResult.Plans(plans, kidsMenuUnavailable && request.children > 0)
    }

    /**
     * "Swap" in the Customize sheet (FR-032): the item in [part] of `plan.picks[pickIndex]` becomes the next one of the
     * same role in preference order (wrapping). A main, drink or meal swap then re-picks the shared sides and desserts
     * so the plan lands on the budget, or the nearest total on its side of it within the cap (AC9). A swapped extra
     * is kept as chosen. A pick or part that cannot be swapped returns [plan] unchanged.
     */
    fun swap(
        request: PlanRequest,
        plan: BudgetPlan,
        pickIndex: Int,
        part: SwapPart,
    ): BudgetPlan {
        val pick = plan.picks.getOrNull(pickIndex) ?: return plan
        val swapped = swapPick(pick, part, request.options) ?: return plan
        val picks = plan.picks.toMutableList().also { it[pickIndex] = swapped }
        return if (part == SwapPart.EXTRA) reprice(plan.kind, picks, request) else rebalance(plan.kind, picks, request)
    }

    private fun swapPick(
        pick: PlanPick,
        part: SwapPart,
        options: PlanOptions,
    ): PlanPick? =
        when {
            pick is PlanPick.MainAndDrink && part == SwapPart.MAIN -> {
                pick.copy(main = next(if (pick.diner.isChild) kidMains else adultMains, pick.main))
            }

            pick is PlanPick.MainAndDrink && part == SwapPart.DRINK -> {
                pick.copy(drink = next(if (pick.diner.isChild) kidDrinks else drinks, pick.drink))
            }

            pick is PlanPick.Meal && part == SwapPart.MEAL -> {
                pick.copy(combo = next(meals, pick.combo))
            }

            pick is PlanPick.Extra && part == SwapPart.EXTRA -> {
                pick.copy(item = next(extras(options).filter { it.role == pick.item.role } + pick.item, pick.item))
            }

            else -> {
                null
            }
        }

    /** Keeps everyone's main and drink and re-picks the shared extras (technical-design §8.3 step 5). */
    private fun rebalance(
        kind: PlanKind,
        picks: List<PlanPick>,
        request: PlanRequest,
    ): BudgetPlan {
        val base = picks.filterNot { it is PlanPick.Extra }
        val baseSubtotal = base.sumOf { pick -> pick.items.sumOf { it.unitPrice.value } }
        val slots = extraSlots(request)
        val reach = reach(slots)
        val budget = request.budget.value
        val cap = cap(request)
        var under: Candidate? = null
        var over: Candidate? = null
        var lowest: Candidate? = null
        reach[0].forEachSetBit { units ->
            val extras = units * unit
            val candidate = Candidate(slots, reach, extras, totals(baseSubtotal + extras).total.value)
            if (candidate.total <= budget && candidate.beats(under)) under = candidate
            if (candidate.total in (budget + 1)..cap && candidate.closerOverThan(over)) over = candidate
            if (lowest == null || candidate.total < lowest!!.total) lowest = candidate
        }
        val target = (if (kind == PlanKind.OVER) over ?: under else under) ?: checkNotNull(lowest)
        return reprice(kind, base + pick(target), request)
    }

    private fun reprice(
        kind: PlanKind,
        picks: List<PlanPick>,
        request: PlanRequest,
    ): BudgetPlan {
        val totals = totals(picks.sumOf { pick -> pick.items.sumOf { it.unitPrice.value } })
        val budget = request.budget
        val newKind =
            when (kind) {
                PlanKind.EXACT, PlanKind.UNDER, PlanKind.OVER -> {
                    when {
                        totals.total == budget -> PlanKind.EXACT
                        totals.total < budget -> PlanKind.UNDER
                        else -> PlanKind.OVER
                    }
                }

                PlanKind.TOO_LOW, PlanKind.MORE_THAN_ENOUGH -> {
                    kind
                }
            }
        return BudgetPlan(newKind, picks, totals)
    }

    private fun build(
        candidate: Candidate,
        kind: PlanKind,
    ): BudgetPlan {
        val picks = pick(candidate)
        return BudgetPlan(kind, picks, totals(candidate.subtotal))
    }

    /** Rebuilds [candidate]'s subtotal slot by slot, preferring mains and extras not used yet (FR-032 variety). */
    private fun pick(candidate: Candidate): List<PlanPick> {
        val usedMains = mutableSetOf<String>()
        val usedExtras = mutableSetOf<String>()
        var left = candidate.subtotal
        return candidate.slots.mapIndexedNotNull { index, slot ->
            val next = candidate.reach[index + 1]
            val option =
                slot.options
                    .filter {
                        it.price <= left && (left - it.price) % unit == 0L &&
                            next[((left - it.price) / unit).toInt()]
                    }.minBy { option ->
                        val key = option.varietyKey
                        if (key != null && (key in usedMains || key in usedExtras)) REPEAT_PENALTY else 0
                    }
            left -= option.price
            option.varietyKey?.let { if (option is Option.ExtraOption) usedExtras += it else usedMains += it }
            option.toPick(slot.diners)
        }
    }

    private fun slots(
        request: PlanRequest,
        shared: Int,
    ): List<Slot> {
        val pairs = adultMains.flatMap { main -> drinks.map { Option.PairOption(main, it, kidFriendly = false) } }
        val adult = pairs + meals.map(Option::MealOption)
        val child = kidMains.flatMap { main -> kidDrinks.map { Option.PairOption(main, it, !kidsMenuUnavailable) } }
        val adultsAlone = request.adults - shared * 2
        return buildList {
            repeat(
                shared,
            ) { add(Slot(listOf(adult(it * 2 + 1), adult(it * 2 + 2)), sharedMeals.map(Option::SharedOption))) }
            repeat(adultsAlone) { add(Slot(listOf(adult(shared * 2 + it + 1)), adult)) }
            repeat(request.children) { add(Slot(listOf(Diner(isChild = true, number = it + 1)), child)) }
            addAll(extraSlots(request))
        }
    }

    /** One optional side or dessert per person, shared by the table (FR-032). */
    private fun extraSlots(request: PlanRequest): List<Slot> {
        val options = extras(request.options)
        if (options.isEmpty()) return emptyList()
        val slot = Slot(emptyList(), listOf(Option.ExtraOption(null)) + options.map { Option.ExtraOption(it) })
        return List(request.people) { slot }
    }

    private fun extras(options: PlanOptions): List<PlannedItem> =
        (if (options.includeSides) sides else emptyList()) + (if (options.includeDesserts) desserts else emptyList())

    /** The highest total an over plan may reach: the budget plus `overCapPercent` (D-27), or the budget in Strict. */
    private fun cap(request: PlanRequest): Long =
        when (request.options.tolerance) {
            PlanTolerance.STRICT -> request.budget.value
            PlanTolerance.UP_TO_CAP -> request.budget.value + request.budget.value * config.overCapPercent / PERCENT
        }

    /** `reach[i]` holds every subtotal (in [unit]s) that slots `i` to the end can make. */
    private fun reach(slots: List<Slot>): List<BitSet> {
        val reach = arrayOfNulls<BitSet>(slots.size + 1)
        reach[slots.size] = BitSet().apply { set(0) }
        for (index in slots.indices.reversed()) {
            val next = checkNotNull(reach[index + 1])
            val current = BitSet()
            slots[index].options.map { it.price / unit }.distinct().forEach { step ->
                next.forEachSetBit { current.set((it + step).toInt()) }
            }
            reach[index] = current
        }
        return reach.map { checkNotNull(it) }
    }

    private fun totals(subtotal: Long): Totals =
        totalsCache.getOrPut(subtotal) {
            PricingEngine.price(Rupiah(subtotal), rules)
        }

    private fun priceUnit(): Long {
        val prices =
            (adultMains + drinks + sides + desserts + meals + sharedMeals).map { it.unitPrice.value }.filter { it > 0 }
        return prices.fold(0L, ::gcd).takeIf { it > 0 } ?: 1L
    }

    private fun product(item: PlannedItem): Product = products.first { it.id == item.id }

    /** A plan candidate: which slots, their reach table, and the subtotal and final total it lands on. */
    private class Candidate(
        val slots: List<Slot>,
        val reach: List<BitSet>,
        val subtotal: Long,
        val total: Long,
    ) {
        /** A higher total wins; on the same total the higher subtotal does (FR-032 ranking 1). */
        fun beats(other: Candidate?): Boolean =
            other == null || total > other.total || (total == other.total && subtotal > other.subtotal)

        /** For the over plan: a lower total wins; on the same total the higher subtotal does. */
        fun closerOverThan(other: Candidate?): Boolean =
            other == null || total < other.total || (total == other.total && subtotal > other.subtotal)

        fun sameAs(other: Candidate): Boolean = total == other.total && subtotal == other.subtotal
    }

    /** One choice to make: whose it is and the options, most preferred first. */
    private class Slot(
        val diners: List<Diner>,
        val options: List<Option>,
    )

    private sealed interface Option {
        val price: Long

        /** The ID repeated choices are counted by (mains and meals, or extras); `null` for "no extra". */
        val varietyKey: String?

        fun toPick(diners: List<Diner>): PlanPick?

        class PairOption(
            val main: PlannedItem,
            val drink: PlannedItem,
            val kidFriendly: Boolean,
        ) : Option {
            override val price = main.unitPrice.value + drink.unitPrice.value
            override val varietyKey = main.id

            override fun toPick(diners: List<Diner>) = PlanPick.MainAndDrink(diners.single(), main, drink, kidFriendly)
        }

        class MealOption(
            val combo: PlannedItem,
        ) : Option {
            override val price = combo.unitPrice.value
            override val varietyKey = combo.id

            override fun toPick(diners: List<Diner>) = PlanPick.Meal(diners.single(), combo)
        }

        class SharedOption(
            val combo: PlannedItem,
        ) : Option {
            override val price = combo.unitPrice.value
            override val varietyKey = combo.id

            override fun toPick(diners: List<Diner>) = PlanPick.SharedMeal(diners[0], diners[1], combo)
        }

        class ExtraOption(
            val item: PlannedItem?,
        ) : Option {
            override val price = item?.unitPrice?.value ?: 0L
            override val varietyKey = item?.id

            override fun toPick(diners: List<Diner>) = item?.let(PlanPick::Extra)
        }
    }

    companion object {
        /**
         * Whether [menu] has an available one-person main and an available drink, the least a plan needs. The Menu
         * shows "Plan by budget" only then (FR-032 entry), so the planner is never opened on a menu it cannot serve.
         */
        fun canPlan(menu: Menu): Boolean =
            menu.products.any { it.available && it.role == ProductRole.MAIN && it.servesAdults == 1 } &&
                menu.products.any { it.available && it.role == ProductRole.DRINK }

        private const val PERCENT = 100L
        private const val REPEAT_PENALTY = 1

        private fun adult(number: Int) = Diner(isChild = false, number = number)

        private fun gcd(
            a: Long,
            b: Long,
        ): Long = if (b == 0L) a else gcd(b, a % b)

        /** The item after [current] in [list], wrapping; [current] itself when it is the only one. */
        private fun next(
            list: List<PlannedItem>,
            current: PlannedItem,
        ): PlannedItem {
            val distinct = list.distinctBy { it.id }
            val index = distinct.indexOfFirst { it.id == current.id }
            return distinct[(index + 1) % distinct.size]
        }

        /**
         * Available products in planner preference order: `plannerWeight` (higher first), then featured items in
         * their featured order, then menu order (FR-032 ranking 3 and 4).
         */
        private fun preferred(menu: Menu): List<Product> {
            val featured = menu.featuredProductIds.withIndex().associate { it.value to it.index }
            return menu.products
                .withIndex()
                .filter { it.value.available }
                .sortedWith(
                    compareByDescending<IndexedValue<Product>> { it.value.plannerWeight }
                        .thenBy { featured[it.value.id] ?: Int.MAX_VALUE }
                        .thenBy { it.index },
                ).map { it.value }
        }

        private fun List<Product>.items(
            menu: Menu,
            role: PlanRole,
        ): List<PlannedItem> = map { product -> productItem(menu, product, role) }

        /** [product] with its defaults, and the first option of each required group without one (A-42). */
        private fun productItem(
            menu: Menu,
            product: Product,
            role: PlanRole,
        ): PlannedItem {
            val custom =
                ProductCustomization.defaults(product, menu).let { defaults ->
                    defaults.missingRequiredGroups.fold(defaults) { custom, group ->
                        group.options.fold(custom) { acc, option ->
                            if (acc.missingRequiredGroups.none { it.id == group.id }) {
                                acc
                            } else {
                                acc.select(
                                    group.id,
                                    option.id,
                                )
                            }
                        }
                    }
                }
            return PlannedItem(
                id = product.id,
                role = role,
                name = product.name,
                unitPrice = custom.unitPrice,
                imageUrl = product.imageUrl,
                tone = menu.toneOf(product),
                cartItem = custom.toCartItem(),
                choices = custom.choices(),
            )
        }

        /** A combo with its first available side and drink, Regular (FR-011 defaults); `null` if it cannot be made. */
        private fun comboItem(
            menu: Menu,
            comboId: String,
        ): PlannedItem? {
            val custom = ComboCustomization.defaults(menu, comboId)?.takeIf { it.combo.id == comboId } ?: return null
            val cartItem = custom.toCartItem() ?: return null
            return PlannedItem(
                id = custom.combo.id,
                role = PlanRole.MEAL,
                name = custom.combo.name,
                unitPrice = custom.unitPrice,
                imageUrl = custom.combo.imageUrl,
                tone = custom.combo.tone,
                cartItem = cartItem,
                choices = custom.choices(),
            )
        }

        private inline fun BitSet.forEachSetBit(action: (Long) -> Unit) {
            var bit = nextSetBit(0)
            while (bit >= 0) {
                action(bit.toLong())
                bit = nextSetBit(bit + 1)
            }
        }
    }
}
