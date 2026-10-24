package com.hitachi.kiosk.core.domain.order

import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.Rupiah

/**
 * What E7's answer asked the customer to review on Your order (FR-016, DEV-046): lines the backend's menu prices
 * differently (`PRICE_CHANGED`) and lines it no longer sells (`ITEM_UNAVAILABLE`). It lives in [OrderState], so it
 * survives navigation between Checkout and Your order, and [OrderSession.submit] refuses while [isBlocking].
 *
 * @property previousUnitPrices the unit price each re-priced line had before ("Price updated · was Rp X"); cleared
 *   by "Review".
 * @property unavailableLineIds the lines to remove before paying; they are not billed.
 * @property pricesChanged "Prices have been updated. Please review your order." is showing and Pay is disabled
 *   until "Review". Normally true exactly when [previousUnitPrices] is not empty, but a `PRICE_CHANGED` that left every
 *   unit price as it was (for example a tax change) still has to be reviewed.
 */
data class ReviewIssues(
    val previousUnitPrices: Map<String, Rupiah> = emptyMap(),
    val unavailableLineIds: Set<String> = emptySet(),
    val pricesChanged: Boolean = previousUnitPrices.isNotEmpty(),
) {
    /** Whether Pay stays disabled: prices not reviewed yet, or an unavailable line not removed (FR-016). */
    val isBlocking: Boolean get() = pricesChanged || unavailableLineIds.isNotEmpty()

    /** "Review" clears the price notes and unblocks Pay (TC-177). */
    fun reviewed(): ReviewIssues = copy(previousUnitPrices = emptyMap(), pricesChanged = false)

    /** [lineId] left the cart, so its price note and its unavailable flag go too. */
    fun without(lineId: String): ReviewIssues =
        copy(previousUnitPrices = previousUnitPrices - lineId, unavailableLineIds = unavailableLineIds - lineId)
}

/**
 * The E7 body of the last submission with the `Idempotency-Key` it was sent with (technical-design §5.3, BR-08).
 * A retry of the same [request] reuses [key], so a network failure that hid a created order can never create a
 * second one; any other body gets a new key.
 */
data class Submission(
    val request: OrderRequest,
    val key: IdempotencyKey,
)
