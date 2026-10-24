package com.hitachi.kiosk.feature.upsell

import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.cart.Upsell
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.testing.fixture.SampleData

/**
 * The design's boards built from the api-spec sample data through the real mapping ([upsellUiState]), so the
 * screenshots show what the app shows.
 */
internal object UpsellTestStates {
    /** Every side and dessert the sample menu can suggest. */
    val ALL_ADD_ONS = listOf("s2", "s3", "s4", "e1", "e2", "e3")

    /** Adds [quantity] of [productId] with its defaults, then [picks] (group to option) and [customize]. */
    fun OrderSession.addProduct(
        productId: String,
        quantity: Int = 1,
        vararg picks: Pair<String, String>,
        customize: (ProductCustomization) -> ProductCustomization = { it },
    ) {
        val menu = SampleData.menu
        var custom = ProductCustomization.defaults(checkNotNull(menu.product(productId)), menu)
        picks.forEach { (group, option) -> custom = custom.select(group, option) }
        custom = customize(custom)
        add(custom.toCartItem(), quantity, custom.unitPrice, custom.choices())
    }

    /**
     * The design's sample cart (`Payment.dc.html`, api-spec E7): Smoky Double Burger meal with extra cheddar and no
     * onion, Crispy Chicken, large French Fries and 2 × Iced Lychee Tea, less ice — 5 items, Rp 167.000.
     */
    fun OrderSession.addSampleCart() {
        addProduct("b2", 1, "mg_meal_upgrade" to "opt_make_meal") {
            it
                .setLevel("mg_b2_ingredients", "ing_cheddar", Level.EXTRA)
                .setLevel("mg_b2_ingredients", "ing_onion", Level.NONE)
        }
        addProduct("c1")
        addProduct("s1", 1, "mg_fries_size" to "opt_large")
        addProduct("d1", 2, "mg_ice" to "opt_ice_less")
    }

    fun sampleOrder(): OrderSession =
        OrderSession(OrderType.DINE_IN, SampleData.menu.menuVersion, SampleData.config.configVersion).apply {
            startBuilding()
            addSampleCart()
        }

    /** A cart that already holds every side and dessert the menu can suggest (TC-172). */
    fun fullOrder(): OrderSession = sampleOrder().apply { ALL_ADD_ONS.forEach { addProduct(it) } }

    fun board(
        order: OrderSession = sampleOrder(),
        added: Map<String, Int> = emptyMap(),
        justAdded: String? = null,
    ): UpsellUiState {
        val ids = Upsell.suggestions(SampleData.menu, SampleData.config.upsell, order.state.value.cart).map { it.id }
        added.forEach { (id, quantity) -> order.addProduct(id, quantity) }
        return upsellUiState(
            catalog = SampleData.catalog,
            order = order.state.value,
            local =
                UpsellLocalState(
                    suggestionIds = ids,
                    justAddedProductId = justAdded,
                    addSerial = added.values.sum(),
                ),
        )
    }

    val Default: UpsellUiState get() = board()

    val NoSuggestions: UpsellUiState get() = board(order = fullOrder())

    /** Cheese Fries added twice earlier, Caramel Sundae just now. */
    val JustAdded: UpsellUiState get() = board(added = mapOf("s2" to 2, "e1" to 1), justAdded = "e1")
}
