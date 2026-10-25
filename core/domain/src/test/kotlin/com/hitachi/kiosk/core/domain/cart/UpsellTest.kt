package com.hitachi.kiosk.core.domain.cart

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.ComboSelection
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Selection
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test

/** FR-014, DEV-105 and DEV-114 (TC-194–TC-197, TC-205). */
class UpsellTest {
    private val menu = SampleData.menu
    private val config = SampleData.config.upsell

    private fun cartOf(vararg items: CartItem): Cart =
        items.fold(Cart()) { cart, item ->
            (cart.add(item, 1, Rupiah(1), emptyList()) as CartAddResult.Added).cart
        }

    private fun productsOf(vararg ids: String): Cart =
        cartOf(
            *ids
                .map {
                    CartItem.ProductItem(it, emptyList())
                }.toTypedArray(),
        )

    private val comboLine =
        CartItem.ComboItem("combo_burger", ComboSelection("cs_fries", "cd_cola", ComboSize.REGULAR))

    private val burgerBurgerOnly =
        CartItem.ProductItem(
            "b2",
            listOf(Selection("mg_b2_size", "opt_b2_regular"), Selection("mg_meal_upgrade", "opt_burger_only")),
        )

    private val burgerMeal =
        CartItem.ProductItem(
            "b2",
            listOf(Selection("mg_b2_size", "opt_b2_regular"), Selection("mg_meal_upgrade", "opt_make_meal")),
        )

    private fun Menu.with(
        id: String,
        change: (Product) -> Product,
    ): Menu = copy(products = products.map { if (it.id == id) change(it) else it })

    private fun ids(
        cart: Cart,
        menu: Menu = this.menu,
        fits: (Rupiah) -> Boolean = { true },
    ) = Upsell.suggestions(menu, config, cart, fits).map { it.id }

    @Test
    fun `a main only gives a drink first, then a side and a dessert (TC-195 a)`() {
        assertThat(ids(productsOf("b1"))).containsExactly("d1", "s1", "e1").inOrder()
    }

    @Test
    fun `a main and a drink gives a side and a dessert (TC-195 b)`() {
        assertThat(ids(productsOf("b1", "d4"))).containsExactly("s1", "e1").inOrder()
    }

    @Test
    fun `a combo only gives a dessert only, because a combo is a meal (TC-195 c)`() {
        assertThat(ids(cartOf(comboLine))).containsExactly("e1")
    }

    @Test
    fun `a main, side, drink and dessert gives nothing (TC-195 d)`() {
        assertThat(ids(productsOf("b1", "s2", "d4", "e1"))).isEmpty()
    }

    @Test
    fun `an empty cart or one without a main gets no drink or side, only a dessert`() {
        assertThat(ids(Cart())).containsExactly("e1")
        assertThat(ids(productsOf("d4"))).containsExactly("e1")
    }

    @Test
    fun `sold-out, in-cart and needs-a-choice items are never suggested (TC-195 e)`() {
        // d1 is sold out, so the drink is d3 (d2 needs a choice of cola).
        val soldOut = menu.with("d1") { it.copy(available = false) }
        assertThat(ids(productsOf("b1"), soldOut)).containsExactly("d3", "s1", "e1").inOrder()
        // s1 is in the cart, so the next side is s2.
        assertThat(ids(productsOf("b1", "s1"))).containsExactly("d1", "e1").inOrder()
        // Only d2 is left to suggest as a drink and it needs a choice, so there is no drink card.
        assertThat(ids(productsOf("b1", "d1", "d3", "d4", "s1", "s2", "s3", "s4", "e1"))).isEmpty()
        assertThat(ids(productsOf("b1", "d1", "d3", "d4", "s1", "s2", "s3", "s4"))).containsExactly("e1")
    }

    @Test
    fun `inside a gap the order is plannerWeight, then featured, then menu order`() {
        val weighted = menu.with("s3") { it.copy(plannerWeight = 5) }
        assertThat(ids(productsOf("b1", "d4", "e1"), weighted)).containsExactly("s3")

        val featured = menu.copy(featuredProductIds = listOf("s4"))
        assertThat(ids(productsOf("b1", "d4", "e1"), featured)).containsExactly("s4")

        // plannerWeight beats featured.
        val both = weighted.copy(featuredProductIds = listOf("s4"))
        assertThat(ids(productsOf("b1", "d4", "e1"), both)).containsExactly("s3")

        // Equal weights keep menu order: s1 before s2 before s3.
        assertThat(ids(productsOf("b1", "d4", "e1"))).containsExactly("s1")
    }

    @Test
    fun `only categories of the upsell config are candidates`() {
        val sidesOnly = config.copy(categoryIds = listOf("cat_sides"))
        assertThat(Upsell.suggestions(menu, sidesOnly, productsOf("b1")).map { it.id }).containsExactly("s1")
    }

    @Test
    fun `the strip holds at most three cards and the config can switch it off`() {
        assertThat(Upsell.MAX_SUGGESTIONS).isEqualTo(3)
        assertThat(Upsell.suggestions(menu, config.copy(maxSuggestions = 9), productsOf("b1"))).hasSize(3)
        assertThat(Upsell.suggestions(menu, config.copy(maxSuggestions = 2), productsOf("b1"))).hasSize(2)
        assertThat(Upsell.suggestions(menu, config.copy(maxSuggestions = 0), productsOf("b1"))).isEmpty()
    }

    @Test
    fun `a plan order with Rp 400 left returns nothing priced above Rp 400 (TC-197, AC5)`() {
        assertThat(ids(productsOf("b1"), fits = { it <= Rupiah(400) })).isEmpty()
        // Rp 16.000 left: the lychee tea, nuggets (4 pc) and a dessert fit, the pricier picks do not.
        assertThat(ids(productsOf("b1"), fits = { it <= Rupiah(16_000) })).containsExactly("d1", "s3", "e1").inOrder()
    }

    @Test
    fun `the budget check prices the order with the extra item, tax and rounding included`() {
        val rules = PricingRules.of(SampleData.config, 500)
        val cart =
            (
                Cart().add(
                    CartItem.ProductItem("b1", emptyList()),
                    1,
                    Rupiah(38_000),
                    emptyList(),
                ) as CartAddResult.Added
            ).cart

        // 38.000 + 3.800 tax = 41.800 → 42.000. One more Rp 8.000 water: 46.000 + 4.600 = 50.600 → 51.000.
        val fits = Upsell.withinBudget(Rupiah(51_000), cart, rules)
        assertThat(fits(Rupiah(8_000))).isTrue()
        assertThat(fits(Rupiah(8_500))).isFalse()
        assertThat(Upsell.withinBudget(Rupiah(40_000), cart, rules)(Rupiah(1))).isFalse()
    }

    @Test
    fun `a product line with Make it a meal on counts as a meal (A-48, DEV-114)`() {
        assertThat(ids(cartOf(burgerMeal))).containsExactly("e1")
        // The same line with the option off still has the drink and side gaps.
        assertThat(ids(cartOf(burgerBurgerOnly))).containsExactly("d1", "s1", "e1").inOrder()
    }

    @Test
    fun `without a marked meal group only a combo counts as a meal (A-48)`() {
        assertThat(ids(cartOf(burgerMeal), SampleData.menuWithoutPurposes)).containsExactly("d1", "s1", "e1").inOrder()
    }

    @Test
    fun `the strip leads with Make it a meal for a main with the option off and covers drink and side (TC-205)`() {
        val cards = Upsell.cards(menu, config, cartOf(burgerBurgerOnly))

        assertThat(cards.map { it.key }).containsExactly("meal:b2", "add:e1").inOrder()
        val meal = cards.first() as UpsellCard.MakeMeal
        assertThat(meal.product.id).isEqualTo("b2")
        assertThat(meal.priceDelta).isEqualTo(Rupiah(18_000))
        assertThat(meal.option.id).isEqualTo("opt_make_meal")
    }

    @Test
    fun `there is no meal card without the API marker, with a meal already or when the upgrade does not fit`() {
        val cart = cartOf(burgerBurgerOnly)

        assertThat(Upsell.cards(SampleData.menuWithoutPurposes, config, cart).map { it.key })
            .containsExactly("add:d1", "add:s1", "add:e1")
            .inOrder()
        assertThat(Upsell.cards(menu, config, cartOf(burgerMeal)).map { it.key }).containsExactly("add:e1")
        assertThat(Upsell.cards(menu, config, cart, fits = { it != Rupiah(18_000) }).map { it.key })
            .containsExactly("add:d1", "add:s2", "add:e1")
            .inOrder()
        assertThat(Upsell.cards(menu, config, cart, mealExcludedLineIds = setOf("l1")).map { it.key })
            .containsExactly("add:d1", "add:s1", "add:e1")
            .inOrder()
    }

    @Test
    fun `cards without a meal option are the ranked products with their default price`() {
        val cards = Upsell.cards(menu, config, productsOf("b1"))

        assertThat(cards.map { it.key }).containsExactly("add:d1", "add:s1", "add:e1").inOrder()
        assertThat(cards.map { (it as UpsellCard.AddProduct).unitPrice })
            .containsExactly(Rupiah(16_000), Rupiah(18_000), Rupiah(15_000))
            .inOrder()
    }

    @Test
    fun `mealSwitch turns one burger into a meal at the option price, and back (TC-205)`() {
        val on = Upsell.mealSwitch(menu, cartOf(burgerBurgerOnly), "b2", on = true)!!

        assertThat(on.lineId).isEqualTo("l1")
        assertThat(on.item.selections).contains(Selection("mg_meal_upgrade", "opt_make_meal"))
        assertThat(on.item.selections).doesNotContain(Selection("mg_meal_upgrade", "opt_burger_only"))
        assertThat(on.unitPrice).isEqualTo(Rupiah(70_000))
        assertThat(on.choices.map { it.toString() }.any { "Make it a meal" in it }).isTrue()

        val off = Upsell.mealSwitch(menu, cartOf(on.item), "b2", on = false)!!
        assertThat(off.item.selections).contains(Selection("mg_meal_upgrade", "opt_burger_only"))
        assertThat(off.unitPrice).isEqualTo(Rupiah(52_000))
    }

    @Test
    fun `mealSwitch keeps the other choices, and has nothing to do when already in that state`() {
        val custom =
            CartItem.ProductItem(
                "b2",
                listOf(
                    Selection("mg_b2_size", "opt_b2_large"),
                    Selection("mg_burger_extras", "ext_egg"),
                    Selection("mg_b2_ingredients", "ing_onion", Level.NONE),
                ),
            )
        val on = Upsell.mealSwitch(menu, cartOf(custom), "b2", on = true)!!

        assertThat(on.item.selections)
            .containsExactly(
                Selection("mg_b2_size", "opt_b2_large"),
                Selection("mg_meal_upgrade", "opt_make_meal"),
                Selection("mg_b2_ingredients", "ing_onion", Level.NONE),
                Selection("mg_burger_extras", "ext_egg"),
            ).inOrder()
        // 52.000 + 6.000 large + 18.000 meal + 6.000 egg.
        assertThat(on.unitPrice).isEqualTo(Rupiah(82_000))

        assertThat(Upsell.mealSwitch(menu, cartOf(burgerMeal), "b2", on = true)).isNull()
        assertThat(Upsell.mealSwitch(menu, cartOf(burgerBurgerOnly), "b2", on = false)).isNull()
        assertThat(Upsell.mealSwitch(menu, productsOf("b1"), "b1", on = true)).isNull()
        assertThat(
            Upsell.mealSwitch(SampleData.menuWithoutPurposes, cartOf(burgerBurgerOnly), "b2", on = true),
        ).isNull()
    }

    @Test
    fun `a card the customer acted on keeps its slot while the other cards re-rank (FR-014 undo)`() {
        val cart = productsOf("b1", "s1")

        // The side card was in slot 1 when it was added; the fresh ranking no longer has it.
        val strip = Upsell.strip(menu, config, cart, held = mapOf("add:s1" to 1))

        assertThat(strip.map { it.key }).containsExactly("add:d1", "add:s1", "add:e1").inOrder()
        // Without the hold the side drops out.
        assertThat(Upsell.strip(menu, config, cart).map { it.key }).containsExactly("add:d1", "add:e1").inOrder()
    }

    @Test
    fun `held cards at the end or whose product is gone do not leave holes`() {
        val gone = menu.copy(products = menu.products.filterNot { it.id == "s1" })

        assertThat(Upsell.strip(gone, config, productsOf("b1", "s1"), held = mapOf("add:s1" to 1)).map { it.key })
            .containsExactly("add:d1", "add:s2", "add:e1")
            .inOrder()
        assertThat(Upsell.strip(menu, config, productsOf("b1", "d1"), held = mapOf("add:d1" to 0)).map { it.key })
            .containsExactly("add:d1", "add:s1", "add:e1")
            .inOrder()
        assertThat(Upsell.strip(menu, config.copy(maxSuggestions = 0), productsOf("b1"), held = mapOf("add:d1" to 0)))
            .isEmpty()
    }

    @Test
    fun `a held meal card stays after the upgrade and counts its meal units`() {
        val cart = cartOf(burgerMeal)

        val strip = Upsell.strip(menu, config, cart, held = mapOf("meal:b2" to 0))

        assertThat(strip.map { it.key }).containsExactly("meal:b2", "add:e1").inOrder()
        assertThat(Upsell.mealUnits(menu, cart, "b2")).isEqualTo(1)
        assertThat(Upsell.mealUnits(menu, cartOf(burgerBurgerOnly), "b2")).isEqualTo(0)
        assertThat(Upsell.mealUnits(SampleData.menuWithoutPurposes, cart, "b2")).isEqualTo(0)
        assertThat(Upsell.card(menu, "nope:b2")).isNull()
        assertThat(Upsell.card(menu, "meal:b1")).isNull()
    }
}
