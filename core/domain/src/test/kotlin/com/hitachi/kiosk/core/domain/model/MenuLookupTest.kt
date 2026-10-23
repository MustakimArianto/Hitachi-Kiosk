package com.hitachi.kiosk.core.domain.model

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test

class MenuLookupTest {
    private val menu = SampleData.menu

    @Test
    fun `only products whose required group has no default need a choice (FR-009, A-08)`() {
        val needChoice = menu.products.filter { menu.needsChoice(it) }.map { it.id }

        // s1 (size) and d1 (ice) are required but have defaults, so quick add uses them (FR-010).
        assertThat(needChoice).containsExactly("b2", "c4", "r1", "d2")
    }

    @Test
    fun `sold-out products are hidden from categories and Popular (A-09)`() {
        val soldOut =
            menu.copy(products = menu.products.map { if (it.id == "c1") it.copy(available = false) else it })

        assertThat(soldOut.visibleProducts("cat_chicken").map { it.id }).containsExactly("c2", "c3", "c4").inOrder()
        assertThat(soldOut.featuredProducts().map { it.id }).containsExactly("b2", "r1", "d1").inOrder()
        assertThat(menu.featuredProducts().map { it.id }).containsExactly("b2", "c1", "r1", "d1").inOrder()
    }

    @Test
    fun `lookups find products, combos, groups and tones`() {
        val b2 = menu.product("b2")!!

        assertThat(menu.product("nope")).isNull()
        assertThat(menu.combo("combo_feast")?.serves).isEqualTo(2)
        assertThat(menu.combo("nope")).isNull()
        assertThat(menu.groupsOf(b2).map { it.id })
            .containsExactly("mg_b2_size", "mg_meal_upgrade", "mg_b2_ingredients", "mg_burger_extras")
            .inOrder()
        assertThat(menu.groupsOf(b2.copy(modifierGroupIds = listOf("missing")))).isEmpty()
        assertThat(menu.toneOf(b2)).isEqualTo("#E9DED0")
        assertThat(menu.toneOf(b2.copy(categoryId = "nope"))).isNull()
        assertThat(menu.sortedCategories().first().id).isEqualTo("cat_burgers")
        assertThat(menu.availableCombos()).hasSize(4)
    }

    @Test
    fun `a group's minimum comes from min, or one when required`() {
        val group = menu.modifierGroups.first { it.id == "mg_burger_extras" }

        assertThat(group.minimumChoices()).isEqualTo(0)
        assertThat(group.copy(min = null, required = true).minimumChoices()).isEqualTo(1)
        assertThat(group.copy(min = null, required = false).minimumChoices()).isEqualTo(0)
        assertThat(menu.modifierGroups.first { it.type == ModifierType.LEVEL }.isSatisfiedByDefaults()).isTrue()
    }
}
