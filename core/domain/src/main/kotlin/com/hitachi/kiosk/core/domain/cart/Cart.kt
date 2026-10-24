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

    /** The order is being paid, so its cart cannot change (BR-08). */
    ORDER_LOCKED,
}

/** One line to add with [Cart.addAll], for example one item of a Budget Plan (FR-032). */
data class CartAddition(
    val item: CartItem,
    val quantity: Int,
    val unitPrice: Rupiah,
    val choices: List<LineChoice>,
)

/** The outcome of [Cart.addAll]. */
sealed interface CartAddAllResult {
    /** Every addition landed; [lineIds] are the lines they created or merged into, in addition order. */
    data class Added(
        val cart: Cart,
        val lineIds: List<String>,
    ) : CartAddAllResult

    /** Nothing changed: one addition would break [limit]. */
    data class LimitReached(
        val limit: CartLimit,
    ) : CartAddAllResult
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
 * The lines of the current order, in the order they were added (FR-010, FR-012). Immutable: every operation returns a
 * new cart. Limits of 20 per line and 30 lines (BR-05) are enforced here, so no screen can bypass them.
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

    /** The line with [lineId], or `null`. */
    fun line(lineId: String): CartLine? = lines.firstOrNull { it.lineId == lineId }

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

    /**
     * Adds every one of [additions] or none of them (FR-032 AC5: a plan lands whole). A merge that would pass 20 counts
     * as [CartLimit.MAX_PER_LINE], because a capped plan would no longer equal the plan.
     */
    fun addAll(additions: List<CartAddition>): CartAddAllResult {
        var cart = this
        val lineIds = mutableListOf<String>()
        additions.forEach { addition ->
            when (val result = cart.add(addition.item, addition.quantity, addition.unitPrice, addition.choices)) {
                is CartAddResult.LimitReached -> {
                    return CartAddAllResult.LimitReached(result.limit)
                }

                is CartAddResult.Added -> {
                    if (result.capped) return CartAddAllResult.LimitReached(CartLimit.MAX_PER_LINE)
                    cart = result.cart
                    lineIds += result.line.lineId
                }
            }
        }
        return CartAddAllResult.Added(cart, lineIds.distinct())
    }

    /**
     * "+" on Your order (FR-012): one more of [lineId]. A line at [MAX_PER_LINE] does not change and reports
     * [CartLimit.MAX_PER_LINE]; the result is [CartAddResult.Added] with `merged = true` otherwise. `null` when the
     * cart has no such line (for example a tap on a line that is sliding out).
     */
    fun increment(lineId: String): CartAddResult? {
        val index = lines.indexOfFirst { it.lineId == lineId }
        return if (index < 0) null else merge(index, 1)
    }

    /** "−" on Your order (FR-012): one fewer of [lineId]; at quantity 1 the line is removed. An unknown line is ignored. */
    fun decrement(lineId: String): Cart {
        val line = line(lineId) ?: return this
        if (line.quantity <= 1) return remove(lineId)
        return copy(lines = lines.map { if (it.lineId == lineId) it.copy(quantity = it.quantity - 1) else it })
    }

    /** The trash icon or "Remove" (FR-012, FR-016 `ITEM_UNAVAILABLE`): the line is gone. An unknown line is ignored. */
    fun remove(lineId: String): Cart = copy(lines = lines.filterNot { it.lineId == lineId })

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
