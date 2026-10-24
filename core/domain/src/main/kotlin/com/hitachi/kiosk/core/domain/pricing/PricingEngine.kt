package com.hitachi.kiosk.core.domain.pricing

import com.hitachi.kiosk.core.domain.cart.CartLine
import com.hitachi.kiosk.core.domain.model.MerchantConfig
import com.hitachi.kiosk.core.domain.model.Promo
import com.hitachi.kiosk.core.domain.model.PromoType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Totals

/**
 * What FR-013 prices with: the merchant's promos and tax rate from config, and the rounding step this device uses
 * (FR-025, A-25).
 *
 * @property taxRateBasisPoints the PB1 rate, `1000` = 10.00 % (api-spec E5). Tax is always exclusive in 1.0 (BR-02).
 * @property roundingStep the step the total is rounded up to; `0` means None (BR-04).
 */
data class PricingRules(
    val promos: List<Promo>,
    val taxRateBasisPoints: Int,
    val roundingStep: Long,
) {
    init {
        require(taxRateBasisPoints >= 0) { "The tax rate cannot be negative" }
        require(roundingStep >= 0) { "The rounding step cannot be negative" }
    }

    companion object {
        /**
         * The rules of [config] with the device's [roundingStep] (FR-025 "Round total to"). A step that config does not
         * allow, or none at all, falls back to config's default step (A-25).
         */
        fun of(
            config: MerchantConfig,
            roundingStep: Long? = null,
        ): PricingRules =
            PricingRules(
                promos = config.promos,
                taxRateBasisPoints = config.tax.rateBasisPoints,
                roundingStep =
                    roundingStep?.takeIf { it in config.rounding.allowedSteps } ?: config.rounding.defaultStep,
            )
    }
}

/**
 * "Add Rp X more to get Rp Y off" (FR-013, `S_Payment_promoNudge`): the closest promo the order has not reached yet.
 *
 * @property remaining how much more subtotal unlocks [discount].
 * @property progressPercent how far the subtotal is towards [threshold], 0–99, for the banner's progress bar.
 */
data class PromoHint(
    val promoId: String,
    val remaining: Rupiah,
    val discount: Rupiah,
    val threshold: Rupiah,
    val progressPercent: Int,
)

/**
 * The order totals of FR-013, used by Your order, the Upsell strip, the Budget Plan and the E7 request alike, so the
 * kiosk and the backend price the same way (D-19). Pure and integer only: no floating point at any step (BR-01).
 */
object PricingEngine {
    private const val BASIS_POINTS = 10_000L
    private const val HALF_BASIS_POINTS = 5_000L
    private const val PERCENT = 100L

    /** The totals of [lines]: `subtotal` = Σ line totals (FR-013). */
    fun price(
        lines: List<CartLine>,
        rules: PricingRules,
    ): Totals = price(lines.fold(Rupiah.Zero) { sum, line -> sum + line.lineTotal }, rules)

    /**
     * The FR-013 steps in order (technical-design §8.1):
     * 1. `discount` = the best promo the subtotal reaches, or 0 (one promo per order, BR-03);
     * 2. `tax` = round half-up of (subtotal − discount) × rate (BR-02);
     * 3. `total` = subtotal − discount + tax, rounded **up** to the step, with the difference as `rounding` (BR-04).
     */
    fun price(
        subtotal: Rupiah,
        rules: PricingRules,
    ): Totals {
        require(subtotal >= Rupiah.Zero) { "A subtotal cannot be negative" }
        val promo = bestPromo(subtotal, rules.promos)
        // A discount never takes the taxable amount below zero.
        val discount = promo?.amount?.let { minOf(it, subtotal) } ?: Rupiah.Zero
        val taxable = subtotal - discount
        val tax = halfUpTax(taxable, rules.taxRateBasisPoints)
        val beforeRounding = taxable + tax
        val total = roundUp(beforeRounding, rules.roundingStep)
        return Totals(
            subtotal = subtotal,
            discount = discount,
            promoId = promo?.id,
            tax = tax,
            rounding = total - beforeRounding,
            total = total,
        )
    }

    /**
     * The nudge of FR-013: shown while the subtotal is above zero and below the closest threshold, and only when no
     * promo applies yet (one promo per order, BR-03). `null` otherwise.
     */
    fun promoHint(
        subtotal: Rupiah,
        rules: PricingRules,
    ): PromoHint? {
        if (subtotal <= Rupiah.Zero || bestPromo(subtotal, rules.promos) != null) return null
        val next =
            rules.promos
                .filter { it.type == PromoType.THRESHOLD_FIXED && it.threshold > subtotal }
                .minWithOrNull(compareBy<Promo> { it.threshold }.thenByDescending { it.amount })
                ?: return null
        return PromoHint(
            promoId = next.id,
            remaining = next.threshold - subtotal,
            discount = next.amount,
            threshold = next.threshold,
            progressPercent = (subtotal.value * PERCENT / next.threshold.value).toInt(),
        )
    }

    /** The largest threshold discount [subtotal] reaches; the first one wins a tie (A-12). */
    private fun bestPromo(
        subtotal: Rupiah,
        promos: List<Promo>,
    ): Promo? =
        promos
            .filter { it.type == PromoType.THRESHOLD_FIXED && subtotal >= it.threshold }
            .maxByOrNull { it.amount }

    /** `(base × bp + 5.000) / 10.000`: half-up in integers (technical-design §8.1, TC-057). */
    private fun halfUpTax(
        base: Rupiah,
        rateBasisPoints: Int,
    ): Rupiah = Rupiah((Math.multiplyExact(base.value, rateBasisPoints.toLong()) + HALF_BASIS_POINTS) / BASIS_POINTS)

    private fun roundUp(
        amount: Rupiah,
        step: Long,
    ): Rupiah {
        if (step == 0L) return amount
        val remainder = amount.value % step
        return if (remainder == 0L) amount else amount + Rupiah(step - remainder)
    }
}
