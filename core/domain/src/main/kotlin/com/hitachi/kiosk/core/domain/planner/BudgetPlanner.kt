package com.hitachi.kiosk.core.domain.planner

import com.hitachi.kiosk.core.domain.cart.ComboCustomization
import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.model.BudgetPlannerConfig
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.availableCombos
import com.hitachi.kiosk.core.domain.model.toneOf
import com.hitachi.kiosk.core.domain.pricing.PricingEngine
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import kotlin.math.abs

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

    /** Whether any plan can be built from this menu (see [canPlan]). */
    val isAvailable: Boolean get() = adultMains.isNotEmpty() && drinks.isNotEmpty()

    /** Step 2 of SCR-19 for [request] (FR-032). */
    fun plan(request: PlanRequest): PlanResult {
        require(request.adults >= 1) { "A plan needs at least one adult" }
        require(request.children >= 0) { "Children cannot be negative" }
        require(request.budget > Rupiah.Zero) { "A plan needs a budget" }
        if (request.people > config.maxGroupSize) return PlanResult.GroupTooLarge(config.maxGroupSize)
        if (!isAvailable) return PlanResult.NoPlan

        val tables = tables(request)
        val found = Selection(request.budget.value, cap(request))
        val totals = LongArray(tables.maxOf { it.reach[0].size }) { UNPRICED }
        tables.forEachIndexed { index, table ->
            table.reach[0].forEachSetBit { units ->
                val subtotal = units * unit
                // The same subtotal comes up in many tables: price it once.
                val total =
                    totals[units.toInt()].takeIf { it != UNPRICED }
                        ?: total(subtotal).also { totals[units.toInt()] = it }
                found.offer(index, subtotal, total)
            }
        }
        val under = found.under
        val plans =
            when {
                under == null -> {
                    listOf(build(tables, checkNotNull(found.cheapest), PlanKind.TOO_LOW))
                }

                under.total == request.budget.value -> {
                    listOf(build(tables, under, PlanKind.EXACT))
                }

                found.over == null && under.sameAs(checkNotNull(found.top)) -> {
                    listOf(build(tables, under, PlanKind.MORE_THAN_ENOUGH))
                }

                else -> {
                    listOfNotNull(
                        build(tables, under, PlanKind.UNDER),
                        found.over?.let { build(tables, it, PlanKind.OVER) },
                    )
                }
            }
        return PlanResult.Plans(plans, kidsMenuUnavailable && request.children > 0)
    }

    /**
     * One [Table] per [Shape]. The slots after the meals (pairs, children, extras) depend only on the number of combo
     * diners, so their reachable sets are computed once per count and shared by the shapes that have it.
     */
    private fun tables(request: PlanRequest): List<Table> {
        val size = subtotalBound(request) / unit.toInt() + 1
        val done = SubtotalSet(size).apply { set(0) }
        val tails = mutableMapOf<Int, Pair<List<Slot>, List<SubtotalSet>>>()
        return shapes(request).map { shape ->
            val (tail, tailReach) =
                tails.getOrPut(shape.comboDiners) {
                    val slots = tailSlots(request, shape)
                    slots to reach(slots, size, listOf(done))
                }
            val head = headSlots(shape)
            Table(head + tail, reach(head, size, tailReach), shape.shared)
        }
    }

    /** The most a plan can cost before tax and promo, so every table can share one set size. */
    private fun subtotalBound(request: PlanRequest): Int {
        val adultMax =
            maxOf(
                (adultMains.maxOfOrNull { it.unitPrice.value } ?: 0L) +
                    (drinks.maxOfOrNull { it.unitPrice.value } ?: 0L),
                meals.maxOfOrNull { it.unitPrice.value } ?: 0L,
                (sharedMeals.maxOfOrNull { it.unitPrice.value } ?: 0L) / 2,
            )
        val kidMax =
            (kidMains.maxOfOrNull { it.unitPrice.value } ?: 0L) + (kidDrinks.maxOfOrNull { it.unitPrice.value } ?: 0L)
        val extraMax = extras(request.options).maxOfOrNull { it.unitPrice.value } ?: 0L
        return (adultMax * request.adults + kidMax * request.children + extraMax * request.people).toInt()
    }

    /**
     * Every way to feed the adults with meals: [Shape.shared] two-person combos, [Shape.solo] one-person combos and
     * pairs for the rest. The number of combo diners sets how many sides the table may add (OQ-15), so each shape is
     * planned on its own.
     */
    private fun shapes(request: PlanRequest): List<Shape> {
        val maxShared = if (sharedMeals.isEmpty()) 0 else request.adults / 2
        return (0..maxShared).flatMap { shared ->
            val alone = request.adults - shared * 2
            (0..if (meals.isEmpty()) 0 else alone).map { solo -> Shape(shared, solo) }
        }
    }

    /**
     * "Swap" in the Customize sheet (FR-032): the item in [part] of `plan.picks[pickIndex]` becomes the next one of the
     * same role in preference order (wrapping). A main, drink or meal swap then re-picks the shared sides and desserts
     * so the plan lands on the budget, or the nearest total on its side of it within the cap (AC9). A swapped extra
     * is kept as chosen. An alternative that cannot stay within the plan's limit (the budget in Strict, otherwise the
     * over cap; a "Need Rp X more" plan has none) is skipped for the next one; when none fits, or the pick or part
     * cannot be swapped, [plan] comes back unchanged.
     */
    fun swap(
        request: PlanRequest,
        plan: BudgetPlan,
        pickIndex: Int,
        part: SwapPart,
    ): BudgetPlan {
        val original = plan.picks.getOrNull(pickIndex) ?: return plan
        val limit = if (plan.kind == PlanKind.TOO_LOW) Long.MAX_VALUE else cap(request)
        var candidate = original
        while (true) {
            candidate = swapPick(candidate, part, request.options) ?: return plan
            if (candidate == original) return plan
            val picks = plan.picks.toMutableList().also { it[pickIndex] = candidate }
            val swapped =
                if (part == SwapPart.EXTRA) reprice(plan.kind, picks, request) else rebalance(plan.kind, picks, request)
            if (swapped.total.value <= limit) return swapped
        }
    }

    /**
     * The swap picker's rows for `plan.picks[pickIndex]` (FR-032, DEV-109, TC-200): the current choice first, then the
     * alternatives in the same role, cheapest total first, at most [MAX_ALTERNATIVES] (the ones nearest the current
     * price). An adult's main plus drink or one-person combo can become any other main plus drink or one-person combo,
     * a child's main plus drink any other kid-friendly pair, a shared two-person combo another two-person combo or two
     * separate meals, and a shared side or dessert another of its kind.
     *
     * Everyone else's choices and the table's extras stay as they are, so the price change shown is the real one; only
     * sides that a new combo diner already has are dropped (OQ-15). An alternative that would take the plan past its
     * limit (the budget, or the over cap for an over plan; never below the current total) is left out, so no row can
     * break the budget or the cap rule. Unknown [pickIndex] gives an empty list.
     */
    fun alternatives(
        request: PlanRequest,
        plan: BudgetPlan,
        pickIndex: Int,
    ): List<PlanAlternative> {
        val pick = plan.picks.getOrNull(pickIndex) ?: return emptyList()
        val limit = maxOf(plan.total.value, if (plan.kind == PlanKind.OVER) cap(request) else request.budget.value)
        val others =
            replacements(pick, request.options)
                .filter { it != listOf(pick) }
                .mapNotNull { replacement ->
                    val picks =
                        plan.picks
                            .toMutableList()
                            .apply {
                                removeAt(
                                    pickIndex,
                                )
                            }.apply { addAll(pickIndex, replacement) }
                    val next = reprice(plan.kind, withoutExtraSides(picks, request), request)
                    next
                        .takeIf { it.total.value <= limit }
                        ?.let { PlanAlternative(replacement, it, it.total.value - plan.total.value, isCurrent = false) }
                }.sortedWith(compareBy({ abs(it.change) }, { it.plan.total.value }))
                .take(MAX_ALTERNATIVES)
                .sortedWith(
                    compareBy({ it.plan.total.value }, {
                        it.replacement.joinToString { p ->
                            p.items.joinToString { i -> i.id }
                        }
                    }),
                )
        return listOf(PlanAlternative(listOf(pick), plan, 0L, isCurrent = true)) + others
    }

    /** What may stand in for [pick], in preference order (not including [pick] itself, which is filtered later). */
    private fun replacements(
        pick: PlanPick,
        options: PlanOptions,
    ): List<List<PlanPick>> =
        when (pick) {
            is PlanPick.MainAndDrink -> {
                if (pick.diner.isChild) {
                    kidPairs().map { listOf(PlanPick.MainAndDrink(pick.diner, it.main, it.drink, it.kidFriendly)) }
                } else {
                    adultChoices(pick.diner)
                }
            }

            is PlanPick.Meal -> {
                adultChoices(pick.diner)
            }

            is PlanPick.SharedMeal -> {
                sharedMeals.map { listOf(PlanPick.SharedMeal(pick.first, pick.second, it)) } +
                    listOfNotNull(separateMeals(pick))
            }

            is PlanPick.Extra -> {
                (extras(options).filter { it.role == pick.item.role }).map { listOf(PlanPick.Extra(it)) }
            }
        }

    /** Every main plus drink and every one-person combo for an adult (BR-13). */
    private fun adultChoices(diner: Diner): List<List<PlanPick>> =
        adultMains.flatMap { main -> drinks.map { listOf<PlanPick>(PlanPick.MainAndDrink(diner, main, it, false)) } } +
            meals.map { listOf<PlanPick>(PlanPick.Meal(diner, it)) }

    private fun kidPairs(): List<Option.PairOption> =
        kidMains.flatMap { main -> kidDrinks.map { Option.PairOption(main, it, !kidsMenuUnavailable) } }

    /** A shared meal for two as two separate meals: the same main plus drink twice, the nearest in price. */
    private fun separateMeals(pick: PlanPick.SharedMeal): List<PlanPick>? {
        val pair =
            adultMains
                .flatMap { main -> drinks.map { main to it } }
                .minByOrNull { (main, drink) ->
                    abs(2 * (main.unitPrice.value + drink.unitPrice.value) - pick.combo.unitPrice.value)
                }
                ?: return null
        return listOf(
            PlanPick.MainAndDrink(pick.first, pair.first, pair.second, false),
            PlanPick.MainAndDrink(pick.second, pair.first, pair.second, false),
        )
    }

    /** Drops the last shared sides that a combo diner already has, so sides stay within people minus combo diners. */
    private fun withoutExtraSides(
        picks: List<PlanPick>,
        request: PlanRequest,
    ): List<PlanPick> {
        val allowed = request.people - picks.sumOf { it.comboDiners }
        var sides = picks.count { it is PlanPick.Extra && it.item.role == PlanRole.SIDE }
        if (sides <= allowed) return picks
        val kept = picks.toMutableList()
        for (index in kept.indices.reversed()) {
            val pick = kept[index]
            if (sides > allowed && pick is PlanPick.Extra && pick.item.role == PlanRole.SIDE) {
                kept.removeAt(index)
                sides--
            }
        }
        return kept
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
        val comboDiners = base.sumOf { pick -> pick.comboDiners }
        val table = extraSlots(request, comboDiners).let { Table(it, reach(it)) }
        val found = Selection(request.budget.value, cap(request))
        table.reach[0].forEachSetBit { units ->
            val extras = units * unit
            found.offer(0, extras, total(baseSubtotal + extras))
        }
        val target =
            (if (kind == PlanKind.OVER) found.over ?: found.under else found.under) ?: checkNotNull(found.cheapest)
        return reprice(kind, base + pick(table, target.subtotal), request)
    }

    private fun reprice(
        kind: PlanKind,
        picks: List<PlanPick>,
        request: PlanRequest,
    ): BudgetPlan {
        val totals = PricingEngine.price(Rupiah(picks.sumOf { pick -> pick.items.sumOf { it.unitPrice.value } }), rules)
        val budget = request.budget
        val newKind =
            when (kind) {
                PlanKind.MORE_THAN_ENOUGH -> {
                    if (totals.total <= budget) kind else PlanKind.OVER
                }

                PlanKind.EXACT, PlanKind.UNDER, PlanKind.OVER -> {
                    when {
                        totals.total == budget -> PlanKind.EXACT
                        totals.total < budget -> PlanKind.UNDER
                        else -> PlanKind.OVER
                    }
                }

                PlanKind.TOO_LOW -> {
                    kind
                }
            }
        return BudgetPlan(newKind, picks, totals)
    }

    private fun build(
        tables: List<Table>,
        found: Found,
        kind: PlanKind,
    ): BudgetPlan {
        // The same subtotal can come from several ways to feed the adults with the same number of shared meals (a
        // main plus drink or a combo each): the one with the fewest repeats wins, and a tie keeps the fewest combos.
        val first = tables[found.table]
        val units = (found.subtotal / unit).toInt()
        val picks =
            tables
                .filter { it.shared == first.shared && it.reach[0][units] }
                .map { pick(it, found.subtotal) }
                .minBy { repeats(it) }
        return BudgetPlan(kind, picks, PricingEngine.price(Rupiah(found.subtotal), rules))
    }

    /** How many mains and meals, and how many extras, a plan uses a second time (FR-032 variety). */
    private fun repeats(picks: List<PlanPick>): Int {
        val mains = picks.filter { it !is PlanPick.Extra }.map { pick -> pick.items.first().id }
        val extras = picks.filterIsInstance<PlanPick.Extra>().map { it.item.id }
        return (mains.size - mains.toSet().size) + (extras.size - extras.toSet().size)
    }

    /** Rebuilds [subtotal] slot by slot, preferring mains and extras not used yet (FR-032 variety). */
    private fun pick(
        table: Table,
        subtotal: Long,
    ): List<PlanPick> {
        val usedMains = mutableSetOf<String>()
        val usedExtras = mutableSetOf<String>()
        var left = subtotal
        return table.slots.mapIndexedNotNull { index, slot ->
            val next = table.reach[index + 1]
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

    /** The two-person combos and then the one-person combos, who come first in the plan. */
    private fun headSlots(shape: Shape): List<Slot> {
        val mealOptions = meals.map(Option::MealOption)
        return buildList {
            repeat(shape.shared) {
                add(Slot(listOf(adult(it * 2 + 1), adult(it * 2 + 2)), sharedMeals.map(Option::SharedOption)))
            }
            repeat(shape.solo) { add(Slot(listOf(adult(shape.shared * 2 + it + 1)), mealOptions)) }
        }
    }

    /** The adults with a main and a drink, the children and the shared sides and desserts. */
    private fun tailSlots(
        request: PlanRequest,
        shape: Shape,
    ): List<Slot> {
        val pairs = adultMains.flatMap { main -> drinks.map { Option.PairOption(main, it, kidFriendly = false) } }
        val child = kidMains.flatMap { main -> kidDrinks.map { Option.PairOption(main, it, !kidsMenuUnavailable) } }
        val first = shape.comboDiners + 1
        return buildList {
            repeat(request.adults - shape.comboDiners) { add(Slot(listOf(adult(first + it)), pairs)) }
            repeat(request.children) { add(Slot(listOf(Diner(isChild = true, number = it + 1)), child)) }
            addAll(extraSlots(request, shape.comboDiners))
        }
    }

    /**
     * One optional side or dessert per person, shared by the table (FR-032). A person whose meal is a combo already
     * has a side, so sides get people minus [comboDiners] slots, and the combo diners' slots take desserts only
     * (OQ-15). Without sides or desserts the slots shrink to what is left.
     */
    private fun extraSlots(
        request: PlanRequest,
        comboDiners: Int,
    ): List<Slot> {
        val options = request.options
        val noExtra = listOf(Option.ExtraOption(null))
        val any = Slot(emptyList(), noExtra + extras(options).map { Option.ExtraOption(it) })
        val dessertsOnly =
            Slot(
                emptyList(),
                noExtra + (if (options.includeDesserts) desserts else emptyList()).map { Option.ExtraOption(it) },
            )
        return when {
            extras(options).isEmpty() -> emptyList()
            else -> List(request.people - comboDiners) { any } + List(comboDiners) { dessertsOnly }
        }.filter { it.options.size > 1 }
    }

    private fun extras(options: PlanOptions): List<PlannedItem> =
        (if (options.includeSides) sides else emptyList()) + (if (options.includeDesserts) desserts else emptyList())

    /** The highest total an over plan may reach: the budget plus `overCapPercent` (D-27), or the budget in Strict. */
    private fun cap(request: PlanRequest): Long =
        when (request.options.tolerance) {
            PlanTolerance.STRICT -> request.budget.value
            PlanTolerance.UP_TO_CAP -> request.budget.value + request.budget.value * config.overCapPercent / PERCENT
        }

    /** [reach] for [slots] alone, with a set size that fits them. */
    private fun reach(slots: List<Slot>): List<SubtotalSet> {
        val size = slots.sumOf { slot -> slot.options.maxOf { it.price } / unit }.toInt() + 1
        return reach(slots, size, listOf(SubtotalSet(size).apply { set(0) }))
    }

    /**
     * The reachable subtotals of [slots] stacked in front of the slots that [tail] describes: entry `i` holds every
     * subtotal (in [unit]s) that slots `i` to the end can make, and [tail] supplies the entries after [slots]. Each
     * option shifts the whole set a word at a time, so the cost depends on the price range, not on how many subtotals
     * are reachable: a menu whose prices share only Rp 1 stays within the time budget (TR-PRF-08).
     */
    private fun reach(
        slots: List<Slot>,
        size: Int,
        tail: List<SubtotalSet>,
    ): List<SubtotalSet> {
        val reach = ArrayList<SubtotalSet>(slots.size + tail.size)
        reach.addAll(tail)
        for (index in slots.indices.reversed()) {
            val next = reach[0]
            val current = SubtotalSet(size)
            slots[index].options.map { (it.price / unit).toInt() }.distinct().forEach { step ->
                current.orShifted(next, step)
            }
            reach.add(0, current)
        }
        return reach
    }

    /** The final total of [subtotal] (FR-013), the only number plans are compared on (BR-15). */
    private fun total(subtotal: Long): Long = PricingEngine.price(Rupiah(subtotal), rules).total.value

    private fun priceUnit(): Long {
        val prices =
            (adultMains + drinks + sides + desserts + meals + sharedMeals).map { it.unitPrice.value }.filter { it > 0 }
        return prices.fold(0L, ::gcd).takeIf { it > 0 } ?: 1L
    }

    private fun product(item: PlannedItem): Product = products.first { it.id == item.id }

    /** How the adults are fed: [shared] two-person combos, [solo] one-person combos, the rest main plus drink. */
    private class Shape(
        val shared: Int,
        val solo: Int,
    ) {
        /** Adults whose meal is a combo, so they already have a side (OQ-15). */
        val comboDiners: Int get() = shared * 2 + solo
    }

    /** The slots of one plan shape with their reachable subtotals. */
    private class Table(
        val slots: List<Slot>,
        val reach: List<SubtotalSet>,
        val shared: Int = 0,
    )

    /** A reachable subtotal of [Table] number [table] and the final total it lands on. */
    private class Found(
        val table: Int,
        val subtotal: Long,
        val total: Long,
    ) {
        fun sameAs(other: Found): Boolean = total == other.total && subtotal == other.subtotal
    }

    /** The best subtotals seen so far: under, over (within [cap]), cheapest and most generous (FR-032). */
    private class Selection(
        private val budget: Long,
        private val cap: Long,
    ) {
        var under: Found? = null
        var over: Found? = null
        var cheapest: Found? = null
        var top: Found? = null

        fun offer(
            table: Int,
            subtotal: Long,
            total: Long,
        ) {
            // A Found is only built when it takes a place, so the many subtotals that lose cost no allocation.
            if (total <= budget && beats(total, subtotal, under)) under = Found(table, subtotal, total)
            if (total in (budget + 1)..cap && closerOver(total, subtotal, over)) over = Found(table, subtotal, total)
            if (total < (cheapest?.total ?: Long.MAX_VALUE)) cheapest = Found(table, subtotal, total)
            if (beats(total, subtotal, top)) top = Found(table, subtotal, total)
        }

        private fun beats(
            total: Long,
            subtotal: Long,
            other: Found?,
        ): Boolean = other == null || total > other.total || (total == other.total && subtotal > other.subtotal)

        private fun closerOver(
            total: Long,
            subtotal: Long,
            other: Found?,
        ): Boolean = other == null || total < other.total || (total == other.total && subtotal > other.subtotal)
    }

    /** A fixed-size set of subtotals in price units, one bit per unit. */
    private class SubtotalSet(
        val size: Int,
    ) {
        private val words = LongArray((size + WORD_BITS - 1) / WORD_BITS)

        /** The last word that can hold a member, so a shift never walks the empty words above it. */
        private var high = -1

        operator fun get(index: Int): Boolean =
            index >= 0 && index / WORD_BITS < words.size && words[index / WORD_BITS] and (1L shl index) != 0L

        fun set(index: Int) {
            words[index / WORD_BITS] = words[index / WORD_BITS] or (1L shl index)
            high = maxOf(high, index / WORD_BITS)
        }

        /** Adds every member of [source] plus [shift]; members that would fall past the end are dropped. */
        fun orShifted(
            source: SubtotalSet,
            shift: Int,
        ) {
            val wordShift = shift / WORD_BITS
            val bitShift = shift % WORD_BITS
            val top = minOf(words.size - 1, source.high + wordShift + (if (bitShift != 0) 1 else 0))
            for (i in top downTo wordShift) {
                val from = i - wordShift
                var word = if (from <= source.high) source.words[from] shl bitShift else 0L
                if (bitShift != 0 && from > 0 && from - 1 <= source.high) {
                    word = word or (source.words[from - 1] ushr (WORD_BITS - bitShift))
                }
                words[i] = words[i] or word
            }
            if (source.high >= 0) high = maxOf(high, top)
        }

        inline fun forEachSetBit(action: (Long) -> Unit) {
            for (i in words.indices) {
                var word = words[i]
                while (word != 0L) {
                    action(i.toLong() * WORD_BITS + word.countTrailingZeroBits())
                    word = word and (word - 1)
                }
            }
        }

        private companion object {
            const val WORD_BITS = 64
        }
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

        /** Rows the swap picker shows besides the current choice (`BudgetPas.dc.html`). */
        const val MAX_ALTERNATIVES = 15
        private const val UNPRICED = -1L

        private fun adult(number: Int) = Diner(isChild = false, number = number)

        /** Adults a pick feeds with a combo: they already have a side (OQ-15). */
        private val PlanPick.comboDiners: Int
            get() =
                when (this) {
                    is PlanPick.Meal -> 1
                    is PlanPick.SharedMeal -> 2
                    else -> 0
                }

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
    }
}
