package com.hitachi.kiosk.core.domain.order

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.ComboSelection
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.OrderLine
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Selection
import com.hitachi.kiosk.core.domain.pricing.PricingEngine
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fake.FakeCatalogSyncRepository
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fake.FakeOrderRepository
import com.hitachi.kiosk.core.testing.fake.FakeSettingsRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** "Pay" end to end against fakes: TC-068, TC-069, TC-070 (client side), TC-071, TC-072, TC-177 (FR-016, DEV-045/046). */
class SubmitOrderTest {
    private val holder = DefaultOrderSessionHolder()
    private val catalogs = FakeCatalogRepository()
    private val sync = FakeCatalogSyncRepository()
    private val settings = FakeSettingsRepository()
    private val orders = FakeOrderRepository()
    private val ids = FakeIdGenerator()
    private val submit = SubmitOrder(holder, catalogs, sync, settings, orders, ids)

    /** l1 = Crispy Chicken, l2 = 2 × Iced Lychee Tea, on Your order. */
    private val session: OrderSession =
        holder.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42").also {
            it.startBuilding()
            it.addProduct("c1")
            it.addProduct("d1", 2)
            it.startReviewing()
        }

    private val state get() = session.state.value

    private fun newCatalog(change: (Catalog) -> Catalog) {
        sync.onSyncNow = { catalogs.set(change(SampleData.catalog)) }
    }

    private val priceChanged =
        DomainError.PriceChanged("menu-119", "cfg-42", SampleData.changedTotals)

    private val menu119 =
        SampleData.menu.copy(
            menuVersion = "menu-119",
            products = SampleData.menu.products.map { if (it.id == "c1") it.copy(price = Rupiah(40_000)) else it },
        )

    // TC-068 ----------------------------------------------------------------------------------------------------------

    @Test
    fun `Pay sends E7 with the lines, the totals and a key, and the session starts paying (TC-068)`() =
        runTest {
            val result = submit()

            assertThat(result).isEqualTo(SubmitResult.Created(SampleData.createdOrder))
            assertThat(state.phase).isEqualTo(OrderPhase.PAYING)
            val request = orders.requests.single()
            assertThat(request.merchantId).isEqualTo(SampleData.MERCHANT_ID)
            assertThat(request.orderType).isEqualTo(OrderType.DINE_IN)
            assertThat(request.menuVersion).isEqualTo("menu-118")
            assertThat(request.configVersion).isEqualTo("cfg-42")
            assertThat(request.roundingStep).isEqualTo(0)
            assertThat(request.lines)
                .containsExactly(
                    OrderLine.ProductLine(
                        "l1",
                        "c1",
                        1,
                        emptyList(),
                        Rupiah(38_000),
                        Rupiah(38_000),
                    ),
                    OrderLine.ProductLine(
                        "l2",
                        "d1",
                        2,
                        listOf(Selection("mg_ice", "opt_ice_regular")),
                        Rupiah(16_000),
                        Rupiah(32_000),
                    ),
                ).inOrder()
            assertThat(request.clientTotals)
                .isEqualTo(PricingEngine.price(state.cart.lines, PricingRules.of(SampleData.config)))
            // 70.000 subtotal, below the 100.000 promo, 10 % tax.
            assertThat(request.clientTotals.total).isEqualTo(Rupiah(77_000))
            assertThat(orders.createKeys).containsExactly(IdempotencyKey("key-1"))
        }

    @Test
    fun `a combo line goes out with its selection`() =
        runTest {
            session.addCartCombo()

            submit()

            val combo =
                orders.requests
                    .single()
                    .lines
                    .filterIsInstance<OrderLine.ComboLine>()
                    .single()
            assertThat(combo.comboId).isEqualTo("combo_burger")
            assertThat(combo.selection).isEqualTo(ComboSelection("cs_fries", "cd_cola", ComboSize.REGULAR))
            assertThat(combo.unitPrice).isEqualTo(Rupiah(56_000))
        }

    @Test
    fun `the device rounding step is priced into the totals and sent (FR-013, A-25)`() =
        runTest {
            settings.value = settings.value.copy(roundingStep = 500)

            submit()

            val request = orders.requests.single()
            assertThat(request.roundingStep).isEqualTo(500)
            assertThat(request.clientTotals.total.value % 500).isEqualTo(0)
            assertThat(request.clientTotals)
                .isEqualTo(PricingEngine.price(state.cart.lines, PricingRules.of(SampleData.config, 500)))
        }

    // TC-069 ----------------------------------------------------------------------------------------------------------

    @Test
    fun `ten rapid taps produce one E7 and one order (TC-069, FR-016 AC1)`() =
        runTest {
            orders.create.succeed(SampleData.createdOrder, delay = 1.seconds)

            val results = List(10) { async { submit() } }.awaitAll()

            assertThat(results.filterIsInstance<SubmitResult.Created>()).hasSize(1)
            assertThat(results.filterIsInstance<SubmitResult.Ignored>()).hasSize(9)
            assertThat(orders.requests).hasSize(1)
            assertThat(state.phase).isEqualTo(OrderPhase.PAYING)
        }

    @Test
    fun `a tap after the order was created is ignored`() =
        runTest {
            submit()

            assertThat(submit()).isEqualTo(SubmitResult.Ignored)
            assertThat(orders.requests).hasSize(1)
        }

    @Test
    fun `nothing happens without an order, on an empty cart or outside Your order`() =
        runTest {
            val empty = SubmitOrder(DefaultOrderSessionHolder(), catalogs, sync, settings, orders, ids)
            assertThat(empty()).isEqualTo(SubmitResult.Ignored)

            session.startBuilding()
            assertThat(submit()).isEqualTo(SubmitResult.Ignored)

            session.startReviewing()
            session.remove("l1")
            session.remove("l2")
            assertThat(submit()).isEqualTo(SubmitResult.Ignored)

            assertThat(orders.requests).isEmpty()
        }

    // Keys (TC-070 client side, BR-08) ---------------------------------------------------------------------------------

    @Test
    fun `a network failure returns to Reviewing and Pay again reuses the same key (TC-070)`() =
        runTest {
            orders.create.enqueue(Outcome.Failure(DomainError.Network()))

            val first = submit()
            assertThat(first).isEqualTo(SubmitResult.Failed(DomainError.Network()))
            assertThat(state.phase).isEqualTo(OrderPhase.REVIEWING)

            val second = submit()

            assertThat(second).isInstanceOf(SubmitResult.Created::class.java)
            assertThat(orders.createKeys).containsExactly(IdempotencyKey("key-1"), IdempotencyKey("key-1"))
            assertThat(orders.requests.distinct()).hasSize(1)
        }

    @Test
    fun `a cart edit after a failure uses a new key`() =
        runTest {
            orders.create.enqueue(Outcome.Failure(DomainError.Server("SERVER_ERROR", 503)))
            submit()
            session.increment("l1")

            submit()

            assertThat(orders.createKeys).containsExactly(IdempotencyKey("key-1"), IdempotencyKey("key-2")).inOrder()
        }

    @Test
    fun `Back to order then Pay creates a new order with a new key (BR-08)`() =
        runTest {
            submit()
            session.backToOrder()

            val result = submit()

            assertThat(result).isInstanceOf(SubmitResult.Created::class.java)
            assertThat(orders.createKeys).containsExactly(IdempotencyKey("key-1"), IdempotencyKey("key-2")).inOrder()
        }

    @Test
    fun `other failures return to Reviewing with the cart untouched`() =
        runTest {
            val before = state.cart
            listOf(
                DomainError.Server("SERVER_ERROR", 503),
                DomainError.Conflict("MERCHANT_CLOSED"),
                DomainError.Unknown,
            ).forEach { error ->
                orders.create.enqueue(Outcome.Failure(error))

                assertThat(submit()).isEqualTo(SubmitResult.Failed(error))
                assertThat(state.phase).isEqualTo(OrderPhase.REVIEWING)
                assertThat(state.cart).isEqualTo(before)
                assertThat(state.issues).isEqualTo(ReviewIssues())
            }
            assertThat(sync.syncedNow).isEmpty()
        }

    @Test
    fun `no cached catalog fails without touching the order`() =
        runTest {
            catalogs.set(null)

            assertThat(submit()).isEqualTo(SubmitResult.Failed(DomainError.Unknown))

            assertThat(state.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(orders.requests).isEmpty()
        }

    @Test
    fun `a cancelled submission leaves Reviewing and keeps its key`() =
        runTest {
            orders.create.succeed(SampleData.createdOrder, delay = 10.seconds)

            val job = launch { submit() }
            runCurrent()
            assertThat(state.phase).isEqualTo(OrderPhase.SUBMITTING)
            job.cancel()
            runCurrent()

            assertThat(state.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(state.submission!!.key).isEqualTo(IdempotencyKey("key-1"))
            orders.create.succeed(SampleData.createdOrder)
            submit()
            assertThat(orders.createKeys.last()).isEqualTo(IdempotencyKey("key-1"))
        }

    // TC-071 PRICE_CHANGED --------------------------------------------------------------------------------------------

    @Test
    fun `PRICE_CHANGED refreshes the catalog, re-prices the cart and returns to Reviewing (TC-071, DEV-046)`() =
        runTest {
            orders.create.enqueue(Outcome.Failure(priceChanged))
            newCatalog { it.copy(menu = menu119) }

            val result = submit()

            assertThat(result).isEqualTo(SubmitResult.PriceChanged)
            assertThat(sync.syncedNow).containsExactly(SampleData.MERCHANT_ID)
            assertThat(state.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(state.cart.line("l1")!!.unitPrice).isEqualTo(Rupiah(40_000))
            assertThat(state.cart.line("l2")!!.unitPrice).isEqualTo(Rupiah(16_000))
            assertThat(state.issues.previousUnitPrices).containsExactly("l1", Rupiah(38_000))
            assertThat(state.issues.pricesChanged).isTrue()
            assertThat(state.menuVersion).isEqualTo("menu-119")
            // No payment was started and no order exists.
            assertThat(orders.pending()).isNull()
            assertThat(orders.cancels).isEmpty()
        }

    @Test
    fun `Pay stays blocked until Review, then sends the re-priced body with a new key`() =
        runTest {
            orders.create.enqueue(Outcome.Failure(priceChanged))
            newCatalog { it.copy(menu = menu119) }
            submit()

            assertThat(submit()).isEqualTo(SubmitResult.Ignored)
            assertThat(orders.requests).hasSize(1)

            session.reviewPrices()
            assertThat(submit()).isInstanceOf(SubmitResult.Created::class.java)

            val retried = orders.requests.last()
            assertThat(orders.createKeys).containsExactly(IdempotencyKey("key-1"), IdempotencyKey("key-2")).inOrder()
            assertThat(retried.menuVersion).isEqualTo("menu-119")
            assertThat(retried.lines.first().unitPrice).isEqualTo(Rupiah(40_000))
            assertThat(retried.clientTotals.subtotal).isEqualTo(Rupiah(72_000))
        }

    @Test
    fun `PRICE_CHANGED with unchanged unit prices still has to be reviewed`() =
        runTest {
            orders.create.enqueue(Outcome.Failure(priceChanged))
            newCatalog { it.copy(config = it.config.copy(configVersion = "cfg-43")) }

            assertThat(submit()).isEqualTo(SubmitResult.PriceChanged)

            assertThat(state.issues.previousUnitPrices).isEmpty()
            assertThat(state.issues.pricesChanged).isTrue()
            assertThat(state.configVersion).isEqualTo("cfg-43")
        }

    @Test
    fun `PRICE_CHANGED flags a line the refreshed menu no longer sells`() =
        runTest {
            orders.create.enqueue(Outcome.Failure(priceChanged))
            newCatalog { catalog ->
                catalog.copy(menu = menu119.copy(products = menu119.products.filterNot { it.id == "d1" }))
            }

            submit()

            assertThat(state.issues.unavailableLineIds).containsExactly("l2")
            assertThat(state.issues.previousUnitPrices).containsExactly("l1", Rupiah(38_000))
        }

    @Test
    fun `a failed refresh is a network failure and the order stays as it was`() =
        runTest {
            orders.create.enqueue(Outcome.Failure(priceChanged))
            sync.nowScript.fail(DomainError.Network())
            val before = state.cart

            val result = submit()

            assertThat(result).isEqualTo(SubmitResult.Failed(DomainError.Network()))
            assertThat(state.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(state.cart).isEqualTo(before)
            assertThat(state.issues).isEqualTo(ReviewIssues())
            assertThat(state.menuVersion).isEqualTo("menu-118")
            // Pay again resends the same body with the same key.
            submit()
            assertThat(orders.createKeys).containsExactly(IdempotencyKey("key-1"), IdempotencyKey("key-1"))
        }

    @Test
    fun `a refresh that leaves no catalog fails without touching the order`() =
        runTest {
            orders.create.enqueue(Outcome.Failure(priceChanged))
            sync.onSyncNow = { catalogs.set(null) }

            assertThat(submit()).isEqualTo(SubmitResult.Failed(DomainError.Unknown))

            assertThat(state.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(state.issues).isEqualTo(ReviewIssues())
        }

    // TC-072 ITEM_UNAVAILABLE -------------------------------------------------------------------------------------------

    @Test
    fun `ITEM_UNAVAILABLE flags the named lines and blocks Pay until they are removed (TC-072)`() =
        runTest {
            orders.create.enqueue(Outcome.Failure(DomainError.ItemUnavailable(listOf("l1"), listOf("c1"))))

            val result = submit()

            assertThat(result).isEqualTo(SubmitResult.ItemUnavailable(setOf("l1")))
            assertThat(state.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(state.issues.unavailableLineIds).containsExactly("l1")
            assertThat(state.cart.lines).hasSize(2)
            assertThat(sync.syncedNow).isEmpty()

            assertThat(submit()).isEqualTo(SubmitResult.Ignored)
            session.remove("l1")
            assertThat(submit()).isInstanceOf(SubmitResult.Created::class.java)
            assertThat(
                orders.requests
                    .last()
                    .lines
                    .map { it.lineId },
            ).containsExactly("l2")
        }

    @Test
    fun `ITEM_UNAVAILABLE by product ID alone flags every line of that product and combos by their ID`() =
        runTest {
            session.addCartCombo()
            session.addProduct("c1", menu = SampleData.menu) // merges into l1
            orders.create.enqueue(
                Outcome.Failure(DomainError.ItemUnavailable(emptyList(), listOf("c1", "combo_burger"))),
            )

            val result = submit()

            assertThat(result).isEqualTo(SubmitResult.ItemUnavailable(setOf("l1", "l3")))
        }

    @Test
    fun `ITEM_UNAVAILABLE naming nothing in the cart changes no line and does not block Pay`() =
        runTest {
            orders.create.enqueue(Outcome.Failure(DomainError.ItemUnavailable(listOf("l9"), listOf("zz"))))

            assertThat(submit()).isEqualTo(SubmitResult.ItemUnavailable(emptySet()))

            assertThat(state.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(state.issues).isEqualTo(ReviewIssues())
        }

    private fun OrderSession.addCartCombo() {
        startBuilding()
        addCombo("combo_burger")
        startReviewing()
    }
}
