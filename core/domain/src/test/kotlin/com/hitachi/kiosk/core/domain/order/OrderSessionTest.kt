package com.hitachi.kiosk.core.domain.order

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.CartLimit
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

class OrderSessionTest {
    private val holder = DefaultOrderSessionHolder()
    private val c1 = CartItem.ProductItem("c1", emptyList())

    @Test
    fun `choosing an order type starts an order on the current catalog (FR-007)`() {
        assertThat(holder.current.value).isNull()
        assertThat(holder.isOrderActive).isFalse()

        val session = holder.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")

        assertThat(holder.current.value).isSameInstanceAs(session)
        assertThat(holder.isOrderActive).isTrue()
        assertThat(session.state.value.phase).isEqualTo(OrderPhase.TYPE_CHOSEN)
        assertThat(session.state.value.menuVersion).isEqualTo("menu-118")
        assertThat(session.state.value.configVersion).isEqualTo("cfg-42")
        assertThat(
            session.state.value.cart
                .isEmpty(),
        ).isTrue()
    }

    @Test
    fun `changing the type keeps the cart (TC-028)`() {
        val session = holder.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
        session.startBuilding()
        session.add(c1, 2, Rupiah(38_000), emptyList())

        val same = holder.chooseOrderType(OrderType.TAKEAWAY, "menu-119", "cfg-43")

        assertThat(same).isSameInstanceAs(session)
        assertThat(same.state.value.orderType).isEqualTo(OrderType.TAKEAWAY)
        assertThat(same.state.value.cart.itemCount).isEqualTo(2)
        // The catalog snapshot is the one the order started with (A-29).
        assertThat(same.state.value.menuVersion).isEqualTo("menu-118")
        assertThat(same.state.value.phase).isEqualTo(OrderPhase.BUILDING)
    }

    @Test
    fun `ending the order clears everything (FR-006 AC5, TC-023)`() {
        val session = holder.chooseOrderType(OrderType.TAKEAWAY, "menu-118", "cfg-42")
        session.add(c1, 3, Rupiah(38_000), emptyList())

        holder.endOrder()
        val next = holder.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")

        assertThat(next).isNotSameInstanceAs(session)
        assertThat(
            next.state.value.cart
                .isEmpty(),
        ).isTrue()
    }

    @Test
    fun `concurrent adds never lose an update`() =
        runTest {
            val session = holder.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")

            withContext(Dispatchers.Default) {
                (1..20).map { async { session.add(c1, 1, Rupiah(38_000), emptyList()) } }.awaitAll()
            }

            assertThat(
                session.state.value.cart
                    .quantityOf(c1),
            ).isEqualTo(20)
            assertThat(session.add(c1, 1, Rupiah(38_000), emptyList()))
                .isEqualTo(CartAddResult.LimitReached(CartLimit.MAX_PER_LINE))
        }
}
