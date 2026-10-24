package com.hitachi.kiosk.feature.item

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.component.RollDirection
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.LineChoice
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Selection
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderSession
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
 * SCR-08 behaviour (FR-010; TC-040–TC-045, TC-176 a/b, FR-010 AC4). Robolectric only because `toRoute` reads the
 * route through an `android.os.Bundle`.
 */
@RunWith(RobolectricTestRunner::class)
class ItemDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalogs = FakeCatalogRepository()
    private val orders = DefaultOrderSessionHolder()
    private val session: OrderSession = orders.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42")

    private fun TestScope.viewModel(productId: String = "b2"): ItemDetailViewModel {
        val vm = ItemDetailViewModel(SavedStateHandle(mapOf("productId" to productId)), catalogs, orders)
        runCurrent()
        return vm
    }

    private fun ItemDetailViewModel.send(vararg events: ItemDetailEvent) = events.forEach(::onEvent)

    private inline fun <reified T : ItemSectionUi> ItemDetailUiState.section(groupId: String): T =
        sections.first { it.groupId == groupId } as T

    @Test
    fun `b2 opens on its defaults with the size still to choose`() =
        runTest(main.dispatcher) {
            val state = viewModel().state.value

            assertThat(state.loaded).isTrue()
            assertThat(state.soldOut).isFalse()
            assertThat(state.product?.name).isEqualTo("Smoky Double Burger")
            assertThat(state.product?.badge).isEqualTo("New")
            assertThat(state.product?.tags).isEqualTo("Beef · Contains gluten, dairy")
            assertThat(state.product?.tone).isEqualTo("#E9DED0")
            assertThat(state.total).isEqualTo("Rp 52.000")
            assertThat(state.quantity).isEqualTo(1)
            assertThat(state.canDecrease).isFalse()
            assertThat(state.cartCount).isEqualTo(0)
            assertThat(state.dock).isEqualTo(ItemDock.EDITING)
        }

    @Test
    fun `sections follow the coordinator's order with b2's copy`() =
        runTest(main.dispatcher) {
            val state = viewModel().state.value

            assertThat(state.sections.map { it.groupId })
                .containsExactly("mg_b2_size", "mg_meal_upgrade", "mg_b2_ingredients", "mg_burger_extras")
                .inOrder()
            val size = state.section<ItemSectionUi.Choice>("mg_b2_size")
            assertThat(size.title).isEqualTo("Choose a size")
            assertThat(size.requiredTag).isEqualTo(RequiredTag.MISSING)
            assertThat(size.options).containsExactly(
                ChoiceOptionUi("opt_b2_regular", "Regular", "Two patties", null, selected = false),
                ChoiceOptionUi("opt_b2_large", "Large", "Three patties", "+Rp 6.000", selected = false),
            )
            val meal = state.section<ItemSectionUi.Switch>("mg_meal_upgrade")
            assertThat(meal.title).isEqualTo("Make it a meal")
            assertThat(meal.description).isEqualTo("Adds fries & a drink")
            assertThat(meal.priceDelta).isEqualTo("+Rp 18.000")
            assertThat(meal.checked).isFalse()
            val levels = state.section<ItemSectionUi.Levels>("mg_b2_ingredients")
            assertThat(levels.expanded).isFalse()
            assertThat(levels.changes).isEmpty()
            assertThat(levels.rows.map { it.level }.toSet()).containsExactly(Level.REGULAR)
            assertThat(levels.rows.first().note).isEqualTo("Extra +Rp 5.000")
            val extras = state.section<ItemSectionUi.Extras>("mg_burger_extras")
            assertThat(extras.chosenNames).isEmpty()
            assertThat(extras.options.map { it.price }).containsExactly("+Rp 8.000", "+Rp 6.000", "+Rp 4.000").inOrder()
        }

    @Test
    fun `two-option groups with a free default become switches`() =
        runTest(main.dispatcher) {
            val fries =
                viewModel("s1")
                    .state.value.sections
                    .single() as ItemSectionUi.Switch
            val tea =
                viewModel("d1")
                    .state.value.sections
                    .single() as ItemSectionUi.Switch

            assertThat(fries.title).isEqualTo("Large")
            assertThat(fries.priceDelta).isEqualTo("+Rp 4.000")
            assertThat(tea.title).isEqualTo("Less ice")
            assertThat(tea.priceDelta).isNull()
        }

    @Test
    fun `TC-044 a required group without a choice disables the CTA with the group name`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            assertThat(vm.state.value.cta).isEqualTo(ItemCta.ChooseFirst("Choose a size", prompt = true))

            vm.send(ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_regular"))
            runCurrent()

            assertThat(vm.state.value.cta).isEqualTo(ItemCta.Add)
            assertThat(
                vm.state.value
                    .section<ItemSectionUi.Choice>("mg_b2_size")
                    .requiredTag,
            ).isEqualTo(RequiredTag.DONE)
        }

    @Test
    fun `a group name that does not ask is worded as Choose name to continue`() =
        runTest(main.dispatcher) {
            assertThat(viewModel("r1").state.value.cta).isEqualTo(ItemCta.ChooseFirst("spice level", prompt = false))
            assertThat(viewModel("c4").state.value.cta)
                .isEqualTo(ItemCta.ChooseFirst("Choose a flavour", prompt = true))
        }

    @Test
    fun `TC-040 meal, cheddar Extra and Fried egg price Rp 81 000 and the summaries read the changes`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            vm.send(
                ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_regular"),
                ItemDetailEvent.SelectOption("mg_meal_upgrade", "opt_make_meal"),
                ItemDetailEvent.ToggleExpanded("mg_b2_ingredients"),
                ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_cheddar", Level.EXTRA),
                ItemDetailEvent.ToggleExpanded("mg_burger_extras"),
                ItemDetailEvent.SelectOption("mg_burger_extras", "ext_egg"),
            )
            runCurrent()

            val open = vm.state.value
            assertThat(open.total).isEqualTo("Rp 81.000")
            assertThat(open.cta).isEqualTo(ItemCta.Add)
            assertThat(open.section<ItemSectionUi.Switch>("mg_meal_upgrade").checked).isTrue()
            assertThat(open.section<ItemSectionUi.Levels>("mg_b2_ingredients").expanded).isTrue()

            vm.send(
                ItemDetailEvent.ToggleExpanded("mg_b2_ingredients"),
                ItemDetailEvent.ToggleExpanded("mg_burger_extras"),
            )
            runCurrent()

            val closed = vm.state.value
            val levels = closed.section<ItemSectionUi.Levels>("mg_b2_ingredients")
            assertThat(levels.expanded).isFalse()
            assertThat(levels.changes).containsExactly(LevelChangeUi("smoked cheddar", Level.EXTRA))
            val extras = closed.section<ItemSectionUi.Extras>("mg_burger_extras")
            assertThat(extras.chosenNames).containsExactly("Fried egg")
            assertThat(extras.chosenTotal).isEqualTo("+Rp 6.000")
        }

    @Test
    fun `TC-042 level changes are summarised in option order`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            vm.send(
                ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_onion", Level.NONE),
                ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_pickles", Level.REGULAR),
                ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_cheddar", Level.EXTRA),
            )
            runCurrent()

            val levels = vm.state.value.section<ItemSectionUi.Levels>("mg_b2_ingredients")
            assertThat(levels.changes)
                .containsExactly(LevelChangeUi("smoked cheddar", Level.EXTRA), LevelChangeUi("onion", Level.NONE))
                .inOrder()
            assertThat(levels.rows.map { it.level })
                .containsExactly(Level.EXTRA, Level.NONE, Level.REGULAR, Level.REGULAR)
                .inOrder()
        }

    @Test
    fun `TC-041 the stepper stops at 1 and 20, and + at 20 shows the toast for 2 4 s`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.send(ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_regular"), ItemDetailEvent.DecreaseQuantity)
            runCurrent()
            assertThat(vm.state.value.quantity).isEqualTo(1)

            repeat(25) { vm.onEvent(ItemDetailEvent.IncreaseQuantity) }
            runCurrent()

            val state = vm.state.value
            assertThat(state.quantity).isEqualTo(20)
            assertThat(state.atMaxQuantity).isTrue()
            assertThat(state.showMaxToast).isTrue()
            assertThat(state.total).isEqualTo("Rp 1.040.000")

            advanceTimeBy(MAX_TOAST_MILLIS + 1)
            assertThat(vm.state.value.showMaxToast).isFalse()

            vm.onEvent(ItemDetailEvent.DecreaseQuantity)
            runCurrent()
            assertThat(vm.state.value.quantity).isEqualTo(19)
            assertThat(vm.state.value.quantityRoll).isEqualTo(RollDirection.DOWN)
        }

    @Test
    fun `TC-043 Add to order adds one line with its choices and swaps to the Added dock`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.send(
                ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_regular"),
                ItemDetailEvent.SelectOption("mg_meal_upgrade", "opt_make_meal"),
                ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_cheddar", Level.EXTRA),
                ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_onion", Level.NONE),
                ItemDetailEvent.AddToOrder,
                // A second tap while the Added dock shows adds nothing.
                ItemDetailEvent.AddToOrder,
            )
            runCurrent()

            val line =
                session.state.value.cart.lines
                    .single()
            assertThat(line.quantity).isEqualTo(1)
            assertThat(line.unitPrice).isEqualTo(Rupiah(75_000))
            assertThat(line.item).isEqualTo(
                CartItem.ProductItem(
                    "b2",
                    listOf(
                        Selection("mg_b2_size", "opt_b2_regular"),
                        Selection("mg_meal_upgrade", "opt_make_meal"),
                        Selection("mg_b2_ingredients", "ing_cheddar", Level.EXTRA),
                        Selection("mg_b2_ingredients", "ing_onion", Level.NONE),
                    ),
                ),
            )
            assertThat(line.choices).containsExactly(
                LineChoice.Option(LocalizedText("Regular", "Reguler")),
                LineChoice.Option(LocalizedText("Make it a meal", "Jadikan paket")),
                LineChoice.LevelChange(LocalizedText("Smoked cheddar", "Cheddar asap"), Level.EXTRA),
                LineChoice.LevelChange(LocalizedText("Onion", "Bawang"), Level.NONE),
            )
            val state = vm.state.value
            assertThat(state.dock).isEqualTo(ItemDock.ADDED)
            assertThat(state.cartCount).isEqualTo(1)
            assertThat(state.cartBumps).isEqualTo(1)
            assertThat(state.showMaxToast).isFalse()
        }

    @Test
    fun `TC-045 an identical add is merged, capped at 20 and shows the toast`() =
        runTest(main.dispatcher) {
            val first = viewModel()
            first.send(ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_regular"))
            repeat(17) { first.onEvent(ItemDetailEvent.IncreaseQuantity) }
            first.onEvent(ItemDetailEvent.AddToOrder)
            runCurrent()
            assertThat(
                session.state.value.cart.lines
                    .single()
                    .quantity,
            ).isEqualTo(18)

            val second = viewModel()
            second.send(ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_regular"))
            repeat(4) { second.onEvent(ItemDetailEvent.IncreaseQuantity) }
            second.onEvent(ItemDetailEvent.AddToOrder)
            runCurrent()

            assertThat(
                session.state.value.cart.lines
                    .single()
                    .quantity,
            ).isEqualTo(20)
            assertThat(second.state.value.showMaxToast).isTrue()
            assertThat(second.state.value.dock).isEqualTo(ItemDock.ADDED)
            assertThat(second.state.value.cartCount).isEqualTo(20)
        }

    @Test
    fun `adding to a full line changes nothing and shows the toast`() =
        runTest(main.dispatcher) {
            val first = viewModel()
            first.send(ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_regular"))
            repeat(19) { first.onEvent(ItemDetailEvent.IncreaseQuantity) }
            first.onEvent(ItemDetailEvent.AddToOrder)
            runCurrent()

            val second = viewModel()
            second.send(ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_regular"), ItemDetailEvent.AddToOrder)
            runCurrent()

            assertThat(
                session.state.value.cart.lines
                    .single()
                    .quantity,
            ).isEqualTo(20)
            assertThat(second.state.value.showMaxToast).isTrue()
            assertThat(second.state.value.dock).isEqualTo(ItemDock.EDITING)
        }

    @Test
    fun `AC4 re-entering the screen starts from the defaults`() =
        runTest(main.dispatcher) {
            val first = viewModel()
            first.send(
                ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_large"),
                ItemDetailEvent.SelectOption("mg_meal_upgrade", "opt_make_meal"),
                ItemDetailEvent.SetLevel("mg_b2_ingredients", "ing_cheddar", Level.EXTRA),
                ItemDetailEvent.IncreaseQuantity,
            )
            runCurrent()
            assertThat(first.state.value.total).isEqualTo("Rp 162.000")

            val again = viewModel().state.value

            assertThat(again.total).isEqualTo("Rp 52.000")
            assertThat(again.quantity).isEqualTo(1)
            assertThat(again.section<ItemSectionUi.Switch>("mg_meal_upgrade").checked).isFalse()
            assertThat(again.section<ItemSectionUi.Levels>("mg_b2_ingredients").changes).isEmpty()
        }

    @Test
    fun `TC-176 b a sold-out product shows No longer available and cannot be added`() =
        runTest(main.dispatcher) {
            val menu = SampleData.menu
            catalogs.set(
                Catalog(
                    SampleData.config,
                    menu.copy(products = menu.products.map { if (it.id == "b2") it.copy(available = false) else it }),
                ),
            )
            val vm = viewModel()
            vm.send(ItemDetailEvent.SelectOption("mg_b2_size", "opt_b2_regular"), ItemDetailEvent.AddToOrder)
            runCurrent()

            val state = vm.state.value
            assertThat(state.soldOut).isTrue()
            assertThat(state.cta).isEqualTo(ItemCta.Unavailable)
            assertThat(state.dock).isEqualTo(ItemDock.EDITING)
            assertThat(
                session.state.value.cart
                    .isEmpty(),
            ).isTrue()
        }

    @Test
    fun `a product missing from the menu is shown as sold out`() =
        runTest(main.dispatcher) {
            val state = viewModel("gone").state.value

            assertThat(state.loaded).isTrue()
            assertThat(state.product).isNull()
            assertThat(state.soldOut).isTrue()
            assertThat(state.cta).isEqualTo(ItemCta.Unavailable)
            assertThat(state.sections).isEmpty()
        }

    @Test
    fun `extras at their max disable the unselected options`() =
        runTest(main.dispatcher) {
            val menu = SampleData.menu
            catalogs.set(
                Catalog(
                    SampleData.config,
                    menu.copy(
                        modifierGroups =
                            menu.modifierGroups.map { if (it.id == "mg_burger_extras") it.copy(max = 2) else it },
                    ),
                ),
            )
            val vm = viewModel()
            vm.send(
                ItemDetailEvent.SelectOption("mg_burger_extras", "ext_bacon"),
                ItemDetailEvent.SelectOption("mg_burger_extras", "ext_egg"),
                ItemDetailEvent.SelectOption("mg_burger_extras", "ext_jala"),
            )
            runCurrent()

            val extras = vm.state.value.section<ItemSectionUi.Extras>("mg_burger_extras")
            assertThat(extras.options.map { it.selected }).containsExactly(true, true, false).inOrder()
            assertThat(extras.options.map { it.enabled }).containsExactly(true, true, false).inOrder()
            assertThat(extras.chosenTotal).isEqualTo("+Rp 14.000")
        }

    @Test
    fun `the pills and dock buttons navigate`() =
        runTest(main.dispatcher) {
            val vm = viewModel()

            vm.onEvent(ItemDetailEvent.BackToMenu)
            assertThat(vm.effect.first()).isEqualTo(ItemDetailEffect.NavigateToMenu)
            vm.onEvent(ItemDetailEvent.ViewOrder)
            assertThat(vm.effect.first()).isEqualTo(ItemDetailEffect.NavigateToYourOrder)
        }
}
