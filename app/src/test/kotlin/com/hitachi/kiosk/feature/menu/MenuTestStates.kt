package com.hitachi.kiosk.feature.menu

import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.domain.time.DayPart
import com.hitachi.kiosk.core.testing.fixture.SampleData

/**
 * The design's boards built from the api-spec sample data through the real mapping ([menuUiState]), so the
 * screenshots show what the app shows.
 */
internal object MenuTestStates {
    /** The design's `scenario: unavailable`: these products are sold out. */
    val UNAVAILABLE_IDS = setOf("b3", "c2", "d1", "e2")

    /** The design's `scenario: emptyCategory`: every dessert is sold out. */
    val DESSERT_IDS = setOf("e1", "e2", "e3")

    /** [SampleData.catalog] with [ids] sold out. */
    fun catalogWithSoldOut(ids: Set<String>): Catalog =
        SampleData.catalog.copy(
            menu =
                SampleData.menu.copy(
                    products = SampleData.menu.products.map { if (it.id in ids) it.copy(available = false) else it },
                ),
        )

    /** Adds [quantity] of [productId] with its defaults, or with [optionId] picked in [groupId]. */
    fun OrderSession.addProduct(
        productId: String,
        quantity: Int = 1,
        groupId: String? = null,
        optionId: String? = null,
    ) {
        val menu = SampleData.menu
        val defaults = ProductCustomization.defaults(checkNotNull(menu.product(productId)), menu)
        val custom = if (groupId != null && optionId != null) defaults.select(groupId, optionId) else defaults
        add(custom.toCartItem(), quantity, custom.unitPrice, custom.choices())
    }

    /**
     * The design's sample cart: Crispy Chicken, large French Fries (Rp 22.000) and 2 × Iced Lychee Tea, or 20 of
     * them for `maxQty` — 4 items, Rp 92.000.
     */
    fun sampleOrder(teaQuantity: Int = 2): OrderSession =
        OrderSession(OrderType.DINE_IN, SampleData.menu.menuVersion, SampleData.config.configVersion).apply {
            startBuilding()
            addProduct("c1")
            addProduct("s1", groupId = "mg_fries_size", optionId = "opt_large")
            addProduct("d1", quantity = teaQuantity)
        }

    fun board(
        selection: MenuSelection = MenuSelection.Popular,
        catalog: Catalog = SampleData.catalog,
        connectivity: Connectivity = Connectivity.ONLINE,
        order: OrderSession = sampleOrder(),
        showMaxToast: Boolean = false,
    ): MenuUiState =
        menuUiState(
            catalog = catalog,
            order = order.state.value,
            connectivity = connectivity,
            local = MenuLocalState(selection = selection, showMaxToast = showMaxToast),
            dayPart = DayPart.AFTERNOON,
        )

    val Default: MenuUiState get() = board()

    val Offline: MenuUiState get() = board(connectivity = Connectivity.OFFLINE)

    val EmptyCategory: MenuUiState
        get() = board(MenuSelection.Category("cat_desserts"), catalogWithSoldOut(DESSERT_IDS))

    val Unavailable: MenuUiState get() = board(catalog = catalogWithSoldOut(UNAVAILABLE_IDS))

    val MaxQty: MenuUiState
        get() = board(MenuSelection.Category("cat_drinks"), order = sampleOrder(teaQuantity = 20), showMaxToast = true)

    /** `S_Menu_drinksTab`: the header pill without the slim row. */
    val DrinksTab: MenuUiState get() = board(MenuSelection.Category("cat_drinks"))

    /** `S_Menu_planOff`: `budgetPlanner.enabled` is false, so neither entry shows (AC7). */
    val PlanOff: MenuUiState
        get() =
            board(
                catalog =
                    SampleData.catalog.copy(
                        config =
                            SampleData.config.copy(
                                budgetPlanner = SampleData.config.budgetPlanner.copy(enabled = false),
                            ),
                    ),
            )

    val Burgers: MenuUiState get() = board(MenuSelection.Category("cat_burgers"))
}
