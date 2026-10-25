package com.hitachi.kiosk.feature.budgetplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hitachi.kiosk.core.designsystem.component.RollDirection
import com.hitachi.kiosk.core.domain.cart.CartAddAllResult
import com.hitachi.kiosk.core.domain.cart.CartLimit
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.BudgetPlannerConfig
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.combo
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.order.PlanTag
import com.hitachi.kiosk.core.domain.planner.BudgetPlan
import com.hitachi.kiosk.core.domain.planner.BudgetPlanner
import com.hitachi.kiosk.core.domain.planner.PlanAlternative
import com.hitachi.kiosk.core.domain.planner.PlanKind
import com.hitachi.kiosk.core.domain.planner.PlanOptions
import com.hitachi.kiosk.core.domain.planner.PlanPick
import com.hitachi.kiosk.core.domain.planner.PlanRequest
import com.hitachi.kiosk.core.domain.planner.PlanResult
import com.hitachi.kiosk.core.domain.planner.PlanRole
import com.hitachi.kiosk.core.domain.planner.PlanTolerance
import com.hitachi.kiosk.core.domain.planner.PlannedItem
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import com.hitachi.kiosk.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.hitachi.kiosk.navigation.BudgetPlanRoute as BudgetPlanDestination

/** "Added to order" shows for about 0.6 s before Your order opens (`BudgetPas.dc.html` `cta()`, DEV-112). */
internal const val ADDED_NAVIGATE_MILLIS = 600L

/** The keypad takes at most 7 digits (`BudgetPas.dc.html`). */
private const val MAX_DRAFT_DIGITS = 7

/** Budgets are exact to the nearest Rp 100 (FR-032). */
private const val BUDGET_STEP = 100L

/** "Use this budget" needs at least Rp 1.000 (`BudgetPas.dc.html`). */
private const val MIN_KEYPAD_BUDGET = 1_000L

/**
 * SCR-19 Budget Plan (FR-032, DEV-043, DEV-107–DEV-112; architecture §5.1 "Budget Plan hand-off"). Builds one
 * [BudgetPlanner] from the catalog the order started with (A-29) and the device rounding step, and recomputes the plan
 * on every input change. Planning and the swap picker's rows run on [planning] (`@DefaultDispatcher`,
 * technical-design §13), latest input wins, and a result for inputs that changed meanwhile is dropped. It keeps the
 * step, the inputs, which plan card is chosen, the swaps and the options; only "Add plan to order" touches the order
 * session, which tags the lines. Once the plan is added, nothing on the screen can change or add it again.
 *
 * Opened with `edit` ("Edit plan" on Your order, DEV-111) it starts on step 2 with the saved budget and group, and
 * "Add plan to order" replaces the plan's lines instead of adding to them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetPlanViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val catalogs: CatalogRepository,
        private val settings: SettingsRepository,
        private val orders: OrderSessionHolder,
        @param:DefaultDispatcher private val planning: CoroutineDispatcher,
    ) : ViewModel() {
        private val edit: Boolean = savedStateHandle.toRoute<BudgetPlanDestination>().edit

        private val model = MutableStateFlow(PlanModel())

        private var swapJob: Job? = null
        private var navigateJob: Job? = null

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
                val tag = if (edit) currentTag() else null
                model.update {
                    it.copy(loaded = true, catalog = catalog, planner = planner).editing(tag).recomputed()
                }
            }
            viewModelScope.launch {
                model.map { it.planSerial }.distinctUntilChanged().collectLatest { serial -> plan(serial) }
            }
        }

        private fun currentTag(): PlanTag? =
            orders.current.value
                ?.state
                ?.value
                ?.planTag

        fun onEvent(event: BudgetPlanEvent) {
            if (model.value.added) {
                // The plan is in the cart and Your order opens in a moment: only "My order" still acts.
                if (event == BudgetPlanEvent.ViewOrder) viewOrder()
                return
            }
            if (!onTableEvent(event)) onPlanEvent(event)
        }

        /** Plans the inputs of [serial] off the main thread; a result for older inputs is dropped. */
        private suspend fun plan(serial: Int) {
            val current = model.value
            val planner = current.planner
            val request = current.request()
            val result =
                if (planner == null ||
                    request == null
                ) {
                    null
                } else {
                    withContext(planning) { planner.plan(request) }
                }
            model.update { if (it.planSerial == serial) it.planned(result) else it }
        }

        private fun viewOrder() {
            navigateJob?.cancel()
            effects.trySend(BudgetPlanEffect.NavigateToYourOrder)
        }

        /** Step 1 and Back; false when [event] belongs to step 2. */
        private fun onTableEvent(event: BudgetPlanEvent): Boolean {
            when (event) {
                is BudgetPlanEvent.PickBudget -> pickBudget(event.amount)
                BudgetPlanEvent.OpenKeypad -> model.update { it.copy(keypadOpen = true, draft = it.budgetDraft()) }
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
                BudgetPlanEvent.AddPlan -> addPlan()
                BudgetPlanEvent.ViewOrder -> viewOrder()
                else -> onOptionEvent(event)
            }
        }

        /** The options of step 2 and the swap picker (DEV-108, DEV-109). */
        private fun onOptionEvent(event: BudgetPlanEvent) {
            when (event) {
                is BudgetPlanEvent.SetIncludeSides -> changeOptions { it.copy(includeSides = event.include) }
                is BudgetPlanEvent.SetIncludeDesserts -> changeOptions { it.copy(includeDesserts = event.include) }
                is BudgetPlanEvent.SetAllowOver -> changeOptions { it.copy(tolerance = event.allow.tolerance()) }
                is BudgetPlanEvent.Swap -> openSwap(event.target)
                is BudgetPlanEvent.PickAlternative -> pickAlternative(event.index)
                BudgetPlanEvent.CloseSwap -> closeSwap()
                BudgetPlanEvent.ResetPlan -> model.update { if (it.canReset) it.reset() else it }
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
                current.keypadOpen -> {
                    model.update { it.copy(keypadOpen = false) }
                }

                current.swapIndex != null -> {
                    closeSwap()
                }

                current.step == BudgetPlanStep.PLAN -> {
                    model.update { it.copy(step = BudgetPlanStep.TABLE, cartFull = false) }
                }

                // "Edit plan" came from Your order, so Back from step 1 returns there with the plan untouched.
                current.editing -> {
                    effects.trySend(BudgetPlanEffect.NavigateToYourOrder)
                }

                else -> {
                    effects.trySend(BudgetPlanEffect.NavigateToMenu)
                }
            }
        }

        private fun selectPlan(side: PlanSide) {
            model.update {
                val index = side.ordinal
                if (index == it.selected || index !in it.plans.indices) {
                    it
                } else {
                    it.copy(selected = index, lastSwap = null, swapIndex = null, cartFull = false)
                }
            }
        }

        /** The chips and the switch recompute the whole plan (`BudgetPas.dc.html`). */
        private fun changeOptions(change: (PlanOptions) -> PlanOptions) {
            model.update {
                val options = change(it.options)
                if (options == it.options) it else it.copy(options = options).recomputed()
            }
        }

        private fun closeSwap() {
            swapJob?.cancel()
            model.update { it.copy(swapIndex = null, alternatives = emptyList()) }
        }

        /** A "Swap" button: the picker's rows for that pick, computed off the main thread (DEV-109). */
        private fun openSwap(target: SwapTarget) {
            val current = model.value
            val plan = current.plans.getOrNull(current.selected) ?: return
            if (target.pickIndex !in plan.picks.indices) return
            loadAlternatives(current, plan, target.pickIndex)
        }

        private fun loadAlternatives(
            current: PlanModel,
            plan: BudgetPlan,
            pickIndex: Int,
        ) {
            val planner = current.planner
            val request = current.request()
            if (planner == null || request == null) return
            swapJob?.cancel()
            swapJob =
                viewModelScope.launch {
                    val rows = withContext(planning) { planner.alternatives(request, plan, pickIndex) }
                    model.update { latest ->
                        // Applied only to the plan it was computed from.
                        if (latest.plans.getOrNull(latest.selected) !== plan) {
                            latest
                        } else {
                            latest.copy(swapIndex = pickIndex, alternatives = rows)
                        }
                    }
                }
        }

        /** A picker row: its plan replaces the chosen one, and the rows are worked out again for the new plan. */
        private fun pickAlternative(index: Int) {
            // One swap at a time: the next tap starts from the plan the last one produced.
            if (swapJob?.isActive == true) return
            val current = model.value
            val alternative = current.alternatives.getOrNull(index) ?: return
            val pickIndex = current.swapIndex ?: return
            if (alternative.isCurrent || current.plans.getOrNull(current.selected) == null) return
            val swapped = alternative.plan
            val next =
                current.copy(
                    plans = current.plans.toMutableList().also { it[current.selected] = swapped },
                    edited = true,
                    lastSwap = SwapTarget(pickIndex),
                    swapSerial = current.swapSerial + 1,
                    cartFull = false,
                )
            model.value = next
            loadAlternatives(next, swapped, pickIndex)
        }

        private fun addPlan() {
            val current = model.value
            if (current.added || current.step != BudgetPlanStep.PLAN || !current.isPlanned) return
            val plan = current.plans.getOrNull(current.selected) ?: return
            val session = orders.current.value
            if (session == null) {
                KioskLog.w("budget_plan_add_without_order")
                return
            }
            val additions = plan.toCartAdditions()
            val budget = Rupiah(current.budget)
            val replace = current.editing && session.state.value.planTag != null
            val result =
                if (replace) {
                    session.replacePlan(additions, budget, current.adults, current.children)
                } else {
                    session.addPlan(additions, budget, current.adults, current.children)
                }
            when (result) {
                is CartAddAllResult.Added -> {
                    model.update { it.copy(added = true, swapIndex = null) }
                    navigateJob =
                        viewModelScope.launch {
                            delay(ADDED_NAVIGATE_MILLIS)
                            effects.send(BudgetPlanEffect.NavigateToYourOrder)
                        }
                }

                // The plan lands whole or not at all (A-43): say so, and let "Review order" open Your order.
                is CartAddAllResult.LimitReached -> {
                    KioskLog.w("budget_plan_add_refused", "limit" to result.limit.name)
                    if (result.limit != CartLimit.ORDER_LOCKED) model.update { it.copy(cartFull = true) }
                }
            }
        }
    }

private fun rollOf(delta: Int): RollDirection = if (delta > 0) RollDirection.UP else RollDirection.DOWN

private fun Boolean.tolerance(): PlanTolerance = if (this) PlanTolerance.UP_TO_CAP else PlanTolerance.STRICT

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
 * @property editing "Edit plan" opened the screen: step 2 first, and the plan replaces the cart's plan lines.
 * @property swapIndex the pick the swap picker is open for, with its [alternatives].
 */
private data class PlanModel(
    val loaded: Boolean = false,
    val catalog: Catalog? = null,
    val planner: BudgetPlanner? = null,
    val budget: Long = 0L,
    val adults: Int = BudgetPlanUiState.DEFAULT_ADULTS,
    val children: Int = BudgetPlanUiState.MIN_CHILDREN,
    val adultsRoll: RollDirection = RollDirection.UP,
    val childrenRoll: RollDirection = RollDirection.UP,
    val options: PlanOptions = PlanOptions(),
    val step: BudgetPlanStep = BudgetPlanStep.TABLE,
    val suggested: PlanResult? = null,
    val plans: List<BudgetPlan> = emptyList(),
    val selected: Int = 0,
    val edited: Boolean = false,
    val editing: Boolean = false,
    val keypadOpen: Boolean = false,
    val draft: String = "",
    val swapIndex: Int? = null,
    val alternatives: List<PlanAlternative> = emptyList(),
    val includedOpen: Boolean = true,
    val priceOpen: Boolean = false,
    val added: Boolean = false,
    val cartFull: Boolean = false,
    val lastSwap: SwapTarget? = null,
    val swapSerial: Int = 0,
    val planSerial: Int = 0,
    val plannedSerial: Int = -1,
) {
    /** Whether [plans] answer the current inputs (planning runs off the main thread). */
    val isPlanned: Boolean get() = plannedSerial == planSerial

    val config: BudgetPlannerConfig get() = catalog?.config?.budgetPlanner ?: BudgetPlannerConfig(enabled = false)

    val groupTooLarge: Boolean get() = adults + children > config.maxGroupSize

    val canSeePlans: Boolean get() = !groupTooLarge && isPlanned && plans.isNotEmpty()

    /** "Reset to suggested" is offered once an option or an item differs from the suggestion (`changed`). */
    val canReset: Boolean get() = edited || options != PlanOptions()

    fun request(): PlanRequest? =
        if (budget <= 0L || planner == null) null else PlanRequest(Rupiah(budget), adults, children, options)

    /** The keypad opens showing the current budget, so it is never lost silently (FR-032, TC-198). */
    fun budgetDraft(): String = if (budget > 0L) budget.toString() else ""

    /** "Edit plan": the saved budget and group, straight to step 2 (DEV-111). Without a tag it changes nothing. */
    fun editing(tag: PlanTag?): PlanModel =
        if (tag == null) {
            this
        } else {
            copy(
                budget = tag.budget.value,
                adults = tag.adults,
                children = tag.children,
                step = BudgetPlanStep.PLAN,
                editing = true,
            )
        }

    /**
     * Asks for the plan of the current inputs and options, without swaps. The plans on screen stay until [planned]
     * replaces them, so an open picker does not flicker. Any change of input also clears the cart-full message.
     */
    fun recomputed(): PlanModel =
        copy(planSerial = planSerial + 1, edited = false, lastSwap = null, swapIndex = null, cartFull = false)

    /** "Reset to suggested": the default options and no swaps. */
    fun reset(): PlanModel = copy(options = PlanOptions()).recomputed()

    /** The planner's answer for the inputs of [planSerial]. */
    fun planned(result: PlanResult?): PlanModel =
        copy(
            suggested = result,
            plans = (result as? PlanResult.Plans)?.plans.orEmpty(),
            selected = 0,
            plannedSerial = planSerial,
        )

    fun toUiState(cartCount: Int): BudgetPlanUiState {
        val quick = config.quickBudgets.map { it.value }
        val custom = budget > 0L && budget !in quick
        val plan = plans.getOrNull(selected)
        val suggested = suggested as? PlanResult.Plans
        val ui =
            if (plan != null && suggested != null && catalog != null) {
                planUi(PlanContext(catalog, Rupiah(budget), options, config, canReset), suggested, plans, selected)
                    .copy(lastSwap = lastSwap, swapSerial = swapSerial)
            } else {
                null
            }
        return BudgetPlanUiState(
            loaded = loaded,
            step = step,
            budget = format(budget),
            hasBudget = budget > 0L,
            perPerson = if (budget > 0L) format(budget / (adults + children) / BUDGET_STEP * BUDGET_STEP) else null,
            chips = quick.map { BudgetChipUi(it, thousandsOf(it), format(it), selected = it == budget) },
            otherChip = if (custom) OtherChip.EDIT else OtherChip.OTHER,
            adults = adults,
            children = children,
            adultsRoll = adultsRoll,
            childrenRoll = childrenRoll,
            maxGroupSize = config.maxGroupSize,
            groupTooLarge = groupTooLarge,
            canSeePlans = canSeePlans,
            plan = ui,
            keypad = keypadUi(),
            swap = swapSheet(plan),
            includedExpanded = includedOpen,
            priceExpanded = priceOpen,
            added = added,
            cartFull = cartFull && plan != null,
            cartCount = cartCount,
        )
    }

    private fun keypadUi(): KeypadUi? =
        if (keypadOpen) {
            val typed = draft.draftAmount()
            KeypadUi(
                amount = format(typed),
                canUse = draft.flooredBudget() >= MIN_KEYPAD_BUDGET,
                empty = draft.isEmpty(),
                tooLow = draft.isNotEmpty() && typed < MIN_KEYPAD_BUDGET,
            )
        } else {
            null
        }

    private fun swapSheet(plan: BudgetPlan?): SwapSheetUi? {
        val menu = catalog?.menu
        val pick = swapIndex?.let { plan?.picks?.getOrNull(it) }
        val open = plan != null && alternatives.isNotEmpty()
        return if (!open || menu == null || pick == null) {
            null
        } else {
            SwapSheetUi(
                title = swapTitle(pick),
                planTotal = format(checkNotNull(plan).total.value),
                rows = alternatives.map { it.toRow(menu) },
            )
        }
    }
}

/** What the step 2 mapping needs besides the plans. */
private class PlanContext(
    val catalog: Catalog,
    val budget: Rupiah,
    val options: PlanOptions,
    val config: BudgetPlannerConfig,
    val canReset: Boolean,
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
                plan.kind == PlanKind.EXACT -> PlanTitle.PERFECT_FIT
                else -> PlanTitle.YOUR_PLAN
            },
        cards =
            if (plans.size > 1) {
                plans.mapIndexed { index, option ->
                    PlanCardUi(
                        side = PlanSide.entries[index],
                        total = IdrFormatter.format(option.total),
                        delta = deltaOf(option, context.budget),
                        items = option.itemCount,
                        selected = index == selected,
                    )
                }
            } else {
                emptyList()
            },
        hero = HeroUi(plan.kind, IdrFormatter.format(plan.total), deltaOf(plan, context.budget)),
        notes = notesOf(suggested, plan, context),
        options =
            PlanOptionsUi(
                includeSides = context.options.includeSides,
                includeDesserts = context.options.includeDesserts,
                allowOver = context.options.tolerance == PlanTolerance.UP_TO_CAP,
                overCapPercent = context.config.overCapPercent,
                canReset = context.canReset,
            ),
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

/**
 * The notes under the hero card follow the plan on screen, so a swap that makes an exact match drops the "only the
 * plan within your budget is shown" line (FR-032 edge cases).
 */
private fun notesOf(
    suggested: PlanResult.Plans,
    plan: BudgetPlan,
    context: PlanContext,
): List<PlanNote> {
    val edge =
        when (plan.kind) {
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
        context.options.tolerance == PlanTolerance.STRICT -> PlanNote.OverOff
        else -> PlanNote.NoOverPlan(context.config.overCapPercent)
    }

/**
 * "What's included": one row per person or shared meal, in plan order, then "For the table" (FR-032). A person's main
 * and drink are one row, "Smoky Double Burger + Iced Lychee Tea", so the customer need not know whether a meal is a
 * combo (`BudgetPas.dc.html`).
 */
private fun groupsOf(
    plan: BudgetPlan,
    menu: Menu,
): List<PlanGroupUi> {
    val shared = mutableListOf<PlanItemUi>()
    val groups =
        plan.picks.mapIndexedNotNull { index, pick ->
            val swap = SwapTarget(index)
            when (pick) {
                is PlanPick.MainAndDrink -> {
                    val label =
                        if (pick.diner.isChild) {
                            PlanGroupLabel.Child(pick.diner.number, pick.kidFriendly)
                        } else {
                            PlanGroupLabel.Adult(pick.diner.number)
                        }
                    PlanGroupUi(label, listOf(pick.pairItem(swap)))
                }

                is PlanPick.Meal -> {
                    PlanGroupUi(
                        PlanGroupLabel.Adult(pick.diner.number),
                        listOf(pick.combo.toUi(PlanItemMeta.Meal(menu.includesOf(pick.combo)), swap)),
                    )
                }

                is PlanPick.SharedMeal -> {
                    PlanGroupUi(
                        PlanGroupLabel.Sharing(pick.first.number, pick.second.number),
                        listOf(pick.combo.toUi(PlanItemMeta.MealForTwo(menu.includesOf(pick.combo)), swap)),
                    )
                }

                is PlanPick.Extra -> {
                    shared += pick.item.toUi(pick.item.extraMeta(), swap)
                    null
                }
            }
        }
    return if (shared.isEmpty()) groups else groups + PlanGroupUi(PlanGroupLabel.Shared, shared)
}

private fun PlannedItem.extraMeta(): PlanItemMeta =
    if (role == PlanRole.DESSERT) PlanItemMeta.Dessert else PlanItemMeta.Side

private fun PlanPick.MainAndDrink.pairItem(swap: SwapTarget?) =
    PlanItemUi(
        name = pairName(),
        meta = PlanItemMeta.MainAndDrink,
        price = IdrFormatter.format(Rupiah(main.unitPrice.value + drink.unitPrice.value)),
        imageUrl = main.imageUrl,
        tone = main.tone,
        swap = swap,
    )

private fun PlanPick.MainAndDrink.pairName(): String =
    "${main.name.resolve().shortMenuName()} + ${drink.name.resolve().shortMenuName()}"

private val PARENTHETICAL = Regex(" \\(.*?\\)")
private val MILLILITRES = Regex(" \\d+ ml$")

/** The short names of the design's rows: "Chicken Nuggets (9 pc)" → "Chicken Nuggets", "600 ml" dropped. */
private fun String.shortMenuName(): String =
    replaceFirst(PARENTHETICAL, "").removePrefix("Classic ").replaceFirst(MILLILITRES, "")

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

private fun swapTitle(pick: PlanPick): SwapTitle =
    when (pick) {
        is PlanPick.MainAndDrink -> {
            if (pick.diner.isChild) SwapTitle.Child(pick.diner.number) else SwapTitle.Adult(pick.diner.number)
        }

        is PlanPick.Meal -> {
            SwapTitle.Adult(pick.diner.number)
        }

        is PlanPick.SharedMeal -> {
            SwapTitle.SharedMeal
        }

        is PlanPick.Extra -> {
            if (pick.item.role == PlanRole.DESSERT) SwapTitle.SharedDessert else SwapTitle.SharedSide
        }
    }

/** A picker row: what stands in for the pick, the price change and the plan total after it. */
private fun PlanAlternative.toRow(menu: Menu): SwapRowUi {
    val first = replacement.first()
    val priceChange =
        when {
            change > 0 -> PriceChange.Up(format(change))
            change < 0 -> PriceChange.Down(format(-change))
            else -> PriceChange.Same
        }
    val total = format(plan.total.value)
    return when (first) {
        is PlanPick.MainAndDrink -> {
            val twice = replacement.size == 2
            SwapRowUi(
                name = if (twice) null else first.pairName(),
                meta = if (twice) PlanItemMeta.Twice(first.pairName()) else PlanItemMeta.MainAndDrink,
                imageUrl = first.main.imageUrl,
                tone = first.main.tone,
                current = isCurrent,
                change = priceChange,
                total = total,
            )
        }

        is PlanPick.Meal -> {
            first.combo.toRow(PlanItemMeta.Meal(menu.includesOf(first.combo)), this, priceChange, total)
        }

        is PlanPick.SharedMeal -> {
            first.combo.toRow(PlanItemMeta.MealForTwo(menu.includesOf(first.combo)), this, priceChange, total)
        }

        is PlanPick.Extra -> {
            first.item.toRow(first.item.extraMeta(), this, priceChange, total)
        }
    }
}

private fun PlannedItem.toRow(
    meta: PlanItemMeta,
    alternative: PlanAlternative,
    change: PriceChange,
    total: String,
) = SwapRowUi(name.resolve(), meta, imageUrl, tone, alternative.isCurrent, change, total)

/** "Price details": Subtotal, Promo, tax, Rounding when not zero, Combo savings and Total (FR-013). */
private fun priceOf(
    plan: BudgetPlan,
    catalog: Catalog,
): PriceDetailsUi {
    val totals = plan.totals
    val hasDiscount = totals.discount > Rupiah.Zero
    val savings =
        plan.picks
            .filter { it is PlanPick.Meal || it is PlanPick.SharedMeal }
            .sumOf { pick ->
                val item = pick.items.first()
                (
                    (
                        catalog.menu
                            .combo(item.id)
                            ?.separatePrice
                            ?.value ?: 0L
                    ) - item.unitPrice.value
                ).coerceAtLeast(0L)
            }
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
        comboSavings = if (savings > 0L) IdrFormatter.format(Rupiah(savings)) else null,
        total = IdrFormatter.format(totals.total),
    )
}
