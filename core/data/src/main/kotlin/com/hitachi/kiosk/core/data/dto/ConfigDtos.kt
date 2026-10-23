package com.hitachi.kiosk.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** E5 response. */
@Serializable
internal data class MerchantConfigDto(
    val configVersion: String,
    val merchantId: String,
    val mode: String? = null,
    val brandName: String,
    val accentColor: String? = null,
    val logoUrl: String? = null,
    val heroPhotoUrl: String? = null,
    val isOpen: Boolean,
    val currency: String,
    val tax: TaxDto,
    val rounding: RoundingDto,
    val promos: List<PromoDto> = emptyList(),
    val orderTypes: List<String> = emptyList(),
    val paymentMethods: List<PaymentMethodConfigDto> = emptyList(),
    val staffPin: StaffPinDto,
    val receipt: ReceiptDto? = null,
    val upsell: UpsellDto? = null,
    val budgetPlanner: BudgetPlannerDto? = null,
    val idlePromos: List<IdlePromoDto> = emptyList(),
)

@Serializable
internal data class TaxDto(
    val label: LocalizedTextDto? = null,
    val rateBasisPoints: Int,
    val inclusive: Boolean,
)

@Serializable
internal data class RoundingDto(
    val allowedSteps: List<Long> = emptyList(),
    @SerialName("default") val defaultStep: Long = 0,
    val mode: String? = null,
)

@Serializable
internal data class PromoDto(
    val id: String,
    val type: String,
    val threshold: Long,
    val amount: Long,
    val label: LocalizedTextDto? = null,
)

@Serializable
internal data class PaymentMethodConfigDto(
    val method: String,
    val enabled: Boolean,
    val qrisMerchantIdMasked: String? = null,
    val qrExpirySecondsAllowed: List<Int> = emptyList(),
    val qrExpirySecondsDefault: Int? = null,
    val terminal: TerminalDto? = null,
)

@Serializable
internal data class ReceiptDto(
    val address: String? = null,
    val npwp: String? = null,
    val defaultFooter: LocalizedTextDto? = null,
)

@Serializable
internal data class UpsellDto(
    val categoryIds: List<String> = emptyList(),
    val maxSuggestions: Int = 0,
)

@Serializable
internal data class BudgetPlannerDto(
    val enabled: Boolean = false,
    val overCapPercent: Int? = null,
    val maxGroupSize: Int? = null,
    val quickBudgets: List<Long> = emptyList(),
)

@Serializable
internal data class IdlePromoDto(
    val imageUrl: String? = null,
)
