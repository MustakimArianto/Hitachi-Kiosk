package com.hitachi.kiosk.feature.budgetplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.designsystem.component.RollDirection
import com.hitachi.kiosk.core.domain.cart.CartAddAllResult
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.BudgetPlannerConfig
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.combo
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.planner.BudgetPlan
import com.hitachi.kiosk.core.domain.planner.BudgetPlanner
import com.hitachi.kiosk.core.domain.planner.Diner
import com.hitachi.kiosk.core.domain.planner.PlanKind
import com.hitachi.kiosk.core.domain.planner.PlanOptions
import com.hitachi.kiosk.core.domain.planner.PlanPick
import com.hitachi.kiosk.core.domain.planner.PlanRequest
import com.hitachi.kiosk.core.domain.planner.PlanResult
import com.hitachi.kiosk.core.domain.planner.PlanRole
import com.hitachi.kiosk.core.domain.planner.PlanTolerance
import com.hitachi.kiosk.core.domain.planner.PlannedItem
import com.hitachi.kiosk.core.domain.planner.SwapPart
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Added to order" shows for 1 s before Your order opens (`BudgetPas.dc.html` `cta()`). */
internal const val ADDED_NAVIGATE_MILLIS = 1_000L

/** The keypad takes at most 7 digits (`BudgetPas.dc.html`). */
private const val MAX_DRAFT_DIGITS = 7

/** Budgets are exact to the nearest Rp 100 (FR-032). */
private const val BUDGET_STEP = 100L

/** "Use this budget" needs at least Rp 1.000 (`BudgetPas.dc.html`). */
private const val MIN_KEYPAD_BUDGET = 1_000L

/**
 * SCR-19 Budget Plan (FR-032, DEV-043, DEV-043a; architecture §5.1 "Budget Plan hand-off"). Builds one
 * [BudgetPlanner] from the catalog the order started with (A-29) and the device rounding step, and recomputes the plan
 * on every input change (pure, ≤ 100 ms). It keeps the step, the inputs, the Just under / Just over choice, the swaps,
 * the Customize toggles and the tolerance; only "Add plan to order" touches the order session, which tags the lines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetPlanViewModel
    @Inject
    constructor(
        private val catalogs: CatalogRepository,
        private val settings: SettingsRepository,
        private val orders: OrderSessionHolder,
    ) : ViewModel() {
        private val model = MutableStateFlow(PlanModel())

        private val cartCount: Flow<Int> =
            orders.current.flatMapLatest { session ->
                session?.state?.map { it.cart.itemCount } ?: flowOf(0)
            }

        val state: StateFlow<BudgetPlanUiState> =
            combine(model, cartCount) { current, count -> current.toUiState(count) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, BudgetPlanUiState())

        private val effects = Channel<BudgetPlanEffect>(Channel.BUFFERED)
        val effect: Flow<BudgetPlanEffect> = effects.receiveAsFlow()

        init {
            viewModelScope.launch {
                // The catalog does not change during an order (A-29); plans use the device rounding step (FR-025).
                val catalog = catalogs.catalog.first()
                val roundingStep = settings.settings.first().roundingStep
                val planner =
                    catalog?.let {
                        BudgetPlanner(it.menu, it.config.budgetPlanner, PricingRules.of(it.config, roundingStep))
                    }
                model.update { it.copy(loaded = true, catalog = catalog, planner = planner).recomputed() }
            }
        }

        fun onEvent(event: BudgetPlanEvent) {
            if (!onTableEvent(event)) onPlanEvent(event)
        }

        /** Step 1 and Back; false when [event] belongs to step 2. */
        private fun onTableEvent(event: BudgetPlanEvent): Boolean {
            when (event) {
                is BudgetPlanEvent.PickBudget -> pickBudget(event.amount)
                BudgetPlanEvent.OpenKeypad -> model.update { it.copy(keypadOpen = true, draft = "") }
                is BudgetPlanEvent.PressKey -> model.update { it.copy(draft = it.draft.press(event.key)) }
                BudgetPlanEvent.CloseKeypad -> model.update { it.copy(keypadOpen = false) }
                BudgetPlanEvent.UseKeypadBudget -> useKeypadBudget()
                BudgetPlanEvent.IncreaseAdults -> changeAdults(1)
                BudgetPlanEvent.DecreaseAdults -> changeAdults(-1)
                BudgetPlanEvent.IncreaseChildren -> changeChildren(1)
                BudgetPlanEvent.DecreaseChildren -> changeChildren(-1)
                BudgetPlanEvent.FitGroup -> fitGroup()
                BudgetPlanEvent.SeePlans -> seePlans()
                BudgetPlanEvent.Back -> back()
                else -> return false
            }
            return true
        }

        /** Step 2 and the "My order" pill. */
        private fun onPlanEvent(event: BudgetPlanEvent) {
            when (event) {
                is BudgetPlanEvent.SelectPlan -> selectPlan(event.side)
                BudgetPlanEvent.ToggleIncluded -> model.update { it.copy(includedOpen = !it.includedOpen) }
                BudgetPlanEvent.TogglePriceDetails -> model.update { it.copy(priceOpen = !it.priceOpen) }
                BudgetPlanEvent.OpenCustomize -> model.update { it.copy(sheetOpen = it.plans.isNotEmpty()) }
                BudgetPlanEvent.AddPlan -> addPlan()
                BudgetPlanEvent.ViewOrder -> effects.trySend(BudgetPlanEffect.NavigateToYourOrder)
                else -> onSheetEvent(event)
            }
        }

        /** The Customize sheet (DEV-043a). */
        private fun onSheetEvent(event: BudgetPlanEvent) {
            when (event) {
                BudgetPlanEvent.CloseCustomize -> model.update { it.copy(sheetOpen = false) }
                is BudgetPlanEvent.SetIncludeSides -> changeOptions { it.copy(includeSides = event.include) }
                is BudgetPlanEvent.SetIncludeDesserts -> changeOptions { it.copy(includeDesserts = event.include) }
                is BudgetPlanEvent.SetTolerance -> changeOptions { it.copy(tolerance = event.tolerance) }
                is BudgetPlanEvent.Swap -> swap(event.target)
                BudgetPlanEvent.ResetPlan -> model.update { if (it.edited) it.recomputed() else it }
                else -> KioskLog.w("budget_plan_unhandled_event", "event" to event::class.simpleName)
            }
        }

        private fun seePlans() {
            model.update { if (it.canSeePlans) it.copy(step = BudgetPlanStep.PLAN) else it }
        }

        private fun pickBudget(amount: Long) {
            model.update { if (it.budget == amount || amount <= 0) it else it.copy(budget = amount).recomputed() }
        }

        private fun useKeypadBudget() {
            model.update {
                val amount = it.draft.flooredBudget()
                if (amount < MIN_KEYPAD_BUDGET) it else it.copy(keypadOpen = false, budget = amount).recomputed()
            }
        }

        private fun changeAdults(delta: Int) {
            model.update {
                val next = it.adults + delta
                if (next !in BudgetPlanUiState.MIN_ADULTS..BudgetPlanUiState.MAX_PER_STEPPER) {
                    it
                } else {
                    it.copy(adults = next, adultsRoll = rollOf(delta)).recomputed()
                }
            }
        }

        private fun changeChildren(delta: Int) {
            model.update {
                val next = it.children + delta
                if (next !in BudgetPlanUiState.MIN_CHILDREN..BudgetPlanUiState.MAX_PER_STEPPER) {
                    it
                } else {
                    it.copy(children = next, childrenRoll = rollOf(delta)).recomputed()
                }
            }
        }

        /** "Set group to 10": keeps the adults (up to the limit) and fills the rest with children. */
        private fun fitGroup() {
            model.update {
                val max = it.config.maxGroupSize
                val adults = it.adults.coerceAtMost(max)
                val children = it.children.coerceAtMost(max - adults)
                it
                    .copy(
                        adults = adults,
                        children = children,
                        adultsRoll = if (adults < it.adults) RollDirection.DOWN else it.adultsRoll,
                        childrenRoll = if (children < it.children) RollDirection.DOWN else it.childrenRoll,
                    ).recomputed()
            }
        }

        private fun back() {
            val current = model.value
            when {
                current.keypadOpen -> model.update { it.copy(keypadOpen = false) }
                current.sheetOpen -> model.update { it.copy(sheetOpen = false) }
                current.step == BudgetPlanStep.PLAN -> model.update { it.copy(step = BudgetPlanStep.TABLE) }
                else -> effects.trySend(BudgetPlanEffect.NavigateToMenu)
            }
        }

        private fun selectPlan(side: PlanSide) {
            model.update {
                val index = side.ordinal
                if (index == it.selected || index !in it.plans.indices) {
                    it
                } else {
                    it.copy(selected = index, lastSwap = null, added = false)
                }
            }
        }

        /** The Customize toggles and the tolerance recompute the whole plan (`BudgetPas.dc.html`). */
        private fun changeOptions(change: (PlanOptions) -> PlanOptions) {
            model.update {
                val options = change(it.options)
                if (options == it.options) it else it.copy(options = options).recomputed()
            }
        }

        private fun swap(target: SwapTarget) {
            model.update { current ->
                val planner = current.planner ?: return@update current
                val request = current.request() ?: return@update current
                val plan = current.plans.getOrNull(current.selected) ?: return@update current
                val swapped = planner.swap(request, plan, target.pickIndex, target.part)
                current.copy(
                    plans = current.plans.toMutableList().also { it[current.selected] = swapped },
                    edited = true,
                    added = false,
                    lastSwap = target,
                    swapSerial = current.swapSerial + 1,
                )
            }
        }

        private fun addPlan() {
            val current = model.value
            if (current.added || current.step != BudgetPlanStep.PLAN) return
            val plan = current.plans.getOrNull(current.selected) ?: return
            val session = orders.current.value
            if (session == null) {
                KioskLog.w("budget_plan_add_without_order")
                return
            }
            val result =
                session.addPlan(plan.toCartAdditions(), Rupiah(current.budget), current.adults, current.children)
            when (result) {
                is CartAddAllResult.Added -> {
                    model.update { it.copy(added = true) }
                    viewModelScope.launch {
                        delay(ADDED_NAVIGATE_MILLIS)
                        effects.send(BudgetPlanEffect.NavigateToYourOrder)
                    }
                }

                // The design has no copy for a full cart; the plan stays on step 2 (FR-032).
                is CartAddAllResult.LimitReached -> {
                    KioskLog.w("budget_plan_add_refused", "limit" to result.limit.name)
                }
            }
        }
    }

private fun rollOf(delta: Int): RollDirection = if (delta > 0) RollDirection.UP else RollDirection.DOWN

/** The keypad draft after [key]: leading zeros dropped, at most [MAX_DRAFT_DIGITS] digits. */
private fun String.press(key: KeypadKey): String {
    if (key == KeypadKey.DELETE) return dropLast(1)
    val next = (this + key.digits).trimStart('0')
    return if (next.length > MAX_DRAFT_DIGITS) this else next
}

private fun String.draftAmount(): Long = toLongOrNull() ?: 0L

/** The typed amount floored to the nearest Rp 100 (FR-032). */
private fun String.flooredBudget(): Long = draftAmount() / BUDGET_STEP * BUDGET_STEP

/**
 * What the ViewModel holds; [toUiState] turns it into what the screen draws.
 *
 * @property suggested the planner's answer for the current inputs, before any swap.
 * @property plans the plans on screen: [suggested]'s, with swaps applied.
 */
private data class PlanModel(
    val loaded: Boolean = false,
    val catalog: Catalog? = null,
    val planner: BudgetPlanner? = null,
    val budget: Long = 0L,
    val adults: Int = BudgetPlanUiState.MIN_ADULTS,
    val children: Int = BudgetPlanUiState.MIN_CHILDREN,
    val adultsRoll: RollDirection = RollDirection.UP,
    val childrenRoll: RollDirection = RollDirection.UP,
    val options: PlanOptions = PlanOptions(),
    val step: BudgetPlanStep = BudgetPlanStep.TABLE,
    val suggested: PlanResult? = null,
    val plans: List<BudgetPlan> = emptyList(),
    val selected: Int = 0,
    val edited: Boolean = false,
    val keypadOpen: Boolean = false,
    val draft: String = "",
    val sheetOpen: Boolean = false,
    val includedOpen: Boolean = false,
    val priceOpen: Boolean = false,
    val added: Boolean = false,
    val lastSwap: SwapTarget? = null,
    val swapSerial: Int = 0,
) {
    val config: BudgetPlannerConfig get() = catalog?.config?.budgetPlanner ?: BudgetPlannerConfig(enabled = false)

    val groupTooLarge: Boolean get() = adults + children > config.maxGroupSize

    val canSeePlans: Boolean get() = !groupTooLarge && plans.isNotEmpty()

    fun request(): PlanRequest? =
        if (budget <= 0L || planner == null) null else PlanRequest(Rupiah(budget), adults, children, options)

    /** The plan for the current inputs and options, without swaps (also "Reset to suggested"). */
    fun recomputed(): PlanModel {
        val request = request()
        val result = if (request == null) null else planner?.plan(request)
        return copy(
            suggested = result,
            plans = (result as? PlanResult.Plans)?.plans.orEmpty(),
            selected = 0,
            edited = false,
            added = false,
            lastSwap = null,
        )
    }

    fun toUiState(cartCount: Int): BudgetPlanUiState {
        val quick = config.quickBudgets.map { it.value }
        val custom = budget > 0L && budget !in quick
        val plan = plans.getOrNull(selected)
        val suggested = suggested as? PlanResult.Plans
        return BudgetPlanUiState(
            loaded = loaded,
            step = step,
            budget = format(budget),
            chips = quick.map { BudgetChipUi(it, thousandsOf(it), format(it), selected = it == budget) },
            otherChip = if (custom) OtherChip.EDIT else OtherChip.OTHER,
            adults = adults,
            children = children,
            adultsRoll = adultsRoll,
            childrenRoll = childrenRoll,
            maxGroupSize = config.maxGroupSize,
            groupTooLarge = groupTooLarge,
            canSeePlans = canSeePlans,
            plan =
                if (plan != null && suggested != null && catalog != null) {
                    planUi(PlanContext(catalog, Rupiah(budget), options, config), suggested, plans, selected)
                        .copy(lastSwap = lastSwap, swapSerial = swapSerial)
                } else {
                    null
                },
            keypad =
                if (keypadOpen) {
                    KeypadUi(
                        format(draft.draftAmount()),
                        draft.flooredBudget() >= MIN_KEYPAD_BUDGET,
                    )
                } else {
                    null
                },
            customize =
                if (sheetOpen && plan != null) {
                    CustomizeUi(
                        includeSides = options.includeSides,
                        includeDesserts = options.includeDesserts,
                        tolerance = options.tolerance,
                        overCapPercent = config.overCapPercent,
                        canReset = edited,
                    )
                } else {
                    null
                },
            includedExpanded = includedOpen,
            priceExpanded = priceOpen,
            added = added,
            cartCount = cartCount,
        )
    }
}

/** What the step 2 mapping needs besides the plans. */
private class PlanContext(
    val catalog: Catalog,
    val budget: Rupiah,
    val options: PlanOptions,
    val config: BudgetPlannerConfig,
)

private fun format(amount: Long): String = IdrFormatter.format(Rupiah(amount))

/** "Rp 50k" for a whole number of thousands (`BudgetPas.dc.html` chips). */
private fun thousandsOf(amount: Long): Long? = (amount / THOUSAND).takeIf { amount % THOUSAND == 0L }

private const val THOUSAND = 1_000L

/** Step 2 for [plans] (the [suggested] plans with swaps), showing `plans[selected]` (FR-032). */
private fun planUi(
    context: PlanContext,
    suggested: PlanResult.Plans,
    plans: List<BudgetPlan>,
    selected: Int,
): PlanUi {
    val plan = plans[selected]
    return PlanUi(
        title =
            when {
                suggested.plans.size > 1 -> PlanTitle.TWO_PLANS
                suggested.hero.kind == PlanKind.EXACT -> PlanTitle.PERFECT_FIT
                else -> PlanTitle.YOUR_PLAN
            },
        options =
            if (plans.size > 1) {
                plans.mapIndexed { index, option ->
                    PlanOptionUi(PlanSide.entries[index], IdrFormatter.format(option.total), index == selected)
                }
            } else {
                emptyList()
            },
        hero = HeroUi(plan.kind, IdrFormatter.format(plan.total), deltaOf(plan, context.budget)),
        notes = notesOf(suggested, context),
        groups = groupsOf(plan, context.catalog.menu),
        itemCount = plan.itemCount,
        price = priceOf(plan, context.catalog),
    )
}

/** The amount the badge names: what is left under the budget, or how far over it the plan is. */
private fun deltaOf(
    plan: BudgetPlan,
    budget: Rupiah,
): String {
    val difference = plan.total.value - budget.value
    return IdrFormatter.format(Rupiah(if (difference < 0) -difference else difference))
}

/** The notes follow the suggested plans, as in `BudgetPas.dc.html`; a swap does not change them. */
private fun notesOf(
    suggested: PlanResult.Plans,
    context: PlanContext,
): List<PlanNote> {
    val edge =
        when (suggested.hero.kind) {
            PlanKind.TOO_LOW -> PlanNote.TooLow
            PlanKind.MORE_THAN_ENOUGH -> PlanNote.Generous
            PlanKind.UNDER, PlanKind.OVER -> noOverNote(suggested, context)
            PlanKind.EXACT -> null
        }
    return listOfNotNull(edge, PlanNote.KidsMenuUnavailable.takeIf { suggested.kidsMenuUnavailable })
}

private fun noOverNote(
    suggested: PlanResult.Plans,
    context: PlanContext,
): PlanNote? =
    when {
        suggested.over != null -> null
        context.options.tolerance == PlanTolerance.STRICT -> PlanNote.Strict
        else -> PlanNote.NoOverPlan(context.config.overCapPercent)
    }

/** "What's included": one group per person or shared meal, in plan order, then "Shared" (FR-032). */
private fun groupsOf(
    plan: BudgetPlan,
    menu: Menu,
): List<PlanGroupUi> {
    val shared = mutableListOf<PlanItemUi>()
    val groups =
        plan.picks.mapIndexedNotNull { index, pick ->
            when (pick) {
                is PlanPick.MainAndDrink -> {
                    PlanGroupUi(
                        label = pick.diner.label(),
                        items =
                            listOf(
                                pick.main.toUi(
                                    if (pick.diner.isChild &&
                                        pick.kidFriendly
                                    ) {
                                        PlanItemMeta.KidMain
                                    } else {
                                        PlanItemMeta.Main
                                    },
                                    SwapTarget(index, SwapPart.MAIN),
                                ),
                                pick.drink.toUi(PlanItemMeta.Drink, SwapTarget(index, SwapPart.DRINK)),
                            ),
                    )
                }

                is PlanPick.Meal -> {
                    PlanGroupUi(
                        label = pick.diner.label(),
                        items =
                            listOf(
                                pick.combo.toUi(
                                    PlanItemMeta.Meal(menu.includesOf(pick.combo)),
                                    SwapTarget(index, SwapPart.MEAL),
                                ),
                            ),
                    )
                }

                is PlanPick.SharedMeal -> {
                    PlanGroupUi(
                        label = PlanGroupLabel.Sharing(pick.first.number, pick.second.number),
                        items = listOf(pick.combo.toUi(PlanItemMeta.MealForTwo(menu.includesOf(pick.combo)), null)),
                    )
                }

                is PlanPick.Extra -> {
                    val meta = if (pick.item.role == PlanRole.DESSERT) PlanItemMeta.Dessert else PlanItemMeta.Side
                    shared += pick.item.toUi(meta, SwapTarget(index, SwapPart.EXTRA))
                    null
                }
            }
        }
    return if (shared.isEmpty()) groups else groups + PlanGroupUi(PlanGroupLabel.Shared, shared)
}

private fun Diner.label(): PlanGroupLabel = if (isChild) PlanGroupLabel.Child(number) else PlanGroupLabel.Adult(number)

private fun Menu.includesOf(item: PlannedItem): String? =
    combo(item.id)
        ?.includes
        ?.resolve()
        ?.takeIf { it.isNotBlank() }

private fun PlannedItem.toUi(
    meta: PlanItemMeta,
    swap: SwapTarget?,
) = PlanItemUi(
    name = name.resolve(),
    meta = meta,
    price = IdrFormatter.format(unitPrice),
    imageUrl = imageUrl,
    tone = tone,
    swap = swap,
)

/** "Price details": Subtotal, Promo, the tax label from config, Rounding when not zero, Total (FR-013). */
private fun priceOf(
    plan: BudgetPlan,
    catalog: Catalog,
): PriceDetailsUi {
    val totals = plan.totals
    val hasDiscount = totals.discount > Rupiah.Zero
    return PriceDetailsUi(
        subtotal = IdrFormatter.format(totals.subtotal),
        promoLabel =
            catalog.config.promos
                .firstOrNull { it.id == totals.promoId }
                ?.label
                ?.resolve()
                ?.takeIf { hasDiscount && it.isNotBlank() },
        discount = if (hasDiscount) IdrFormatter.format(totals.discount) else null,
        taxLabel =
            catalog.config.tax.label
                ?.resolve()
                ?.takeIf { it.isNotBlank() },
        tax = IdrFormatter.format(totals.tax),
        rounding = totals.rounding.takeIf { it != Rupiah.Zero }?.let(IdrFormatter::formatSigned),
        total = IdrFormatter.format(totals.total),
    )
}
