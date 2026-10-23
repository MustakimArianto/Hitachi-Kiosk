package com.hitachi.kiosk.core.data.mapper

import com.hitachi.kiosk.core.data.dto.BudgetPlannerDto
import com.hitachi.kiosk.core.data.dto.MerchantConfigDto
import com.hitachi.kiosk.core.data.dto.PaymentMethodConfigDto
import com.hitachi.kiosk.core.data.dto.PromoDto
import com.hitachi.kiosk.core.domain.model.BudgetPlannerConfig
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.model.MerchantConfig
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentMethodConfig
import com.hitachi.kiosk.core.domain.model.Promo
import com.hitachi.kiosk.core.domain.model.PromoType
import com.hitachi.kiosk.core.domain.model.ReceiptConfig
import com.hitachi.kiosk.core.domain.model.RoundingConfig
import com.hitachi.kiosk.core.domain.model.RoundingMode
import com.hitachi.kiosk.core.domain.model.TaxConfig
import com.hitachi.kiosk.core.domain.model.UpsellConfig

/**
 * E5 → [MerchantConfig]. Unknown promo types, order types and payment methods (for example the reserved `COUNTER`)
 * are dropped with a warning; an unknown mode falls back to `SINGLE`, the only 1.0 mode.
 */
internal fun MerchantConfigDto.toDomain(): MerchantConfig =
    MerchantConfig(
        configVersion = configVersion,
        merchantId = merchantId,
        mode = enumOr(mode, KioskMode.SINGLE, "config.mode"),
        brandName = brandName,
        accentColor = accentColor,
        logoUrl = logoUrl,
        heroPhotoUrl = heroPhotoUrl,
        isOpen = isOpen,
        currency = currency,
        tax =
            TaxConfig(
                label = tax.label?.toDomain(),
                rateBasisPoints = tax.rateBasisPoints,
                inclusive = tax.inclusive,
            ),
        rounding =
            RoundingConfig(
                allowedSteps = rounding.allowedSteps,
                defaultStep = rounding.defaultStep,
                mode = enumOr(rounding.mode, RoundingMode.UP, "config.rounding.mode"),
            ),
        promos =
            promos.mapKnown<PromoDto, PromoType, Promo>("config.promos.type", { it.type }) { dto, type ->
                Promo(
                    id = dto.id,
                    type = type,
                    threshold = dto.threshold.toRupiah(),
                    amount = dto.amount.toRupiah(),
                    label = dto.label?.toDomain(),
                )
            },
        orderTypes =
            orderTypes.mapKnown<String, OrderType, OrderType>(
                "config.orderTypes",
                { it },
            ) { _, type -> type },
        paymentMethods =
            paymentMethods.mapKnown<PaymentMethodConfigDto, PaymentMethod, PaymentMethodConfig>(
                "config.paymentMethods.method",
                { it.method },
            ) { dto, method -> dto.toDomain(method) },
        staffPin = staffPin.toDomain(),
        receipt =
            ReceiptConfig(
                address = receipt?.address,
                npwp = receipt?.npwp,
                defaultFooter = receipt?.defaultFooter?.toDomain(),
            ),
        upsell =
            UpsellConfig(
                categoryIds = upsell?.categoryIds.orEmpty(),
                maxSuggestions = upsell?.maxSuggestions ?: 0,
            ),
        budgetPlanner = budgetPlanner?.toDomain() ?: BudgetPlannerConfig(enabled = false),
        idlePromoImageUrls = idlePromos.map { it.imageUrl },
    )

private fun PaymentMethodConfigDto.toDomain(method: PaymentMethod): PaymentMethodConfig =
    PaymentMethodConfig(
        method = method,
        enabled = enabled,
        qrisMerchantIdMasked = qrisMerchantIdMasked,
        qrExpirySecondsAllowed = qrExpirySecondsAllowed,
        qrExpirySecondsDefault = qrExpirySecondsDefault,
        terminal = terminal?.toDomain(),
    )

private fun BudgetPlannerDto.toDomain(): BudgetPlannerConfig {
    val defaults = BudgetPlannerConfig(enabled = enabled)
    return BudgetPlannerConfig(
        enabled = enabled,
        overCapPercent = overCapPercent ?: defaults.overCapPercent,
        maxGroupSize = maxGroupSize ?: defaults.maxGroupSize,
        quickBudgets = quickBudgets.map { it.toRupiah() },
    )
}
