package com.hitachi.kiosk.feature.item

import com.hitachi.kiosk.core.domain.model.Level

/**
 * Preview states mirroring the `ItemDetail.dc.html` sample (Smoky Double Burger, Regular chosen, 4 items in the
 * order). Screenshot tests build their states through the ViewModel and the sample menu instead.
 */
internal object ItemDetailSamples {
    private val sizes =
        ItemSectionUi.Choice(
            groupId = "mg_b2_size",
            title = "Choose a size",
            options =
                listOf(
                    ChoiceOptionUi("opt_b2_regular", "Regular", "Two patties", null, selected = true),
                    ChoiceOptionUi("opt_b2_large", "Large", "Three patties", "+Rp 6.000", selected = false),
                ),
            requiredTag = RequiredTag.DONE,
        )

    private val meal =
        ItemSectionUi.Switch(
            groupId = "mg_meal_upgrade",
            title = "Make it a meal",
            description = "Adds fries & a drink",
            priceDelta = "+Rp 18.000",
            imageUrl = null,
            checked = false,
            onOptionId = "opt_make_meal",
            offOptionId = "opt_burger_only",
        )

    private val customize =
        ItemSectionUi.Levels(
            groupId = "mg_b2_ingredients",
            title = "Customize",
            expanded = false,
            changes = emptyList(),
            rows =
                listOf(
                    LevelRowUi("ing_cheddar", "Smoked cheddar", "Extra +Rp 5.000", Level.REGULAR),
                    LevelRowUi("ing_onion", "Onion", "Fresh, diced", Level.REGULAR),
                    LevelRowUi("ing_pickles", "Pickles", "Dill", Level.REGULAR),
                    LevelRowUi("ing_sauce", "Smoky sauce", "House recipe", Level.REGULAR),
                ),
        )

    private val extras =
        ItemSectionUi.Extras(
            groupId = "mg_burger_extras",
            title = "Add extras",
            expanded = false,
            chosenNames = emptyList(),
            chosenTotal = null,
            options =
                listOf(
                    ExtraOptionUi("ext_bacon", "Beef bacon", "+Rp 8.000", null, selected = false, enabled = true),
                    ExtraOptionUi("ext_egg", "Fried egg", "+Rp 6.000", null, selected = false, enabled = true),
                    ExtraOptionUi("ext_jala", "Jalapeños", "+Rp 4.000", null, selected = false, enabled = true),
                ),
        )

    val Default =
        ItemDetailUiState(
            loaded = true,
            product =
                ItemProductUi(
                    name = "Smoky Double Burger",
                    price = "Rp 52.000",
                    description =
                        "Two flame-grilled beef patties, smoked cheddar, pickles, onion and smoky house sauce on a " +
                            "toasted sesame bun.",
                    badge = "New",
                    kcal = 780,
                    tags = "Beef · Contains gluten, dairy",
                    imageUrl = null,
                    tone = "#E9DED0",
                ),
            sections = listOf(sizes, meal, customize, extras),
            total = "Rp 52.000",
            cartCount = 4,
        )

    val RequiredMissing =
        Default.copy(
            sections =
                listOf(
                    sizes.copy(
                        options = sizes.options.map { it.copy(selected = false) },
                        requiredTag = RequiredTag.MISSING,
                    ),
                    meal,
                    customize,
                    extras,
                ),
            cta = ItemCta.ChooseFirst(groupName = "Choose a size", prompt = true),
        )

    val SoldOut = Default.copy(soldOut = true, cta = ItemCta.Unavailable)
}
