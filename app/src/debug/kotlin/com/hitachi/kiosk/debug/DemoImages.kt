package com.hitachi.kiosk.debug

import com.hitachi.kiosk.core.domain.model.Combo
import com.hitachi.kiosk.core.domain.model.ComboSlotOption
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Merchant
import com.hitachi.kiosk.core.domain.model.MerchantConfig
import com.hitachi.kiosk.core.domain.model.Product

/**
 * Debug only: photos for the sample "Urban Grill" catalog of the fakes and the Prism mock, so the Menu, Item detail,
 * Combo, Idle and splash screens can be checked with real pictures. The images are bundled WebP files in
 * `src/debug/assets/demo` and Coil loads them through `file:///android_asset/`. A real backend URL is never replaced:
 * only a missing one or the `cdn.example` placeholder of api-spec §4 is (technical-design §4.3).
 */
object DemoImages {
    private const val BASE = "file:///android_asset/demo/"
    private const val PLACEHOLDER_HOST = "cdn.example"

    private fun url(name: String) = "$BASE$name.webp"

    private val products =
        mapOf(
            "b1" to "cheeseburger",
            "b2" to "double_burger",
            "b3" to "chicken_burger",
            "b4" to "bbq_bacon_burger",
            "c1" to "fried_chicken",
            "c2" to "wings_six",
            "c3" to "nuggets_nine",
            "c4" to "chicken_bucket",
            "r1" to "sambal_bowl",
            "r2" to "teriyaki_bowl",
            "r3" to "geprek_bowl",
            "r4" to "beef_bowl",
            "s1" to "fries",
            "s2" to "cheese_fries",
            "s3" to "nuggets_four",
            "s4" to "wings_three",
            "d1" to "lychee_tea",
            "d2" to "cola",
            "d3" to "iced_latte",
            "d4" to "water",
            "e1" to "caramel_sundae",
            "e2" to "chocolate_sundae",
            "e3" to "soft_serve",
        )

    private val combos =
        mapOf(
            "combo_burger" to "cheeseburger",
            "combo_chicken" to "fried_chicken",
            "combo_bowl" to "teriyaki_bowl",
            "combo_feast" to "feast_for_two",
        )

    private val slotOptions =
        mapOf(
            "cs_fries" to "fries",
            "cs_cheese" to "cheese_fries",
            "cs_nuggets" to "nuggets_four",
            "cd_cola" to "cola",
            "cd_tea" to "lychee_tea",
            "cd_coffee" to "iced_latte",
            "cd_water" to "water",
        )

    private val modifierOptions = mapOf("opt_make_meal" to "meal_addon")

    private val idlePromos = listOf("promo_family_feast", "promo_feast_for_two")

    /** The menu with a photo on every product, combo and combo option the demo set covers. */
    fun apply(menu: Menu): Menu =
        menu.copy(
            products = menu.products.map { it.withImage(products[it.id]) },
            combos = menu.combos.map { it.withImage(combos[it.id]) },
            modifierGroups =
                menu.modifierGroups.map { group ->
                    group.copy(
                        options =
                            group.options.map {
                                it.copy(imageUrl = it.imageUrl.orDemo(modifierOptions[it.id]?.let(::url)))
                            },
                    )
                },
            comboSlots =
                menu.comboSlots.copy(
                    sides = menu.comboSlots.sides.map { it.withImage(slotOptions[it.id]) },
                    drinks = menu.comboSlots.drinks.map { it.withImage(slotOptions[it.id]) },
                ),
        )

    /** The config with the Urban Grill logo, the hero photo and the two idle promo posters. */
    fun apply(config: MerchantConfig): MerchantConfig =
        config.copy(
            logoUrl = config.logoUrl.orDemo(url("logo")),
            heroPhotoUrl = config.heroPhotoUrl.orDemo(url("bbq_bacon_burger")),
            idlePromoImageUrls =
                if (config.idlePromoImageUrls.all { it == null || PLACEHOLDER_HOST in it }) {
                    idlePromos.map(::url)
                } else {
                    config.idlePromoImageUrls.mapIndexed { index, existing ->
                        existing.orDemo(url(idlePromos[index % idlePromos.size]))
                    }
                },
        )

    /** The merchant list with the logo and cover photo on the merchants that only have the `cdn.example` placeholder. */
    fun apply(merchants: List<Merchant>): List<Merchant> =
        merchants.map {
            it.copy(
                logoUrl = it.logoUrl.orPlaceholderDemo(url("logo")),
                photoUrl = it.photoUrl.orPlaceholderDemo(url("bbq_bacon_burger")),
            )
        }

    private fun Product.withImage(name: String?) = copy(imageUrl = imageUrl.orDemo(name?.let(::url)))

    private fun Combo.withImage(name: String?) = copy(imageUrl = imageUrl.orDemo(name?.let(::url)))

    private fun ComboSlotOption.withImage(name: String?) = copy(imageUrl = imageUrl.orDemo(name?.let(::url)))

    private fun String?.orPlaceholderDemo(demo: String): String? =
        if (this != null && PLACEHOLDER_HOST in this) demo else this

    private fun String?.orDemo(demo: String?): String? =
        if (this == null || PLACEHOLDER_HOST in this) demo ?: this else this
}
