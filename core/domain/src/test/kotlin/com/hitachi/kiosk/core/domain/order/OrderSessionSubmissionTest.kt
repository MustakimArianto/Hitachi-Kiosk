package com.hitachi.kiosk.core.domain.order

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.CartAddition
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The E7 key, the review issues and Pay blocking of the OrderSession (FR-016, DEV-045, DEV-046, TC-069, TC-070). */
class OrderSessionSubmissionTest {
    private val keys = AtomicInteger()
    private val nextKey = { IdempotencyKey("key-${keys.incrementAndGet()}") }

    /** A body that depends only on the cart, like the real one. */
    private fun body(state: OrderState): OrderRequest =
        OrderRequest(
            merchantId = "mer_urbangrill",
            orderType = state.orderType,
            menuVersion = state.menuVersion,
            configVersion = state.configVersion,
            roundingStep = 0,
            lines = emptyList(),
            clientTotals =
                Totals(state.cart.subtotal, Rupiah.Zero, null, Rupiah.Zero, Rupiah.Zero, state.cart.subtotal),
        )

    private fun session() = reviewingSession { addProduct("c1") }

    @Test
    fun `the first submit locks the order and settles a key`() {
        val session = session()

        val submission = session.submit(::body, nextKey)

        assertThat(submission!!.key).isEqualTo(IdempotencyKey("key-1"))
        assertThat(submission.request).isEqualTo(body(session.state.value))
        assertThat(session.state.value.phase).isEqualTo(OrderPhase.SUBMITTING)
        assertThat(session.state.value.submission).isEqualTo(submission)
    }

    @Test
    fun `a second submit while submitting changes nothing and asks for no key`() {
        val session = session()
        session.submit(::body, nextKey)

        assertThat(session.submit(::body, nextKey)).isNull()
        assertThat(keys.get()).isEqualTo(1)
    }

    @Test
    fun `a retry of the same body reuses the key after a failure (TC-070)`() {
        val session = session()
        val first = session.submit(::body, nextKey)!!
        session.submissionFailed()

        val retry = session.submit(::body, nextKey)!!

        assertThat(retry.key).isEqualTo(first.key)
        assertThat(keys.get()).isEqualTo(1)
    }

    @Test
    fun `a cart edit gets a new key`() {
        val session = session()
        val first = session.submit(::body, nextKey)!!
        session.submissionFailed()
        session.increment("l1")

        val second = session.submit(::body, nextKey)!!

        assertThat(second.key).isNotEqualTo(first.key)
        assertThat(second.request).isNotEqualTo(first.request)
    }

    @Test
    fun `editing and editing back reuses the key because the body is the same again`() {
        val session = session()
        val first = session.submit(::body, nextKey)!!
        session.submissionFailed()
        session.increment("l1")
        session.decrement("l1")

        assertThat(session.submit(::body, nextKey)!!.key).isEqualTo(first.key)
    }

    @Test
    fun `a re-priced cart gets a new key`() {
        val session = session()
        val first = session.submit(::body, nextKey)!!
        val cart =
            session.state.value.cart
                .withUnitPrices(mapOf("l1" to Rupiah(40_000)))
        val issues = ReviewIssues(previousUnitPrices = mapOf("l1" to Rupiah(38_000)))
        session.priceChanged(cart, "menu-119", "cfg-42", issues)
        session.reviewPrices()

        val second = session.submit(::body, nextKey)!!

        assertThat(second.key).isNotEqualTo(first.key)
    }

    @Test
    fun `Back to order forgets the key (BR-08, TC-086)`() {
        val session = session()
        val first = session.submit(::body, nextKey)!!
        session.orderCreated()
        assertThat(session.backToOrder()).isTrue()
        assertThat(session.state.value.submission).isNull()

        val second = session.submit(::body, nextKey)!!

        assertThat(second.key).isNotEqualTo(first.key)
    }

    @Test
    fun `submit refuses an empty cart`() {
        val session = reviewingSession { }

        assertThat(session.submit(::body, nextKey)).isNull()
        assertThat(session.submit()).isFalse()
    }

    @Test
    fun `100 racing taps on two threads settle exactly one submission (FR-016 AC1)`() =
        runTest {
            val session = session()

            val results =
                withContext(Dispatchers.Default) {
                    List(100) { async { session.submit(::body, nextKey) } }.awaitAll()
                }

            // A racer that loses the compare-and-set may have drawn a key it never uses; only one submission lands.
            assertThat(results.count { it != null }).isEqualTo(1)
            assertThat(session.state.value.submission).isEqualTo(results.single { it != null })
        }

    // PRICE_CHANGED and ITEM_UNAVAILABLE ----------------------------------------------------------------------------

    @Test
    fun `price changed returns to Reviewing with the new cart, versions and issues`() {
        val session = reviewingSession { addProduct("c1") }
        session.submit(::body, nextKey)
        val cart =
            session.state.value.cart
                .withUnitPrices(mapOf("l1" to Rupiah(40_000)))
        val issues = ReviewIssues(previousUnitPrices = mapOf("l1" to Rupiah(38_000)))

        assertThat(session.priceChanged(cart, "menu-119", "cfg-43", issues)).isTrue()

        val state = session.state.value
        assertThat(state.phase).isEqualTo(OrderPhase.REVIEWING)
        assertThat(state.cart).isEqualTo(cart)
        assertThat(state.menuVersion).isEqualTo("menu-119")
        assertThat(state.configVersion).isEqualTo("cfg-43")
        assertThat(state.issues).isEqualTo(issues)
    }

    @Test
    fun `price changed and unavailable only apply while submitting`() {
        val session = session()
        val before = session.state.value

        assertThat(session.priceChanged(before.cart, "m", "c", ReviewIssues(pricesChanged = true))).isFalse()
        assertThat(session.itemsUnavailable(setOf("l1"))).isFalse()
        assertThat(session.state.value).isEqualTo(before)
    }

    @Test
    fun `Pay is refused until the prices are reviewed (TC-177)`() {
        val session = session()
        session.submit(::body, nextKey)
        session.priceChanged(session.state.value.cart, "menu-119", "cfg-42", ReviewIssues(pricesChanged = true))

        assertThat(session.submit(::body, nextKey)).isNull()
        assertThat(session.submit()).isFalse()

        assertThat(session.reviewPrices()).isTrue()
        assertThat(session.state.value.issues.isBlocking).isFalse()
        assertThat(session.submit(::body, nextKey)).isNotNull()
    }

    @Test
    fun `an unavailable line is flagged and blocks Pay until it is removed (TC-072)`() {
        val session =
            reviewingSession {
                addProduct("c1")
                addProduct("d1")
            }
        session.submit(::body, nextKey)

        assertThat(session.itemsUnavailable(setOf("l1", "l99"))).isTrue()

        assertThat(session.state.value.phase).isEqualTo(OrderPhase.REVIEWING)
        assertThat(session.state.value.issues.unavailableLineIds).containsExactly("l1")
        assertThat(session.submit(::body, nextKey)).isNull()

        assertThat(session.remove("l1")).isTrue()
        assertThat(session.state.value.issues.unavailableLineIds).isEmpty()
        assertThat(session.submit(::body, nextKey)).isNotNull()
    }

    @Test
    fun `removing every unavailable line at once clears the flags and the plan tag`() {
        val session =
            reviewingSession {
                addPlan(
                    listOf(CartAddition(CartItem.ProductItem("c1", emptyList()), 1, Rupiah(38_000), emptyList())),
                    Rupiah(50_000),
                    1,
                    0,
                )
                addProduct("d1")
            }
        session.submit(::body, nextKey)
        session.itemsUnavailable(setOf("l1", "l2"))

        assertThat(session.removeUnavailable()).isTrue()

        val state = session.state.value
        assertThat(state.cart.isEmpty()).isTrue()
        assertThat(state.issues).isEqualTo(ReviewIssues())
        assertThat(state.planTag).isNull()
        assertThat(session.removeUnavailable()).isFalse()
    }

    @Test
    fun `price notes and flags of a removed line go with it`() {
        val session =
            reviewingSession {
                addProduct("c1")
                addProduct("d1")
            }
        session.submit(::body, nextKey)
        session.priceChanged(
            session.state.value.cart,
            "menu-119",
            "cfg-42",
            ReviewIssues(previousUnitPrices = mapOf("l1" to Rupiah(1), "l2" to Rupiah(2))),
        )

        session.remove("l1")

        assertThat(session.state.value.issues.previousUnitPrices).containsExactly("l2", Rupiah(2))
        // Removing the changed line does not review the prices: the customer still taps Review.
        assertThat(session.state.value.issues.pricesChanged).isTrue()
    }

    @Test
    fun `the issues survive a trip to Checkout and back to Your order (TC-071)`() {
        val session = session()
        session.submit(::body, nextKey)
        session.priceChanged(session.state.value.cart, "menu-119", "cfg-42", ReviewIssues(pricesChanged = true))

        session.startBuilding()
        session.startReviewing()

        assertThat(session.state.value.issues.pricesChanged).isTrue()
    }

    @Test
    fun `review actions are refused once the order is paying`() {
        val session = session()
        session.submit(::body, nextKey)
        session.orderCreated()

        assertThat(session.reviewPrices()).isFalse()
        assertThat(session.removeUnavailable()).isFalse()
    }
}
