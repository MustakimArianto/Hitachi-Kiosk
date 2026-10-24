package com.hitachi.kiosk.feature.order

import com.hitachi.kiosk.core.domain.cart.LineChoice
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.PaymentMethod

/**
 * Sample states for previews, after the design's sample cart (75.000 + 38.000 + 22.000 + 2 × 16.000, rounding
 * Rp 500 → Rp 173.000). The screenshot tests build their boards from `SampleData` through the real mapping instead.
 */
internal object YourOrderSamples {
    private fun option(name: String) = LineChoice.Option(LocalizedText(en = name))

    private val lines =
        listOf(
            OrderLineUi(
                lineId = "l1",
                name = "Smoky Double Burger Meal",
                choices =
                    listOf(
                        option("Fries"),
                        option("Cola"),
                        LineChoice.LevelChange(LocalizedText(en = "Cheddar"), Level.EXTRA),
                        LineChoice.LevelChange(LocalizedText(en = "Onion"), Level.NONE),
                    ),
                imageUrl = null,
                tone = "#E9DED0",
                quantity = 1,
                total = "Rp 75.000",
            ),
            OrderLineUi("l2", "Crispy Chicken (2 pc)", emptyList(), null, "#E3D4C2", 1, "Rp 38.000"),
            OrderLineUi("l3", "French Fries", listOf(option("Large")), null, "#EADCCB", 1, "Rp 22.000"),
            OrderLineUi(
                "l4",
                "Iced Lychee Tea",
                listOf(option("Regular"), option("Less ice")),
                null,
                "#DFE3DC",
                2,
                "Rp 32.000",
            ),
        )

    val Default =
        YourOrderUiState(
            loaded = true,
            itemCount = 5,
            lines = lines,
            empty = false,
            totals =
                OrderTotalsUi(
                    subtotal = "Rp 167.000",
                    discount = "Rp 10.000",
                    promoLabel = "Rp 10.000 off",
                    tax = "Rp 15.700",
                    taxLabel = "Restaurant tax (PB1 10%)",
                    rounding = "Rp 300",
                    total = "Rp 173.000",
                ),
            methods = listOf(PaymentMethod.QRIS, PaymentMethod.CARD),
            selectedMethod = PaymentMethod.QRIS,
            pay = PayUi.Ready("Rp 173.000"),
        )

    val Plan = Default.copy(planTag = PlanTagUi(adults = 2, children = 1, budget = "Rp 150.000"))

    val PriceChanged =
        Default.copy(
            banner = OrderBannerUi.PriceChanged,
            lines = listOf(lines[0].copy(total = "Rp 79.000", previousTotal = "Rp 75.000")) + lines.drop(1),
            pay = PayUi.Blocked(PayBlock.PRICE_CHANGED),
        )

    val ItemUnavailable =
        Default.copy(
            banner = OrderBannerUi.ItemUnavailable(1),
            lines = lines.mapIndexed { i, l -> if (i == 2) l.copy(unavailable = true) else l },
            pay = PayUi.Blocked(PayBlock.ITEM_UNAVAILABLE),
        )

    val Offline = Default.copy(banner = OrderBannerUi.Offline, pay = PayUi.Blocked(PayBlock.OFFLINE))

    val NoMethods = Default.copy(methods = emptyList(), selectedMethod = null, pay = PayUi.Blocked(PayBlock.NO_METHODS))

    val EmptyTray =
        YourOrderUiState(
            loaded = true,
            methods = listOf(PaymentMethod.QRIS, PaymentMethod.CARD),
            selectedMethod = PaymentMethod.QRIS,
        )

    val PromoNudge =
        Default.copy(
            itemCount = 4,
            banner = OrderBannerUi.PromoNudge(remaining = "Rp 8.000", discount = "Rp 10.000", progress = 0.92f),
            lines = lines.drop(1),
            totals =
                OrderTotalsUi(
                    subtotal = "Rp 92.000",
                    tax = "Rp 9.200",
                    taxLabel = "Restaurant tax (PB1 10%)",
                    rounding = "Rp 300",
                    total = "Rp 101.500",
                ),
            pay = PayUi.Ready("Rp 101.500"),
        )
}
