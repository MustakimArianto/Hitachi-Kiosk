package com.hitachi.kiosk.core.domain.cart

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Selection
import org.junit.Assert.assertThrows
import org.junit.Test

class CartTest {
    private val c1 = CartItem.ProductItem("c1", emptyList())
    private val d1 = CartItem.ProductItem("d1", listOf(Selection("mg_ice", "opt_ice_regular")))

    private fun Cart.added(
        item: CartItem,
        quantity: Int = 1,
        price: Long = 38_000,
    ): CartAddResult.Added = add(item, quantity, Rupiah(price), emptyList()) as CartAddResult.Added

    @Test
    fun `quick add merges identical lines and totals the dock (FR-009 AC2, TC-038)`() {
        val cart =
            Cart()
                .added(c1)
                .cart
                .added(c1)
                .cart
                .added(d1, price = 16_000)
                .cart

        assertThat(cart.lines.map { it.lineId to it.quantity }).containsExactly("l1" to 2, "l2" to 1).inOrder()
        assertThat(cart.itemCount).isEqualTo(3)
        assertThat(cart.subtotal).isEqualTo(Rupiah(92_000))
        assertThat(cart.quantityOf(c1)).isEqualTo(2)
        assertThat(cart.quantityOf(CartItem.ProductItem("x", emptyList()))).isEqualTo(0)
    }

    @Test
    fun `different selections make different lines`() {
        val cart =
            Cart()
                .added(d1)
                .cart
                .added(d1.copy(selections = emptyList()))
                .cart

        assertThat(cart.lines).hasSize(2)
        assertThat(cart.lines.last().lineId).isEqualTo("l2")
    }

    @Test
    fun `a merge is capped at 20 and says so (FR-010 edge case, TC-045)`() {
        val eighteen = Cart().added(c1, quantity = 18).cart

        val result = eighteen.added(c1, quantity = 5)

        assertThat(result.merged).isTrue()
        assertThat(result.capped).isTrue()
        assertThat(result.line.quantity).isEqualTo(20)
        assertThat(result.cart.add(c1, 1, Rupiah(38_000), emptyList()))
            .isEqualTo(CartAddResult.LimitReached(CartLimit.MAX_PER_LINE))
    }

    @Test
    fun `a new line over 20 is capped too`() {
        val result = Cart().added(c1, quantity = 25)

        assertThat(result.capped).isTrue()
        assertThat(result.merged).isFalse()
        assertThat(result.line.quantity).isEqualTo(20)
    }

    @Test
    fun `an order holds at most 30 lines (BR-05)`() {
        val full =
            (1..Cart.MAX_LINES).fold(Cart()) { cart, n ->
                cart.added(CartItem.ProductItem("p$n", emptyList())).cart
            }

        assertThat(full.lines).hasSize(30)
        assertThat(full.add(CartItem.ProductItem("p31", emptyList()), 1, Rupiah(1), emptyList()))
            .isEqualTo(CartAddResult.LimitReached(CartLimit.MAX_LINES))
        // An identical line still merges when the cart is full.
        assertThat(full.add(CartItem.ProductItem("p1", emptyList()), 1, Rupiah(1), emptyList()))
            .isInstanceOf(CartAddResult.Added::class.java)
    }

    @Test
    fun `quantity must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { Cart().add(c1, 0, Rupiah(1), emptyList()) }
        assertThat(Cart().isEmpty()).isTrue()
    }

    @Test
    fun `plus and minus change one line and the totals (FR-012 AC1, TC-050)`() {
        val cart =
            Cart()
                .added(c1)
                .cart
                .added(d1, quantity = 2, price = 16_000)
                .cart

        val more = cart.increment("l1") as CartAddResult.Added
        val fewer = more.cart.decrement("l2")

        assertThat(more.line.quantity).isEqualTo(2)
        assertThat(more.merged).isTrue()
        assertThat(fewer.lines.map { it.lineId to it.quantity }).containsExactly("l1" to 2, "l2" to 1).inOrder()
        assertThat(fewer.subtotal).isEqualTo(Rupiah(2 * 38_000 + 16_000))
        assertThat(fewer.itemCount).isEqualTo(3)
    }

    @Test
    fun `minus at quantity 1 removes the line (TC-051)`() {
        val cart =
            Cart()
                .added(c1)
                .cart
                .added(d1, price = 16_000)
                .cart

        val removed = cart.decrement("l1")

        assertThat(removed.lines.map { it.lineId }).containsExactly("l2")
        assertThat(removed.line("l1")).isNull()
        assertThat(removed.remove("l2").isEmpty()).isTrue()
    }

    @Test
    fun `plus stops at 20 and unknown lines change nothing (BR-05, TC-052)`() {
        val cart = Cart().added(c1, quantity = 20).cart

        assertThat(cart.increment("l1")).isEqualTo(CartAddResult.LimitReached(CartLimit.MAX_PER_LINE))
        assertThat(cart.increment("nope")).isNull()
        assertThat(cart.decrement("nope")).isEqualTo(cart)
        assertThat(cart.remove("nope")).isEqualTo(cart)
    }

    @Test
    fun `a removed line's ID is never reused`() {
        val cart =
            Cart()
                .added(c1)
                .cart
                .remove("l1")
                .added(d1)
                .cart

        assertThat(cart.lines.single().lineId).isEqualTo("l2")
    }
}
