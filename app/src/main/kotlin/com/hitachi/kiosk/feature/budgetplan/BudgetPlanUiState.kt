package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.designsystem.component.RollDirection
import com.hitachi.kiosk.core.domain.planner.PlanKind
import com.hitachi.kiosk.core.domain.planner.PlanTolerance
import com.hitachi.kiosk.core.domain.planner.SwapPart

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
 * @property chips the quick budgets from config `quickBudgets`.
 * @property otherChip "Other" (no custom amount) or "Edit" (a custom amount, drawn selected).
 * @property groupTooLarge more people than [maxGroupSize]: the group card and the disabled "Reduce group" dock.
 * @property canSeePlans "See plans" is enabled: a budget, a group within the limit and a plan.
 * @property plan step 2's content, or `null` when there is no plan for the inputs.
 * @property keypad the "Other" keypad while it is open.
 * @property customize the Customize sheet while it is open.
 * @property added "Added to order" is showing; Your order opens 1 s later (FR-032 AC5).
 * @property cartCount units in the order, for the "My order" badge.
 */
@Immutable
data class BudgetPlanUiState(
    val loaded: Boolean = false,
    val step: BudgetPlanStep = BudgetPlanStep.TABLE,
    val budget: String = "",
    val chips: List<BudgetChipUi> = emptyList(),
    val otherChip: OtherChip = OtherChip.OTHER,
    val adults: Int = MIN_ADULTS,
    val children: Int = MIN_CHILDREN,
    val adultsRoll: RollDirection = RollDirection.UP,
    val childrenRoll: RollDirection = RollDirection.UP,
    val maxGroupSize: Int = DEFAULT_MAX_GROUP,
    val groupTooLarge: Boolean = false,
    val canSeePlans: Boolean = false,
    val plan: PlanUi? = null,
    val keypad: KeypadUi? = null,
    val customize: CustomizeUi? = null,
    val includedExpanded: Boolean = false,
    val priceExpanded: Boolean = false,
    val added: Boolean = false,
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

/** The "Other" keypad: the typed [amount] and whether "Use this budget" is enabled (≥ Rp 1.000 after flooring). */
@Immutable
data class KeypadUi(
    val amount: String,
    val canUse: Boolean,
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

/** Which of the two plans a segment shows. */
enum class PlanSide { UNDER, OVER }

/**
 * Step 2 "Your plan" for the chosen plan.
 *
 * @property options "Just under | Just over"; empty when there is only one plan (FR-032, AC10).
 * @property notes the lines under the hero card's meta, in order.
 * @property groups "What's included", per person then the shared items.
 * @property lastSwap the item just swapped, which slides in; [swapSerial] grows with every swap.
 */
@Immutable
data class PlanUi(
    val title: PlanTitle,
    val options: List<PlanOptionUi>,
    val hero: HeroUi,
    val notes: List<PlanNote>,
    val groups: List<PlanGroupUi>,
    val itemCount: Int,
    val price: PriceDetailsUi,
    val lastSwap: SwapTarget? = null,
    val swapSerial: Int = 0,
)

/** One segment of "Just under | Just over". */
@Immutable
data class PlanOptionUi(
    val side: PlanSide,
    val total: String,
    val selected: Boolean,
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

    /** "Strict budget: only plans at or under your budget are shown." */
    data object Strict : PlanNote

    /** "Kids' menu not available": children got the cheapest main (BR-14, D-28). */
    data object KidsMenuUnavailable : PlanNote
}

/** One group of "What's included" and of the Customize item list. */
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

    data class Child(
        val number: Int,
    ) : PlanGroupLabel

    /** "Adult 1 & 2 · sharing": a two-person meal. */
    data class Sharing(
        val first: Int,
        val second: Int,
    ) : PlanGroupLabel

    /** "Shared": the table's sides and desserts. */
    data object Shared : PlanGroupLabel
}

/** One planned item; [swap] is `null` for an item without "Swap" (a shared meal). */
@Immutable
data class PlanItemUi(
    val name: String,
    val meta: PlanItemMeta,
    val price: String,
    val imageUrl: String?,
    val tone: String?,
    val swap: SwapTarget?,
)

/** The small line under an item's name ("Main · kid-friendly", "Meal · Cheeseburger · fries · cola"). */
@Immutable
sealed interface PlanItemMeta {
    data object Main : PlanItemMeta

    data object KidMain : PlanItemMeta

    data object Drink : PlanItemMeta

    data class Meal(
        val includes: String?,
    ) : PlanItemMeta

    data class MealForTwo(
        val includes: String?,
    ) : PlanItemMeta

    data object Side : PlanItemMeta

    data object Dessert : PlanItemMeta
}

/** Which item a "Swap" replaces: a pick of the plan and the part of it. */
@Immutable
data class SwapTarget(
    val pickIndex: Int,
    val part: SwapPart,
)

/** "Price details": the same breakdown as checkout (FR-013). [rounding] shows only when it is not zero. */
@Immutable
data class PriceDetailsUi(
    val subtotal: String,
    val promoLabel: String?,
    val discount: String?,
    val taxLabel: String?,
    val tax: String,
    val rounding: String?,
    val total: String,
)

/** The Customize sheet (DEV-043a). [canReset] is true once an item was swapped. */
@Immutable
data class CustomizeUi(
    val includeSides: Boolean,
    val includeDesserts: Boolean,
    val tolerance: PlanTolerance,
    val overCapPercent: Int,
    val canReset: Boolean,
)

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

    /** "Just under" or "Just over". */
    data class SelectPlan(
        val side: PlanSide,
    ) : BudgetPlanEvent

    data object ToggleIncluded : BudgetPlanEvent

    data object TogglePriceDetails : BudgetPlanEvent

    data object OpenCustomize : BudgetPlanEvent

    /** "Done", the close button or the scrim. */
    data object CloseCustomize : BudgetPlanEvent

    data class SetIncludeSides(
        val include: Boolean,
    ) : BudgetPlanEvent

    data class SetIncludeDesserts(
        val include: Boolean,
    ) : BudgetPlanEvent

    data class SetTolerance(
        val tolerance: PlanTolerance,
    ) : BudgetPlanEvent

    data class Swap(
        val target: SwapTarget,
    ) : BudgetPlanEvent

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
