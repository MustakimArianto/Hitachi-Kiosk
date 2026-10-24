package com.hitachi.kiosk.feature.combo

import com.hitachi.kiosk.core.domain.cart.ComboMissing

/**
 * Preview states mirroring the `Combo.dc.html` sample (Cheeseburger Meal, Fries, Cola, Regular; 4 items in the order).
 * Screenshot tests build their states through the ViewModel and the sample menu instead.
 */
internal object ComboSamples {
    private fun card(
        id: String,
        name: String,
        includes: String,
        price: String,
        saving: String,
        tone: String,
        selected: Boolean = false,
    ) = ComboCardUi(id, name, includes, price, saving, imageUrl = null, tone = tone, selected = selected)

    private fun option(
        id: String,
        name: String,
        upcharge: String?,
        selected: Boolean = false,
    ) = SlotOptionUi(id, name, upcharge, imageUrl = null, available = true, selected = selected)

    val Default =
        ComboUiState(
            loaded = true,
            combos =
                listOf(
                    card(
                        "combo_burger",
                        "Cheeseburger Meal",
                        "Cheeseburger, side, drink",
                        "Rp 56.000",
                        "Rp 14.000",
                        "#E9DED0",
                        selected = true,
                    ),
                    card(
                        "combo_chicken",
                        "Crispy Chicken Meal",
                        "2 pc chicken, side, drink",
                        "Rp 59.000",
                        "Rp 11.000",
                        "#E3D4C2",
                    ),
                    card(
                        "combo_bowl",
                        "Rice Bowl Combo",
                        "Sambal matah bowl, side, drink",
                        "Rp 52.000",
                        "Rp 12.000",
                        "#EADCCB",
                    ),
                    card(
                        "combo_feast",
                        "Feast for Two",
                        "2 burgers, 2 sides, 2 drinks",
                        "Rp 109.000",
                        "Rp 31.000",
                        "#E6DCD3",
                    ),
                ),
            sides =
                SlotSectionUi(
                    visible =
                        listOf(
                            option("cs_fries", "Fries", null, selected = true),
                            option("cs_cheese", "Cheese fries", "+Rp 8.000"),
                            option("cs_nuggets", "Nuggets", "+Rp 4.000"),
                        ),
                    hiddenCount = 1,
                    tone = "#E6DCD3",
                ),
            drinks =
                SlotSectionUi(
                    visible =
                        listOf(
                            option("cd_cola", "Cola", null, selected = true),
                            option("cd_tea", "Lychee tea", "+Rp 2.000"),
                            option("cd_coffee", "Iced latte", "+Rp 4.000"),
                        ),
                    hiddenCount = 1,
                    tone = "#DFE3DC",
                ),
            largeUpcharge = "+Rp 6.000",
            summary = ComboSummaryUi("Cheeseburger Meal", "Fries", "Cola", large = false, missing = null),
            total = "Rp 56.000",
            cartCount = 4,
        )

    val RequiredMissing =
        Default.copy(
            sides =
                Default.sides.copy(
                    visible = Default.sides.visible.map { it.copy(selected = false) },
                    missing = true,
                ),
            drinks =
                Default.drinks.copy(
                    visible = Default.drinks.visible.map { it.copy(selected = false) },
                    missing = true,
                ),
            summary =
                ComboSummaryUi(
                    "Cheeseburger Meal",
                    null,
                    null,
                    large = false,
                    missing = ComboMissing.SIDE_AND_DRINK,
                ),
            cta = ComboCta.PICK_SIDE_AND_DRINK,
        )
}
