package com.hitachi.kiosk.feature.budgetplan

import androidx.lifecycle.SavedStateHandle
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

    /** Lines that leave room for 1 more, so a plan of 3 or more new lines cannot fit (`S_BudgetPas_cartFull`). */
    private const val FULL_CART_LINES = 29

    fun build(
        vararg events: BudgetPlanEvent,
        cartLines: Int = 1,
    ): BudgetPlanUiState {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val orders = DefaultOrderSessionHolder()
            val session = orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            if (cartLines == 1) {
                session.add(CartItem.ProductItem("c1", emptyList()), BOARD_CART_COUNT, Rupiah(38_000), emptyList())
            } else {
                repeat(cartLines) {
                    session.add(CartItem.ProductItem("fill$it", emptyList()), 1, Rupiah(1_000), emptyList())
                }
            }
            val handle = SavedStateHandle(mapOf("edit" to false))
            val vm = BudgetPlanViewModel(handle, FakeCatalogRepository(), FakeSettingsRepository(), orders, dispatcher)
            events.forEach(vm::onEvent)
            return vm.state.value
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** Step 1 inputs: a chip, then the steppers from the default 2 adults and 0 children. */
    private fun inputs(
        budget: Long,
        adults: Int,
        children: Int,
    ): Array<BudgetPlanEvent> =
        (
            listOf(BudgetPlanEvent.PickBudget(budget)) +
                List(maxOf(adults - 2, 0)) { BudgetPlanEvent.IncreaseAdults } +
                List(maxOf(2 - adults, 0)) { BudgetPlanEvent.DecreaseAdults } +
                List(children) { BudgetPlanEvent.IncreaseChildren }
        ).toTypedArray()

    /** `S_BudgetPas_step1`: 2 adults, no budget. */
    fun stepOneEmpty() = build()

    /** `S_BudgetPas_step1Budget`: Rp 150.000 for 2 adults. */
    fun stepOne() = build(*inputs(150_000, 2, 0))

    /** `S_BudgetPas_keypadMin`: the keypad with 500 typed. */
    fun keypadMin() =
        build(
            BudgetPlanEvent.OpenKeypad,
            BudgetPlanEvent.PressKey(KeypadKey.FIVE),
            BudgetPlanEvent.PressKey(KeypadKey.ZERO),
            BudgetPlanEvent.PressKey(KeypadKey.ZERO),
        )

    /** `S_BudgetPas_twoPlans`: 2 adults and 1 child at Rp 150.000 after "See plans" (AC1). */
    fun stepTwo(vararg more: BudgetPlanEvent) = build(*inputs(150_000, 2, 1), BudgetPlanEvent.SeePlans, *more)

    /** `S_BudgetPas_exact`: 1 adult and 1 child at Rp 99.000 (AC2). */
    fun exact() = build(*inputs(99_000, 1, 1), BudgetPlanEvent.SeePlans)

    /** `S_BudgetPas_tooLow`: 2 adults and 1 child at Rp 100.000 (AC3). */
    fun tooLow() = build(*inputs(100_000, 2, 1), BudgetPlanEvent.SeePlans)

    /** `S_BudgetPas_moreThanEnough`: 1 adult at Rp 200.000. */
    fun moreThanEnough() = build(*inputs(200_000, 1, 0), BudgetPlanEvent.SeePlans)

    /** `S_BudgetPas_group`: 8 adults and 4 children at Rp 200.000 (AC11). */
    fun group() = build(*inputs(200_000, 8, 4))

    /** `S_BudgetPas_detailsOpen`: Price details open and What's included closed. */
    fun detailsOpen() = stepTwo(BudgetPlanEvent.ToggleIncluded, BudgetPlanEvent.TogglePriceDetails)

    /** `S_BudgetPas_optionsOff`: no sides, no desserts, no over plan. */
    fun optionsOff() =
        stepTwo(
            BudgetPlanEvent.SetIncludeSides(false),
            BudgetPlanEvent.SetIncludeDesserts(false),
            BudgetPlanEvent.SetAllowOver(false),
        )

    /** `S_BudgetPas_swapOpen`: the picker for Adult 1's meal. */
    fun swapOpen() = stepTwo(BudgetPlanEvent.Swap(SwapTarget(0)))

    /** `S_BudgetPas_cartFull`: the plan was refused. */
    fun cartFull() =
        build(*inputs(150_000, 2, 1), BudgetPlanEvent.SeePlans, BudgetPlanEvent.AddPlan, cartLines = FULL_CART_LINES)

    /** `S_BudgetPas_added`: after "Add plan to order". */
    fun added() = stepTwo(BudgetPlanEvent.AddPlan)

    /** Step 1 with the keypad open and 123456 typed (TC-171). */
    fun keypadOpen() =
        build(
            *inputs(150_000, 2, 1),
            BudgetPlanEvent.OpenKeypad,
            *Array(6) { BudgetPlanEvent.PressKey(KeypadKey.DELETE) },
            *listOf(KeypadKey.ONE, KeypadKey.TWO, KeypadKey.THREE, KeypadKey.FOUR, KeypadKey.FIVE, KeypadKey.SIX)
                .map { BudgetPlanEvent.PressKey(it) }
                .toTypedArray(),
        )
}
