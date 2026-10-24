package com.hitachi.kiosk.feature.upsell

import com.hitachi.kiosk.core.domain.model.ProductRole

/**
 * Sample states for previews, after the design's sample data (`Upsell.dc.html`: 5 items, Rp 167.000, and its four
 * suggestions). The screenshot tests build their boards from `SampleData` through the real mapping instead.
 */
internal object UpsellSamples {
    private const val CHICKEN_TONE = "#E3D4C2"
    private const val SIDES_TONE = "#E6DCD3"
    private const val DRINKS_TONE = "#DFE3DC"
    private const val DESSERTS_TONE = "#E9DED0"

    private val suggestions =
        listOf(
            UpsellSuggestionUi(
                id = "s2",
                name = "Cheese Fries",
                role = ProductRole.SIDE,
                description = "Melted cheddar",
                kcal = 450,
                price = "Rp 26.000",
                imageUrl = null,
                tone = SIDES_TONE,
            ),
            UpsellSuggestionUi(
                id = "s1",
                name = "French Fries (Regular)",
                role = ProductRole.SIDE,
                description = "Sea salt",
                kcal = 320,
                price = "Rp 18.000",
                imageUrl = null,
                tone = SIDES_TONE,
            ),
            UpsellSuggestionUi(
                id = "e1",
                name = "Caramel Sundae",
                role = ProductRole.DESSERT,
                description = "Soft serve",
                kcal = 330,
                price = "Rp 15.000",
                imageUrl = null,
                tone = DESSERTS_TONE,
            ),
            UpsellSuggestionUi(
                id = "e3",
                name = "Pandan Soft Serve",
                role = ProductRole.DESSERT,
                description = "Local favourite",
                kcal = 210,
                price = "Rp 12.000",
                imageUrl = null,
                tone = DRINKS_TONE,
            ),
        )

    private val thumbnails =
        listOf(
            UpsellThumbnailUi("l4", null, DRINKS_TONE),
            UpsellThumbnailUi("l3", null, SIDES_TONE),
            UpsellThumbnailUi("l2", null, CHICKEN_TONE),
        )

    val Default =
        UpsellUiState(
            loaded = true,
            suggestions = suggestions,
            itemCount = 5,
            subtotal = "Rp 167.000",
            total = "Rp 172.700",
            thumbnails = thumbnails,
        )

    val JustAdded =
        Default.copy(
            suggestions =
                suggestions.map { if (it.id == "e1") it.copy(addedQuantity = 1, justAdded = true) else it },
            itemCount = 6,
            subtotal = "Rp 182.000",
            total = "Rp 189.200",
            thumbnails = listOf(UpsellThumbnailUi("l5", null, DESSERTS_TONE)) + thumbnails.take(2),
            addSerial = 1,
        )

    val NoSuggestions = Default.copy(suggestions = emptyList())
}
