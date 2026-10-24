package com.hitachi.kiosk.feature.idle

/** Sample states of `Idle.dc.html` for previews and screenshot tests (three promo slides, "Urban Grill"). */
internal object IdleSamples {
    private val promos = List(3) { IdleSlideUi(imageUrl = null) }

    val Default = IdleUiState(brandName = "Urban Grill", slides = promos)

    /** `S_Idle_issues`: the red dot on the staff corner. */
    val Issues = Default.copy(showIssueDot = true)

    /** No promo images: one tone slide (TC-024). */
    val NoPromos = IdleUiState(brandName = "Urban Grill")
}
