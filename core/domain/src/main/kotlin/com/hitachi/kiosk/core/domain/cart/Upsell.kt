package com.hitachi.kiosk.core.domain.cart

import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.UpsellConfig
import com.hitachi.kiosk.core.domain.model.needsChoice
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.model.sortedCategories
import com.hitachi.kiosk.core.domain.model.visibleProducts
import com.hitachi.kiosk.core.domain.order.OrderState

/** SCR-09 rules (FR-014, D-16, DEV-039). */
object Upsell {
    /** The 2 × 2 grid of the design holds at most four suggestions (`Upsell.dc.html`). */
    const val MAX_SUGGESTIONS: Int = 4

    /**
     * Whether "View order" on the Menu goes through SCR-09: the toggle is on (FR-026) and SCR-09 was not offered yet
     * in this order (FR-014 AC1, AC2). With nothing left to suggest SCR-09 shows its "Nothing more to suggest" state
     * (`S_Upsell_noSuggestions`, PO decision 2026-10-24).
     */
    fun shouldOffer(
        settings: KioskSettings,
        order: OrderState,
    ): Boolean = settings.upsellEnabled && !order.upsellShown

    /**
     * Up to `maxSuggestions` (at most [MAX_SUGGESTIONS]) available products of the upsell categories, in category
     * and menu order (FR-014). Products already in the cart are left out (FR-014 rule), and so are products that need
     * a choice, because a suggestion is added with its default options (FR-014 AC3, A-08).
     */
    fun suggestions(
        menu: Menu,
        config: UpsellConfig,
        cart: Cart,
    ): List<Product> {
        val inCart =
            cart.lines
                .mapNotNull { (it.item as? CartItem.ProductItem)?.productId }
                .toSet()
        val limit = config.maxSuggestions.coerceIn(0, MAX_SUGGESTIONS)
        return menu
            .sortedCategories()
            .filter { it.id in config.categoryIds }
            .flatMap { menu.visibleProducts(it.id) }
            .filter { it.id !in inCart && menu.product(it.id) != null && !menu.needsChoice(it) }
            .take(limit)
    }
}
