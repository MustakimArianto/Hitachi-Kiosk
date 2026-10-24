package com.hitachi.kiosk.core.domain.order

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.hitachi.kiosk.core.domain.cart.CartAddAllResult
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.CartAddition
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.CartLimit
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.OrderPhase.BUILDING
import com.hitachi.kiosk.core.domain.order.OrderPhase.CANCELLED
import com.hitachi.kiosk.core.domain.order.OrderPhase.COMPLETED
import com.hitachi.kiosk.core.domain.order.OrderPhase.PAID
import com.hitachi.kiosk.core.domain.order.OrderPhase.PAYING
import com.hitachi.kiosk.core.domain.order.OrderPhase.REVIEWING
import com.hitachi.kiosk.core.domain.order.OrderPhase.SUBMITTING
import com.hitachi.kiosk.core.domain.order.OrderPhase.TIMED_OUT
import com.hitachi.kiosk.core.domain.order.OrderPhase.TYPE_CHOSEN
import org.junit.Test

/** Every edge of architecture §5.1, tried from every phase (DEV-041). */
class OrderSessionMachineTest {
    private val c1 = CartItem.ProductItem("c1", emptyList())
    private val d4 = CartItem.ProductItem("d4", emptyList())

    /** The phase each transition leads to, and the phases it is allowed from. */
    private val edges: Map<String, Triple<(OrderSession) -> Boolean, OrderPhase, Set<OrderPhase>>> =
        mapOf(
            "startBuilding" to Triple(OrderSession::startBuilding, BUILDING, setOf(TYPE_CHOSEN, BUILDING, REVIEWING)),
            "startReviewing" to Triple(OrderSession::startReviewing, REVIEWING, setOf(BUILDING, REVIEWING)),
            "submit" to Triple(OrderSession::submit, SUBMITTING, setOf(REVIEWING)),
            "submissionFailed" to Triple(OrderSession::submissionFailed, REVIEWING, setOf(SUBMITTING)),
            "orderCreated" to Triple(OrderSession::orderCreated, PAYING, setOf(SUBMITTING)),
            "backToOrder" to Triple(OrderSession::backToOrder, REVIEWING, setOf(PAYING)),
            "paid" to Triple(OrderSession::paid, PAID, setOf(PAYING)),
            "complete" to Triple(OrderSession::complete, COMPLETED, setOf(PAID)),
            "cancel" to Triple(OrderSession::cancel, CANCELLED, setOf(TYPE_CHOSEN, BUILDING, REVIEWING)),
            "timeOut" to Triple(OrderSession::timeOut, TIMED_OUT, setOf(TYPE_CHOSEN, BUILDING, REVIEWING)),
        )

    @Test
    fun `each transition moves only from the phases architecture 5_1 allows`() {
        edges.forEach { (name, edge) ->
            val (move, target, from) = edge
            OrderPhase.entries.forEach { phase ->
                val session = sessionIn(phase)

                val moved = move(session)

                val expected = phase in from
                assertWithMessage("$name from $phase").that(moved).isEqualTo(expected)
                assertWithMessage("$name from $phase, phase after")
                    .that(session.state.value.phase)
                    .isEqualTo(if (expected) target else phase)
            }
        }
    }

    @Test
    fun `the happy path runs from TypeChosen to Completed`() {
        val session = sessionWithCart()

        assertThat(session.startReviewing()).isTrue()
        assertThat(session.submit()).isTrue()
        assertThat(session.orderCreated()).isTrue()
        assertThat(session.paid()).isTrue()
        assertThat(session.complete()).isTrue()
        assertThat(session.state.value.phase).isEqualTo(COMPLETED)
    }

    @Test
    fun `Pay needs something to pay for (FR-012 AC2)`() {
        val session = OrderSession(OrderType.DINE_IN, "menu-118", "cfg-42")
        session.startBuilding()
        session.startReviewing()

        assertThat(session.submit()).isFalse()
        assertThat(session.state.value.phase).isEqualTo(REVIEWING)
    }

    @Test
    fun `the cart is locked from Submitting on (BR-08)`() {
        listOf(SUBMITTING, PAYING, PAID, COMPLETED, CANCELLED, TIMED_OUT).forEach { phase ->
            val session = sessionIn(phase)
            val before = session.state.value

            assertThat(session.add(c1, 1, Rupiah(38_000), emptyList()))
                .isEqualTo(CartAddResult.LimitReached(CartLimit.ORDER_LOCKED))
            assertThat(session.increment("l1")).isEqualTo(CartAddResult.LimitReached(CartLimit.ORDER_LOCKED))
            assertThat(session.decrement("l1")).isFalse()
            assertThat(session.remove("l1")).isFalse()
            assertThat(session.addPlan(listOf(addition(d4)), Rupiah(1), 1, 0))
                .isEqualTo(CartAddAllResult.LimitReached(CartLimit.ORDER_LOCKED))
            assertThat(session.markUpsellShown()).isFalse()
            assertThat(session.changeOrderType(OrderType.TAKEAWAY)).isFalse()
            assertWithMessage("$phase").that(session.state.value).isEqualTo(before)
        }
    }

    @Test
    fun `Your order edits the cart while reviewing (FR-012)`() {
        val session = sessionWithCart()
        session.startReviewing()

        assertThat(session.increment("l1")).isInstanceOf(CartAddResult.Added::class.java)
        assertThat(session.decrement("l1")).isTrue()
        assertThat(session.remove("l1")).isTrue()
        assertThat(session.remove("l1")).isFalse()
        assertThat(session.increment("l1")).isNull()
        assertThat(
            session.state.value.cart
                .isEmpty(),
        ).isTrue()
    }

    @Test
    fun `the upsell is offered once per order (FR-014 AC2)`() {
        val session = sessionWithCart()

        assertThat(session.state.value.upsellShown).isFalse()
        assertThat(session.markUpsellShown()).isTrue()
        assertThat(session.state.value.upsellShown).isTrue()
    }

    @Test
    fun `a plan lands whole and tags its lines (FR-032 AC5, A-40)`() {
        val session = sessionWithCart()

        val result = session.addPlan(listOf(addition(d4, 2), addition(c1)), Rupiah(150_000), 2, 1)

        assertThat(result).isInstanceOf(CartAddAllResult.Added::class.java)
        assertThat(session.state.value.planTag)
            .isEqualTo(PlanTag(Rupiah(150_000), adults = 2, children = 1, lineIds = listOf("l2", "l1")))
        assertThat(session.state.value.cart.itemCount).isEqualTo(4)
    }

    @Test
    fun `editing a plan line clears the tag, other lines do not (architecture 5_1)`() {
        val session = OrderSession(OrderType.DINE_IN, "menu-118", "cfg-42")
        session.add(c1, 1, Rupiah(38_000), emptyList())
        session.addPlan(listOf(addition(d4)), Rupiah(50_000), 1, 0)

        session.increment("l1")
        assertThat(session.state.value.planTag).isNotNull()
        session.add(CartItem.ProductItem("e1", emptyList()), 1, Rupiah(15_000), emptyList())
        assertThat(session.state.value.planTag).isNotNull()

        session.decrement("l2")
        assertThat(session.state.value.planTag).isNull()
    }

    @Test
    fun `adding a menu item identical to a plan line clears the tag`() {
        val session = OrderSession(OrderType.DINE_IN, "menu-118", "cfg-42")
        session.addPlan(listOf(addition(d4)), Rupiah(50_000), 1, 0)

        session.add(d4, 1, Rupiah(8_000), emptyList())

        assertThat(session.state.value.planTag).isNull()
    }

    @Test
    fun `a plan that would break a limit changes nothing (BR-05)`() {
        val session = OrderSession(OrderType.DINE_IN, "menu-118", "cfg-42")
        session.add(d4, 19, Rupiah(8_000), emptyList())
        val before = session.state.value

        val result = session.addPlan(listOf(addition(c1), addition(d4, 2)), Rupiah(100_000), 2, 0)

        assertThat(result).isEqualTo(CartAddAllResult.LimitReached(CartLimit.MAX_PER_LINE))
        assertThat(session.state.value).isEqualTo(before)
    }

    @Test
    fun `a full cart refuses a plan with a new line`() {
        val session = OrderSession(OrderType.DINE_IN, "menu-118", "cfg-42")
        repeat(30) { session.add(CartItem.ProductItem("p$it", emptyList()), 1, Rupiah(1_000), emptyList()) }

        assertThat(session.addPlan(listOf(addition(d4)), Rupiah(1), 1, 0))
            .isEqualTo(CartAddAllResult.LimitReached(CartLimit.MAX_LINES))
    }

    private fun addition(
        item: CartItem,
        quantity: Int = 1,
    ) = CartAddition(item, quantity, Rupiah(8_000), emptyList())

    private fun sessionWithCart(): OrderSession =
        OrderSession(OrderType.DINE_IN, "menu-118", "cfg-42").also {
            it.startBuilding()
            it.add(c1, 1, Rupiah(38_000), emptyList())
        }

    /** A session driven to [phase] along valid edges, with one line in the cart. */
    private fun sessionIn(phase: OrderPhase): OrderSession {
        val session = OrderSession(OrderType.DINE_IN, "menu-118", "cfg-42")
        if (phase == TYPE_CHOSEN) return session
        session.add(c1, 1, Rupiah(38_000), emptyList())
        val path =
            when (phase) {
                TYPE_CHOSEN -> {
                    emptyList()
                }

                BUILDING -> {
                    listOf(session::startBuilding)
                }

                REVIEWING -> {
                    listOf(session::startBuilding, session::startReviewing)
                }

                SUBMITTING -> {
                    listOf(session::startBuilding, session::startReviewing, session::submit)
                }

                PAYING -> {
                    listOf(session::startBuilding, session::startReviewing, session::submit, session::orderCreated)
                }

                PAID -> {
                    listOf(
                        session::startBuilding,
                        session::startReviewing,
                        session::submit,
                        session::orderCreated,
                        session::paid,
                    )
                }

                COMPLETED -> {
                    listOf(
                        session::startBuilding,
                        session::startReviewing,
                        session::submit,
                        session::orderCreated,
                        session::paid,
                        session::complete,
                    )
                }

                CANCELLED -> {
                    listOf(session::cancel)
                }

                TIMED_OUT -> {
                    listOf(session::timeOut)
                }
            }
        path.forEach { step -> check(step()) }
        check(session.state.value.phase == phase)
        return session
    }
}
