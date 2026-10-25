package com.hitachi.kiosk.core.domain.order

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.CartAddAllResult
import com.hitachi.kiosk.core.domain.cart.CartAddition
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.CartLimit
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import org.junit.Test

/** Editing and replacing a Budget Plan in the cart (FR-032, DEV-111, DEV-112; TC-203). */
class OrderSessionPlanTest {
    private val c1 = CartItem.ProductItem("c1", emptyList())
    private val d4 = CartItem.ProductItem("d4", emptyList())
    private val e1 = CartItem.ProductItem("e1", emptyList())
    private val b1 = CartItem.ProductItem("b1", emptyList())

    private fun session() = OrderSession(OrderType.DINE_IN, "menu-118", "cfg-42").also { it.startBuilding() }

    private fun addition(
        item: CartItem,
        quantity: Int = 1,
    ) = CartAddition(item, quantity, Rupiah(8_000), emptyList())

    private fun OrderSession.units(item: CartItem): Int = state.value.cart.quantityOf(item)

    @Test
    fun `a plan records the units it added to each line`() {
        val session = session()
        session.add(c1, 1, Rupiah(38_000), emptyList())

        session.addPlan(listOf(addition(c1, 2), addition(d4)), Rupiah(100_000), 2, 0)

        val tag = checkNotNull(session.state.value.planTag)
        assertThat(tag.quantities).containsExactly("l1", 2, "l2", 1)
        assertThat(session.units(c1)).isEqualTo(3)
    }

    @Test
    fun `replacing a plan takes out its lines and puts the new ones in, other lines stay (TC-203)`() {
        val session = session()
        session.add(e1, 1, Rupiah(15_000), emptyList())
        session.addPlan(listOf(addition(c1), addition(d4, 2)), Rupiah(100_000), 2, 0)

        val result = session.replacePlan(listOf(addition(b1), addition(d4)), Rupiah(150_000), 3, 1)

        assertThat(result).isInstanceOf(CartAddAllResult.Added::class.java)
        val state = session.state.value
        assertThat(session.units(e1)).isEqualTo(1)
        assertThat(session.units(c1)).isEqualTo(0)
        assertThat(session.units(b1)).isEqualTo(1)
        assertThat(session.units(d4)).isEqualTo(1)
        assertThat(state.planTag?.budget).isEqualTo(Rupiah(150_000))
        assertThat(state.planTag?.adults).isEqualTo(3)
        assertThat(state.planTag?.children).isEqualTo(1)
        val tagged =
            state.planTag
                ?.lineIds
                .orEmpty()
                .mapNotNull { state.cart.line(it)?.item }
        assertThat(tagged).containsExactly(b1, d4)
    }

    @Test
    fun `replacing a plan keeps the units the customer had of the same item`() {
        val session = session()
        session.add(d4, 1, Rupiah(8_000), emptyList())
        session.addPlan(listOf(addition(d4, 2)), Rupiah(50_000), 1, 0)
        assertThat(session.units(d4)).isEqualTo(3)

        session.replacePlan(listOf(addition(c1)), Rupiah(50_000), 1, 0)

        assertThat(session.units(d4)).isEqualTo(1)
        assertThat(session.units(c1)).isEqualTo(1)
    }

    @Test
    fun `a replacement that breaks a limit changes nothing, the old plan stays (BR-05)`() {
        val session = session()
        repeat(28) { session.add(CartItem.ProductItem("p$it", emptyList()), 1, Rupiah(1_000), emptyList()) }
        session.addPlan(listOf(addition(c1)), Rupiah(50_000), 1, 0)
        val before = session.state.value

        val result =
            session.replacePlan(
                listOf(addition(b1), addition(d4), addition(e1)),
                Rupiah(90_000),
                2,
                0,
            )

        // Taking the plan out leaves 28 lines, and three new lines would make 31.
        assertThat(result).isEqualTo(CartAddAllResult.LimitReached(CartLimit.MAX_LINES))
        assertThat(session.state.value).isEqualTo(before)
    }

    @Test
    fun `without a plan tag a replacement is a plain add`() {
        val session = session()
        session.add(e1, 1, Rupiah(15_000), emptyList())

        session.replacePlan(listOf(addition(c1)), Rupiah(50_000), 1, 0)

        assertThat(session.units(e1)).isEqualTo(1)
        assertThat(session.units(c1)).isEqualTo(1)
        assertThat(session.state.value.planTag).isNotNull()
    }

    @Test
    fun `a locked order refuses a replacement`() {
        val session = session()
        session.addPlan(listOf(addition(c1)), Rupiah(50_000), 1, 0)
        session.startReviewing()
        session.submit()
        val before = session.state.value

        val result = session.replacePlan(listOf(addition(d4)), Rupiah(50_000), 1, 0)

        assertThat(result).isEqualTo(CartAddAllResult.LimitReached(CartLimit.ORDER_LOCKED))
        assertThat(session.state.value).isEqualTo(before)
    }

    @Test
    fun `Plan added is due once after a plan is added or replaced (DEV-112)`() {
        val session = session()
        assertThat(session.state.value.planAdded).isFalse()
        assertThat(session.acknowledgePlanAdded()).isFalse()

        session.addPlan(listOf(addition(c1)), Rupiah(50_000), 1, 0)
        assertThat(session.state.value.planAdded).isTrue()
        assertThat(session.acknowledgePlanAdded()).isTrue()
        assertThat(session.state.value.planAdded).isFalse()
        assertThat(session.acknowledgePlanAdded()).isFalse()

        session.replacePlan(listOf(addition(d4)), Rupiah(50_000), 1, 0)
        assertThat(session.state.value.planAdded).isTrue()
    }
}
