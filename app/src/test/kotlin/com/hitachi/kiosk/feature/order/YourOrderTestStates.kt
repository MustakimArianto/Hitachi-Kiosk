package com.hitachi.kiosk.feature.order

import com.hitachi.kiosk.core.domain.cart.CartAddition
import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.domain.order.ReviewIssues
import com.hitachi.kiosk.core.testing.fixture.SampleData

/**
 * The design's boards built from the api-spec sample data through the real mapping ([yourOrderUiState]), so the
 * screenshots show what the app shows. The sample cart is the design's: the Smoky Double Burger as a meal with extra
 * cheddar and no onion (Rp 75.000), Crispy Chicken (Rp 38.000), large French Fries (Rp 22.000) and 2 × Iced Lychee
 * Tea with less ice (Rp 16.000), with rounding to Rp 500: Rp 173.000.
 */
internal object YourOrderTestStates {
    /** The design's `roundingStep` default. */
    val ROUNDED = KioskSettings(roundingStep = 500)

    fun burgerMeal(): ProductCustomization =
        defaults("b2")
            .select("mg_b2_size", "opt_b2_regular")
            .select("mg_meal_upgrade", "opt_make_meal")
            .setLevel("mg_b2_ingredients", "ing_cheddar", Level.EXTRA)
            .setLevel("mg_b2_ingredients", "ing_onion", Level.NONE)

    fun defaults(productId: String): ProductCustomization {
        val menu = SampleData.menu
        return ProductCustomization.defaults(checkNotNull(menu.product(productId)), menu)
    }

    fun OrderSession.addCustom(
        custom: ProductCustomization,
        quantity: Int = 1,
    ) {
        add(custom.toCartItem(), quantity, custom.unitPrice, custom.choices())
    }

    /** The design's four lines (l1–l4), or the `promoNudge` tray without the burger meal. */
    fun sampleOrder(withBurger: Boolean = true): OrderSession =
        OrderSession(OrderType.DINE_IN, SampleData.menu.menuVersion, SampleData.config.configVersion).apply {
            startBuilding()
            if (withBurger) addCustom(burgerMeal())
            addCustom(defaults("c1"))
            addCustom(defaults("s1").select("mg_fries_size", "opt_large"))
            addCustom(defaults("d1").select("mg_ice", "opt_ice_less"), quantity = 2)
            startReviewing()
        }

    /** The same lines as a Budget Plan for 2 adults and 1 child at Rp 150.000 (the `plan` prop). */
    fun planOrder(): OrderSession =
        OrderSession(OrderType.DINE_IN, SampleData.menu.menuVersion, SampleData.config.configVersion).apply {
            startBuilding()
            val additions =
                listOf(
                    burgerMeal() to 1,
                    defaults("c1") to 1,
                    defaults("s1").select("mg_fries_size", "opt_large") to 1,
                    defaults("d1").select("mg_ice", "opt_ice_less") to 2,
                ).map { (custom, quantity) ->
                    CartAddition(custom.toCartItem(), quantity, custom.unitPrice, custom.choices())
                }
            addPlan(additions, Rupiah(150_000), adults = 2, children = 1)
            startReviewing()
        }

    /** [SampleData.catalog] with no payment method enabled in config (`noMethods`). */
    val catalogWithoutMethods: Catalog =
        SampleData.catalog.copy(
            config =
                SampleData.config.copy(
                    paymentMethods = SampleData.config.paymentMethods.map { it.copy(enabled = false) },
                ),
        )

    fun board(
        order: OrderSession? = sampleOrder(),
        catalog: Catalog = SampleData.catalog,
        settings: KioskSettings = ROUNDED,
        connectivity: Connectivity = Connectivity.ONLINE,
        local: YourOrderLocalState = YourOrderLocalState(),
    ): YourOrderUiState = yourOrderUiState(catalog, order?.state?.value, settings, connectivity, local)

    val Default: YourOrderUiState get() = board()

    val DetailsOpen: YourOrderUiState get() = board(local = YourOrderLocalState(detailsOpen = true))

    val Plan: YourOrderUiState get() = board(order = planOrder())

    /**
     * `S_Payment_priceChanged`: the order was submitted and E7 re-priced the burger meal from Rp 75.000 to Rp 79.000,
     * so the session carries the re-priced cart and the review notes ("Price updated · was Rp 75.000").
     */
    val PriceChanged: YourOrderUiState
        get() {
            val order = sampleOrder()
            check(order.submit())
            val cart = order.state.value.cart
            val issues = ReviewIssues(previousUnitPrices = mapOf("l1" to Rupiah(75_000)), pricesChanged = true)
            check(order.priceChanged(cart.withUnitPrices(mapOf("l1" to Rupiah(79_000))), "menu-119", "cfg-42", issues))
            return board(order = order)
        }

    /** `S_Payment_itemUnavailable`: E7 answered `ITEM_UNAVAILABLE` for the large fries (line l3). */
    val ItemUnavailable: YourOrderUiState
        get() {
            val order = sampleOrder()
            check(order.submit())
            check(order.itemsUnavailable(setOf("l3")))
            return board(order = order)
        }

    val Offline: YourOrderUiState get() = board(connectivity = Connectivity.OFFLINE)

    val NoMethods: YourOrderUiState get() = board(catalog = catalogWithoutMethods)

    val EmptyTray: YourOrderUiState
        get() =
            board(
                order =
                    OrderSession(OrderType.DINE_IN, SampleData.menu.menuVersion, SampleData.config.configVersion)
                        .apply { startBuilding() },
            )

    val PromoNudge: YourOrderUiState get() = board(order = sampleOrder(withBurger = false))

    val CardSelected: YourOrderUiState
        get() = board(local = YourOrderLocalState(selectedMethod = PaymentMethod.CARD))
}
