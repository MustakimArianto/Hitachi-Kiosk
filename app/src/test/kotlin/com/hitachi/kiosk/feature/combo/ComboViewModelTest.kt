package com.hitachi.kiosk.feature.combo

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.ComboCustomization
import com.hitachi.kiosk.core.domain.cart.ComboMissing
import com.hitachi.kiosk.core.domain.cart.LineChoice
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.ComboSelection
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SCR-07 behaviour (FR-011; TC-046–TC-049, TC-176 c/d). Robolectric only because `toRoute` reads the route through
 * an `android.os.Bundle`.
 */
@RunWith(RobolectricTestRunner::class)
class ComboViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalogs = FakeCatalogRepository()
    private val orders = DefaultOrderSessionHolder()
    private val session = orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")

    private fun TestScope.viewModel(comboId: String? = null): ComboViewModel {
        val args = if (comboId == null) emptyMap() else mapOf("comboId" to comboId)
        val vm = ComboViewModel(SavedStateHandle(args), catalogs, orders)
        runCurrent()
        return vm
    }

    private fun ComboViewModel.send(vararg events: ComboEvent) = events.forEach(::onEvent)

    @Test
    fun `TC-046 Combo opens on the defaults`() =
        runTest(main.dispatcher) {
            val state = viewModel().state.value

            assertThat(state.loaded).isTrue()
            assertThat(state.combos.map { it.name })
                .containsExactly("Cheeseburger Meal", "Crispy Chicken Meal", "Rice Bowl Combo", "Feast for Two")
                .inOrder()
            assertThat(state.combos.single { it.selected }.id).isEqualTo("combo_burger")
            assertThat(state.combos.first().saving).isEqualTo("Rp 14.000")
            assertThat(state.combos.first().price).isEqualTo("Rp 56.000")
            assertThat(
                state.sides.visible.map { it.name },
            ).containsExactly("Fries", "Cheese fries", "Nuggets").inOrder()
            assertThat(
                state.sides.visible
                    .single { it.selected }
                    .name,
            ).isEqualTo("Fries")
            assertThat(state.sides.hiddenCount).isEqualTo(1)
            assertThat(state.sides.tone).isEqualTo("#E6DCD3")
            assertThat(
                state.drinks.visible.map { it.name },
            ).containsExactly("Cola", "Lychee tea", "Iced latte").inOrder()
            assertThat(
                state.drinks.visible
                    .single { it.selected }
                    .name,
            ).isEqualTo("Cola")
            assertThat(state.drinks.hiddenCount).isEqualTo(1)
            assertThat(state.drinks.tone).isEqualTo("#DFE3DC")
            assertThat(
                state.sides.visible.map { it.upcharge },
            ).containsExactly(null, "+Rp 8.000", "+Rp 4.000").inOrder()
            assertThat(state.large).isFalse()
            assertThat(state.largeUpcharge).isEqualTo("+Rp 6.000")
            assertThat(state.total).isEqualTo("Rp 56.000")
            assertThat(state.summary).isEqualTo(ComboSummaryUi("Cheeseburger Meal", "Fries", "Cola", false, null))
            assertThat(state.cta).isEqualTo(ComboCta.ADD)
        }

    @Test
    fun `the route's combo is preselected`() =
        runTest(main.dispatcher) {
            val state = viewModel("combo_bowl").state.value

            assertThat(state.combos.single { it.selected }.id).isEqualTo("combo_bowl")
            assertThat(state.total).isEqualTo("Rp 52.000")
        }

    @Test
    fun `TC-047 Cheese fries, Iced latte and Large make Rp 74 000`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            vm.send(
                ComboEvent.SelectSide("cs_cheese"),
                ComboEvent.SelectDrink("cd_coffee"),
                ComboEvent.SelectLarge(true),
            )
            runCurrent()

            val state = vm.state.value
            assertThat(state.total).isEqualTo("Rp 74.000")
            assertThat(state.summary)
                .isEqualTo(ComboSummaryUi("Cheeseburger Meal", "Cheese fries", "Iced latte", true, null))
        }

    @Test
    fun `TC-048 Feast for Two multiplies the upcharges and their labels`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            vm.send(
                ComboEvent.SelectCombo("combo_feast"),
                ComboEvent.SelectSide("cs_nuggets"),
                ComboEvent.SelectLarge(true),
            )
            runCurrent()

            val state = vm.state.value
            assertThat(state.total).isEqualTo("Rp 129.000")
            assertThat(
                state.sides.visible
                    .single { it.id == "cs_nuggets" }
                    .upcharge,
            ).isEqualTo("+Rp 8.000")
            assertThat(state.largeUpcharge).isEqualTo("+Rp 12.000")
            assertThat(state.combos.single { it.selected }.id).isEqualTo("combo_feast")
        }

    @Test
    fun `More sides reveals the rest and a selected hidden option stays visible after Show fewer`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            vm.onEvent(ComboEvent.ToggleMoreSides)
            runCurrent()
            assertThat(
                vm.state.value.sides.visible
                    .map { it.id },
            ).containsExactly("cs_fries", "cs_cheese", "cs_nuggets", "cs_corn")
                .inOrder()

            vm.send(ComboEvent.SelectSide("cs_corn"), ComboEvent.ToggleMoreSides)
            runCurrent()

            val sides = vm.state.value.sides
            assertThat(sides.expanded).isFalse()
            assertThat(sides.visible.map { it.id }).containsExactly("cs_fries", "cs_cheese", "cs_nuggets", "cs_corn")
            assertThat(sides.visible.last().selected).isTrue()
        }

    @Test
    fun `TC-176 d a sold-out option is greyed and cannot be picked`() =
        runTest(main.dispatcher) {
            catalogs.set(Catalog(SampleData.config, ComboTestStates.soldOutMenu))
            val vm = viewModel()

            vm.send(ComboEvent.SelectSide("cs_cheese"), ComboEvent.SelectDrink("cd_coffee"))
            runCurrent()

            val state = vm.state.value
            assertThat(
                state.sides.visible
                    .single { it.id == "cs_cheese" }
                    .available,
            ).isFalse()
            assertThat(
                state.drinks.visible
                    .single { it.id == "cd_coffee" }
                    .available,
            ).isFalse()
            assertThat(state.summary?.side).isEqualTo("Fries")
            assertThat(state.summary?.drink).isEqualTo("Cola")
            assertThat(state.total).isEqualTo("Rp 56.000")
        }

    @Test
    fun `TC-176 c a missing side or drink disables the CTA with the right words`() {
        val defaults = requireNotNull(ComboCustomization.defaults(SampleData.menu))

        val both = comboUiState(SampleData.menu, defaults.copy(sideId = null, drinkId = null))
        val side = comboUiState(SampleData.menu, defaults.copy(sideId = null))
        val drink = comboUiState(SampleData.menu, defaults.copy(drinkId = null))

        assertThat(both.cta).isEqualTo(ComboCta.PICK_SIDE_AND_DRINK)
        assertThat(both.summary?.missing).isEqualTo(ComboMissing.SIDE_AND_DRINK)
        assertThat(both.sides.missing).isTrue()
        assertThat(both.drinks.missing).isTrue()
        assertThat(both.total).isEqualTo("Rp 56.000")
        assertThat(side.cta).isEqualTo(ComboCta.CHOOSE_SIDE)
        assertThat(side.drinks.missing).isFalse()
        assertThat(drink.cta).isEqualTo(ComboCta.CHOOSE_DRINK)
    }

    @Test
    fun `TC-049 Add combo adds one line, a second tap while Added adds nothing, and Added ends after 1 8 s`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.send(
                ComboEvent.SelectSide("cs_cheese"),
                ComboEvent.SelectDrink("cd_coffee"),
                ComboEvent.SelectLarge(true),
            )

            vm.send(ComboEvent.AddCombo, ComboEvent.AddCombo)
            runCurrent()

            val line =
                session.state.value.cart.lines
                    .single()
            assertThat(line.quantity).isEqualTo(1)
            assertThat(line.unitPrice).isEqualTo(Rupiah(74_000))
            assertThat(line.item)
                .isEqualTo(
                    CartItem.ComboItem("combo_burger", ComboSelection("cs_cheese", "cd_coffee", ComboSize.LARGE)),
                )
            assertThat(line.choices.last()).isEqualTo(LineChoice.Size(ComboSize.LARGE))
            assertThat(vm.state.value.cta).isEqualTo(ComboCta.ADDED)
            assertThat(vm.state.value.cartCount).isEqualTo(1)
            assertThat(vm.state.value.cartBumps).isEqualTo(1)

            advanceTimeBy(KioskMotion.Combo.ADDED_STATE_MILLIS.toLong() + 1)
            assertThat(vm.state.value.cta).isEqualTo(ComboCta.ADD)

            vm.onEvent(ComboEvent.AddCombo)
            runCurrent()
            assertThat(
                session.state.value.cart.lines
                    .single()
                    .quantity,
            ).isEqualTo(2)
        }

    @Test
    fun `changing an option ends the Added state, picking the same one does not`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.onEvent(ComboEvent.AddCombo)
            vm.onEvent(ComboEvent.SelectSide("cs_fries"))
            runCurrent()
            assertThat(vm.state.value.cta).isEqualTo(ComboCta.ADDED)

            vm.onEvent(ComboEvent.SelectDrink("cd_tea"))
            runCurrent()

            assertThat(vm.state.value.cta).isEqualTo(ComboCta.ADD)
            assertThat(vm.state.value.total).isEqualTo("Rp 58.000")
        }

    @Test
    fun `the pills navigate`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            vm.onEvent(ComboEvent.BackToMenu)
            assertThat(vm.effect.first()).isEqualTo(ComboEffect.NavigateToMenu)
            vm.onEvent(ComboEvent.ViewOrder)
            assertThat(vm.effect.first()).isEqualTo(ComboEffect.NavigateToYourOrder)
        }
}
