package com.hitachi.kiosk.feature.merchant

/**
 * Sample states for previews and screenshot tests, matching the api-spec E2 example (Urban Grill live; Bella Pasta,
 * Sakura Ramen, Taco Loco and Fresh Bowl coming soon). Image URLs are null, so no test touches the network.
 */
internal object MerchantsSamples {
    val UrbanGrill =
        MerchantCardUi(
            id = "mch_urban_grill",
            name = "Urban Grill",
            cuisine = "Burgers & grill",
            photoUrl = null,
            logoUrl = null,
            isOpen = true,
        )

    val ComingSoon =
        listOf(
            ComingSoonRowUi(id = "mch_bella_pasta", name = "Bella Pasta", initial = "B", cuisine = "Italian kitchen"),
            ComingSoonRowUi(
                id = "mch_sakura_ramen",
                name = "Sakura Ramen",
                initial = "S",
                cuisine = "Japanese noodles",
            ),
            ComingSoonRowUi(id = "mch_taco_loco", name = "Taco Loco", initial = "T", cuisine = "Mexican street food"),
            ComingSoonRowUi(
                id = "mch_fresh_bowl",
                name = "Fresh Bowl",
                initial = "F",
                cuisine = "Salads & healthy bowls",
            ),
        )

    val Default = MerchantsUiState(MerchantsContent.Loaded(live = listOf(UrbanGrill), comingSoon = ComingSoon))

    val Closed =
        MerchantsUiState(
            MerchantsContent.Loaded(
                live = listOf(UrbanGrill.copy(isOpen = false, opensAt = "10:00")),
                comingSoon = ComingSoon,
            ),
        )

    val Binding =
        MerchantsUiState(
            MerchantsContent.Loaded(
                live = listOf(UrbanGrill),
                comingSoon = ComingSoon,
                bindingMerchantId = UrbanGrill.id,
            ),
        )

    val BindFailed = MerchantsUiState(MerchantsContent.Failed(MerchantsFailure.BIND, UrbanGrill.name))

    val MenuDownloadFailed = MerchantsUiState(MerchantsContent.Failed(MerchantsFailure.MENU_DOWNLOAD, UrbanGrill.name))
}
