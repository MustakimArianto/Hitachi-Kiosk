package com.hitachi.kiosk.core.domain.cart

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.ComboSelection
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.combo
import com.hitachi.kiosk.core.domain.pricing.MenuPricing
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test

class ComboCustomizationTest {
    private val menu = SampleData.menu
    private val defaults = ComboCustomization.defaults(menu)!!

    @Test
    fun `defaults are the first combo, side and drink, Regular (TC-046)`() {
        assertThat(defaults.combo.id).isEqualTo("combo_burger")
        assertThat(defaults.side?.id).isEqualTo("cs_fries")
        assertThat(defaults.drink?.id).isEqualTo("cd_cola")
        assertThat(defaults.size).isEqualTo(ComboSize.REGULAR)
        assertThat(defaults.unitPrice).isEqualTo(Rupiah(56_000))
        assertThat(MenuPricing.comboSaving(defaults.combo)).isEqualTo(Rupiah(14_000))
        assertThat(ComboCustomization.defaults(menu, "combo_feast")?.combo?.id).isEqualTo("combo_feast")
        assertThat(ComboCustomization.defaults(menu, "nope")?.combo?.id).isEqualTo("combo_burger")
        assertThat(ComboCustomization.defaults(menu.copy(combos = emptyList()))).isNull()
    }

    @Test
    fun `upcharges add once for a single combo (FR-011 AC1, TC-047)`() {
        val custom = defaults.selectSide("cs_cheese").selectDrink("cd_coffee").selectSize(ComboSize.LARGE)

        assertThat(custom.unitPrice).isEqualTo(Rupiah(74_000))
        assertThat(custom.toCartItem())
            .isEqualTo(CartItem.ComboItem("combo_burger", ComboSelection("cs_cheese", "cd_coffee", ComboSize.LARGE)))
    }

    @Test
    fun `a combo for two doubles every upcharge and its labels (FR-011 AC2, TC-048)`() {
        val feast =
            defaults
                .selectCombo(
                    menu.combo("combo_feast")!!,
                ).selectSide("cs_nuggets")
                .selectSize(ComboSize.LARGE)
        val nuggets = menu.comboSlots.sides.first { it.id == "cs_nuggets" }

        assertThat(feast.unitPrice).isEqualTo(Rupiah(129_000))
        assertThat(feast.upchargeOf(nuggets)).isEqualTo(Rupiah(8_000))
        assertThat(feast.largeUpcharge).isEqualTo(Rupiah(12_000))
    }

    @Test
    fun `sold-out and unknown options cannot be picked (S_Combo_soldOut)`() {
        val soldOut =
            menu.copy(
                comboSlots =
                    menu.comboSlots.copy(
                        sides = menu.comboSlots.sides.map { it.copy(available = it.id != "cs_fries") },
                        drinks = menu.comboSlots.drinks.map { it.copy(available = it.id != "cd_coffee") },
                    ),
            )
        val custom = ComboCustomization.defaults(soldOut)!!

        assertThat(custom.side?.id).isEqualTo("cs_cheese")
        assertThat(custom.selectSide("cs_fries")).isEqualTo(custom)
        assertThat(custom.selectDrink("cd_coffee")).isEqualTo(custom)
        assertThat(custom.selectDrink("nope")).isEqualTo(custom)
    }

    @Test
    fun `a missing side or drink blocks adding (S_Combo_requiredMissing)`() {
        val none = defaults.copy(sideId = null, drinkId = null)

        assertThat(none.missing).isEqualTo(ComboMissing.SIDE_AND_DRINK)
        assertThat(none.copy(sideId = "cs_fries").missing).isEqualTo(ComboMissing.DRINK)
        assertThat(none.copy(drinkId = "cd_cola").missing).isEqualTo(ComboMissing.SIDE)
        assertThat(defaults.missing).isNull()
        assertThat(none.toCartItem()).isNull()
        assertThat(none.copy(sideId = "cs_fries").toCartItem()).isNull()
        assertThat(none.unitPrice).isEqualTo(Rupiah(56_000))
    }

    @Test
    fun `choices are side, drink and size`() {
        assertThat(defaults.choices().map { it::class })
            .containsExactly(
                LineChoice.Option::class,
                LineChoice.Option::class,
                LineChoice.Size::class,
            ).inOrder()
        assertThat(defaults.copy(sideId = null).choices()).hasSize(2)
    }

    @Test
    fun `saving is never negative`() {
        val combo = defaults.combo.copy(separatePrice = Rupiah(10_000))

        assertThat(MenuPricing.comboSaving(combo)).isEqualTo(Rupiah.Zero)
    }
}
