package com.hitachi.kiosk.core.domain.order

import com.hitachi.kiosk.core.domain.cart.Cart
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.CartLine
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.combo
import com.hitachi.kiosk.core.domain.model.groupsOf
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.pricing.MenuPricing

/**
 * The cart after `PRICE_CHANGED` (FR-016, DEV-046): [cart] with unit prices from the refreshed menu, and the
 * [issues] to show on Your order.
 */
data class RepricedCart(
    val cart: Cart,
    val issues: ReviewIssues,
)

/**
 * Re-prices a cart from a refreshed [Menu] with the same selections, using the same [MenuPricing] as the screens that
 * built the lines (FR-010, FR-011), so no second pricing rule exists. A line whose product, combo, option, side or
 * drink is gone or sold out is not re-priced; it keeps its old price and is flagged unavailable. A changed unit price
 * is recorded as its previous price ("Price updated · was Rp X").
 */
object CartRepricer {
    /** The result always has [ReviewIssues.pricesChanged] set: the backend said the totals differ (FR-016). */
    fun reprice(
        cart: Cart,
        menu: Menu,
    ): RepricedCart {
        val newPrices = mutableMapOf<String, Rupiah>()
        val previous = mutableMapOf<String, Rupiah>()
        val unavailable = mutableSetOf<String>()
        cart.lines.forEach { line ->
            val price = menu.unitPriceOf(line)
            when {
                price == null -> {
                    unavailable += line.lineId
                }

                price != line.unitPrice -> {
                    newPrices[line.lineId] = price
                    previous[line.lineId] = line.unitPrice
                }
            }
        }
        return RepricedCart(
            cart = cart.withUnitPrices(newPrices),
            issues =
                ReviewIssues(
                    previousUnitPrices = previous,
                    unavailableLineIds = unavailable,
                    pricesChanged = true,
                ),
        )
    }

    /** The line's unit price on [this] menu, or `null` when the line can no longer be sold. */
    private fun Menu.unitPriceOf(line: CartLine): Rupiah? =
        when (val item = line.item) {
            is CartItem.ProductItem -> productPrice(item)
            is CartItem.ComboItem -> comboPrice(item)
        }

    private fun Menu.productPrice(item: CartItem.ProductItem): Rupiah? {
        val product = product(item.productId)?.takeIf { it.available } ?: return null
        val groups = groupsOf(product)
        val stillOffered =
            item.selections.all { selection ->
                groups.firstOrNull { it.id == selection.groupId }?.options?.any { it.id == selection.optionId } == true
            }
        return if (stillOffered) MenuPricing.productUnitPrice(product, groups, item.selections) else null
    }

    private fun Menu.comboPrice(item: CartItem.ComboItem): Rupiah? {
        val combo = combo(item.comboId)?.takeIf { it.available } ?: return null
        val side = comboSlots.sides.firstOrNull { it.id == item.selection.sideId }?.takeIf { it.available }
        val drink = comboSlots.drinks.firstOrNull { it.id == item.selection.drinkId }?.takeIf { it.available }
        if (side == null || drink == null) return null
        return MenuPricing.comboUnitPrice(
            combo = combo,
            sideUpcharge = side.upcharge,
            drinkUpcharge = drink.upcharge,
            size = item.selection.size,
            largeUpcharge = comboSlots.sizeLargeUpcharge,
        )
    }
}
