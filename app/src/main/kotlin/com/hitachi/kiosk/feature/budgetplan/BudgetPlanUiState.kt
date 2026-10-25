package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.designsystem.component.RollDirection
import com.hitachi.kiosk.core.domain.planner.PlanKind

/** The two steps of SCR-19 (architecture §11: one route, an internal step). */
enum class BudgetPlanStep {
    /** Step 1 "Your table": budget and people. */
    TABLE,

    /** Step 2 "Your plan": the hero plan card. */
    PLAN,
}

/**
 * SCR-19 Budget Plan (FR-032; `BudgetPas.dc.html` and its `S_BudgetPas_*` boards). The screen draws exactly this;
 * the ViewModel runs the planner and computes every flag and amount. Amounts are already formatted ("Rp 150.000").
 *
 * @property loaded false until the cached catalog has been read; the screen then shows only its background.
 * @property budget the budget, formatted; "Rp 0" until one is chosen (TC-166).
 * @property hasBudget a budget is chosen: the amount is drawn in ink, the caption names the per-person amount, and
 *   the dock shows the amount instead of "Choose a budget to continue" (D-35, A-46).
 * @property perPerson the budget divided by the group, rounded down to Rp 100, formatted; `null` without a budget.
 * @property chips the quick budgets from config `quickBudgets`.
 * @property otherChip "Other" (no custom amount) or "Edit" (a custom amount, drawn selected).
 * @property groupTooLarge more people than [maxGroupSize]: the group card and the disabled "Reduce group" dock.
 * @property canSeePlans "See plans" is enabled: a budget, a group within the limit and a plan.
 * @property plan step 2's content, or `null` when there is no plan for the inputs.
 * @property keypad the "Other" keypad while it is open.
 * @property swap the swap picker while it is open (DEV-109).
 * @property added "Added to order" is showing; Your order opens about 0.6 s later (FR-032 AC5, DEV-112).
 * @property cartFull the plan was refused because it would break the cart limits: the message shows and the dock
 *   button becomes "Review order" (FR-032 AC15, DEV-111).
 * @property cartCount units in the order, for the "My order" badge.
 */
@Immutable
data class BudgetPlanUiState(
    val loaded: Boolean = false,
    val step: BudgetPlanStep = BudgetPlanStep.TABLE,
    val budget: String = "",
    val hasBudget: Boolean = false,
    val perPerson: String? = null,
    val chips: List<BudgetChipUi> = emptyList(),
    val otherChip: OtherChip = OtherChip.OTHER,
    val adults: Int = DEFAULT_ADULTS,
    val children: Int = MIN_CHILDREN,
    val adultsRoll: RollDirection = RollDirection.UP,
    val childrenRoll: RollDirection = RollDirection.UP,
    val maxGroupSize: Int = DEFAULT_MAX_GROUP,
    val groupTooLarge: Boolean = false,
    val canSeePlans: Boolean = false,
    val plan: PlanUi? = null,
    val keypad: KeypadUi? = null,
    val swap: SwapSheetUi? = null,
    val includedExpanded: Boolean = true,
    val priceExpanded: Boolean = false,
    val added: Boolean = false,
    val cartFull: Boolean = false,
    val cartCount: Int = 0,
) {
    val people: Int get() = adults + children

    val canDecreaseAdults: Boolean get() = adults > MIN_ADULTS

    val canIncreaseAdults: Boolean get() = adults < MAX_PER_STEPPER

    val canDecreaseChildren: Boolean get() = children > MIN_CHILDREN

    val canIncreaseChildren: Boolean get() = children < MAX_PER_STEPPER

    companion object {
        /** Adults 1–10, children 0–10 (FR-032 Step 1). */
        const val MIN_ADULTS: Int = 1
        const val MIN_CHILDREN: Int = 0
        const val MAX_PER_STEPPER: Int = 10

        /** Adults start at 2 and children at 0 (A-46, D-35). */
        const val DEFAULT_ADULTS: Int = 2

        /** Config `maxGroupSize` default (FR-032). */
        const val DEFAULT_MAX_GROUP: Int = 10
    }
}

/**
 * A quick budget chip. [thousands] is set for a whole number of thousands, drawn "Rp 50k" (`BudgetPas.dc.html`);
 * any other amount is drawn as [formatted].
 */
@Immutable
data class BudgetChipUi(
    val amount: Long,
    val thousands: Long?,
    val formatted: String,
    val selected: Boolean,
)

/** The fifth chip: "Other" opens the keypad; once a custom amount is set it reads "Edit" and is selected. */
enum class OtherChip { OTHER, EDIT }

/**
 * The "Other" keypad: the typed [amount] (the current budget when it opens) and whether "Use this budget" is enabled
 * (≥ Rp 1.000 after flooring). [empty] is nothing typed yet, so the amount is drawn in the placeholder grey; [tooLow]
 * is something typed below Rp 1.000, so "Minimum Rp 1.000" shows under it in the warning color (FR-032, TC-198).
 */
@Immutable
data class KeypadUi(
    val amount: String,
    val canUse: Boolean,
    val empty: Boolean = false,
    val tooLow: Boolean = false,
)

/** The keys of the budget keypad, in grid order (`BudgetPas.dc.html`). */
enum class KeypadKey(
    val digits: String,
) {
    ONE("1"),
    TWO("2"),
    THREE("3"),
    FOUR("4"),
    FIVE("5"),
    SIX("6"),
    SEVEN("7"),
    EIGHT("8"),
    NINE("9"),
    THOUSAND("000"),
    ZERO("0"),
    DELETE(""),
}

/** Step 2's title (FR-032): both plans, an exact match, or one plan. */
enum class PlanTitle { TWO_PLANS, PERFECT_FIT, YOUR_PLAN }

/** Which of the two plans a card shows. */
enum class PlanSide { UNDER, OVER }

/**
 * Step 2 "Your plan" for the chosen plan.
 *
 * @property cards the two selectable plan cards, "Within your budget" and "A little extra"; empty when there is only
 *   one plan, which the [hero] card shows (FR-032, AC10).
 * @property notes the lines under the hero card's meta, in order.
 * @property options the "Add sides" and "Add desserts" chips, the over-budget switch and "Reset to suggested".
 * @property groups "What's included": one meal per person, then "For the table".
 * @property lastSwap the item just swapped, which slides in; [swapSerial] grows with every swap.
 */
@Immutable
data class PlanUi(
    val title: PlanTitle,
    val cards: List<PlanCardUi>,
    val hero: HeroUi,
    val notes: List<PlanNote>,
    val options: PlanOptionsUi,
    val groups: List<PlanGroupUi>,
    val itemCount: Int,
    val price: PriceDetailsUi,
    val lastSwap: SwapTarget? = null,
    val swapSerial: Int = 0,
)

/** One of the two plan cards: its [kind] is UNDER or OVER, [delta] the amount left or over, [items] its item count. */
@Immutable
data class PlanCardUi(
    val side: PlanSide,
    val total: String,
    val delta: String,
    val items: Int,
    val selected: Boolean,
)

/**
 * The options of Step 2: [overCapPercent] is config `overCapPercent`; [canReset] is true once an option or an item
 * differs from the suggestion.
 */
@Immutable
data class PlanOptionsUi(
    val includeSides: Boolean,
    val includeDesserts: Boolean,
    val allowOver: Boolean,
    val overCapPercent: Int,
    val canReset: Boolean,
)

/**
 * The hero plan card (FR-032 table). [delta] is the amount its badge names: left, over or still needed; [total] is the
 * final total, tax included.
 */
@Immutable
data class HeroUi(
    val kind: PlanKind,
    val total: String,
    val delta: String,
)

/** A note under the hero card (FR-032 edge cases, D-28). */
@Immutable
sealed interface PlanNote {
    /** "This is the lowest-priced complete meal for your group…" */
    data object TooLow : PlanNote

    /** "This is our most generous plan for your group, and it still fits." */
    data object Generous : PlanNote

    /** "No plan fits within 10% over your budget…", with config `overCapPercent`. */
    data class NoOverPlan(
        val percent: Int,
    ) : PlanNote

    /** "A second plan is hidden while "Allow a little over budget" is off." */
    data object OverOff : PlanNote

    /** "Kids' menu not available": children got the cheapest main (BR-14, D-28). */
    data object KidsMenuUnavailable : PlanNote
}

/** One group of "What's included": a person's meal, or the table's shared items. */
@Immutable
data class PlanGroupUi(
    val label: PlanGroupLabel,
    val items: List<PlanItemUi>,
)

/** The heading of a [PlanGroupUi]. */
@Immutable
sealed interface PlanGroupLabel {
    data class Adult(
        val number: Int,
    ) : PlanGroupLabel

    /** "Child 1 · kid-friendly" when the main is tagged kid-friendly. */
    data class Child(
        val number: Int,
        val kidFriendly: Boolean = false,
    ) : PlanGroupLabel

    /** "Adults 1 & 2 · sharing": a two-person meal. */
    data class Sharing(
        val first: Int,
        val second: Int,
    ) : PlanGroupLabel

    /** "For the table": the table's sides and desserts. */
    data object Shared : PlanGroupLabel
}

/** One planned meal or extra; [swap] opens the swap picker for it. */
@Immutable
data class PlanItemUi(
    val name: String,
    val meta: PlanItemMeta,
    val price: String,
    val imageUrl: String?,
    val tone: String?,
    val swap: SwapTarget?,
)

/** The small line under an item's name: "Main + drink", what a combo includes, or "Side". */
@Immutable
sealed interface PlanItemMeta {
    data object MainAndDrink : PlanItemMeta

    /** A one-person combo; [includes] is "fries, cola" when the menu says. */
    data class Meal(
        val includes: String?,
    ) : PlanItemMeta

    data class MealForTwo(
        val includes: String?,
    ) : PlanItemMeta

    data object Side : PlanItemMeta

    data object Dessert : PlanItemMeta

    /** "Cheeseburger + Cola, twice": a swap row for two separate meals. */
    data class Twice(
        val pair: String,
    ) : PlanItemMeta
}

/** Which pick of the plan a "Swap" button changes. */
@Immutable
data class SwapTarget(
    val pickIndex: Int,
)

/**
 * "Price details": the same breakdown as checkout (FR-013). [rounding] shows only when it is not zero; [comboSavings]
 * is what the plan's combos save over buying the same food apart, already included in the total.
 */
@Immutable
data class PriceDetailsUi(
    val subtotal: String,
    val promoLabel: String?,
    val discount: String?,
    val taxLabel: String?,
    val tax: String,
    val rounding: String?,
    val comboSavings: String?,
    val total: String,
)

/**
 * The swap picker (DEV-109, `S_BudgetPas_swapOpen`): [title] says what is being swapped, [planTotal] is the plan's
 * total now, and [rows] are the current choice and its alternatives with the price change and the new total.
 */
@Immutable
data class SwapSheetUi(
    val title: SwapTitle,
    val planTotal: String,
    val rows: List<SwapRowUi>,
)

/** The picker's heading. */
@Immutable
sealed interface SwapTitle {
    data class Adult(
        val number: Int,
    ) : SwapTitle

    data class Child(
        val number: Int,
    ) : SwapTitle

    data object SharedMeal : SwapTitle

    data object SharedSide : SwapTitle

    data object SharedDessert : SwapTitle
}

/**
 * One picker row. [name] is `null` for "Two separate meals". [change] is the plan total's change; [total] the plan
 * total after choosing the row.
 */
@Immutable
data class SwapRowUi(
    val name: String?,
    val meta: PlanItemMeta,
    val imageUrl: String?,
    val tone: String?,
    val current: Boolean,
    val change: PriceChange,
    val total: String,
)

/** "Same price", "+Rp 2.000" or "-Rp 1.000". */
@Immutable
sealed interface PriceChange {
    data object Same : PriceChange

    data class Up(
        val amount: String,
    ) : PriceChange

    data class Down(
        val amount: String,
    ) : PriceChange
}

/** What SCR-19 can ask for. */
sealed interface BudgetPlanEvent {
    /** A quick budget chip. */
    data class PickBudget(
        val amount: Long,
    ) : BudgetPlanEvent

    /** "Other" or "Edit": opens the keypad with an empty amount. */
    data object OpenKeypad : BudgetPlanEvent

    data class PressKey(
        val key: KeypadKey,
    ) : BudgetPlanEvent

    /** "Cancel" or the scrim. */
    data object CloseKeypad : BudgetPlanEvent

    /** "Use this budget". */
    data object UseKeypadBudget : BudgetPlanEvent

    data object IncreaseAdults : BudgetPlanEvent

    data object DecreaseAdults : BudgetPlanEvent

    data object IncreaseChildren : BudgetPlanEvent

    data object DecreaseChildren : BudgetPlanEvent

    /** "Set group to 10". */
    data object FitGroup : BudgetPlanEvent

    /** "See plans" on the step 1 dock. */
    data object SeePlans : BudgetPlanEvent

    /** The back pill or system Back: closes an overlay, then step 2 → step 1, then step 1 → Menu. */
    data object Back : BudgetPlanEvent

    /** The "My order" pill. */
    data object ViewOrder : BudgetPlanEvent

    /** One of the two plan cards. */
    data class SelectPlan(
        val side: PlanSide,
    ) : BudgetPlanEvent

    data object ToggleIncluded : BudgetPlanEvent

    data object TogglePriceDetails : BudgetPlanEvent

    data class SetIncludeSides(
        val include: Boolean,
    ) : BudgetPlanEvent

    data class SetIncludeDesserts(
        val include: Boolean,
    ) : BudgetPlanEvent

    /** The "Allow a little over budget" switch. */
    data class SetAllowOver(
        val allow: Boolean,
    ) : BudgetPlanEvent

    /** A "Swap" button: opens the picker for that pick. */
    data class Swap(
        val target: SwapTarget,
    ) : BudgetPlanEvent

    /** A row of the swap picker (an index into [SwapSheetUi.rows]). The current row does nothing. */
    data class PickAlternative(
        val index: Int,
    ) : BudgetPlanEvent

    /** "Done", the close button or the scrim of the swap picker. */
    data object CloseSwap : BudgetPlanEvent

    /** "Reset to suggested". */
    data object ResetPlan : BudgetPlanEvent

    /** "Add plan to order". */
    data object AddPlan : BudgetPlanEvent
}

/** One-off effects of SCR-19. */
sealed interface BudgetPlanEffect {
    data object NavigateToMenu : BudgetPlanEffect

    data object NavigateToYourOrder : BudgetPlanEffect
}
