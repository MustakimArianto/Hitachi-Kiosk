package com.hitachi.kiosk.feature.budgetplan

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.domain.planner.PlanKind
import com.hitachi.kiosk.core.domain.planner.PlanTolerance
import com.hitachi.kiosk.core.domain.planner.SwapPart
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

/** SCR-19 behaviour (FR-032, DEV-043, DEV-043a; TC-162–TC-164, TC-166, TC-167, TC-169–TC-171). */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetPlanViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalogs = FakeCatalogRepository()
    private val settings = FakeSettingsRepository()
    private val orders = DefaultOrderSessionHolder()
    private val session: OrderSession = orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")

    private fun TestScope.viewModel(catalog: Catalog = SampleData.catalog): BudgetPlanViewModel {
        catalogs.set(catalog)
        val vm = BudgetPlanViewModel(catalogs, settings, orders, main.dispatcher)
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

    /** Step 1 inputs, then "See plans". */
    private fun TestScope.plan(
        vm: BudgetPlanViewModel,
        budget: Long,
        adults: Int,
        children: Int,
    ) {
        send(vm, BudgetPlanEvent.PickBudget(budget))
        repeat(adults - 1) { send(vm, BudgetPlanEvent.IncreaseAdults) }
        repeat(children) { send(vm, BudgetPlanEvent.IncreaseChildren) }
        send(vm, BudgetPlanEvent.SeePlans)
    }

    private val BudgetPlanViewModel.ui get() = state.value

    private val BudgetPlanViewModel.plan get() = checkNotNull(state.value.plan)

    @Test
    fun `the screen opens with no budget, one adult and See plans disabled (TC-166)`() =
        runTest(main.dispatcher) {
            val ui = viewModel().ui

            assertThat(ui.loaded).isTrue()
            assertThat(ui.step).isEqualTo(BudgetPlanStep.TABLE)
            assertThat(ui.budget).isEqualTo("Rp 0")
            assertThat(ui.chips.map { it.amount }).containsExactly(50_000L, 100_000L, 150_000L, 200_000L).inOrder()
            assertThat(ui.chips.map { it.thousands }).containsExactly(50L, 100L, 150L, 200L).inOrder()
            assertThat(ui.chips.none { it.selected }).isTrue()
            assertThat(ui.otherChip).isEqualTo(OtherChip.OTHER)
            assertThat(ui.adults).isEqualTo(1)
            assertThat(ui.children).isEqualTo(0)
            assertThat(ui.canDecreaseAdults).isFalse()
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
    fun `a chip sets the budget and enables See plans`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            send(vm, BudgetPlanEvent.PickBudget(100_000))

            assertThat(vm.ui.budget).isEqualTo("Rp 100.000")
            assertThat(
                vm.ui.chips
                    .single { it.selected }
                    .amount,
            ).isEqualTo(100_000L)
            assertThat(vm.ui.otherChip).isEqualTo(OtherChip.OTHER)
            assertThat(vm.ui.canSeePlans).isTrue()
        }

    @Test
    fun `the keypad floors to Rp 100 and the chip reads Edit (TC-171)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            send(vm, BudgetPlanEvent.PickBudget(100_000), BudgetPlanEvent.OpenKeypad)
            assertThat(vm.ui.keypad).isEqualTo(KeypadUi("Rp 0", canUse = false))

            listOf(KeypadKey.ONE, KeypadKey.TWO, KeypadKey.THREE, KeypadKey.FOUR, KeypadKey.FIVE, KeypadKey.SIX)
                .forEach { send(vm, BudgetPlanEvent.PressKey(it)) }
            assertThat(vm.ui.keypad).isEqualTo(KeypadUi("Rp 123.456", canUse = true))

            send(vm, BudgetPlanEvent.UseKeypadBudget)

            assertThat(vm.ui.keypad).isNull()
            assertThat(vm.ui.budget).isEqualTo("Rp 123.400")
            assertThat(vm.ui.otherChip).isEqualTo(OtherChip.EDIT)
            assertThat(vm.ui.chips.none { it.selected }).isTrue()
        }

    @Test
    fun `the keypad drops leading zeros, deletes, keeps 7 digits and needs Rp 1_000`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            send(vm, BudgetPlanEvent.OpenKeypad)

            send(vm, BudgetPlanEvent.PressKey(KeypadKey.ZERO), BudgetPlanEvent.PressKey(KeypadKey.NINE))
            send(vm, BudgetPlanEvent.PressKey(KeypadKey.NINE), BudgetPlanEvent.PressKey(KeypadKey.NINE))
            assertThat(vm.ui.keypad).isEqualTo(KeypadUi("Rp 999", canUse = false))
            send(vm, BudgetPlanEvent.UseKeypadBudget)
            assertThat(vm.ui.keypad).isNotNull()

            send(vm, BudgetPlanEvent.PressKey(KeypadKey.THOUSAND), BudgetPlanEvent.PressKey(KeypadKey.THOUSAND))
            assertThat(vm.ui.keypad?.amount).isEqualTo("Rp 999.000")
            send(vm, BudgetPlanEvent.PressKey(KeypadKey.DELETE))
            assertThat(vm.ui.keypad?.amount).isEqualTo("Rp 99.900")

            send(vm, BudgetPlanEvent.CloseKeypad)
            assertThat(vm.ui.keypad).isNull()
            assertThat(vm.ui.budget).isEqualTo("Rp 0")
        }

    @Test
    fun `adults stay between 1 and 10 and children between 0 and 10 (TC-166)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            send(vm, BudgetPlanEvent.DecreaseAdults, BudgetPlanEvent.DecreaseChildren)
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
            repeat(7) { send(vm, BudgetPlanEvent.IncreaseAdults) }
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
    fun `2 adults and a child at Rp 150_000 get two plans, the under one first (AC1, TC-161)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            plan(vm, 150_000, adults = 2, children = 1)

            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.PLAN)
            assertThat(vm.plan.title).isEqualTo(PlanTitle.TWO_PLANS)
            assertThat(vm.plan.options)
                .containsExactly(
                    PlanOptionUi(PlanSide.UNDER, "Rp 149.600", selected = true),
                    PlanOptionUi(PlanSide.OVER, "Rp 150.700", selected = false),
                ).inOrder()
            assertThat(vm.plan.hero).isEqualTo(HeroUi(PlanKind.UNDER, "Rp 149.600", "Rp 400"))
            assertThat(vm.plan.notes).isEmpty()
            assertThat(vm.ui.includedExpanded).isFalse()
            assertThat(vm.ui.priceExpanded).isFalse()
        }

    @Test
    fun `Just over shows the over plan`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)

            send(vm, BudgetPlanEvent.SelectPlan(PlanSide.OVER))

            assertThat(vm.plan.hero).isEqualTo(HeroUi(PlanKind.OVER, "Rp 150.700", "Rp 700"))
            assertThat(vm.plan.options.map { it.selected }).containsExactly(false, true).inOrder()
        }

    @Test
    fun `What's included groups the items per person, with kid-friendly mains for the child`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)

            val groups = vm.plan.groups
            assertThat(groups.map { it.label }.take(3))
                .containsExactly(PlanGroupLabel.Adult(1), PlanGroupLabel.Adult(2), PlanGroupLabel.Child(1))
                .inOrder()
            val child = groups.single { it.label == PlanGroupLabel.Child(1) }
            assertThat(child.items.map { it.meta }).containsExactly(PlanItemMeta.KidMain, PlanItemMeta.Drink).inOrder()
            assertThat(vm.plan.itemCount).isEqualTo(groups.sumOf { it.items.size })
            groups.filter { it.label == PlanGroupLabel.Shared }.flatMap { it.items }.forEach {
                assertThat(it.meta).isAnyOf(PlanItemMeta.Side, PlanItemMeta.Dessert)
            }
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
            assertThat(vm.ui.includedExpanded).isTrue()
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
    fun `Back closes an open keypad or sheet first`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)
            send(vm, BudgetPlanEvent.OpenCustomize)
            assertThat(vm.ui.customize).isNotNull()

            send(vm, BudgetPlanEvent.Back)

            assertThat(vm.ui.customize).isNull()
            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.PLAN)
        }

    @Test
    fun `1 adult and a child at Rp 99_000 is one exact match (AC2, TC-162, TC-169)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            plan(vm, 99_000, adults = 1, children = 1)

            assertThat(vm.plan.title).isEqualTo(PlanTitle.PERFECT_FIT)
            assertThat(vm.plan.options).isEmpty()
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
            assertThat(vm.plan.options).isEmpty()
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
                            if (it.role ==
                                ProductRole.MAIN
                            ) {
                                it.copy(kidFriendly = false)
                            } else {
                                it
                            }
                        },
                )
            val vm = viewModel(SampleData.catalog.copy(menu = noKids))

            plan(vm, 120_000, adults = 1, children = 2)

            assertThat(vm.plan.notes).contains(PlanNote.KidsMenuUnavailable)
            val child = vm.plan.groups.first { it.label == PlanGroupLabel.Child(1) }
            assertThat(child.items.first().meta).isEqualTo(PlanItemMeta.Main)
        }

    @Test
    fun `Strict hides Just over and shows the Strict note (AC10, TC-170)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)
            send(vm, BudgetPlanEvent.SelectPlan(PlanSide.OVER), BudgetPlanEvent.OpenCustomize)

            send(vm, BudgetPlanEvent.SetTolerance(PlanTolerance.STRICT))

            assertThat(vm.ui.customize?.tolerance).isEqualTo(PlanTolerance.STRICT)
            assertThat(vm.plan.options).isEmpty()
            assertThat(vm.plan.title).isEqualTo(PlanTitle.YOUR_PLAN)
            assertThat(vm.plan.hero.kind).isEqualTo(PlanKind.UNDER)
            assertThat(vm.plan.notes).containsExactly(PlanNote.Strict)

            send(vm, BudgetPlanEvent.SetTolerance(PlanTolerance.UP_TO_CAP))
            assertThat(vm.plan.options).hasSize(2)
        }

    @Test
    fun `Include desserts off removes every dessert (TC-170)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)
            send(vm, BudgetPlanEvent.OpenCustomize)

            send(vm, BudgetPlanEvent.SetIncludeDesserts(false), BudgetPlanEvent.SetIncludeSides(false))

            assertThat(vm.ui.customize?.includeDesserts).isFalse()
            assertThat(vm.ui.customize?.includeSides).isFalse()
            assertThat(
                vm.plan.groups
                    .flatMap { it.items }
                    .map { it.meta },
            ).containsNoneOf(PlanItemMeta.Dessert, PlanItemMeta.Side)
        }

    @Test
    fun `a swap rebalances under the budget, enables Reset, and Reset restores the plan (TC-170)`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)
            send(vm, BudgetPlanEvent.OpenCustomize)
            val suggested = vm.plan
            assertThat(vm.ui.customize?.canReset).isFalse()
            val adultMain =
                suggested.groups
                    .first()
                    .items
                    .first()
            val target = checkNotNull(adultMain.swap)
            assertThat(target.part).isAnyOf(SwapPart.MAIN, SwapPart.MEAL)

            send(vm, BudgetPlanEvent.Swap(target))

            val swapped = vm.plan
            assertThat(
                swapped.groups
                    .first()
                    .items
                    .first()
                    .name,
            ).isNotEqualTo(adultMain.name)
            assertThat(swapped.hero.kind).isAnyOf(PlanKind.UNDER, PlanKind.EXACT)
            assertThat(amount(swapped.hero.total)).isAtMost(150_000)
            assertThat(swapped.lastSwap).isEqualTo(target)
            assertThat(swapped.swapSerial).isEqualTo(1)
            assertThat(vm.ui.customize?.canReset).isTrue()

            send(vm, BudgetPlanEvent.ResetPlan)

            assertThat(vm.plan.groups).isEqualTo(suggested.groups)
            assertThat(vm.plan.hero).isEqualTo(suggested.hero)
            assertThat(vm.ui.customize?.canReset).isFalse()
        }

    @Test
    fun `Done closes the sheet and keeps the plan`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            plan(vm, 150_000, adults = 2, children = 1)
            send(vm, BudgetPlanEvent.OpenCustomize)
            val target =
                checkNotNull(
                    vm.plan.groups
                        .first()
                        .items
                        .last()
                        .swap,
                )
            send(vm, BudgetPlanEvent.Swap(target))
            val swapped = vm.plan.groups

            send(vm, BudgetPlanEvent.CloseCustomize)

            assertThat(vm.ui.customize).isNull()
            assertThat(vm.plan.groups).isEqualTo(swapped)
        }

    @Test
    fun `Add plan to order fills the cart, shows Added, and opens Your order after 1 s (AC5, TC-167)`() =
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
    fun `a locked order refuses the plan and stays on step 2`() =
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
            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.PLAN)
            assertThat(effects).isEmpty()
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
            val vm = BudgetPlanViewModel(catalogs, settings, orders, main.dispatcher)
            runCurrent()

            send(vm, BudgetPlanEvent.PickBudget(150_000), BudgetPlanEvent.SeePlans)

            assertThat(vm.ui.loaded).isTrue()
            assertThat(vm.ui.chips).hasSize(4)
            assertThat(vm.ui.canSeePlans).isFalse()
            assertThat(vm.ui.step).isEqualTo(BudgetPlanStep.TABLE)
        }

    private fun amount(formatted: String): Long = formatted.filter(Char::isDigit).toLong()
}
