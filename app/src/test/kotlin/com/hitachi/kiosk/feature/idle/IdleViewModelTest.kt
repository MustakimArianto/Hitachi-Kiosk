package com.hitachi.kiosk.feature.idle

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** SCR-04 behaviour (FR-006; TC-021, TC-023, TC-024). */
class IdleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val sessions = DefaultOrderSessionHolder()

    private fun viewModel() = IdleViewModel(catalog, sessions)

    private fun TestScope.effectsOf(vm: IdleViewModel): List<IdleEffect> {
        val effects = mutableListOf<IdleEffect>()
        backgroundScope.launch { vm.effect.toList(effects) }
        return effects
    }

    @Test
    fun `entering Idle ends the order and its cart`() =
        runTest(main.dispatcher) {
            sessions.chooseOrderType(OrderType.TAKEAWAY, "menu-118", "cfg-42")
            val vm = viewModel()
            runCurrent()

            vm.onEvent(IdleEvent.Shown)

            assertThat(sessions.current.value).isNull()
        }

    @Test
    fun `the brand and one slide per promo image come from the cached config`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            runCurrent()

            assertThat(vm.state.value.brandName).isEqualTo("Urban Grill")
            assertThat(
                vm.state.value.slides
                    .map { it.imageUrl },
            ).containsExactlyElementsIn(SampleData.config.idlePromoImageUrls)
                .inOrder()
            assertThat(vm.state.value.showIssueDot).isFalse()
        }

    @Test
    fun `no promo images is one tone slide`() =
        runTest(main.dispatcher) {
            catalog.set(SampleData.catalog.copy(config = SampleData.config.copy(idlePromoImageUrls = emptyList())))
            val vm = viewModel()
            runCurrent()

            assertThat(vm.state.value.slides).containsExactly(IdleSlideUi(imageUrl = null))
        }

    @Test
    fun `a missing promo image keeps its slide as a tone placeholder`() =
        runTest(main.dispatcher) {
            val urls = listOf("https://cdn.example/a.jpg", null)
            catalog.set(SampleData.catalog.copy(config = SampleData.config.copy(idlePromoImageUrls = urls)))
            val vm = viewModel()
            runCurrent()

            assertThat(vm.state.value.slides).containsExactly(IdleSlideUi(urls[0]), IdleSlideUi(null)).inOrder()
        }

    @Test
    fun `without a cached catalog the chip is hidden and one tone slide shows`() =
        runTest(main.dispatcher) {
            catalog.set(null)
            val vm = viewModel()
            runCurrent()

            assertThat(vm.state.value).isEqualTo(IdleUiState())
        }

    @Test
    fun `a tap starts one order even when tapped twice`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            vm.onEvent(IdleEvent.Shown)

            vm.onEvent(IdleEvent.TapToOrder)
            vm.onEvent(IdleEvent.TapToOrder)
            vm.onEvent(IdleEvent.StaffHold)
            runCurrent()

            assertThat(effects).containsExactly(IdleEffect.StartOrder)
        }

    @Test
    fun `the staff hold opens staff settings, and Idle works again when it comes back`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val effects = effectsOf(vm)
            vm.onEvent(IdleEvent.Shown)

            vm.onEvent(IdleEvent.StaffHold)
            vm.onEvent(IdleEvent.Shown)
            vm.onEvent(IdleEvent.TapToOrder)
            runCurrent()

            assertThat(effects).containsExactly(IdleEffect.OpenStaffSettings, IdleEffect.StartOrder).inOrder()
        }
}
