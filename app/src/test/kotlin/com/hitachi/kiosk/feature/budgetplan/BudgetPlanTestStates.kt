package com.hitachi.kiosk.feature.budgetplan

import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fake.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Budget Plan states built the real way: the sample menu and config, the real planner and the ViewModel driven by
 * events. The design boards show 4 items already in the order, so 4 items are added first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal object BudgetPlanTestStates {
    private const val BOARD_CART_COUNT = 4

    fun build(vararg events: BudgetPlanEvent): BudgetPlanUiState {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val orders = DefaultOrderSessionHolder()
            val session = orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            session.add(CartItem.ProductItem("c1", emptyList()), BOARD_CART_COUNT, Rupiah(38_000), emptyList())
            val vm = BudgetPlanViewModel(FakeCatalogRepository(), FakeSettingsRepository(), orders)
            events.forEach(vm::onEvent)
            return vm.state.value
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** Step 1 inputs: a chip, then the steppers. */
    private fun inputs(
        budget: Long,
        adults: Int,
        children: Int,
    ): Array<BudgetPlanEvent> =
        (
            listOf(BudgetPlanEvent.PickBudget(budget)) +
                List(adults - 1) { BudgetPlanEvent.IncreaseAdults } +
                List(children) { BudgetPlanEvent.IncreaseChildren }
        ).toTypedArray()

    /** The `default` scenario's step 1: Rp 150.000, 2 adults and 1 child. */
    fun stepOne() = build(*inputs(150_000, 2, 1))

    /** The `default` scenario after "See plans": Just under Rp 149.600 and Just over Rp 150.700 (AC1). */
    fun stepTwo(vararg more: BudgetPlanEvent) = build(*inputs(150_000, 2, 1), BudgetPlanEvent.SeePlans, *more)

    /** `S_BudgetPas_exact`: 1 adult and 1 child at Rp 99.000 (AC2). */
    fun exact() = build(*inputs(99_000, 1, 1), BudgetPlanEvent.SeePlans)

    /** `S_BudgetPas_tooLow`: 2 adults and 1 child at Rp 100.000 (AC3). */
    fun tooLow() = build(*inputs(100_000, 2, 1), BudgetPlanEvent.SeePlans)

    /** `S_BudgetPas_moreThanEnough`: 1 adult at Rp 200.000. */
    fun moreThanEnough() = build(*inputs(200_000, 1, 0), BudgetPlanEvent.SeePlans)

    /** `S_BudgetPas_group`: 8 adults and 4 children at Rp 200.000 (AC11). */
    fun group() = build(*inputs(200_000, 8, 4))

    /** Step 2 with the Customize sheet open. */
    fun customizeOpen() = stepTwo(BudgetPlanEvent.OpenCustomize)

    /** Step 2 with both rows open. */
    fun expanded() = stepTwo(BudgetPlanEvent.ToggleIncluded, BudgetPlanEvent.TogglePriceDetails)

    /** Step 2 after "Add plan to order". */
    fun added() = stepTwo(BudgetPlanEvent.AddPlan)

    /** Step 1 with the keypad open and 123456 typed (TC-171). */
    fun keypadOpen() =
        build(
            *inputs(150_000, 2, 1),
            BudgetPlanEvent.OpenKeypad,
            *listOf(KeypadKey.ONE, KeypadKey.TWO, KeypadKey.THREE, KeypadKey.FOUR, KeypadKey.FIVE, KeypadKey.SIX)
                .map { BudgetPlanEvent.PressKey(it) }
                .toTypedArray(),
        )
}
