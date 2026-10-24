package com.hitachi.kiosk.feature.combo

import androidx.lifecycle.SavedStateHandle
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.ComboCustomization
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.ComboSlotOption
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Combo states built the real way: the sample menu, a real order session and the ViewModel driven by events. The
 * design boards show 4 items already in the order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal object ComboTestStates {
    /** The sample menu with Cheese fries and Iced latte sold out, as on `S_Combo_soldOut`. */
    val soldOutMenu: Menu =
        SampleData.menu.let { menu ->
            menu.copy(
                comboSlots =
                    menu.comboSlots.copy(
                        sides = menu.comboSlots.sides.soldOut("cs_cheese"),
                        drinks = menu.comboSlots.drinks.soldOut("cd_coffee"),
                    ),
            )
        }

    private fun List<ComboSlotOption>.soldOut(id: String) = map { if (it.id == id) it.copy(available = false) else it }

    fun build(
        vararg events: ComboEvent,
        comboId: String? = null,
        catalog: Catalog = SampleData.catalog,
        cartCount: Int = 4,
    ): ComboUiState {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val orders = DefaultOrderSessionHolder()
            val session = orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            // Earlier lines of the order, so the "My order" badge shows the board's count.
            val earlier = CartItem.ProductItem("c1", emptyList())
            if (cartCount > 0) session.add(earlier, cartCount, Rupiah(38_000), emptyList())
            val vm =
                ComboViewModel(
                    SavedStateHandle(if (comboId == null) emptyMap() else mapOf("comboId" to comboId)),
                    FakeCatalogRepository(catalog),
                    orders,
                )
            events.forEach(vm::onEvent)
            return vm.state.value
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** The `Combo` artboard: the FR-011 defaults. */
    fun default() = build()

    /** `S_Combo_requiredMissing`: no side and no drink picked yet. */
    fun requiredMissing(): ComboUiState {
        val defaults = requireNotNull(ComboCustomization.defaults(SampleData.menu))
        return comboUiState(SampleData.menu, defaults.copy(sideId = null, drinkId = null), cartCount = 4)
    }

    /** `S_Combo_soldOut`: Cheese fries and Iced latte sold out. */
    fun soldOut() = build(catalog = SampleData.catalog.copy(menu = soldOutMenu))

    /** TC-048: Feast for Two, Nuggets, Large. */
    fun feast() =
        build(
            ComboEvent.SelectCombo("combo_feast"),
            ComboEvent.SelectSide("cs_nuggets"),
            ComboEvent.SelectLarge(true),
        )

    /** Just after "Add combo". */
    fun added() = build(ComboEvent.AddCombo)
}
