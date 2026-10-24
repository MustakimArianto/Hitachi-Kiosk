package com.hitachi.kiosk.feature.upsell

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fake.FakeSettingsRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import com.hitachi.kiosk.feature.upsell.UpsellTestStates.ALL_ADD_ONS
import com.hitachi.kiosk.feature.upsell.UpsellTestStates.addProduct
import com.hitachi.kiosk.feature.upsell.UpsellTestStates.addSampleCart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** SCR-09 behaviour (FR-014, DEV-039; TC-063, TC-064, TC-172). */
@OptIn(ExperimentalCoroutinesApi::class)
class UpsellViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalogs = FakeCatalogRepository()
    private val sessions = DefaultOrderSessionHolder()
    private val settings = FakeSettingsRepository(KioskSettings())

    /** The design's sample cart on the Menu, about to tap "View order". */
    private fun startOrder(fill: OrderSession.() -> Unit = { addSampleCart() }): OrderSession =
        sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42").apply {
            startBuilding()
            fill()
        }

    private fun TestScope.viewModel(): UpsellViewModel {
        val vm = UpsellViewModel(catalogs, sessions, settings)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private fun TestScope.effectsOf(vm: UpsellViewModel): List<UpsellEffect> {
        val effects = mutableListOf<UpsellEffect>()
        backgroundScope.launch { vm.effect.toList(effects) }
        return effects
    }

    private val UpsellViewModel.ui get() = state.value

    @Test
    fun `opening marks the upsell as shown and moves the order to Reviewing (FR-014 AC2)`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            assertThat(session.state.value.upsellShown).isFalse()

            viewModel()

            assertThat(session.state.value.upsellShown).isTrue()
            assertThat(session.state.value.phase).isEqualTo(OrderPhase.REVIEWING)
        }

    @Test
    fun `the strip and the grid follow the design's sample cart`() =
        runTest(main.dispatcher) {
            startOrder()
            val ui = viewModel().ui

            assertThat(ui.loaded).isTrue()
            assertThat(ui.noSuggestions).isFalse()
            assertThat(ui.orderType).isEqualTo(OrderType.DINE_IN)
            assertThat(ui.itemCount).isEqualTo(5)
            assertThat(ui.subtotal).isEqualTo("Rp 167.000")
            // Subtotal 167.000 − promo 10.000 + PB1 15.700 (FR-013).
            assertThat(ui.total).isEqualTo("Rp 172.700")
            assertThat(ui.thumbnails.map { it.tone }).containsExactly("#DFE3DC", "#E6DCD3", "#E3D4C2").inOrder()
            val cheeseFries = ui.suggestions.first()
            assertThat(cheeseFries)
                .isEqualTo(
                    UpsellSuggestionUi(
                        id = "s2",
                        name = "Cheese Fries",
                        role = ProductRole.SIDE,
                        description = "Melted cheddar",
                        kcal = 450,
                        price = "Rp 26.000",
                        imageUrl = null,
                        tone = "#E6DCD3",
                    ),
                )
        }

    @Test
    fun `suggestions leave out what the cart already holds (TC-064)`() =
        runTest(main.dispatcher) {
            startOrder()
            val ui = viewModel().ui

            // French Fries (s1) is in the sample cart, so the sides and the first dessert fill the four places.
            assertThat(ui.suggestions.map { it.id }).containsExactly("s2", "s3", "s4", "e1").inOrder()
        }

    @Test
    fun `quick add puts one unit with the default options in the cart and updates the totals (FR-014 AC3, TC-064)`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()

            vm.onEvent(UpsellEvent.QuickAdd("e1"))
            runCurrent()

            val line =
                session.state.value.cart.lines
                    .last()
            assertThat(line.item).isEqualTo(CartItem.ProductItem("e1", emptyList()))
            assertThat(line.quantity).isEqualTo(1)
            assertThat(line.unitPrice).isEqualTo(Rupiah(15_000))
            val ui = vm.ui
            assertThat(ui.itemCount).isEqualTo(6)
            assertThat(ui.subtotal).isEqualTo("Rp 182.000")
            assertThat(ui.total).isEqualTo("Rp 189.200")
            assertThat(ui.addSerial).isEqualTo(1)
            assertThat(ui.thumbnails.first().lineId).isEqualTo(line.lineId)
        }

    @Test
    fun `quick add with defaults picks the default size of a side that has one`() =
        runTest(main.dispatcher) {
            val session = startOrder { addProduct("c1") }
            val vm = viewModel()
            assertThat(vm.ui.suggestions.map { it.id }).contains("s1")

            vm.onEvent(UpsellEvent.QuickAdd("s1"))
            runCurrent()

            val line =
                session.state.value.cart.lines
                    .last()
            assertThat(line.unitPrice).isEqualTo(Rupiah(18_000))
            val item = line.item as CartItem.ProductItem
            assertThat(item.selections.map { it.optionId }).containsExactly("opt_regular")
        }

    @Test
    fun `the total uses the device rounding step (FR-025)`() =
        runTest(main.dispatcher) {
            settings.update { it.copy(roundingStep = 500) }
            startOrder()
            val vm = viewModel()

            vm.onEvent(UpsellEvent.QuickAdd("e1"))
            runCurrent()

            assertThat(vm.ui.total).isEqualTo("Rp 189.500")
        }

    @Test
    fun `an added card keeps its place, shows the check for 1,4 s and then N added`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()

            vm.onEvent(UpsellEvent.QuickAdd("s2"))
            runCurrent()

            assertThat(vm.ui.suggestions.map { it.id }).containsExactly("s2", "s3", "s4", "e1").inOrder()
            val added = vm.ui.suggestions.first()
            assertThat(added.addedQuantity).isEqualTo(1)
            assertThat(added.inCart).isTrue()
            assertThat(added.justAdded).isTrue()

            advanceTimeBy(1_399)
            runCurrent()
            assertThat(
                vm.ui.suggestions
                    .first()
                    .justAdded,
            ).isTrue()
            advanceTimeBy(2)
            runCurrent()
            assertThat(
                vm.ui.suggestions
                    .first()
                    .justAdded,
            ).isFalse()

            vm.onEvent(UpsellEvent.QuickAdd("s2"))
            runCurrent()
            assertThat(
                vm.ui.suggestions
                    .first()
                    .addedQuantity,
            ).isEqualTo(2)
            assertThat(vm.ui.addSerial).isEqualTo(2)
        }

    @Test
    fun `quick add ignores a product that is not suggested`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()
            val before = session.state.value.cart

            vm.onEvent(UpsellEvent.QuickAdd("b3"))
            vm.onEvent(UpsellEvent.QuickAdd("missing"))
            runCurrent()

            assertThat(session.state.value.cart).isEqualTo(before)
            assertThat(vm.ui.addSerial).isEqualTo(0)
        }

    @Test
    fun `with every add-on in the cart it shows Nothing more to suggest (TC-172)`() =
        runTest(main.dispatcher) {
            startOrder {
                addSampleCart()
                ALL_ADD_ONS.forEach { addProduct(it) }
            }
            val ui = viewModel().ui

            assertThat(ui.loaded).isTrue()
            assertThat(ui.suggestions).isEmpty()
            assertThat(ui.noSuggestions).isTrue()
        }

    @Test
    fun `nothing shows before the catalog is read, then the suggestions use the cart at opening`() =
        runTest(main.dispatcher) {
            catalogs.set(null)
            val session = startOrder()
            val vm = viewModel()
            assertThat(vm.ui.loaded).isFalse()
            assertThat(vm.ui.noSuggestions).isFalse()

            catalogs.set(SampleData.catalog)
            runCurrent()

            assertThat(vm.ui.loaded).isTrue()
            assertThat(vm.ui.suggestions.map { it.id }).containsExactly("s2", "s3", "s4", "e1").inOrder()
            assertThat(session.state.value.upsellShown).isTrue()
        }

    @Test
    fun `No thanks and Continue open Your order, Menu goes back`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            vm.onEvent(UpsellEvent.NoThanks)
            vm.onEvent(UpsellEvent.Continue)
            vm.onEvent(UpsellEvent.BackToMenu)
            runCurrent()

            assertThat(effects)
                .containsExactly(UpsellEffect.OpenYourOrder, UpsellEffect.OpenYourOrder, UpsellEffect.BackToMenu)
                .inOrder()
        }

    @Test
    fun `without an order it shows the empty cart and adds nothing`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            vm.onEvent(UpsellEvent.QuickAdd("s2"))
            runCurrent()

            assertThat(vm.ui.itemCount).isEqualTo(0)
            assertThat(vm.ui.addSerial).isEqualTo(0)
        }
}
