package com.hitachi.kiosk.core.domain.cart

import com.hitachi.kiosk.core.domain.model.ComboSelection
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Selection

/** What a cart line holds. Two lines are identical when their items are equal (FR-010 merge rule). */
sealed interface CartItem {
    data class ProductItem(
        val productId: String,
        val selections: List<Selection>,
    ) : CartItem

    data class ComboItem(
        val comboId: String,
        val selection: ComboSelection,
    ) : CartItem
}

/** One choice in a line description; the UI words it from string resources (FR-010 AC3, NFR-008). */
sealed interface LineChoice {
    /** A chosen option or combo side/drink, shown by its name. */
    data class Option(
        val name: LocalizedText,
    ) : LineChoice

    /** An ingredient moved off its default level: "No onion", "Extra smoked cheddar". */
    data class LevelChange(
        val name: LocalizedText,
        val level: Level,
    ) : LineChoice

    /** A combo size: "Regular" or "Large". */
    data class Size(
        val size: ComboSize,
    ) : LineChoice
}

/** One line of the order (FR-010, FR-012). [unitPrice] is the price when the line was added. */
data class CartLine(
    val lineId: String,
    val item: CartItem,
    val quantity: Int,
    val unitPrice: Rupiah,
    val choices: List<LineChoice>,
) {
    val lineTotal: Rupiah get() = unitPrice * quantity
}

/** Why an add did not change the cart (BR-05). */
enum class CartLimit {
    /** The identical line already holds 20 ("Maximum 20 per item"). */
    MAX_PER_LINE,

    /** The order already has 30 lines. */
    MAX_LINES,
}

/** The outcome of [Cart.add]. */
sealed interface CartAddResult {
    /**
     * [line] was added or merged into. [capped] is true when the quantity was cut to 20, so the UI shows
     * "Maximum 20 per item" (FR-010 edge case, TC-045).
     */
    data class Added(
        val cart: Cart,
        val line: CartLine,
        val merged: Boolean,
        val capped: Boolean,
    ) : CartAddResult

    /** Nothing changed because of [limit]. */
    data class LimitReached(
        val limit: CartLimit,
    ) : CartAddResult
}

/**
 * The lines of the current order, in the order they were added (FR-010, FR-012). Immutable. 0.3.0 has the add
 * operations; increment, decrement and removal arrive with SCR-10 in 0.4.0 (DEV-036).
 */
data class Cart(
    val lines: List<CartLine> = emptyList(),
    private val nextLineNumber: Int = 1,
) {
    /** Units across all lines, for the dock "N items" (FR-009). */
    val itemCount: Int get() = lines.sumOf { it.quantity }

    /** Σ line totals, the dock total (FR-009 AC2). */
    val subtotal: Rupiah get() = lines.fold(Rupiah.Zero) { sum, line -> sum + line.lineTotal }

    fun isEmpty(): Boolean = lines.isEmpty()

    /** The quantity of the line identical to [item], or 0. */
    fun quantityOf(item: CartItem): Int = lines.firstOrNull { it.item == item }?.quantity ?: 0

    /**
     * Adds [quantity] of [item] at [unitPrice]. An identical line is merged into, capped at [MAX_PER_LINE]
     * (FR-010, A-10); a new line needs room under [MAX_LINES] (BR-05).
     */
    fun add(
        item: CartItem,
        quantity: Int,
        unitPrice: Rupiah,
        choices: List<LineChoice>,
    ): CartAddResult {
        require(quantity >= 1) { "Quantity must be at least 1" }
        val index = lines.indexOfFirst { it.item == item }
        return when {
            index >= 0 -> {
                merge(index, quantity)
            }

            lines.size >= MAX_LINES -> {
                CartAddResult.LimitReached(CartLimit.MAX_LINES)
            }

            else -> {
                val line =
                    CartLine(
                        lineId = "l$nextLineNumber",
                        item = item,
                        quantity = minOf(quantity, MAX_PER_LINE),
                        unitPrice = unitPrice,
                        choices = choices,
                    )
                val next = Cart(lines = lines + line, nextLineNumber = nextLineNumber + 1)
                CartAddResult.Added(next, line, merged = false, capped = quantity > MAX_PER_LINE)
            }
        }
    }

    /** Adds [quantity] to the line at [index], capped at [MAX_PER_LINE]; a full line does not change. */
    private fun merge(
        index: Int,
        quantity: Int,
    ): CartAddResult {
        val existing = lines[index]
        if (existing.quantity >= MAX_PER_LINE) return CartAddResult.LimitReached(CartLimit.MAX_PER_LINE)
        val wanted = existing.quantity + quantity
        val merged = existing.copy(quantity = minOf(wanted, MAX_PER_LINE))
        val next = copy(lines = lines.toMutableList().also { it[index] = merged })
        return CartAddResult.Added(next, merged, merged = true, capped = wanted > MAX_PER_LINE)
    }

    companion object {
        /** BR-05, A-10. */
        const val MAX_PER_LINE: Int = 20

        /** BR-05, A-10. */
        const val MAX_LINES: Int = 30
    }
}
