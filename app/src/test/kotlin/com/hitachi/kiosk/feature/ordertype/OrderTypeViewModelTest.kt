package com.hitachi.kiosk.feature.ordertype

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** SCR-05 behaviour (FR-007; TC-025–TC-028, TC-174). */
class OrderTypeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val sessions = DefaultOrderSessionHolder()

    private fun viewModel() = OrderTypeViewModel(catalog, sessions)

    private fun offering(vararg types: OrderType) =
        catalog.set(SampleData.catalog.copy(config = SampleData.config.copy(orderTypes = types.toList())))

    private fun TestScope.effectsOf(vm: OrderTypeViewModel): List<OrderTypeEffect> {
        val effects = mutableListOf<OrderTypeEffect>()
        backgroundScope.launch { vm.effect.toList(effects) }
        return effects
    }

    @Test
    fun `both types show as cards, Dine in first, without a note`() =
        runTest(main.dispatcher) {
            offering(OrderType.TAKEAWAY, OrderType.DINE_IN)
            val vm = viewModel()
            runCurrent()

            assertThat(vm.state.value)
                .isEqualTo(
                    OrderTypeUiState(
                        brandName = "Urban Grill",
                        options = listOf(OrderType.DINE_IN, OrderType.TAKEAWAY),
                    ),
                )
        }

    @Test
    fun `with Takeaway off only Dine in shows, with the Takeaway note`() =
        runTest(main.dispatcher) {
            offering(OrderType.DINE_IN)
            val vm = viewModel()
            runCurrent()

            assertThat(vm.state.value.options).containsExactly(OrderType.DINE_IN)
            assertThat(vm.state.value.unavailable).isEqualTo(OrderType.TAKEAWAY)
        }

    @Test
    fun `with Dine in off only Takeaway shows, with the Dine in note`() =
        runTest(main.dispatcher) {
            offering(OrderType.TAKEAWAY)
            val vm = viewModel()
            runCurrent()

            assertThat(vm.state.value.options).containsExactly(OrderType.TAKEAWAY)
            assertThat(vm.state.value.unavailable).isEqualTo(OrderType.DINE_IN)
        }

    @Test
    fun `a pick starts the order on the cached catalog and goes to Menu after 950 ms`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            runCurrent()

            vm.onEvent(OrderTypeEvent.Pick(OrderType.DINE_IN))
            runCurrent()

            assertThat(vm.state.value.picked).isEqualTo(OrderType.DINE_IN)
            val order =
                sessions.current.value!!
                    .state.value
            assertThat(order.orderType).isEqualTo(OrderType.DINE_IN)
            assertThat(order.menuVersion).isEqualTo(SampleData.menu.menuVersion)
            assertThat(order.configVersion).isEqualTo(SampleData.config.configVersion)

            advanceTimeBy(949)
            runCurrent()
            assertThat(effects).isEmpty()
            advanceTimeBy(1)
            runCurrent()
            assertThat(effects).containsExactly(OrderTypeEffect.NavigateToMenu)
        }

    @Test
    fun `a second pick is ignored`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            runCurrent()

            vm.onEvent(OrderTypeEvent.Pick(OrderType.DINE_IN))
            advanceTimeBy(300)
            vm.onEvent(OrderTypeEvent.Pick(OrderType.TAKEAWAY))
            advanceTimeBy(2_000)
            runCurrent()

            assertThat(vm.state.value.picked).isEqualTo(OrderType.DINE_IN)
            assertThat(
                sessions.current.value!!
                    .state.value.orderType,
            ).isEqualTo(OrderType.DINE_IN)
            assertThat(effects).containsExactly(OrderTypeEffect.NavigateToMenu)
        }

    @Test
    fun `a type that is not offered cannot be picked`() =
        runTest(main.dispatcher) {
            offering(OrderType.DINE_IN)
            val vm = viewModel()
            runCurrent()

            vm.onEvent(OrderTypeEvent.Pick(OrderType.TAKEAWAY))
            runCurrent()

            assertThat(vm.state.value.picked).isNull()
            assertThat(sessions.current.value).isNull()
        }

    @Test
    fun `coming back from Menu Change shows fresh cards, and the new type keeps the cart`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            runCurrent()
            vm.onEvent(OrderTypeEvent.Pick(OrderType.DINE_IN))
            advanceTimeBy(1_000)
            val session = sessions.current.value!!
            val burger = ProductCustomization.defaults(SampleData.menu.product("s1")!!, SampleData.menu)
            session.add(burger.toCartItem(), quantity = 2, unitPrice = burger.unitPrice, choices = burger.choices())

            vm.onEvent(OrderTypeEvent.Shown)
            assertThat(vm.state.value.picked).isNull()
            vm.onEvent(OrderTypeEvent.Pick(OrderType.TAKEAWAY))
            advanceTimeBy(1_000)
            runCurrent()

            assertThat(sessions.current.value).isSameInstanceAs(session)
            assertThat(session.state.value.orderType).isEqualTo(OrderType.TAKEAWAY)
            assertThat(session.state.value.cart.itemCount).isEqualTo(2)
            assertThat(effects).containsExactly(OrderTypeEffect.NavigateToMenu, OrderTypeEffect.NavigateToMenu)
        }

    @Test
    fun `without a cached catalog there are no cards and a pick does nothing`() =
        runTest(main.dispatcher) {
            catalog.set(null)
            val vm = viewModel()
            runCurrent()

            vm.onEvent(OrderTypeEvent.Pick(OrderType.DINE_IN))
            runCurrent()

            assertThat(vm.state.value.options).isEmpty()
            assertThat(sessions.current.value).isNull()
        }
}
