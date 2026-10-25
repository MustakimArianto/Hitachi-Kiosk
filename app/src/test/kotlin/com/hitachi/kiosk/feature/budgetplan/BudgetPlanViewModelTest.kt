package com.hitachi.kiosk.feature.budgetplan

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.domain.planner.PlanKind
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fake.FakeSettingsRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SCR-19 behaviour (FR-032, DEV-043, DEV-107–DEV-112; TC-162–TC-164, TC-166, TC-167, TC-169, TC-171, TC-198–TC-204). */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetPlanViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalogs = FakeCatalogRepository()
    private val settings = FakeSettingsRepository()
    private val orders = DefaultOrderSessionHolder()
    private val session: OrderSession = orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")

    private fun TestScope.viewModel(
        catalog: Catalog = SampleData.catalog,
        edit: Boolean = false,
    ): BudgetPlanViewModel {
        catalogs.set(catalog)
        val vm =
            BudgetPlanViewModel(
                SavedStateHandle(mapOf("edit" to edit)),
                catalogs,
                settings,
                orders,
                main.dispatcher,
            )
        runCurrent()
        return vm
    }

    private fun TestScope.effectsOf(vm: BudgetPlanViewModel): List<BudgetPlanEffect> {
        val effects = mutableListOf<BudgetPlanEffect>()
        backgroundScope.launch { vm.effect.toList(effects) }
        runCurrent()
        return effects
    }

    private fun TestScope.send(
        vm: BudgetPlanViewModel,
        vararg events: BudgetPlanEvent,
    ) {
        events.forEach(vm::onEvent)
        runCurrent()
    }

    /** Step 1 inputs from the default 2 adults and no children, then "See plans". */
    private fun TestScope.plan(
        vm: BudgetPlanViewModel,
        budget: Long,
        adults: Int,
        children: Int,
    ) {
        send(vm, BudgetPlanEvent.PickBudget(budget))
        repeat(maxOf(adults - 2, 0)) { send(vm, BudgetPlanEvent.IncreaseAdults) }
        repeat(maxOf(2 - adults, 0)) { send(vm, BudgetPlanEvent.DecreaseAdults) }
        repeat(children) { send(vm, BudgetPlanEvent.IncreaseChildren) }
        send(vm, BudgetPlanEvent.SeePlans)
    }

    private val BudgetPlanViewModel.ui get() = state.value

    private val BudgetPlanViewModel.plan get() = checkNotNull(state.value.plan)

    @Test
    fun `the screen opens with no budget, 2 adults and See plans disabled (TC-198)`() =
        runTest(main.dispatcher) {
            val ui = viewModel().ui

            assertThat(ui.loaded).isTrue()
            assertThat(ui.step).isEqualTo(BudgetPlanStep.TABLE)
            assertThat(ui.budget).isEqualTo("Rp 0")
            assertThat(ui.hasBudget).isFalse()
            assertThat(ui.perPerson).isNull()
            assertThat(ui.chips.map { it.amount }).containsExactly(50_000L, 100_000L, 150_000L, 200_000L).inOrder()
            assertThat(ui.chips.map { it.thousands }).containsExactly(50L, 100L, 150L, 200L).inOrder()
            assertThat(ui.chips.none { it.selected }).isTrue()
            assertThat(ui.otherChip).isEqualTo(OtherChip.OTHER)
            assertThat(ui.adults).isEqualTo(2)
            assertThat(ui.children).isEqualTo(0)
            assertThat(ui.canDecreaseAdults).isTrue()
            assertThat(ui.canDecreaseChildren).isFalse()
            assertThat(ui.canSeePlans).isFalse()
            assertThat(ui.plan).isNull()
        }

    @Test
    fun `See plans does nothing without a budget`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            send(vm, BudgetPlanEvent.SeePlans)

            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.TABLE)
        }

    @Test
    fun `a chip sets the budget and the per-person amount, rounded down to Rp 100 (A-46)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            send(vm, BudgetPlanEvent.PickBudget(100_000))

            assertThat(vm.ui.budget).isEqualTo("Rp 100.000")
            assertThat(vm.ui.perPerson).isEqualTo("Rp 50.000")
            assertThat(vm.ui.canSeePlans).isTrue()

            send(vm, BudgetPlanEvent.IncreaseChildren)
            assertThat(vm.ui.perPerson).isEqualTo("Rp 33.300")
        }

    @Test
    fun `the keypad shows the current amount, floors to Rp 100 and the chip reads Edit (TC-171, TC-198)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            send(vm, BudgetPlanEvent.PickBudget(100_000), BudgetPlanEvent.OpenKeypad)
            assertThat(vm.ui.keypad).isEqualTo(KeypadUi("Rp 100.000", canUse = true))

            repeat(6) { send(vm, BudgetPlanEvent.PressKey(KeypadKey.DELETE)) }
            assertThat(vm.ui.keypad).isEqualTo(KeypadUi("Rp 0", canUse = false, empty = true))

            listOf(KeypadKey.ONE, KeypadKey.TWO, KeypadKey.THREE, KeypadKey.FOUR, KeypadKey.FIVE, KeypadKey.SIX)
                .forEach { send(vm, BudgetPlanEvent.PressKey(it)) }
            assertThat(vm.ui.keypad).isEqualTo(KeypadUi("Rp 123.456", canUse = true))

            send(vm, BudgetPlanEvent.UseKeypadBudget)

            assertThat(vm.ui.keypad).isNull()
            assertThat(vm.ui.budget).isEqualTo("Rp 123.400")
            assertThat(vm.ui.otherChip).isEqualTo(OtherChip.EDIT)
            assertThat(vm.ui.chips.none { it.selected }).isTrue()

            // Reopening shows the amount that was used.
            send(vm, BudgetPlanEvent.OpenKeypad)
            assertThat(vm.ui.keypad?.amount).isEqualTo("Rp 123.400")
        }

    @Test
    fun `the keypad flags an amount under Rp 1_000 and Use this budget does nothing (TC-198)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            send(vm, BudgetPlanEvent.OpenKeypad)

            send(vm, BudgetPlanEvent.PressKey(KeypadKey.ZERO), BudgetPlanEvent.PressKey(KeypadKey.FIVE))
            send(vm, BudgetPlanEvent.PressKey(KeypadKey.ZERO), BudgetPlanEvent.PressKey(KeypadKey.ZERO))
            assertThat(vm.ui.keypad).isEqualTo(KeypadUi("Rp 500", canUse = false, tooLow = true))
            send(vm, BudgetPlanEvent.UseKeypadBudget)
            assertThat(vm.ui.keypad).isNotNull()

            send(vm, BudgetPlanEvent.PressKey(KeypadKey.THOUSAND), BudgetPlanEvent.PressKey(KeypadKey.THOUSAND))
            // 500 plus two "000" would be 8 digits: the keypad takes 7, so the second "000" is refused.
            assertThat(vm.ui.keypad?.amount).isEqualTo("Rp 500.000")
            send(vm, BudgetPlanEvent.PressKey(KeypadKey.DELETE))
            assertThat(vm.ui.keypad?.amount).isEqualTo("Rp 50.000")

            send(vm, BudgetPlanEvent.CloseKeypad)
            assertThat(vm.ui.keypad).isNull()
            assertThat(vm.ui.budget).isEqualTo("Rp 0")
        }

    @Test
    fun `adults stay between 1 and 10 and children between 0 and 10 (TC-166)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            repeat(3) { send(vm, BudgetPlanEvent.DecreaseAdults, BudgetPlanEvent.DecreaseChildren) }
            assertThat(vm.ui.adults).isEqualTo(1)
            assertThat(vm.ui.children).isEqualTo(0)

            repeat(12) { send(vm, BudgetPlanEvent.IncreaseAdults, BudgetPlanEvent.IncreaseChildren) }
            assertThat(vm.ui.adults).isEqualTo(10)
            assertThat(vm.ui.children).isEqualTo(10)
            assertThat(vm.ui.canIncreaseAdults).isFalse()
            assertThat(vm.ui.canIncreaseChildren).isFalse()
        }

    @Test
    fun `11 people is a group too large until Set group to 10 (TC-166, AC11)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            send(vm, BudgetPlanEvent.PickBudget(200_000))
            repeat(6) { send(vm, BudgetPlanEvent.IncreaseAdults) }
            repeat(3) { send(vm, BudgetPlanEvent.IncreaseChildren) }

            assertThat(vm.ui.people).isEqualTo(11)
            assertThat(vm.ui.groupTooLarge).isTrue()
            assertThat(vm.ui.maxGroupSize).isEqualTo(10)
            assertThat(vm.ui.canSeePlans).isFalse()
            send(vm, BudgetPlanEvent.SeePlans)
            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.TABLE)

            send(vm, BudgetPlanEvent.FitGroup)

            assertThat(vm.ui.adults).isEqualTo(8)
            assertThat(vm.ui.children).isEqualTo(2)
            assertThat(vm.ui.groupTooLarge).isFalse()
            assertThat(vm.ui.canSeePlans).isTrue()
        }

    @Test
    fun `2 adults and a child at Rp 150_000 get two plan cards, the under one selected (AC1, TC-199)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            plan(vm, 150_000, adults = 2, children = 1)

            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.PLAN)
            assertThat(vm.plan.title).isEqualTo(PlanTitle.TWO_PLANS)
            assertThat(vm.plan.cards.map { it.total }).containsExactly("Rp 149.600", "Rp 150.700").inOrder()
            assertThat(vm.plan.cards.map { it.delta }).containsExactly("Rp 400", "Rp 700").inOrder()
            assertThat(vm.plan.cards.map { it.selected }).containsExactly(true, false).inOrder()
            assertThat(vm.plan.hero).isEqualTo(HeroUi(PlanKind.UNDER, "Rp 149.600", "Rp 400"))
            assertThat(vm.plan.notes).isEmpty()
            assertThat(vm.ui.includedExpanded).isTrue()
            assertThat(vm.ui.priceExpanded).isFalse()
        }

    @Test
    fun `selecting the second card shows the over plan`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)

            send(vm, BudgetPlanEvent.SelectPlan(PlanSide.OVER))

            assertThat(vm.plan.hero).isEqualTo(HeroUi(PlanKind.OVER, "Rp 150.700", "Rp 700"))
            assertThat(vm.plan.cards.map { it.selected }).containsExactly(false, true).inOrder()
        }

    @Test
    fun `What's included has one row per person, with the kid-friendly tag for the child`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)

            val groups = vm.plan.groups
            assertThat(groups.map { it.label }.take(3))
                .containsExactly(
                    PlanGroupLabel.Adult(1),
                    PlanGroupLabel.Adult(2),
                    PlanGroupLabel.Child(1, kidFriendly = true),
                ).inOrder()
            groups.take(3).forEach { assertThat(it.items).hasSize(1) }
            assertThat(
                groups
                    .first()
                    .items
                    .single()
                    .name,
            ).contains(" + ")
            assertThat(vm.plan.itemCount).isAtLeast(groups.size)
            groups.filter { it.label == PlanGroupLabel.Shared }.flatMap { it.items }.forEach {
                assertThat(it.meta).isAnyOf(PlanItemMeta.Side, PlanItemMeta.Dessert)
            }
            groups.flatMap { it.items }.forEach { assertThat(it.swap).isNotNull() }
        }

    @Test
    fun `Price details shows the promo and the config's tax label`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)

            val price = vm.plan.price
            assertThat(price.total).isEqualTo("Rp 149.600")
            assertThat(price.promoLabel).isEqualTo("Rp 10.000 off over Rp 100.000")
            assertThat(price.discount).isEqualTo("Rp 10.000")
            assertThat(price.taxLabel).isEqualTo("Restaurant tax (PB1 10%)")
            assertThat(price.rounding).isNull()

            send(vm, BudgetPlanEvent.ToggleIncluded, BudgetPlanEvent.TogglePriceDetails)
            assertThat(vm.ui.includedExpanded).isFalse()
            assertThat(vm.ui.priceExpanded).isTrue()
        }

    @Test
    fun `Back from step 2 keeps the inputs, Back from step 1 opens the Menu (TC-171)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            plan(vm, 150_000, adults = 2, children = 1)

            send(vm, BudgetPlanEvent.Back)

            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.TABLE)
            assertThat(vm.ui.budget).isEqualTo("Rp 150.000")
            assertThat(vm.ui.adults).isEqualTo(2)
            assertThat(vm.ui.children).isEqualTo(1)
            assertThat(effects).isEmpty()

            send(vm, BudgetPlanEvent.Back)

            assertThat(effects).containsExactly(BudgetPlanEffect.NavigateToMenu)
        }

    @Test
    fun `1 adult and a child at Rp 99_000 is one exact match (AC2, TC-162, TC-169)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            plan(vm, 99_000, adults = 1, children = 1)

            assertThat(vm.plan.title).isEqualTo(PlanTitle.PERFECT_FIT)
            assertThat(vm.plan.cards).isEmpty()
            assertThat(vm.plan.hero.kind).isEqualTo(PlanKind.EXACT)
            assertThat(vm.plan.hero.total).isEqualTo("Rp 99.000")
            assertThat(vm.plan.notes).isEmpty()
            assertThat(vm.plan.price.subtotal).isEqualTo("Rp 100.000")
        }

    @Test
    fun `2 adults and a child at Rp 100_000 need Rp 16_600 more (AC3, TC-163)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            plan(vm, 100_000, adults = 2, children = 1)

            assertThat(vm.plan.title).isEqualTo(PlanTitle.YOUR_PLAN)
            assertThat(vm.plan.hero).isEqualTo(HeroUi(PlanKind.TOO_LOW, "Rp 116.600", "Rp 16.600"))
            assertThat(vm.plan.notes).containsExactly(PlanNote.TooLow)
        }

    @Test
    fun `1 adult at Rp 200_000 has more than enough (TC-164)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            plan(vm, 200_000, adults = 1, children = 0)

            assertThat(vm.plan.title).isEqualTo(PlanTitle.YOUR_PLAN)
            assertThat(vm.plan.hero.kind).isEqualTo(PlanKind.MORE_THAN_ENOUGH)
            assertThat(vm.plan.notes).containsExactly(PlanNote.Generous)
        }

    @Test
    fun `without an over plan in the cap the under plan carries the cap note`() =
        runTest(main.dispatcher) {
            val config =
                SampleData.config.copy(
                    budgetPlanner = SampleData.config.budgetPlanner.copy(overCapPercent = 0),
                )
            val vm = viewModel(SampleData.catalog.copy(config = config))

            plan(vm, 150_000, adults = 2, children = 1)

            assertThat(vm.plan.title).isEqualTo(PlanTitle.YOUR_PLAN)
            assertThat(vm.plan.cards).isEmpty()
            assertThat(vm.plan.notes).containsExactly(PlanNote.NoOverPlan(0))
        }

    @Test
    fun `without a kid-friendly main the plan notes the kids' menu (AC4, D-28)`() =
        runTest(main.dispatcher) {
            val menu = SampleData.menu
            val noKids =
                menu.copy(
                    products =
                        menu.products.map {
                            if (it.role == ProductRole.MAIN) it.copy(kidFriendly = false) else it
                        },
                )
            val vm = viewModel(SampleData.catalog.copy(menu = noKids))

            plan(vm, 120_000, adults = 1, children = 2)

            assertThat(vm.plan.notes).contains(PlanNote.KidsMenuUnavailable)
            val child = vm.plan.groups.first { it.label is PlanGroupLabel.Child }
            assertThat(child.label).isEqualTo(PlanGroupLabel.Child(1, kidFriendly = false))
        }

    @Test
    fun `turning Allow a little over budget off shows one plan and its note, on shows both (AC10, TC-199)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)
            send(vm, BudgetPlanEvent.SelectPlan(PlanSide.OVER))

            send(vm, BudgetPlanEvent.SetAllowOver(false))

            assertThat(vm.plan.options.allowOver).isFalse()
            assertThat(vm.plan.cards).isEmpty()
            assertThat(vm.plan.title).isEqualTo(PlanTitle.YOUR_PLAN)
            assertThat(vm.plan.hero.kind).isEqualTo(PlanKind.UNDER)
            assertThat(vm.plan.notes).containsExactly(PlanNote.OverOff)
            assertThat(vm.plan.options.canReset).isTrue()

            send(vm, BudgetPlanEvent.SetAllowOver(true))
            assertThat(vm.plan.cards).hasSize(2)
        }

    @Test
    fun `the chips remove every side or dessert (TC-199)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)

            send(vm, BudgetPlanEvent.SetIncludeDesserts(false), BudgetPlanEvent.SetIncludeSides(false))

            assertThat(vm.plan.options.includeDesserts).isFalse()
            assertThat(vm.plan.options.includeSides).isFalse()
            assertThat(
                vm.plan.groups
                    .flatMap { it.items }
                    .map { it.meta },
            ).containsNoneOf(PlanItemMeta.Dessert, PlanItemMeta.Side)
        }

    @Test
    fun `the swap picker lists the current choice first with the price change and total (TC-200)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)

            send(vm, BudgetPlanEvent.Swap(SwapTarget(0)))

            val swap = checkNotNull(vm.ui.swap)
            assertThat(swap.title).isEqualTo(SwapTitle.Adult(1))
            assertThat(swap.planTotal).isEqualTo("Rp 149.600")
            assertThat(swap.rows.first().current).isTrue()
            assertThat(swap.rows.first().change).isEqualTo(PriceChange.Same)
            assertThat(swap.rows.drop(1).none { it.current }).isTrue()
            assertThat(swap.rows.size).isGreaterThan(1)
            swap.rows.forEach { assertThat(amount(it.total)).isAtMost(150_000) }
        }

    @Test
    fun `picking a row swaps at once, keeps the picker open and enables Reset, which restores the plan (TC-200)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)
            val suggested = vm.plan
            assertThat(suggested.options.canReset).isFalse()
            send(vm, BudgetPlanEvent.Swap(SwapTarget(0)))
            val row = checkNotNull(vm.ui.swap).rows[1]

            send(vm, BudgetPlanEvent.PickAlternative(1))

            val swapped = vm.plan
            assertThat(
                swapped.groups
                    .first()
                    .items
                    .single()
                    .name,
            ).isNotEqualTo(
                suggested.groups
                    .first()
                    .items
                    .single()
                    .name,
            )
            assertThat(amount(swapped.hero.total)).isEqualTo(amount(row.total))
            assertThat(amount(swapped.hero.total)).isAtMost(150_000)
            assertThat(swapped.lastSwap).isEqualTo(SwapTarget(0))
            assertThat(swapped.swapSerial).isEqualTo(1)
            assertThat(swapped.options.canReset).isTrue()
            assertThat(vm.ui.swap).isNotNull()
            assertThat(
                vm.ui.swap
                    ?.rows
                    ?.first()
                    ?.current,
            ).isTrue()

            send(vm, BudgetPlanEvent.CloseSwap)
            assertThat(vm.ui.swap).isNull()
            assertThat(vm.plan.groups).isEqualTo(swapped.groups)

            send(vm, BudgetPlanEvent.ResetPlan)

            assertThat(vm.plan.groups).isEqualTo(suggested.groups)
            assertThat(vm.plan.hero).isEqualTo(suggested.hero)
            assertThat(vm.plan.options.canReset).isFalse()
        }

    @Test
    fun `picking the current row changes nothing, and Back closes the picker first`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)
            val before = vm.plan
            send(vm, BudgetPlanEvent.Swap(SwapTarget(0)))

            send(vm, BudgetPlanEvent.PickAlternative(0))
            assertThat(vm.plan.groups).isEqualTo(before.groups)

            send(vm, BudgetPlanEvent.Back)
            assertThat(vm.ui.swap).isNull()
            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.PLAN)
        }

    @Test
    fun `Add plan to order fills the cart, shows Added, and opens Your order after 0_6 s (AC5, DEV-112)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            plan(vm, 150_000, adults = 2, children = 1)

            send(vm, BudgetPlanEvent.AddPlan)

            val order = session.state.value
            assertThat(order.cart.itemCount).isEqualTo(vm.plan.itemCount)
            assertThat(order.cart.subtotal).isEqualTo(Rupiah(amount(vm.plan.price.subtotal)))
            assertThat(order.planTag?.budget).isEqualTo(Rupiah(150_000))
            assertThat(order.planTag?.adults).isEqualTo(2)
            assertThat(order.planTag?.children).isEqualTo(1)
            assertThat(order.planAdded).isTrue()
            assertThat(vm.ui.added).isTrue()
            assertThat(vm.ui.cartCount).isEqualTo(vm.plan.itemCount)

            advanceTimeBy(ADDED_NAVIGATE_MILLIS - 1)
            runCurrent()
            assertThat(effects).isEmpty()
            advanceTimeBy(1)
            runCurrent()
            assertThat(effects).containsExactly(BudgetPlanEffect.NavigateToYourOrder)
        }

    @Test
    fun `a second tap on Add plan to order does nothing`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            plan(vm, 150_000, adults = 2, children = 1)

            send(vm, BudgetPlanEvent.AddPlan, BudgetPlanEvent.AddPlan)
            advanceTimeBy(ADDED_NAVIGATE_MILLIS * 2)
            runCurrent()

            assertThat(session.state.value.cart.itemCount).isEqualTo(vm.plan.itemCount)
            assertThat(effects).containsExactly(BudgetPlanEffect.NavigateToYourOrder)
        }

    @Test
    fun `once added, switching or swapping cannot add the plan twice, and My order navigates once`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            plan(vm, 150_000, adults = 2, children = 1)
            val count = vm.plan.itemCount

            send(
                vm,
                BudgetPlanEvent.AddPlan,
                BudgetPlanEvent.SelectPlan(PlanSide.OVER),
                BudgetPlanEvent.Back,
                BudgetPlanEvent.AddPlan,
                BudgetPlanEvent.ViewOrder,
            )
            advanceTimeBy(ADDED_NAVIGATE_MILLIS * 2)
            runCurrent()

            assertThat(session.state.value.cart.itemCount).isEqualTo(count)
            assertThat(effects).containsExactly(BudgetPlanEffect.NavigateToYourOrder)
        }

    @Test
    fun `a plan that would break the cart limit says so and Review order opens Your order (TC-201)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            repeat(29) { session.add(CartItem.ProductItem("fill$it", emptyList()), 1, Rupiah(1_000), emptyList()) }
            plan(vm, 150_000, adults = 2, children = 1)
            val before = session.state.value

            send(vm, BudgetPlanEvent.AddPlan)

            assertThat(vm.ui.cartFull).isTrue()
            assertThat(vm.ui.added).isFalse()
            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.PLAN)
            assertThat(session.state.value).isEqualTo(before)
            advanceTimeBy(ADDED_NAVIGATE_MILLIS)
            runCurrent()
            assertThat(effects).isEmpty()

            send(vm, BudgetPlanEvent.ViewOrder)
            assertThat(effects).containsExactly(BudgetPlanEffect.NavigateToYourOrder)
        }

    @Test
    fun `changing an option clears the cart-full message`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            repeat(29) { session.add(CartItem.ProductItem("fill$it", emptyList()), 1, Rupiah(1_000), emptyList()) }
            plan(vm, 150_000, adults = 2, children = 1)
            send(vm, BudgetPlanEvent.AddPlan)
            assertThat(vm.ui.cartFull).isTrue()

            send(vm, BudgetPlanEvent.SetAllowOver(false))

            assertThat(vm.ui.cartFull).isFalse()
        }

    @Test
    fun `a locked order refuses the plan without the cart-full message and stays on step 2`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            plan(vm, 150_000, adults = 2, children = 1)
            session.add(CartItem.ProductItem("c1", emptyList()), 1, Rupiah(38_000), emptyList())
            session.startBuilding()
            session.startReviewing()
            assertThat(session.submit()).isTrue()

            send(vm, BudgetPlanEvent.AddPlan)
            advanceTimeBy(ADDED_NAVIGATE_MILLIS)
            runCurrent()

            assertThat(vm.ui.added).isFalse()
            assertThat(vm.ui.cartFull).isFalse()
            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.PLAN)
            assertThat(effects).isEmpty()
        }

    @Test
    fun `Edit plan opens step 2 with the saved budget and group and replaces the plan lines (TC-203)`() =
        runTest(main.dispatcher) {
            session.add(CartItem.ProductItem("fill", emptyList()), 2, Rupiah(1_000), emptyList())
            val first = viewModel()
            plan(first, 150_000, adults = 2, children = 1)
            send(first, BudgetPlanEvent.AddPlan)
            val firstCount = first.plan.itemCount
            assertThat(session.state.value.cart.itemCount).isEqualTo(firstCount + 2)

            val vm = viewModel(edit = true)
            val effects = effectsOf(vm)

            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.PLAN)
            assertThat(vm.ui.budget).isEqualTo("Rp 150.000")
            assertThat(vm.ui.adults).isEqualTo(2)
            assertThat(vm.ui.children).isEqualTo(1)
            assertThat(vm.ui.plan).isNotNull()

            // A bigger group at the same budget changes the plan; step 1 keeps the saved values to edit.
            send(vm, BudgetPlanEvent.Back, BudgetPlanEvent.PickBudget(200_000))
            send(vm, BudgetPlanEvent.SeePlans)
            send(vm, BudgetPlanEvent.AddPlan)

            val order = session.state.value
            assertThat(order.planTag?.budget).isEqualTo(Rupiah(200_000))
            assertThat(order.cart.quantityOf(CartItem.ProductItem("fill", emptyList()))).isEqualTo(2)
            assertThat(order.cart.itemCount).isEqualTo(vm.plan.itemCount + 2)
            advanceTimeBy(ADDED_NAVIGATE_MILLIS)
            runCurrent()
            assertThat(effects).containsExactly(BudgetPlanEffect.NavigateToYourOrder)
        }

    @Test
    fun `Edit plan without a plan in the cart opens the normal step 1`() =
        runTest(main.dispatcher) {
            val vm = viewModel(edit = true)

            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.TABLE)
            assertThat(vm.ui.budget).isEqualTo("Rp 0")
        }

    @Test
    fun `Back from step 1 of an edit returns to Your order`() =
        runTest(main.dispatcher) {
            val first = viewModel()
            plan(first, 150_000, adults = 2, children = 1)
            send(first, BudgetPlanEvent.AddPlan)
            val vm = viewModel(edit = true)
            val effects = effectsOf(vm)

            send(vm, BudgetPlanEvent.Back, BudgetPlanEvent.Back)

            assertThat(effects).containsExactly(BudgetPlanEffect.NavigateToYourOrder)
        }

    @Test
    fun `My order opens Your order`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)

            send(vm, BudgetPlanEvent.ViewOrder)

            assertThat(effects).containsExactly(BudgetPlanEffect.NavigateToYourOrder)
        }

    @Test
    fun `without a catalog nothing can be planned`() =
        runTest(main.dispatcher) {
            catalogs.set(null)
            val vm =
                BudgetPlanViewModel(
                    SavedStateHandle(mapOf("edit" to false)),
                    catalogs,
                    settings,
                    orders,
                    main.dispatcher,
                )
            runCurrent()

            send(vm, BudgetPlanEvent.PickBudget(150_000), BudgetPlanEvent.SeePlans)

            assertThat(vm.ui.loaded).isTrue()
            assertThat(vm.ui.chips).hasSize(4)
            assertThat(vm.ui.canSeePlans).isFalse()
            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.TABLE)
        }

    private fun amount(formatted: String): Long = formatted.filter(Char::isDigit).toLong()
}
