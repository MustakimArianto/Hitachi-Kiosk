package com.hitachi.kiosk.core.domain.order

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.Cart
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.ModifierGroup
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Selection
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test

/** DEV-046: `PRICE_CHANGED` re-prices every line from the refreshed menu with the same selections (FR-016). */
class CartRepricerTest {
    private val menu = SampleData.menu

    /** l1 = c1, l2 = 2 × d1, l3 = Cheeseburger Meal. */
    private val cart =
        reviewingSession {
            addProduct("c1")
            addProduct("d1", 2)
            addCombo("combo_burger")
        }.state.value.cart

    private fun Menu.withProduct(
        id: String,
        change: Product.() -> Product,
    ): Menu = copy(products = products.map { if (it.id == id) it.change() else it })

    private fun Menu.withGroup(
        groupId: String,
        change: ModifierGroup.() -> ModifierGroup,
    ): Menu = copy(modifierGroups = modifierGroups.map { if (it.id == groupId) it.change() else it })

    @Test
    fun `an unchanged menu changes no price but is still flagged for review`() {
        val result = CartRepricer.reprice(cart, menu)

        assertThat(result.cart).isEqualTo(cart)
        assertThat(result.issues.previousUnitPrices).isEmpty()
        assertThat(result.issues.unavailableLineIds).isEmpty()
        assertThat(result.issues.pricesChanged).isTrue()
        assertThat(result.issues.isBlocking).isTrue()
    }

    @Test
    fun `a changed product price re-prices that line and records the old unit price`() {
        val next = menu.withProduct("c1") { copy(price = Rupiah(40_000)) }

        val result = CartRepricer.reprice(cart, next)

        assertThat(result.cart.line("l1")!!.unitPrice).isEqualTo(Rupiah(40_000))
        assertThat(result.cart.line("l2")).isEqualTo(cart.line("l2"))
        assertThat(result.issues.previousUnitPrices).containsExactly("l1", Rupiah(38_000))
        assertThat(result.issues.unavailableLineIds).isEmpty()
    }

    @Test
    fun `a quantity is kept and the line total follows the new price`() {
        val next = menu.withProduct("d1") { copy(price = Rupiah(17_000)) }

        val line = CartRepricer.reprice(cart, next).cart.line("l2")!!

        assertThat(line.quantity).isEqualTo(2)
        assertThat(line.lineTotal).isEqualTo(Rupiah(34_000))
    }

    @Test
    fun `a combo is re-priced from its price and upcharges`() {
        val next =
            menu.copy(
                combos = menu.combos.map { if (it.id == "combo_burger") it.copy(price = Rupiah(60_000)) else it },
            )

        val result = CartRepricer.reprice(cart, next)

        assertThat(result.cart.line("l3")!!.unitPrice).isEqualTo(Rupiah(60_000))
        assertThat(result.issues.previousUnitPrices).containsExactly("l3", Rupiah(56_000))
    }

    @Test
    fun `a product that is gone or sold out becomes an unavailable line that keeps its price`() {
        val soldOut = menu.withProduct("c1") { copy(available = false) }
        val gone = menu.copy(products = menu.products.filterNot { it.id == "d1" })

        val first = CartRepricer.reprice(cart, soldOut)
        val second = CartRepricer.reprice(cart, gone)

        assertThat(first.issues.unavailableLineIds).containsExactly("l1")
        assertThat(first.cart.line("l1")!!.unitPrice).isEqualTo(Rupiah(38_000))
        assertThat(first.issues.previousUnitPrices).isEmpty()
        assertThat(second.issues.unavailableLineIds).containsExactly("l2")
    }

    @Test
    fun `a combo that is sold out, or whose side or drink is gone, becomes unavailable`() {
        val comboOut = menu.copy(combos = menu.combos.map { it.copy(available = false) })
        val sideOut =
            menu.copy(
                comboSlots = menu.comboSlots.copy(sides = menu.comboSlots.sides.map { it.copy(available = false) }),
            )
        val drinkGone = menu.copy(comboSlots = menu.comboSlots.copy(drinks = emptyList()))

        listOf(comboOut, sideOut, drinkGone).forEach {
            assertThat(CartRepricer.reprice(cart, it).issues.unavailableLineIds).containsExactly("l3")
        }
    }

    @Test
    fun `a line whose option is gone becomes unavailable`() {
        val fries = reviewingSession { addProduct("s1") }.state.value.cart
        val withoutRegular =
            menu.withGroup("mg_fries_size") { copy(options = options.filterNot { it.id == "opt_regular" }) }

        val result = CartRepricer.reprice(fries, withoutRegular)

        assertThat(result.issues.unavailableLineIds).containsExactly("l1")
    }

    @Test
    fun `an option that costs more re-prices the line with the same selections`() {
        val fries = reviewingSession { addProduct("s1") }.state.value.cart
        val dearer =
            menu.withGroup("mg_fries_size") {
                copy(options = options.map { if (it.id == "opt_regular") it.copy(priceDelta = Rupiah(1_000)) else it })
            }

        val result = CartRepricer.reprice(fries, dearer)

        assertThat(
            result.cart.lines
                .single()
                .unitPrice,
        ).isEqualTo(Rupiah(19_000))
        assertThat(result.issues.previousUnitPrices).containsExactly("l1", Rupiah(18_000))
    }

    @Test
    fun `a selection of a group the product no longer has makes the line unavailable`() {
        val item = CartItem.ProductItem("c1", listOf(Selection("mg_ice", "opt_ice_less")))
        val base = (Cart().add(item, 1, Rupiah(38_000), emptyList()) as CartAddResult.Added).cart

        assertThat(CartRepricer.reprice(base, menu).issues.unavailableLineIds).containsExactly("l1")
    }
}
