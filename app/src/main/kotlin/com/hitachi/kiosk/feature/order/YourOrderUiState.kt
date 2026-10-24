package com.hitachi.kiosk.feature.order

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.domain.cart.LineChoice
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.Rupiah

/**
 * SCR-10 Your order (FR-012, FR-013, FR-015; `Payment.dc.html` and its `S_Payment_*` boards). The screen draws
 * exactly this: the ViewModel computes every amount, flag and the Pay state.
 *
 * @property loaded false until the first state arrives, so the empty tray does not flash.
 * @property itemCount units across the billable lines, for "N items" (FR-012 AC3).
 * @property planTag the "Budget Plan · 2 adults · 1 child · Rp 150.000" row, while the plan's lines are untouched.
 * @property banner the one banner at the top of the list, by priority (offline, prices changed, item unavailable,
 *   promo nudge).
 * @property lines the order lines, including lines that are sliding out ([OrderLineUi.leaving]).
 * @property empty the tray has no lines left: the empty state replaces the list (`S_Payment_emptyTray`).
 * @property methods the payment method tiles, QRIS first; empty shows "No payment methods right now" (A-14).
 * @property selectedMethod the inverted tile; the first visible method by default (FR-015).
 * @property pay whether Pay can be tapped, or why not (its label).
 * @property showMaxToast "Maximum 20 per item" is on screen after a "+" on a line at 20.
 */
@Immutable
data class YourOrderUiState(
    val loaded: Boolean = false,
    val orderType: OrderType = OrderType.DINE_IN,
    val itemCount: Int = 0,
    val planTag: PlanTagUi? = null,
    val banner: OrderBannerUi? = null,
    val lines: List<OrderLineUi> = emptyList(),
    val empty: Boolean = true,
    val totals: OrderTotalsUi = OrderTotalsUi(),
    val detailsOpen: Boolean = false,
    val methods: List<PaymentMethod> = emptyList(),
    val selectedMethod: PaymentMethod? = null,
    val pay: PayUi = PayUi.Blocked(PayBlock.EMPTY),
    val showMaxToast: Boolean = false,
) {
    /** The method tiles are replaced by "No payment methods right now" (`S_Payment_noMethods`, FR-015). */
    val noMethods: Boolean get() = loaded && methods.isEmpty()
}

/** "Budget Plan · {adults} adults · {children} child · Rp {budget}" (FR-012, A-40). */
@Immutable
data class PlanTagUi(
    val adults: Int,
    val children: Int,
    val budget: String,
)

/**
 * The Details rows of the summary panel and its large Total (FR-013 display, FR-015).
 *
 * @property discount the promo amount, or `null` when no promo applies (no Promo row).
 * @property promoLabel the promo's label from config ("Rp 10.000 off over Rp 100.000").
 * @property taxLabel the tax label from config ("Restaurant tax (PB1 10%)"); `null` uses the generic "Tax".
 * @property rounding the rounding adjustment, or `null` when it is zero (no Rounding row, A-13).
 */
@Immutable
data class OrderTotalsUi(
    val subtotal: String = IdrFormatter.format(Rupiah.Zero),
    val discount: String? = null,
    val promoLabel: String? = null,
    val tax: String = IdrFormatter.format(Rupiah.Zero),
    val taxLabel: String? = null,
    val rounding: String? = null,
    val total: String = IdrFormatter.format(Rupiah.Zero),
) {
    /** How many rows Details shows: Subtotal and tax always, Promo and Rounding when non-zero. */
    val detailRows: Int get() = 2 + (if (discount != null) 1 else 0) + (if (rounding != null) 1 else 0)
}

/**
 * One order line (FR-012).
 *
 * @property choices the options in order; the screen words them ("Fries · Cola · Extra cheddar · No onion").
 * @property tone the category (product) or combo placeholder tone (NFR-013).
 * @property total the formatted line total; ignored when [unavailable].
 * @property previousTotal the line total before `PRICE_CHANGED` re-priced it: "Price updated · was Rp X".
 * @property unavailable `ITEM_UNAVAILABLE` named this line: "No longer available" with "Remove", no stepper.
 * @property leaving the line was just removed and slides out (460 ms); it is no longer in the cart or the totals.
 */
@Immutable
data class OrderLineUi(
    val lineId: String,
    val name: String,
    val choices: List<LineChoice>,
    val imageUrl: String?,
    val tone: String?,
    val quantity: Int,
    val total: String,
    val previousTotal: String? = null,
    val unavailable: Boolean = false,
    val leaving: Boolean = false,
)

/** The banner at the top of the list (`Payment.dc.html` `bannerTitle`). Only one shows at a time. */
@Immutable
sealed interface OrderBannerUi {
    /** "Ordering is paused — reconnecting…" (`S_Payment_offline`, FR-028). */
    data object Offline : OrderBannerUi

    /** "Prices have been updated. Please review your order." with "Review" (`S_Payment_priceChanged`, FR-016). */
    data object PriceChanged : OrderBannerUi

    /** "1 item is no longer available." with "Remove it" (`S_Payment_itemUnavailable`, FR-016). */
    data class ItemUnavailable(
        val count: Int,
    ) : OrderBannerUi

    /**
     * "Add Rp X more to get Rp 10.000 off" with a progress bar (`S_Payment_promoNudge`, FR-013). Does not block Pay.
     *
     * @property progress how far the subtotal is towards the threshold, 0–1.
     */
    data class PromoNudge(
        val remaining: String,
        val discount: String,
        val progress: Float,
    ) : OrderBannerUi
}

/** The Pay button of the summary panel (FR-015). */
@Immutable
sealed interface PayUi {
    /** "Pay Rp X": [total] always equals the panel Total (TC-066). */
    data class Ready(
        val total: String,
    ) : PayUi

    /** Pay is disabled and shows [reason] as its label (FR-015 blocking states). */
    data class Blocked(
        val reason: PayBlock,
    ) : PayUi
}

/** Why Pay is disabled, in the design's priority order after [EMPTY] (`Payment.dc.html` `blockLabel`). */
enum class PayBlock {
    /** "Add an item to pay" (FR-012 AC2). */
    EMPTY,

    /** "Pay unavailable while offline" (FR-028). */
    OFFLINE,

    /** "Review your order to continue" (FR-016 `PRICE_CHANGED`). */
    PRICE_CHANGED,

    /** "Remove the unavailable item to pay" (FR-016 `ITEM_UNAVAILABLE`). */
    ITEM_UNAVAILABLE,

    /** "Pay unavailable": no payment method (A-14). */
    NO_METHODS,
}

/** What Your order can ask for. */
sealed interface YourOrderEvent {
    /** "Add more" in the header. */
    data object AddMore : YourOrderEvent

    /** "Back to menu" in the empty tray. */
    data object BackToMenu : YourOrderEvent

    /** "+" on a line. */
    data class Increment(
        val lineId: String,
    ) : YourOrderEvent

    /** "−" on a line; the trash icon at quantity 1 removes it. */
    data class Decrement(
        val lineId: String,
    ) : YourOrderEvent

    /** "Remove" on an unavailable line. */
    data class Remove(
        val lineId: String,
    ) : YourOrderEvent

    /** "Review" on the price-changed banner. */
    data object ReviewPrices : YourOrderEvent

    /** "Remove it" on the unavailable banner: every unavailable line goes. */
    data object RemoveUnavailable : YourOrderEvent

    /** The "Total · Details" toggle. */
    data object ToggleDetails : YourOrderEvent

    /** A payment method tile. */
    data class SelectMethod(
        val method: PaymentMethod,
    ) : YourOrderEvent

    /** "Pay Rp X". */
    data object Pay : YourOrderEvent
}

/** One-off navigation of SCR-10 (PRD §5.1). */
sealed interface YourOrderEffect {
    /** "Add more" or "Back to menu": back to SCR-06. */
    data object BackToMenu : YourOrderEffect

    /** "Pay Rp X": on to SCR-11 Checkout with the chosen [method]. Order submission (E7) arrives in 0.5.0. */
    data class Pay(
        val method: PaymentMethod,
    ) : YourOrderEffect
}
