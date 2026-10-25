package com.hitachi.kiosk.feature.menu

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.model.BadgeType
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.time.DayPart
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fake.TestClock
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import kotlin.time.Instant

/** SCR-06 behaviour (FR-009, DEV-027, DEV-028; TC-033, TC-034, TC-038, TC-039, TC-175). */
@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalogs = FakeCatalogRepository()
    private val sessions = DefaultOrderSessionHolder()
    private val connectivity = FakeConnectivityMonitor()

    /** 07:15 UTC is 14:15 in Jakarta: "Good afternoon", as in the design. */
    private val clock = TestClock()
    private val jakarta = ZoneId.of("Asia/Jakarta")

    private fun startOrder() = sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")

    private fun TestScope.viewModel(): MenuViewModel {
        val vm = MenuViewModel(catalogs, sessions, connectivity, clock, jakarta)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private fun TestScope.effectsOf(vm: MenuViewModel): List<MenuEffect> {
        val effects = mutableListOf<MenuEffect>()
        backgroundScope.launch { vm.effect.toList(effects) }
        return effects
    }

    private val MenuViewModel.ui get() = state.value

    private fun MenuViewModel.send(vararg events: MenuEvent) = events.forEach(::onEvent)

    @Test
    fun `the default view is Popular with the budget row and four featured items (TC-033)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()

            val ui = vm.ui
            assertThat(ui.loaded).isTrue()
            assertThat(ui.brandName).isEqualTo("Urban Grill")
            assertThat(ui.orderType).isEqualTo(OrderType.DINE_IN)
            assertThat(ui.selection).isEqualTo(MenuSelection.Popular)
            assertThat(ui.showBudgetPlan).isTrue()
            assertThat(ui.sectionTitle).isNull()
            assertThat(ui.products.map { it.id }).containsExactly("b2", "c1", "r1", "d1").inOrder()
            assertThat(ui.pills)
                .containsExactly(
                    MenuPillUi.Popular(active = true),
                    MenuPillUi.Category("cat_burgers", "Burgers", active = false),
                    MenuPillUi.Category("cat_chicken", "Chicken", active = false),
                    MenuPillUi.Category("cat_bowls", "Rice bowls", active = false),
                    MenuPillUi.Meals("Meals"),
                    MenuPillUi.Category("cat_sides", "Sides", active = false),
                    MenuPillUi.Category("cat_drinks", "Drinks", active = false),
                    MenuPillUi.Category("cat_desserts", "Desserts", active = false),
                ).inOrder()
            assertThat(ui.dock).isEqualTo(CartDockUi(itemCount = 0, total = "Rp 0", thumbnails = emptyList()))
            assertThat(ui.offline).isFalse()
        }

    @Test
    fun `opening the Menu moves the order to Building`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            viewModel()

            assertThat(session.state.value.phase).isEqualTo(OrderPhase.BUILDING)
        }

    @Test
    fun `cards show name, price and the badge from the menu (TC-037)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            vm.send(MenuEvent.Select(MenuSelection.Category("cat_burgers")))
            runCurrent()

            val b2 = vm.ui.products.first { it.id == "b2" }
            assertThat(b2.name).isEqualTo("Smoky Double Burger")
            assertThat(b2.price).isEqualTo("Rp 52.000")
            assertThat(b2.tone).isEqualTo("#E9DED0")
            assertThat(b2.badge).isEqualTo(MenuBadgeUi(BadgeType.NEW, "New"))
            assertThat(b2.badge?.isAccent).isTrue()
            val b3 = vm.ui.products.first { it.id == "b3" }
            assertThat(b3.badge).isEqualTo(MenuBadgeUi(BadgeType.SPICY, "Spicy"))
            assertThat(b3.badge?.isAccent).isFalse()
        }

    @Test
    fun `a category switch is an immediate in-memory filter (TC-034)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()

            vm.send(MenuEvent.Select(MenuSelection.Category("cat_chicken")))
            runCurrent()

            assertThat(currentTime).isEqualTo(0)
            val ui = vm.ui
            assertThat(ui.products.map { it.id }).containsExactly("c1", "c2", "c3", "c4").inOrder()
            assertThat(ui.sectionTitle).isEqualTo("Chicken")
            // The slim row is Popular only; the header pill is on every tab (D-35, TC-202).
            assertThat(ui.showBudgetPlan).isFalse()
            assertThat(ui.showPlanPill).isTrue()
            assertThat(ui.pills).contains(MenuPillUi.Category("cat_chicken", "Chicken", active = true))
            assertThat(ui.pills).contains(MenuPillUi.Popular(active = false))
        }

    @Test
    fun `quick add merges identical lines and the dock sums them (TC-038)`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()

            vm.send(MenuEvent.QuickAdd("c1"), MenuEvent.QuickAdd("c1"), MenuEvent.QuickAdd("d1"))
            runCurrent()

            val lines = session.state.value.cart.lines
            assertThat(lines).hasSize(2)
            assertThat((lines[0].item as CartItem.ProductItem).productId).isEqualTo("c1")
            assertThat(lines[0].quantity).isEqualTo(2)
            assertThat(vm.ui.dock.itemCount).isEqualTo(3)
            assertThat(vm.ui.dock.total).isEqualTo("Rp 92.000")
            // Most recent first.
            assertThat(
                vm.ui.dock.thumbnails
                    .map { it.tone },
            ).containsExactly("#DFE3DC", "#E3D4C2").inOrder()
            assertThat(vm.ui.addSerial).isEqualTo(3)
            assertThat(vm.ui.lastAddedProductId).isEqualTo("d1")
            assertThat(
                vm.ui.products
                    .single { it.justAdded }
                    .id,
            ).isEqualTo("d1")

            // The check shows for 1.4 s.
            advanceTimeBy(1_399)
            runCurrent()
            assertThat(vm.ui.products.any { it.justAdded }).isTrue()
            advanceTimeBy(1)
            runCurrent()
            assertThat(vm.ui.products.none { it.justAdded }).isTrue()
        }

    @Test
    fun `quick add uses the default options (FR-009)`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()
            vm.send(MenuEvent.QuickAdd("d1"))
            runCurrent()

            val line =
                session.state.value.cart.lines
                    .single()
            assertThat(line.unitPrice.value).isEqualTo(16_000)
            assertThat(line.choices).isEmpty()
        }

    @Test
    fun `products that need a choice show Choose and are never quick-added (TC-039)`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()

            val needsChoice = mutableSetOf<String>()
            listOf("cat_burgers", "cat_chicken", "cat_bowls", "cat_sides", "cat_drinks").forEach { id ->
                vm.send(MenuEvent.Select(MenuSelection.Category(id)))
                runCurrent()
                needsChoice +=
                    vm.ui.products
                        .filter { it.needsChoice }
                        .map { it.id }
            }
            assertThat(needsChoice).containsExactly("b2", "c4", "r1", "d2")

            vm.send(MenuEvent.QuickAdd("b2"), MenuEvent.QuickAdd("c4"))
            runCurrent()
            assertThat(
                session.state.value.cart
                    .isEmpty(),
            ).isTrue()
            assertThat(vm.ui.addSerial).isEqualTo(0)
        }

    @Test
    fun `the 21st add shows the toast for 2,4 s and dims the card (TC-175 d)`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()
            vm.send(MenuEvent.Select(MenuSelection.Category("cat_drinks")))
            repeat(20) { vm.send(MenuEvent.QuickAdd("d1")) }
            runCurrent()
            assertThat(vm.ui.showMaxToast).isFalse()
            assertThat(
                vm.ui.products
                    .first { it.id == "d1" }
                    .atMax,
            ).isTrue()
            assertThat(
                vm.ui.products
                    .first { it.id == "d3" }
                    .atMax,
            ).isFalse()

            vm.send(MenuEvent.QuickAdd("d1"))
            runCurrent()

            assertThat(vm.ui.showMaxToast).isTrue()
            assertThat(
                session.state.value.cart.lines
                    .single()
                    .quantity,
            ).isEqualTo(20)
            assertThat(vm.ui.addSerial).isEqualTo(20)
            advanceTimeBy(2_400)
            runCurrent()
            assertThat(vm.ui.showMaxToast).isFalse()
        }

    @Test
    fun `sold-out products are not shown (TC-175 c)`() =
        runTest(main.dispatcher) {
            catalogs.set(MenuTestStates.catalogWithSoldOut(MenuTestStates.UNAVAILABLE_IDS))
            startOrder()
            val vm = viewModel()

            assertThat(vm.ui.products.map { it.id }).containsExactly("b2", "c1", "r1").inOrder()
            vm.send(MenuEvent.Select(MenuSelection.Category("cat_burgers")))
            runCurrent()
            assertThat(vm.ui.products.map { it.id }).containsExactly("b1", "b2", "b4").inOrder()
        }

    @Test
    fun `a sold-out category is empty and See popular items returns to Popular (TC-175 a)`() =
        runTest(main.dispatcher) {
            catalogs.set(MenuTestStates.catalogWithSoldOut(MenuTestStates.DESSERT_IDS))
            startOrder()
            val vm = viewModel()

            vm.send(MenuEvent.Select(MenuSelection.Category("cat_desserts")))
            runCurrent()
            assertThat(vm.ui.products).isEmpty()
            assertThat(vm.ui.sectionTitle).isEqualTo("Desserts")

            vm.send(MenuEvent.Select(MenuSelection.Popular))
            runCurrent()
            assertThat(vm.ui.products).hasSize(4)
        }

    @Test
    fun `offline shows the banner and disables adds and View order (TC-175 b)`() =
        runTest(main.dispatcher) {
            val session = startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            connectivity.set(Connectivity.OFFLINE)
            runCurrent()
            assertThat(vm.ui.offline).isTrue()
            assertThat(vm.ui.addEnabled).isFalse()
            assertThat(vm.ui.viewOrderEnabled).isFalse()

            vm.send(MenuEvent.QuickAdd("c1"), MenuEvent.ViewOrder)
            runCurrent()
            assertThat(
                session.state.value.cart
                    .isEmpty(),
            ).isTrue()
            assertThat(effects).isEmpty()

            // Checking is not offline: the banner shows only when OFFLINE.
            connectivity.set(Connectivity.CHECKING)
            runCurrent()
            assertThat(vm.ui.offline).isFalse()
        }

    @Test
    fun `browsing still works offline`() =
        runTest(main.dispatcher) {
            startOrder()
            connectivity.set(Connectivity.OFFLINE)
            val vm = viewModel()
            val effects = effectsOf(vm)

            vm.send(MenuEvent.Select(MenuSelection.Category("cat_chicken")), MenuEvent.OpenProduct("c1"))
            runCurrent()

            assertThat(vm.ui.sectionTitle).isEqualTo("Chicken")
            assertThat(effects).containsExactly(MenuEffect.OpenProduct("c1"))
        }

    @Test
    fun `the budget row is hidden when the planner is disabled`() =
        runTest(main.dispatcher) {
            val config = SampleData.config
            catalogs.set(
                SampleData.catalog.copy(
                    config = config.copy(budgetPlanner = config.budgetPlanner.copy(enabled = false)),
                ),
            )
            startOrder()
            val vm = viewModel()

            assertThat(vm.ui.showBudgetPlan).isFalse()
            assertThat(vm.ui.showPlanPill).isFalse()
        }

    @Test
    fun `the Meals pill is hidden without an available combo`() =
        runTest(main.dispatcher) {
            val menu = SampleData.menu
            catalogs.set(
                SampleData.catalog.copy(menu = menu.copy(combos = menu.combos.map { it.copy(available = false) })),
            )
            startOrder()
            val vm = viewModel()

            assertThat(vm.ui.pills.filterIsInstance<MenuPillUi.Meals>()).isEmpty()
        }

    @Test
    fun `the greeting follows the local hour (A-07)`() =
        runTest(main.dispatcher) {
            startOrder()
            assertThat(viewModel().ui.dayPart).isEqualTo(DayPart.AFTERNOON)

            clock.setWallClock(Instant.parse("2026-10-22T02:00:00Z")) // 09:00 in Jakarta
            assertThat(viewModel().ui.dayPart).isEqualTo(DayPart.MORNING)

            clock.setWallClock(Instant.parse("2026-10-22T12:00:00Z")) // 19:00 in Jakarta
            assertThat(viewModel().ui.dayPart).isEqualTo(DayPart.EVENING)
        }

    @Test
    fun `every control sends its navigation effect`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            vm.send(
                MenuEvent.OpenProduct("b2"),
                MenuEvent.OpenCombos,
                MenuEvent.OpenBudgetPlan,
                MenuEvent.ChangeOrderType,
                MenuEvent.Cancel,
                MenuEvent.ViewOrder,
            )
            runCurrent()

            assertThat(effects)
                .containsExactly(
                    MenuEffect.OpenProduct("b2"),
                    MenuEffect.OpenCombos,
                    MenuEffect.OpenBudgetPlan,
                    MenuEffect.ChangeOrderType,
                    MenuEffect.Cancel,
                    MenuEffect.ViewOrder,
                ).inOrder()
        }

    @Test
    fun `View order opens Your order with an empty cart`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            vm.send(MenuEvent.ViewOrder)
            runCurrent()

            assertThat(effects).containsExactly(MenuEffect.ViewOrder)
        }

    @Test
    fun `the order type pill follows the session`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()

            sessions.chooseOrderType(OrderType.TAKEAWAY, "menu-118", "cfg-42")
            runCurrent()

            assertThat(vm.ui.orderType).isEqualTo(OrderType.TAKEAWAY)
        }

    @Test
    fun `without an order the menu still shows with an empty dock`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            vm.send(MenuEvent.QuickAdd("c1"))
            runCurrent()

            assertThat(vm.ui.products).hasSize(4)
            assertThat(vm.ui.dock.itemCount).isEqualTo(0)
            assertThat(vm.ui.addSerial).isEqualTo(0)
        }

    @Test
    fun `without a catalog the menu is empty, not broken`() =
        runTest(main.dispatcher) {
            catalogs.set(null)
            startOrder()
            val vm = viewModel()

            vm.send(MenuEvent.Select(MenuSelection.Category("cat_burgers")), MenuEvent.QuickAdd("c1"))
            runCurrent()

            assertThat(vm.ui.loaded).isTrue()
            assertThat(vm.ui.selection).isEqualTo(MenuSelection.Popular)
            assertThat(vm.ui.products).isEmpty()
            assertThat(vm.ui.pills).containsExactly(MenuPillUi.Popular(active = true))
        }

    @Test
    fun `View order always goes straight to Your order, the suggestion strip lives there (FR-014, D-33)`() =
        runTest(main.dispatcher) {
            startOrder()
            val vm = viewModel()
            val effects = effectsOf(vm)

            vm.send(MenuEvent.ViewOrder)
            runCurrent()
            vm.send(MenuEvent.ViewOrder)
            runCurrent()

            assertThat(effects).containsExactly(MenuEffect.ViewOrder, MenuEffect.ViewOrder).inOrder()
        }

    @Test
    fun `the budget row is hidden for a menu a plan cannot be built from (FR-032 entry)`() =
        runTest(main.dispatcher) {
            catalogs.set(
                SampleData.catalog.copy(
                    menu =
                        SampleData.menu.copy(
                            products = SampleData.menu.products.filterNot { it.categoryId == "cat_drinks" },
                        ),
                ),
            )
            startOrder()

            val ui = viewModel().ui
            assertThat(ui.showBudgetPlan).isFalse()
            assertThat(ui.showPlanPill).isFalse()
        }
}
