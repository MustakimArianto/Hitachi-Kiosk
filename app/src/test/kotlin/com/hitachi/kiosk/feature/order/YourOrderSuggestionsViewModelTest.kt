package com.hitachi.kiosk.feature.order

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.CartAddition
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fake.FakeSettingsRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import com.hitachi.kiosk.feature.order.YourOrderTestStates.addCustom
import com.hitachi.kiosk.feature.order.YourOrderTestStates.defaults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The "Goes well with your order" strip on SCR-10 (FR-014, D-33, DEV-106, DEV-114; TC-194–TC-197, TC-205): what it
 * offers, the add and undo, the Budget Plan limit, the blockers and the meal card.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YourOrderSuggestionsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalogs = FakeCatalogRepository()
    private val sessions = DefaultOrderSessionHolder()
    private val connectivity = FakeConnectivityMonitor()
    private val settings = FakeSettingsRepository(YourOrderTestStates.SUGGESTING)

    private fun TestScope.viewModel(): YourOrderViewModel {
        val vm = YourOrderViewModel(catalogs, sessions, connectivity, settings)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
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

    /** The design's sample cart: a meal burger, chicken, large fries and two teas. */
    private fun startSample(): OrderSession =
        sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42").apply {
            startBuilding()
            addCustom(YourOrderTestStates.burgerMeal())
            addCustom(defaults("c1"))
            addCustom(defaults("s1").select("mg_fries_size", "opt_large"))
            addCustom(defaults("d1").select("mg_ice", "opt_ice_less"), quantity = 2)
        }

    /** One main in the order: Crispy Chicken. */
    private fun startMain(): OrderSession =
        sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42").apply {
            startBuilding()
            addCustom(defaults("c1"))
        }

    // ---- The "Goes well with your order" strip (FR-014, D-33; TC-194–TC-197, TC-205) ----

    private fun startBurgerOnly(): OrderSession =
        sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42").apply {
            startBuilding()
            addCustom(defaults("b2").select("mg_b2_size", "opt_b2_regular"))
            addCustom(defaults("s1"))
            addCustom(defaults("d1"))
        }

    private fun YourOrderViewModel.keys() = ui.suggestions?.cards?.map { it.key }

    @Test
    fun `the strip leads with a drink and follows the toggle with no extra tap (TC-194, FR-014 AC1)`() =
        runTest(main.dispatcher) {
            settings.value = YourOrderTestStates.SUGGESTING
            startMain()
            val vm = viewModel()

            assertThat(vm.keys()).containsExactly("add:d1", "add:s1", "add:e1").inOrder()
            assertThat(vm.ui.suggestions?.budgetLimited).isFalse()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready("Rp 42.000"))

            settings.value = YourOrderTestStates.ROUNDED
            runCurrent()
            assertThat(vm.ui.suggestions).isNull()
        }

    @Test
    fun `an order with a meal, side and drink is only offered a dessert (TC-195, FR-014 AC2, AC4)`() =
        runTest(main.dispatcher) {
            settings.value = YourOrderTestStates.SUGGESTING
            startSample()
            val vm = viewModel()

            // The sample order has a meal burger, a side and drinks: only the dessert is missing.
            assertThat(vm.keys()).containsExactly("add:e1")

            send(vm, YourOrderEvent.SuggestionAdd("add:e1"))
            // The dessert card stays so it can be undone; nothing else is on offer.
            assertThat(vm.keys()).containsExactly("add:e1")
            assertThat(
                vm.ui.suggestions
                    ?.cards
                    ?.single()
                    ?.quantity,
            ).isEqualTo(1)
            assertThat(vm.ui.pay).isInstanceOf(PayUi.Ready::class.java)
        }

    @Test
    fun `add puts one unit with default options in the order, the stepper undoes it in place (TC-196, AC3)`() =
        runTest(main.dispatcher) {
            settings.value = YourOrderTestStates.SUGGESTING
            val session = startMain()
            val vm = viewModel()

            send(vm, YourOrderEvent.SuggestionAdd("add:s1"))

            // The side was in slot 1: its card keeps the slot while the order re-ranks around it.
            assertThat(vm.keys()).containsExactly("add:d1", "add:s1", "add:e1").inOrder()
            assertThat(
                vm.ui.suggestions
                    ?.cards
                    ?.map { it.quantity },
            ).containsExactly(0, 1, 0).inOrder()
            assertThat(
                session.state.value.cart.lines
                    .map { it.lineId },
            ).containsExactly("l1", "l2").inOrder()
            assertThat(vm.ui.totals.subtotal).isEqualTo("Rp 56.000")

            send(vm, YourOrderEvent.SuggestionAdd("add:s1"))
            assertThat(
                vm.ui.suggestions
                    ?.cards
                    ?.map { it.quantity },
            ).containsExactly(0, 2, 0).inOrder()

            send(vm, YourOrderEvent.SuggestionRemove("add:s1"), YourOrderEvent.SuggestionRemove("add:s1"))
            advanceTimeBy(500)
            runCurrent()
            assertThat(
                vm.ui.suggestions
                    ?.cards
                    ?.map { it.quantity },
            ).containsExactly(0, 0, 0).inOrder()
            assertThat(vm.ui.totals.subtotal).isEqualTo("Rp 38.000")
            assertThat(session.state.value.cart.lines).hasSize(1)
        }

    @Test
    fun `the strip is hidden behind every blocker and the empty tray (design)`() =
        runTest(main.dispatcher) {
            settings.value = YourOrderTestStates.SUGGESTING
            startMain()
            val vm = viewModel()
            assertThat(vm.ui.suggestions).isNotNull()

            connectivity.set(Connectivity.OFFLINE)
            runCurrent()
            assertThat(vm.ui.suggestions).isNull()
            connectivity.set(Connectivity.ONLINE)
            runCurrent()
            assertThat(vm.ui.suggestions).isNotNull()

            catalogs.set(YourOrderTestStates.catalogWithoutMethods)
            runCurrent()
            assertThat(vm.ui.suggestions).isNull()
        }

    /** A Budget Plan of Crispy Chicken and the Classic Cheeseburger (Rp 84.000 with tax and rounding). */
    private fun startPlan(budget: Long): OrderSession =
        sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42").apply {
            startBuilding()
            val additions =
                listOf(defaults("c1"), defaults("b1")).map {
                    CartAddition(it.toCartItem(), 1, it.unitPrice, it.choices())
                }
            addPlan(additions, Rupiah(budget), adults = 2, children = 0)
        }

    @Test
    fun `a Budget Plan order only gets what fits the budget, and the added card stays (TC-197, AC5)`() =
        runTest(main.dispatcher) {
            settings.value = YourOrderTestStates.SUGGESTING
            startPlan(100_000)
            val vm = viewModel()

            // Rp 16.000 of the Rp 100.000 budget are left: the water and the Pandan soft serve fit, nothing else does.
            assertThat(vm.keys()).containsExactly("add:d4", "add:e3").inOrder()
            assertThat(vm.ui.suggestions?.budgetLimited).isTrue()

            send(vm, YourOrderEvent.SuggestionAdd("add:e3"))

            // Rp 97.000 of Rp 100.000: the water no longer fits, so it leaves; the added card stays for its stepper.
            assertThat(vm.ui.totals.total).isEqualTo("Rp 97.000")
            assertThat(vm.keys()).containsExactly("add:e3")
            assertThat(
                vm.ui.suggestions
                    ?.cards
                    ?.map { it.quantity },
            ).containsExactly(1)
        }

    @Test
    fun `a plan with Rp 400 left shows no strip and never blocks Pay (TC-197, AC4, AC5)`() =
        runTest(main.dispatcher) {
            settings.value = YourOrderTestStates.SUGGESTING
            startPlan(84_400)
            val vm = viewModel()

            assertThat(vm.ui.totals.total).isEqualTo("Rp 84.000")
            assertThat(vm.ui.suggestions).isNull()
            assertThat(vm.ui.pay).isEqualTo(PayUi.Ready("Rp 84.000"))
        }

    @Test
    fun `Make it a meal switches the option on for one unit at the option price and back (TC-205)`() =
        runTest(main.dispatcher) {
            settings.value = YourOrderTestStates.SUGGESTING
            val session = startBurgerOnly()
            val vm = viewModel()

            assertThat(vm.keys()).containsExactly("meal:b2", "add:e1").inOrder()
            val card =
                vm.ui.suggestions!!
                    .cards
                    .first()
            assertThat(card.kind).isEqualTo(SuggestionKind.UPGRADE)
            assertThat(card.note).isEqualTo("Adds fries & a drink")
            assertThat(card.price).isEqualTo("Rp 18.000")
            assertThat(card.pricePlus).isTrue()
            val before = vm.ui.totals.subtotal

            send(vm, YourOrderEvent.SuggestionAdd("meal:b2"))

            assertThat(
                session.state.value.cart
                    .line("l1")
                    ?.unitPrice,
            ).isEqualTo(Rupiah(70_000))
            assertThat(vm.ui.totals.subtotal).isNotEqualTo(before)
            assertThat(
                vm
                    .line("l1")
                    .choices
                    .map { it.toString() }
                    .any { "Make it a meal" in it },
            ).isTrue()
            // The card stays with its stepper: 1 meal, and no unit is left to upgrade.
            assertThat(vm.keys()).containsExactly("meal:b2", "add:e1").inOrder()
            assertThat(
                vm.ui.suggestions!!
                    .cards
                    .first()
                    .quantity,
            ).isEqualTo(1)
            assertThat(
                vm.ui.suggestions!!
                    .cards
                    .first()
                    .canAddMore,
            ).isFalse()
            // A meal covers the drink and side, and Pay is never blocked.
            assertThat(vm.ui.pay).isInstanceOf(PayUi.Ready::class.java)

            send(vm, YourOrderEvent.SuggestionRemove("meal:b2"))
            assertThat(
                session.state.value.cart
                    .line("l1")
                    ?.unitPrice,
            ).isEqualTo(Rupiah(52_000))
            assertThat(
                vm.ui.suggestions!!
                    .cards
                    .first()
                    .quantity,
            ).isEqualTo(0)
        }

    @Test
    fun `without the API marker there is no meal card (A-48, OQ-17)`() =
        runTest(main.dispatcher) {
            settings.value = YourOrderTestStates.SUGGESTING
            catalogs.set(SampleData.catalogWithoutPurposes)
            startBurgerOnly()
            val vm = viewModel()

            assertThat(vm.keys()).containsExactly("add:e1")
            send(vm, YourOrderEvent.SuggestionAdd("meal:b2"))
            assertThat(
                vm.ui.lines
                    .first()
                    .total,
            ).isEqualTo("Rp 52.000")
        }
}
