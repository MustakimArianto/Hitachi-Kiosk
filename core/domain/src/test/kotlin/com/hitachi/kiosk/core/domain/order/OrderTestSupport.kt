package com.hitachi.kiosk.core.domain.order

import com.hitachi.kiosk.core.domain.cart.ComboCustomization
import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.testing.fixture.SampleData

/** Adds [quantity] of the product [id] with its defaults, priced like quick add (FR-009, FR-010). */
internal fun OrderSession.addProduct(
    id: String,
    quantity: Int = 1,
    menu: Menu = SampleData.menu,
) {
    val customization = ProductCustomization.defaults(requireNotNull(menu.product(id)), menu)
    add(customization.toCartItem(), quantity, customization.unitPrice, customization.choices())
}

/** Adds [quantity] of the combo [id] with its defaults (FR-011). */
internal fun OrderSession.addCombo(
    id: String,
    quantity: Int = 1,
    menu: Menu = SampleData.menu,
) {
    val customization = requireNotNull(ComboCustomization.defaults(menu, id))
    add(requireNotNull(customization.toCartItem()), quantity, customization.unitPrice, customization.choices())
}

/** A dine-in order on the sample catalog, on Your order, with whatever [fill] adds. */
internal fun reviewingSession(fill: OrderSession.() -> Unit): OrderSession =
    OrderSession(OrderType.DINE_IN, "menu-118", "cfg-42").also {
        it.startBuilding()
        it.fill()
        it.startReviewing()
    }
