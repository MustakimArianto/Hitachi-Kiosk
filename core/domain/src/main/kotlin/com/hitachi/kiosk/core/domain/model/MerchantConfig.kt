package com.hitachi.kiosk.core.domain.model

/** Merchant configuration from E5: branding, tax, promos, payment, PIN and feature settings (FR-013, FR-024). */
data class MerchantConfig(
    val configVersion: String,
    val merchantId: String,
    val mode: KioskMode,
    val brandName: String,
    val accentColor: String?,
    val logoUrl: String?,
    val heroPhotoUrl: String?,
    val isOpen: Boolean,
    val currency: String,
    val tax: TaxConfig,
    val rounding: RoundingConfig,
    val promos: List<Promo>,
    val orderTypes: List<OrderType>,
    val paymentMethods: List<PaymentMethodConfig>,
    val staffPin: StaffPinHash,
    val receipt: ReceiptConfig,
    val upsell: UpsellConfig,
    val budgetPlanner: BudgetPlannerConfig,
    val idlePromoImageUrls: List<String?>,
)

/** Tax rate in basis points: 1000 = 10.00 % (api-spec E5). */
data class TaxConfig(
    val label: LocalizedText?,
    val rateBasisPoints: Int,
    val inclusive: Boolean,
)

/** Allowed rounding steps in Rupiah; 0 means no rounding (FR-013). */
data class RoundingConfig(
    val allowedSteps: List<Long>,
    val defaultStep: Long,
    val mode: RoundingMode,
)

enum class RoundingMode { UP }

/** A promotion; 1.0 supports one fixed discount over a threshold (FR-013). */
data class Promo(
    val id: String,
    val type: PromoType,
    val threshold: Rupiah,
    val amount: Rupiah,
    val label: LocalizedText?,
)

enum class PromoType { THRESHOLD_FIXED }

enum class OrderType { DINE_IN, TAKEAWAY }

/** A payment method the merchant offers (FR-015, FR-025). */
data class PaymentMethodConfig(
    val method: PaymentMethod,
    val enabled: Boolean,
    val qrisMerchantIdMasked: String?,
    val qrExpirySecondsAllowed: List<Int>,
    val qrExpirySecondsDefault: Int?,
    val terminal: TerminalInfo?,
)

/** An EDC terminal as the backend describes it. */
data class TerminalInfo(
    val tid: String,
    val connection: String,
)

data class ReceiptConfig(
    val address: String?,
    val npwp: String?,
    val defaultFooter: LocalizedText?,
)

data class UpsellConfig(
    val categoryIds: List<String>,
    val maxSuggestions: Int,
)

/** Budget Plan settings (FR-032). Defaults follow api-spec E5 and PRD FR-032 (D-27). */
data class BudgetPlannerConfig(
    val enabled: Boolean,
    val overCapPercent: Int = 10,
    val maxGroupSize: Int = 10,
    val quickBudgets: List<Rupiah> = DEFAULT_QUICK_BUDGETS,
) {
    companion object {
        /** The Step 1 chips when config sends none: Rp 50k / 100k / 150k / 200k (FR-032). */
        val DEFAULT_QUICK_BUDGETS: List<Rupiah> = listOf(50_000L, 100_000L, 150_000L, 200_000L).map(::Rupiah)
    }
}
