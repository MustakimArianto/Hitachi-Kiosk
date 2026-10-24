package com.hitachi.kiosk.feature.menu

import com.hitachi.kiosk.core.domain.model.BadgeType

/**
 * Sample states for previews, after the design's sample data (Urban Grill, the cart c1 + large fries + 2 × d1). The
 * screenshot tests build their boards from `SampleData` through the real mapping instead.
 */
internal object MenuSamples {
    private const val BURGERS_TONE = "#E9DED0"
    private const val CHICKEN_TONE = "#E3D4C2"
    private const val BOWLS_TONE = "#EADCCB"
    private const val SIDES_TONE = "#E6DCD3"
    private const val DRINKS_TONE = "#DFE3DC"

    private val pills =
        listOf(
            MenuPillUi.Popular(active = true),
            MenuPillUi.Category("cat_burgers", "Burgers", active = false),
            MenuPillUi.Category("cat_chicken", "Chicken", active = false),
            MenuPillUi.Category("cat_bowls", "Rice bowls", active = false),
            MenuPillUi.Meals("Meals"),
            MenuPillUi.Category("cat_sides", "Sides", active = false),
            MenuPillUi.Category("cat_drinks", "Drinks", active = false),
            MenuPillUi.Category("cat_desserts", "Desserts", active = false),
        )

    private val dock =
        CartDockUi(
            itemCount = 4,
            total = "Rp 92.000",
            thumbnails =
                listOf(
                    DockThumbnailUi("l3", null, DRINKS_TONE),
                    DockThumbnailUi("l2", null, SIDES_TONE),
                    DockThumbnailUi("l1", null, CHICKEN_TONE),
                ),
        )

    val Popular =
        MenuUiState(
            loaded = true,
            brandName = "Urban Grill",
            pills = pills,
            showBudgetPlan = true,
            products =
                listOf(
                    MenuProductUi(
                        id = "b2",
                        name = "Smoky Double Burger",
                        price = "Rp 52.000",
                        imageUrl = null,
                        tone = BURGERS_TONE,
                        badge = MenuBadgeUi(BadgeType.NEW, "New"),
                        needsChoice = true,
                    ),
                    MenuProductUi(
                        id = "c1",
                        name = "Crispy Chicken (2 pc)",
                        price = "Rp 38.000",
                        imageUrl = null,
                        tone = CHICKEN_TONE,
                        badge = MenuBadgeUi(BadgeType.BEST_SELLER, "Best seller"),
                    ),
                    MenuProductUi(
                        id = "r1",
                        name = "Sambal Matah Rice Bowl",
                        price = "Rp 34.000",
                        imageUrl = null,
                        tone = BOWLS_TONE,
                        badge = MenuBadgeUi(BadgeType.LOCAL, "Local"),
                        needsChoice = true,
                    ),
                    MenuProductUi(
                        id = "d1",
                        name = "Iced Lychee Tea",
                        price = "Rp 16.000",
                        imageUrl = null,
                        tone = DRINKS_TONE,
                    ),
                ),
            dock = dock,
        )

    val EmptyDesserts =
        Popular.copy(
            pills =
                pills.map {
                    when (it) {
                        is MenuPillUi.Popular -> it.copy(active = false)
                        is MenuPillUi.Category -> it.copy(active = it.id == "cat_desserts")
                        is MenuPillUi.Meals -> it
                    }
                },
            selection = MenuSelection.Category("cat_desserts"),
            showBudgetPlan = false,
            sectionTitle = "Desserts",
            products = emptyList(),
        )
}
