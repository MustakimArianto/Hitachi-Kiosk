package com.hitachi.kiosk.core.domain.planner

import com.hitachi.kiosk.core.domain.cart.CartAddition
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.LineChoice
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Totals

/** "Strict" or "Up to +10 %" in the Customize sheet (FR-032, D-27). */
enum class PlanTolerance {
    /** Only plans at or under the budget. */
    STRICT,

    /** An over plan is allowed up to `overCapPercent` above the budget. */
    UP_TO_CAP,
}

/** The Customize options (FR-032). The defaults are the suggested plan. */
data class PlanOptions(
    val includeSides: Boolean = true,
    val includeDesserts: Boolean = true,
    val tolerance: PlanTolerance = PlanTolerance.UP_TO_CAP,
)

/** Step 1 of SCR-19: the final-total budget and who is eating (FR-032). */
data class PlanRequest(
    val budget: Rupiah,
    val adults: Int,
    val children: Int,
    val options: PlanOptions = PlanOptions(),
) {
    val people: Int get() = adults + children
}

/** Which hero card a plan is (FR-032 table): the tag, title and badge follow from it. */
enum class PlanKind {
    /** "Exact match": the final total equals the budget. */
    EXACT,

    /** "Best fit": the closest total under the budget. */
    UNDER,

    /** "Slightly above budget": the closest total over it, within the cap. */
    OVER,

    /** "Need Rp X more": the budget is below the cheapest complete plan. */
    TOO_LOW,

    /** "More than enough": the budget covers the most generous plan. */
    MORE_THAN_ENOUGH,
}

/** The role an item plays in a plan (FR-032), for the "What's included" meta and the Swap rules. */
enum class PlanRole { MAIN, DRINK, MEAL, SIDE, DESSERT }

/** Who a pick feeds: "Adult 2" or "Child 1" (FR-032 "What's included"). [number] counts from 1 per kind. */
data class Diner(
    val isChild: Boolean,
    val number: Int,
)

/**
 * One menu item as a plan uses it: the default options of its groups, or the first option of a group without a
 * default (A-42), so it can go to the cart exactly as planned (FR-032 AC5).
 */
data class PlannedItem(
    val id: String,
    val role: PlanRole,
    val name: LocalizedText,
    val unitPrice: Rupiah,
    val imageUrl: String?,
    val tone: String?,
    val cartItem: CartItem,
    val choices: List<LineChoice>,
)

/** One entry of a plan: a person's main and drink, a person's meal, a meal two adults share, or a shared extra. */
sealed interface PlanPick {
    /** The items of the pick, in display order. */
    val items: List<PlannedItem>

    /** An adult's or child's main and drink (BR-13, BR-14). */
    data class MainAndDrink(
        val diner: Diner,
        val main: PlannedItem,
        val drink: PlannedItem,
        val kidFriendly: Boolean,
    ) : PlanPick {
        override val items: List<PlannedItem> get() = listOf(main, drink)
    }

    /** A combo with `serves` = 1 covers one adult's main and drink (BR-13). */
    data class Meal(
        val diner: Diner,
        val combo: PlannedItem,
    ) : PlanPick {
        override val items: List<PlannedItem> get() = listOf(combo)
    }

    /** A combo with `serves` = 2 covers two adults (BR-13). It is not swapped (`BudgetPas.dc.html`). */
    data class SharedMeal(
        val first: Diner,
        val second: Diner,
        val combo: PlannedItem,
    ) : PlanPick {
        override val items: List<PlannedItem> get() = listOf(combo)
    }

    /** A side or dessert for the table, at most one per person (FR-032). */
    data class Extra(
        val item: PlannedItem,
    ) : PlanPick {
        override val items: List<PlannedItem> get() = listOf(item)
    }
}

/** Which part of a pick a Swap replaces (FR-032 Customize). */
enum class SwapPart { MAIN, DRINK, MEAL, EXTRA }

/**
 * A complete plan with its final totals from the `PricingEngine` (BR-15).
 *
 * @property picks per person first (shared meals, adults, children), then the shared extras.
 */
data class BudgetPlan(
    val kind: PlanKind,
    val picks: List<PlanPick>,
    val totals: Totals,
) {
    /** "{k} items" on the hero card and the dock (FR-032). */
    val itemCount: Int get() = picks.sumOf { it.items.size }

    /** The final total, tax included. */
    val total: Rupiah get() = totals.total

    /** What "Add plan to order" puts in the cart: one unit per planned item; the cart merges identical ones. */
    fun toCartAdditions(): List<CartAddition> =
        picks.flatMap { it.items }.map { CartAddition(it.cartItem, 1, it.unitPrice, it.choices) }
}

/**
 * One row of the swap picker (FR-032, DEV-109): what takes the place of one pick and the plan that results.
 *
 * @property replacement the picks that stand where the swapped pick stood: one, or two when a shared meal for two
 *   becomes two separate meals. For [isCurrent] it is the pick itself.
 * @property plan the whole plan with [replacement] in it, priced by the `PricingEngine`; its total is the "Total Rp X"
 *   of the row, and its notes and kind follow from it.
 * @property change the plan's total minus the swapped plan's total, in rupiah: the "+Rp 2.000" of the row.
 */
data class PlanAlternative(
    val replacement: List<PlanPick>,
    val plan: BudgetPlan,
    val change: Long,
    val isCurrent: Boolean,
)

/** The answer to a [PlanRequest] (FR-032). */
sealed interface PlanResult {
    /** More people than `maxGroupSize`: "Please order in smaller groups" (FR-032 AC11). */
    data class GroupTooLarge(
        val maxGroupSize: Int,
    ) : PlanResult

    /** The menu has nothing a plan can be built from. The Menu hides the entry row in this case. */
    data object NoPlan : PlanResult

    /**
     * The hero plan first, then the "Just over" plan when there is one (FR-032).
     *
     * @property kidsMenuUnavailable no kid-friendly main exists, so children got the cheapest main (BR-14, D-28).
     */
    data class Plans(
        val plans: List<BudgetPlan>,
        val kidsMenuUnavailable: Boolean,
    ) : PlanResult {
        val hero: BudgetPlan get() = plans.first()

        val over: BudgetPlan? get() = plans.getOrNull(1)
    }
}
