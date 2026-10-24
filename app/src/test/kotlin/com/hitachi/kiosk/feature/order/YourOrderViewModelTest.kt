package com.hitachi.kiosk.feature.order

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.cart.CartAddition
import com.hitachi.kiosk.core.domain.cart.ComboCustomization
import com.hitachi.kiosk.core.domain.cart.LineChoice
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.domain.order.ReviewIssues
import com.hitachi.kiosk.core.domain.order.SubmitOrder
import com.hitachi.kiosk.core.domain.order.SubmitResult
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fake.FakeCatalogSyncRepository
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fake.FakeOrderRepository
import com.hitachi.kiosk.core.testing.fake.FakeSettingsRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import com.hitachi.kiosk.feature.order.YourOrderTestStates.addCustom
import com.hitachi.kiosk.feature.order.YourOrderTestStates.burgerMeal
import com.hitachi.kiosk.feature.order.YourOrderTestStates.defaults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * SCR-10 behaviour (FR-012, FR-013, FR-015; DEV-037, DEV-037a, DEV-038; TC-050–TC-054, TC-065–TC-067, TC-173,
 * TC-177).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YourOrderViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalogs = FakeCatalogRepository()
    private val sessions = DefaultOrderSessionHolder()
    private val connectivity = FakeConnectivityMonitor()
    private val settings = FakeSettingsRepository(YourOrderTestStates.ROUNDED)
    private val sync = FakeCatalogSyncRepository()
    private val orders = FakeOrderRepository()
    private val submitOrder = SubmitOrder(sessions, catalogs, sync, settings, orders, FakeIdGenerator())

    /** The design's sample cart in the app's order session: l1 burger meal, l2 chicken, l3 large fries, l4 2 × tea. */
    private fun startOrder(withBurger: Boolean = true): OrderSession =
        sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42").apply {
            startBuilding()
            if (withBurger) addCustom(burgerMeal())
            addCustom(defaults("c1"))
            addCustom(defaults("s1").select("mg_fries_size", "opt_large"))
            addCustom(defaults("d1").select("mg_ice", "opt_ice_less"), quantity = 2)
        }

    private fun TestScope.viewModel(): YourOrderViewModel {
        val vm = YourOrderViewModel(catalogs, sessions, connectivity, settings)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private fun TestScope.effectsOf(vm: YourOrderViewModel): List<YourOrderEffect> {
        val effects = mutableListOf<YourOrderEffect>()
        backgroundScope.launch { vm.effect.toList(effects) }
        return effects
    }

    private val YourOrderViewModel.ui get() = state.value

    private fun YourOrderViewModel.line(lineId: String) = ui.lines.single { it.lineId == lineId }

    private fun TestScope.send(
        vm: YourOrderViewModel,
        vararg events: YourOrderEvent,
    ) {
        events.forEach(vm::onEvent)
        runCurrent()
    }

    @Test
    fun `the design sample cart totals Rp 173000 with rounding to Rp 500 (FR-013 AC1, AC3)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()

            val ui = vm.ui
            assertThat(ui.loaded).isTrue()
            assertThat(ui.empty).isFalse()
            assertThat(ui.itemCount).isEqualTo(5)
            assertThat(ui.lines.map { it.total })
                .containsExactly("Rp 75.000", "Rp 38.000", "Rp 22.000", "Rp 32.000")
                .inOrder()
            assertThat(ui.totals)
                .isEqualTo(
                    OrderTotalsUi(
                        subtotal = "Rp 167.000",
                        discount = "Rp 10.000",
                        promoLabel = "Rp 10.000 off over Rp 100.000",
                        tax = "Rp 15.700",
                        taxLabel = "Restaurant tax (PB1 10%)",
                        rounding = "Rp 300",
                        total = "Rp 173.000",
                    ),
                )
            assertThat(ui.totals.detailRows).isEqualTo(4)
            assertThat(ui.pay).isEqualTo(PayUi.Ready("Rp 173.000"))
            assertThat(ui.banner).isNull()
            assertThat(ui.orderType).isEqualTo(OrderType.DINE_IN)
        }

    @Test
    fun `without rounding the sample is Rp 172700 and has no Rounding row`() =
        runTest(main.dispatcher) {
            settings.value = KioskSettings()
            startOrder()
            val vm = viewModel()

            assertThat(vm.ui.totals.total).isEqualTo("Rp 172.700")
            assertThat(vm.ui.totals.rounding).isNull()
            assertThat(vm.ui.totals.detailRows).isEqualTo(3)
        }

    @Test
    fun `lines carry the name, worded choices and the category or combo tone`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val combo = checkNotNull(ComboCustomization.defaults(SampleData.menu, "combo_chicken"))
            session.add(checkNotNull(combo.toCartItem()), 1, combo.unitPrice, combo.choices())
            val vm = viewModel()

            val burger = vm.line("l1")
            assertThat(burger.name).isEqualTo("Smoky Double Burger")
            assertThat(burger.tone).isEqualTo("#E9DED0")
            assertThat(burger.choices).contains(LineChoice.LevelChange(LocalizedText("Onion", "Bawang"), Level.NONE))
            assertThat(vm.line("l4").tone).isEqualTo("#DFE3DC")
            val comboLine = vm.line("l5")
            assertThat(comboLine.name).isEqualTo("Crispy Chicken Meal")
            assertThat(comboLine.tone).isEqualTo("#E3D4C2")
        }

    @Test
    fun `opening Your order moves the order to Reviewing and Add more back to Building`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            assertThat(session.state.value.phase).isEqualTo(OrderPhase.REVIEWING)

            send(vm, YourOrderEvent.AddMore)
            assertThat(session.state.value.phase).isEqualTo(OrderPhase.BUILDING)

            session.startReviewing()
            send(vm, YourOrderEvent.BackToMenu)
            assertThat(session.state.value.phase).isEqualTo(OrderPhase.BUILDING)
            assertThat(effects).containsExactly(YourOrderEffect.BackToMenu, YourOrderEffect.BackToMenu)
        }

    @Test
    fun `plus and minus update the line and every total at once, and Pay always equals the total (TC-050, TC-066)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()

            send(vm, YourOrderEvent.Increment("l3"))
            assertThat(vm.line("l3").quantity).isEqualTo(2)
            assertThat(vm.line("l3").total).isEqualTo("Rp 44.000")
            // 189.000 − 10.000 = 179.000 + 17.900 tax = 196.900, rounded up to 197.000.
            assertThat(vm.ui.totals.subtotal).isEqualTo("Rp 189.000")
            assertThat(vm.ui.totals.total).isEqualTo("Rp 197.000")
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready(vm.ui.totals.total))

            send(vm, YourOrderEvent.Decrement("l4"))
            assertThat(vm.line("l4").quantity).isEqualTo(1)
            assertThat(vm.line("l4").total).isEqualTo("Rp 16.000")
            assertThat(vm.ui.itemCount).isEqualTo(5)
            assertThat(vm.ui.totals.subtotal).isEqualTo("Rp 173.000")
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready(vm.ui.totals.total))
        }

    @Test
    fun `minus at quantity 1 removes the line, which slides out for 460 ms (TC-051)`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()

            send(vm, YourOrderEvent.Decrement("l2"))

            assertThat(
                session.state.value.cart
                    .line("l2"),
            ).isNull()
            val leaving = vm.line("l2")
            assertThat(leaving.leaving).isTrue()
            assertThat(vm.ui.lines.map { it.lineId }).containsExactly("l1", "l2", "l3", "l4").inOrder()
            // The totals drop at once, not after the slide.
            assertThat(vm.ui.totals.subtotal).isEqualTo("Rp 129.000")
            assertThat(vm.ui.itemCount).isEqualTo(4)

            advanceTimeBy(459)
            runCurrent()
            assertThat(vm.ui.lines.map { it.lineId }).contains("l2")
            advanceTimeBy(1)
            runCurrent()
            assertThat(vm.ui.lines.map { it.lineId }).containsExactly("l1", "l3", "l4").inOrder()
        }

    @Test
    fun `removing every line shows the empty tray and Add an item to pay (TC-053, FR-012 AC2)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()

            send(
                vm,
                YourOrderEvent.Decrement("l1"),
                YourOrderEvent.Decrement("l2"),
                YourOrderEvent.Decrement("l3"),
                YourOrderEvent.Decrement("l4"),
                YourOrderEvent.Decrement("l4"),
            )
            assertThat(vm.ui.itemCount).isEqualTo(0)
            assertThat(vm.ui.pay).isEqualTo(PayUi.Blocked(PayBlock.EMPTY))
            assertThat(vm.ui.empty).isFalse()

            advanceTimeBy(460)
            runCurrent()
            assertThat(vm.ui.empty).isTrue()
            assertThat(vm.ui.lines).isEmpty()
            assertThat(vm.ui.totals.total).isEqualTo("Rp 0")
            assertThat(vm.ui.banner).isNull()
        }

    @Test
    fun `the item count follows the units (TC-054)`() =
        runTest(main.dispatcher) {
            val session = sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            session.startBuilding()
            session.addCustom(defaults("c1"))
            val vm = viewModel()
            assertThat(vm.ui.itemCount).isEqualTo(1)

            send(vm, YourOrderEvent.Increment("l1"))
            assertThat(vm.ui.itemCount).isEqualTo(2)
        }

    @Test
    fun `plus at 20 shows Maximum 20 per item for 2_4 s and changes nothing`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            session.addCustom(defaults("d1").select("mg_ice", "opt_ice_less"), quantity = 18)
            val vm = viewModel()
            assertThat(vm.line("l4").quantity).isEqualTo(20)

            send(vm, YourOrderEvent.Increment("l4"))

            assertThat(vm.line("l4").quantity).isEqualTo(20)
            assertThat(vm.ui.showMaxToast).isTrue()
            advanceTimeBy(2_400)
            runCurrent()
            assertThat(vm.ui.showMaxToast).isFalse()
        }

    @Test
    fun `methods are config and settings together, QRIS first and selected (TC-065)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            assertThat(vm.ui.methods).containsExactly(PaymentMethod.QRIS, PaymentMethod.CARD).inOrder()
            assertThat(vm.ui.selectedMethod).isEqualTo(PaymentMethod.QRIS)

            settings.value = YourOrderTestStates.ROUNDED.copy(paymentMethods = setOf(PaymentMethod.QRIS))
            runCurrent()

            assertThat(vm.ui.methods).containsExactly(PaymentMethod.QRIS)
            assertThat(vm.ui.noMethods).isFalse()
        }

    @Test
    fun `a selected method that is switched off falls back to the first one`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            send(vm, YourOrderEvent.SelectMethod(PaymentMethod.CARD))
            assertThat(vm.ui.selectedMethod).isEqualTo(PaymentMethod.CARD)

            settings.value = YourOrderTestStates.ROUNDED.copy(paymentMethods = setOf(PaymentMethod.QRIS))
            runCurrent()

            assertThat(vm.ui.selectedMethod).isEqualTo(PaymentMethod.QRIS)
        }

    @Test
    fun `no method available disables Pay with Pay unavailable (TC-067)`() =
        runTest(main.dispatcher) {
            catalogs.set(YourOrderTestStates.catalogWithoutMethods)
            startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            assertThat(vm.ui.methods).isEmpty()
            assertThat(vm.ui.noMethods).isTrue()
            assertThat(vm.ui.selectedMethod).isNull()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Blocked(PayBlock.NO_METHODS))

            send(vm, YourOrderEvent.Pay)
            assertThat(effects).isEmpty()
        }

    @Test
    fun `Details toggles open and closed and Pay keeps the total (TC-173)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            assertThat(vm.ui.detailsOpen).isFalse()

            send(vm, YourOrderEvent.ToggleDetails)
            assertThat(vm.ui.detailsOpen).isTrue()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready("Rp 173.000"))

            send(vm, YourOrderEvent.ToggleDetails)
            assertThat(vm.ui.detailsOpen).isFalse()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready("Rp 173.000"))
        }

    @Test
    fun `Pay goes to Checkout with the chosen method, and Checkout submits the order`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            send(vm, YourOrderEvent.SelectMethod(PaymentMethod.CARD), YourOrderEvent.Pay)

            assertThat(effects).containsExactly(YourOrderEffect.Pay(PaymentMethod.CARD))
            assertThat(session.state.value.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(orders.requests).isEmpty()
        }

    @Test
    fun `ten rapid Pay taps emit one navigation until the screen is shown again (TC-069)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            send(vm, *Array(10) { YourOrderEvent.Pay })
            assertThat(effects).containsExactly(YourOrderEffect.Pay(PaymentMethod.QRIS))

            // Back from Checkout the composition starts again, and Pay works once more.
            send(vm, YourOrderEvent.Shown, YourOrderEvent.Pay, YourOrderEvent.Pay)
            assertThat(effects).hasSize(2)
        }

    @Test
    fun `Pay does nothing once the order is submitting or paying`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            check(session.submit())
            send(vm, YourOrderEvent.Pay)

            assertThat(effects).isEmpty()
        }

    @Test
    fun `offline shows the banner and disables Pay until the kiosk is back (FR-028)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            connectivity.set(Connectivity.OFFLINE)
            runCurrent()
            assertThat(vm.ui.banner).isEqualTo(OrderBannerUi.Offline)
            assertThat(vm.ui.pay).isEqualTo(PayUi.Blocked(PayBlock.OFFLINE))
            send(vm, YourOrderEvent.Pay)
            assertThat(effects).isEmpty()

            connectivity.set(Connectivity.ONLINE)
            runCurrent()
            assertThat(vm.ui.banner).isNull()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready("Rp 173.000"))
        }

    @Test
    fun `under the threshold the promo nudge shows and Pay stays enabled (FR-013, S_Payment_promoNudge)`() =
        runTest(main.dispatcher) {
            startOrder(withBurger = false)
            val vm = viewModel()

            assertThat(vm.ui.banner).isEqualTo(OrderBannerUi.PromoNudge("Rp 8.000", "Rp 10.000", 0.92f))
            assertThat(vm.ui.totals.discount).isNull()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready("Rp 101.500"))
        }

    @Test
    fun `a Budget Plan order shows its tag until one of its lines changes (FR-012, A-40)`() =
        runTest(main.dispatcher) {
            val session = sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")
            session.startBuilding()
            YourOrderTestStates.planOrder().state.value.cart.lines.let { lines ->
                session.addPlan(
                    lines.map { CartAddition(it.item, it.quantity, it.unitPrice, it.choices) },
                    Rupiah(150_000),
                    adults = 2,
                    children = 1,
                )
            }
            val vm = viewModel()
            assertThat(vm.ui.planTag).isEqualTo(PlanTagUi(adults = 2, children = 1, budget = "Rp 150.000"))

            send(vm, YourOrderEvent.Increment("l2"))
            assertThat(vm.ui.planTag).isNull()
        }

    @Test
    fun `without an order the screen shows the empty tray`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            assertThat(vm.ui.loaded).isTrue()
            assertThat(vm.ui.empty).isTrue()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Blocked(PayBlock.EMPTY))
            send(vm, YourOrderEvent.Increment("l1"), YourOrderEvent.Decrement("l1"))
            assertThat(vm.ui.empty).isTrue()
        }

    @Test
    fun `nothing is drawn before the catalog arrives`() =
        runTest(main.dispatcher) {
            catalogs.set(null)
            startOrder()
            val vm = viewModel()

            assertThat(vm.ui).isEqualTo(YourOrderUiState())
        }

    // DEV-046, DEV-051: the review states, from the real session and E7's answers (TC-071, TC-072, TC-177). -----------

    private val priceChangedError = DomainError.PriceChanged("menu-119", "cfg-42", SampleData.changedTotals)

    /** The sample menu after the backend raised the Crispy Chicken from Rp 38.000 to Rp 40.000. */
    private val catalog119 =
        SampleData.catalog.copy(
            menu =
                SampleData.menu.copy(
                    menuVersion = "menu-119",
                    products =
                        SampleData.menu.products.map { if (it.id == "c1") it.copy(price = Rupiah(40_000)) else it },
                ),
        )

    private val session: OrderSession get() = checkNotNull(sessions.current.value)

    /** "Pay" at Checkout: E7 answers `PRICE_CHANGED`, the catalog is refreshed and the cart re-priced. */
    private suspend fun payAndGetPriceChanged() {
        orders.create.enqueue(Outcome.Failure(priceChangedError))
        sync.onSyncNow = { catalogs.set(catalog119) }
        assertThat(submitOrder()).isEqualTo(SubmitResult.PriceChanged)
    }

    /** "Pay" at Checkout: E7 answers `ITEM_UNAVAILABLE` for [lineIds] (the large fries, l3, by default). */
    private suspend fun payAndGetUnavailable(vararg lineIds: String = arrayOf("l3")) {
        orders.create.enqueue(Outcome.Failure(DomainError.ItemUnavailable(lineIds.toList(), emptyList())))
        assertThat(submitOrder()).isEqualTo(SubmitResult.ItemUnavailable(lineIds.toSet()))
    }

    @Test
    fun `PRICE_CHANGED shows the banner and the old price, blocks Pay, and Review re-enables it (TC-071, TC-177)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            payAndGetPriceChanged()
            runCurrent()

            assertThat(session.state.value.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(vm.ui.banner).isEqualTo(OrderBannerUi.PriceChanged)
            assertThat(vm.line("l2").total).isEqualTo("Rp 40.000")
            assertThat(vm.line("l2").previousTotal).isEqualTo("Rp 38.000")
            assertThat(vm.line("l1").previousTotal).isNull()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Blocked(PayBlock.PRICE_CHANGED))
            send(vm, YourOrderEvent.Pay)
            assertThat(effects).isEmpty()

            send(vm, YourOrderEvent.ReviewPrices)

            assertThat(vm.ui.banner).isNull()
            assertThat(vm.line("l2").previousTotal).isNull()
            // 169.000 - 10.000 promo = 159.000, + 15.900 tax = 174.900, rounded up to Rp 500.
            assertThat(vm.ui.totals.subtotal).isEqualTo("Rp 169.000")
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready("Rp 175.000"))
            send(vm, YourOrderEvent.Pay)
            assertThat(effects).hasSize(1)
        }

    @Test
    fun `after the refresh the screen prices from the new catalog and the next E7 carries the same total`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            payAndGetPriceChanged()
            runCurrent()
            send(vm, YourOrderEvent.ReviewPrices)
            val shown = vm.ui.totals.total

            assertThat(submitOrder()).isInstanceOf(SubmitResult.Created::class.java)

            val request = orders.requests.last()
            assertThat(request.menuVersion).isEqualTo("menu-119")
            assertThat(IdrFormatter.format(request.clientTotals.total)).isEqualTo(shown)
            assertThat(shown).isEqualTo("Rp 175.000")
            assertThat(orders.createKeys.distinct()).hasSize(2)
        }

    @Test
    fun `PRICE_CHANGED that leaves every unit price as it was still has to be reviewed`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            orders.create.enqueue(Outcome.Failure(priceChangedError))
            val newConfig = SampleData.catalog.copy(config = SampleData.config.copy(configVersion = "cfg-43"))
            sync.onSyncNow = { catalogs.set(newConfig) }

            assertThat(submitOrder()).isEqualTo(SubmitResult.PriceChanged)
            runCurrent()

            assertThat(vm.ui.banner).isEqualTo(OrderBannerUi.PriceChanged)
            assertThat(vm.ui.lines.map { it.previousTotal }).containsExactly(null, null, null, null)
            assertThat(vm.ui.pay).isEqualTo(PayUi.Blocked(PayBlock.PRICE_CHANGED))
            send(vm, YourOrderEvent.ReviewPrices)
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready("Rp 173.000"))
        }

    @Test
    fun `ITEM_UNAVAILABLE flags the line, bills the rest and blocks Pay until Remove (TC-072, TC-177)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            payAndGetUnavailable()
            runCurrent()

            assertThat(vm.ui.banner).isEqualTo(OrderBannerUi.ItemUnavailable(1))
            assertThat(vm.line("l3").unavailable).isTrue()
            assertThat(vm.ui.itemCount).isEqualTo(4)
            assertThat(vm.ui.totals.subtotal).isEqualTo("Rp 145.000")
            assertThat(vm.ui.pay).isEqualTo(PayUi.Blocked(PayBlock.ITEM_UNAVAILABLE))
            send(vm, YourOrderEvent.Pay)
            assertThat(effects).isEmpty()

            send(vm, YourOrderEvent.Remove("l3"))

            assertThat(
                session.state.value.cart
                    .line("l3"),
            ).isNull()
            assertThat(session.state.value.issues).isEqualTo(ReviewIssues())
            assertThat(vm.ui.banner).isNull()
            assertThat(vm.ui.totals.subtotal).isEqualTo("Rp 145.000")
            assertThat(vm.ui.pay).isInstanceOf(PayUi.Ready::class.java)
            // The line keeps its "No longer available" look while it slides out for 460 ms.
            assertThat(vm.line("l3").let { it.leaving to it.unavailable }).isEqualTo(true to true)
            advanceTimeBy(460)
            runCurrent()
            assertThat(vm.ui.lines.map { it.lineId }).containsExactly("l1", "l2", "l4").inOrder()
        }

    @Test
    fun `the banner Remove it takes every unavailable line out at once`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            payAndGetUnavailable("l2", "l3")
            runCurrent()
            assertThat(vm.ui.banner).isEqualTo(OrderBannerUi.ItemUnavailable(2))

            send(vm, YourOrderEvent.RemoveUnavailable)

            assertThat(
                session.state.value.cart.lines
                    .map { it.lineId },
            ).containsExactly("l1", "l4").inOrder()
            assertThat(vm.ui.banner).isNull()
            assertThat(
                vm.ui.lines
                    .filter { it.leaving }
                    .map { it.lineId },
            ).containsExactly("l2", "l3")
            assertThat(vm.ui.pay).isInstanceOf(PayUi.Ready::class.java)
            advanceTimeBy(460)
            runCurrent()
            assertThat(vm.ui.lines.map { it.lineId }).containsExactly("l1", "l4").inOrder()
        }

    @Test
    fun `the minus on another line leaves the flag, and the trash on the flagged line clears it`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            payAndGetUnavailable("l3")
            runCurrent()

            send(vm, YourOrderEvent.Decrement("l4"))
            assertThat(vm.ui.banner).isEqualTo(OrderBannerUi.ItemUnavailable(1))
            assertThat(vm.line("l4").quantity).isEqualTo(1)

            send(vm, YourOrderEvent.Decrement("l3"))
            assertThat(session.state.value.issues.unavailableLineIds).isEmpty()
            assertThat(vm.ui.banner).isNull()
        }

    @Test
    fun `removing the unavailable line while prices still wait for Review keeps Pay blocked`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            session.run {
                check(submit())
                check(
                    priceChanged(
                        cart = state.value.cart,
                        menuVersion = "menu-119",
                        configVersion = "cfg-42",
                        issues = ReviewIssues(mapOf("l1" to Rupiah(70_000)), setOf("l3"), pricesChanged = true),
                    ),
                )
            }
            runCurrent()
            assertThat(vm.ui.banner).isEqualTo(OrderBannerUi.PriceChanged)

            send(vm, YourOrderEvent.Remove("l3"))
            assertThat(vm.ui.banner).isEqualTo(OrderBannerUi.PriceChanged)
            assertThat(vm.ui.pay).isEqualTo(PayUi.Blocked(PayBlock.PRICE_CHANGED))

            send(vm, YourOrderEvent.ReviewPrices)
            assertThat(vm.ui.banner).isNull()
            assertThat(vm.ui.pay).isInstanceOf(PayUi.Ready::class.java)
        }

    @Test
    fun `blocking states follow the design priority offline, prices, unavailable, then the promo nudge`() =
        runTest(main.dispatcher) {
            startOrder(withBurger = false)
            val vm = viewModel()
            // Below the promo threshold, so the nudge would show.
            assertThat(vm.ui.banner).isInstanceOf(OrderBannerUi.PromoNudge::class.java)
            session.run {
                check(submit())
                check(
                    priceChanged(
                        cart = state.value.cart,
                        menuVersion = "menu-118",
                        configVersion = "cfg-42",
                        issues = ReviewIssues(mapOf("l1" to Rupiah(30_000)), setOf("l2"), pricesChanged = true),
                    ),
                )
            }
            runCurrent()

            connectivity.set(Connectivity.OFFLINE)
            runCurrent()
            assertThat(vm.ui.let { it.banner to it.pay })
                .isEqualTo(OrderBannerUi.Offline to PayUi.Blocked(PayBlock.OFFLINE))

            connectivity.set(Connectivity.ONLINE)
            runCurrent()
            assertThat(vm.ui.let { it.banner to it.pay })
                .isEqualTo(OrderBannerUi.PriceChanged to PayUi.Blocked(PayBlock.PRICE_CHANGED))

            send(vm, YourOrderEvent.ReviewPrices)
            assertThat(vm.ui.let { it.banner to it.pay })
                .isEqualTo(OrderBannerUi.ItemUnavailable(1) to PayUi.Blocked(PayBlock.ITEM_UNAVAILABLE))

            send(vm, YourOrderEvent.RemoveUnavailable)
            assertThat(vm.ui.banner).isInstanceOf(OrderBannerUi.PromoNudge::class.java)
            // The promo nudge never blocks.
            assertThat(vm.ui.pay).isInstanceOf(PayUi.Ready::class.java)
        }

    @Test
    fun `an offline kiosk blocks Pay but a checking one does not (DEV-051, TC-122, TC-123)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            connectivity.set(Connectivity.CHECKING)
            runCurrent()
            assertThat(vm.ui.banner).isNull()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready("Rp 173.000"))
            send(vm, YourOrderEvent.Pay)
            assertThat(effects).hasSize(1)

            send(vm, YourOrderEvent.Shown)
            connectivity.set(Connectivity.OFFLINE)
            runCurrent()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Blocked(PayBlock.OFFLINE))
            send(vm, YourOrderEvent.Pay)
            assertThat(effects).hasSize(1)

            // Back online: Pay re-enables by itself, with no other action.
            connectivity.set(Connectivity.ONLINE)
            runCurrent()
            send(vm, YourOrderEvent.Pay)
            assertThat(effects).hasSize(2)
        }

    @Test
    fun `the empty tray and no payment method are blocking states of their own`() {
        assertThat(YourOrderTestStates.NoMethods.pay).isEqualTo(PayUi.Blocked(PayBlock.NO_METHODS))
        assertThat(YourOrderTestStates.EmptyTray.pay).isEqualTo(PayUi.Blocked(PayBlock.EMPTY))
    }

    @Test
    fun `the review boards come from real session states`() {
        assertThat(YourOrderTestStates.PriceChanged.banner).isEqualTo(OrderBannerUi.PriceChanged)
        assertThat(
            YourOrderTestStates.PriceChanged.lines
                .first()
                .previousTotal,
        ).isEqualTo("Rp 75.000")
        assertThat(YourOrderTestStates.ItemUnavailable.banner).isEqualTo(OrderBannerUi.ItemUnavailable(1))
        assertThat(YourOrderTestStates.ItemUnavailable.pay).isEqualTo(PayUi.Blocked(PayBlock.ITEM_UNAVAILABLE))
    }
}
