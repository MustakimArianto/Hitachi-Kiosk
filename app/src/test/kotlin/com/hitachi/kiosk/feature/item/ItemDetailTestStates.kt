package com.hitachi.kiosk.feature.item

import androidx.lifecycle.SavedStateHandle
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.Level
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
 * Item states built the real way: the sample menu, a real order session and the ViewModel driven by events. The
 * design boards show 4 items already in the order, so [cartCount] items are added first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal object ItemDetailTestStates {
    private val regular = ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_regular")

    fun build(
        vararg events: ItemDetailEvent,
        productId: String = "b2",
        catalog: Catalog = SampleData.catalog,
        cartCount: Int = 4,
    ): ItemDetailUiState {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val orders = DefaultOrderSessionHolder()
            val session = orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            // Earlier lines of the order, so the "My order" badge shows the board's count.
            val earlier = CartItem.ProductItem("c1", emptyList())
            if (cartCount > 0) session.add(earlier, cartCount, Rupiah(38_000), emptyList())
            val vm =
                ItemDetailViewModel(
                    SavedStateHandle(mapOf("productId" to productId)),
                    FakeCatalogRepository(catalog),
                    orders,
                )
            events.forEach(vm::onEvent)
            return vm.state.value
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** The `ItemDetail` artboard: Regular chosen. */
    fun default() = build(regular)

    /** `S_ItemDetail_requiredMissing`: the defaults, no size. */
    fun requiredMissing() = build()

    /** `S_ItemDetail_soldOut`: b2 sold out while open, Regular chosen as on the board. */
    fun soldOut(): ItemDetailUiState {
        val menu = SampleData.menu
        val soldOut = menu.copy(products = menu.products.map { if (it.id == "b2") it.copy(available = false) else it })
        return build(regular, catalog = SampleData.catalog.copy(menu = soldOut))
    }

    /** The `maxQty` scenario: 20 and the toast. */
    fun maxQuantity() = build(regular, *Array(20) { ItemDetailEvent.IncreaseQuantity })

    /** TC-042: Customize open with Onion None, Pickles Regular and Cheddar Extra. */
    fun customizeOpen() =
        build(
            regular,
            ItemDetailEvent.ToggleExpanded("mg_b2_ingredients"),
            ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_onion", Level.NONE),
            ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_pickles", Level.REGULAR),
            ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_cheddar", Level.EXTRA),
        )

    /** After "Add to order". */
    fun added() = build(regular, ItemDetailEvent.AddToOrder)
}
