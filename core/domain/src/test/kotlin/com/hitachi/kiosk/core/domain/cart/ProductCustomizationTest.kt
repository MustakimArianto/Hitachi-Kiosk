package com.hitachi.kiosk.core.domain.cart

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Selection
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test

class ProductCustomizationTest {
    private val menu = SampleData.menu
    private val smokyDouble = ProductCustomization.defaults(menu.product("b2")!!, menu)

    @Test
    fun `the design sample costs Rp 81_000 (FR-010 AC1, TC-040)`() {
        val custom =
            smokyDouble
                .select("mg_b2_size", "opt_b2_regular")
                .select("mg_meal_upgrade", "opt_make_meal")
                .setLevel("mg_b2_ingredients", "ing_cheddar", Level.EXTRA)
                .select("mg_burger_extras", "ext_egg")

        assertThat(custom.unitPrice).isEqualTo(Rupiah(81_000))
        assertThat(custom.isComplete).isTrue()
    }

    @Test
    fun `defaults are meal off, ingredients Regular and the base price (FR-010 AC4)`() {
        assertThat(smokyDouble.unitPrice).isEqualTo(Rupiah(52_000))
        assertThat(smokyDouble.isSelected("mg_meal_upgrade", "opt_burger_only")).isTrue()
        assertThat(smokyDouble.levelOf("mg_b2_ingredients", "ing_onion")).isEqualTo(Level.REGULAR)
        assertThat(smokyDouble.chosen["mg_burger_extras"]).isEmpty()
    }

    @Test
    fun `a required group without a default blocks adding (TC-044)`() {
        assertThat(smokyDouble.missingRequiredGroups.map { it.id }).containsExactly("mg_b2_size")
        assertThat(smokyDouble.isComplete).isFalse()

        assertThat(smokyDouble.select("mg_b2_size", "opt_b2_large").unitPrice).isEqualTo(Rupiah(58_000))
    }

    @Test
    fun `a single group keeps one option and ignores unknown ones`() {
        val large = smokyDouble.select("mg_b2_size", "opt_b2_regular").select("mg_b2_size", "opt_b2_large")

        assertThat(large.chosen["mg_b2_size"]).containsExactly("opt_b2_large")
        assertThat(large.select("mg_b2_size", "nope")).isEqualTo(large)
        assertThat(large.select("nope", "opt_b2_large")).isEqualTo(large)
        assertThat(large.select("mg_b2_ingredients", "ing_onion")).isEqualTo(large)
    }

    @Test
    fun `a multi group toggles and stops at its max`() {
        val three =
            smokyDouble
                .select("mg_burger_extras", "ext_jala")
                .select("mg_burger_extras", "ext_bacon")
                .select("mg_burger_extras", "ext_egg")

        // Kept in option order, whatever the tap order.
        assertThat(three.chosen["mg_burger_extras"]).containsExactly("ext_bacon", "ext_egg", "ext_jala").inOrder()
        assertThat(three.isAtMax("mg_burger_extras")).isTrue()
        assertThat(three.select("mg_burger_extras", "ext_egg").chosen["mg_burger_extras"])
            .containsExactly("ext_bacon", "ext_jala")

        val capped = menu.modifierGroups.first { it.id == "mg_burger_extras" }.copy(max = 1)
        val oneMax = smokyDouble.copy(groups = smokyDouble.groups.map { if (it.id == capped.id) capped else it })
        val one = oneMax.select("mg_burger_extras", "ext_bacon")
        assertThat(one.select("mg_burger_extras", "ext_egg")).isEqualTo(one)
        assertThat(smokyDouble.isAtMax("mg_b2_size")).isFalse()
        assertThat(smokyDouble.isAtMax("nope")).isFalse()
    }

    @Test
    fun `levels price per level and only changes become selections`() {
        val custom =
            smokyDouble
                .select("mg_b2_size", "opt_b2_regular")
                .setLevel("mg_b2_ingredients", "ing_onion", Level.NONE)
                .setLevel("mg_b2_ingredients", "ing_pickles", Level.REGULAR)
                .setLevel("mg_b2_ingredients", "ing_cheddar", Level.EXTRA)

        assertThat(custom.selections())
            .containsExactly(
                Selection("mg_b2_size", "opt_b2_regular"),
                Selection("mg_meal_upgrade", "opt_burger_only"),
                Selection("mg_b2_ingredients", "ing_cheddar", Level.EXTRA),
                Selection("mg_b2_ingredients", "ing_onion", Level.NONE),
            ).inOrder()
        assertThat(custom.unitPrice).isEqualTo(Rupiah(57_000))
        assertThat(custom.setLevel("mg_b2_size", "opt_b2_regular", Level.EXTRA)).isEqualTo(custom)
        assertThat(custom.setLevel("mg_b2_ingredients", "nope", Level.EXTRA)).isEqualTo(custom)
    }

    @Test
    fun `identical choices give equal cart items, so lines merge (FR-010)`() {
        val first =
            smokyDouble
                .select(
                    "mg_b2_size",
                    "opt_b2_large",
                ).setLevel("mg_b2_ingredients", "ing_onion", Level.NONE)
        val second =
            smokyDouble
                .setLevel("mg_b2_ingredients", "ing_onion", Level.EXTRA)
                .setLevel("mg_b2_ingredients", "ing_onion", Level.NONE)
                .select("mg_b2_size", "opt_b2_large")

        assertThat(first.toCartItem()).isEqualTo(second.toCartItem())
        assertThat(first.toCartItem().productId).isEqualTo("b2")
    }

    @Test
    fun `choices list what differs from the defaults (FR-010 AC3)`() {
        val custom =
            smokyDouble
                .select("mg_b2_size", "opt_b2_regular")
                .select("mg_meal_upgrade", "opt_make_meal")
                .setLevel("mg_b2_ingredients", "ing_cheddar", Level.EXTRA)
                .setLevel("mg_b2_ingredients", "ing_onion", Level.NONE)

        assertThat(custom.choices())
            .containsExactly(
                LineChoice.Option(LocalizedText("Regular", "Reguler")),
                LineChoice.Option(LocalizedText("Make it a meal", "Jadikan paket")),
                LineChoice.LevelChange(LocalizedText("Smoked cheddar", "Cheddar asap"), Level.EXTRA),
                LineChoice.LevelChange(LocalizedText("Onion", "Bawang"), Level.NONE),
            ).inOrder()
    }

    @Test
    fun `a product without groups is complete at its base price`() {
        val c1 = ProductCustomization.defaults(menu.product("c1")!!, menu)

        assertThat(c1.isComplete).isTrue()
        assertThat(c1.unitPrice).isEqualTo(Rupiah(38_000))
        assertThat(c1.selections()).isEmpty()
        assertThat(c1.choices()).isEmpty()
    }
}
