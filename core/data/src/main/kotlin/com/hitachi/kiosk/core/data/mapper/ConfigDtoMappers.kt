package com.hitachi.kiosk.core.data.mapper

import com.hitachi.kiosk.core.data.dto.BudgetPlannerDto
import com.hitachi.kiosk.core.data.dto.IdlePromoDto
import com.hitachi.kiosk.core.data.dto.LocalizedTextDto
import com.hitachi.kiosk.core.data.dto.MerchantConfigDto
import com.hitachi.kiosk.core.data.dto.PaymentMethodConfigDto
import com.hitachi.kiosk.core.data.dto.PromoDto
import com.hitachi.kiosk.core.data.dto.ReceiptDto
import com.hitachi.kiosk.core.data.dto.RoundingDto
import com.hitachi.kiosk.core.data.dto.StaffPinDto
import com.hitachi.kiosk.core.data.dto.TaxDto
import com.hitachi.kiosk.core.data.dto.TerminalDto
import com.hitachi.kiosk.core.data.dto.UpsellDto
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.MerchantConfig
import com.hitachi.kiosk.core.domain.model.PaymentMethodConfig
import com.hitachi.kiosk.core.domain.model.StaffPinHash

/**
 * [MerchantConfig] → the E5 wire form, so the cache stores the config as E5 JSON and reads it back through the same
 * [toDomain] mapper (technical-design §6.1). `toDomain(toDto(config)) == config` for every config the mapper makes.
 */
internal fun MerchantConfig.toDto(): MerchantConfigDto =
    MerchantConfigDto(
        configVersion = configVersion,
        merchantId = merchantId,
        mode = mode.name,
        brandName = brandName,
        accentColor = accentColor,
        logoUrl = logoUrl,
        heroPhotoUrl = heroPhotoUrl,
        isOpen = isOpen,
        currency = currency,
        tax = TaxDto(label = tax.label?.toDto(), rateBasisPoints = tax.rateBasisPoints, inclusive = tax.inclusive),
        rounding =
            RoundingDto(
                allowedSteps = rounding.allowedSteps,
                defaultStep = rounding.defaultStep,
                mode = rounding.mode.name,
            ),
        promos =
            promos.map {
                PromoDto(
                    id = it.id,
                    type = it.type.name,
                    threshold = it.threshold.value,
                    amount = it.amount.value,
                    label = it.label?.toDto(),
                )
            },
        orderTypes = orderTypes.map { it.name },
        paymentMethods = paymentMethods.map { it.toDto() },
        staffPin = staffPin.toDto(),
        receipt =
            ReceiptDto(
                address = receipt.address,
                npwp = receipt.npwp,
                defaultFooter = receipt.defaultFooter?.toDto(),
            ),
        upsell = UpsellDto(categoryIds = upsell.categoryIds, maxSuggestions = upsell.maxSuggestions),
        budgetPlanner =
            BudgetPlannerDto(
                enabled = budgetPlanner.enabled,
                overCapPercent = budgetPlanner.overCapPercent,
                maxGroupSize = budgetPlanner.maxGroupSize,
                quickBudgets = budgetPlanner.quickBudgets.map { it.value },
            ),
        idlePromos = idlePromoImageUrls.map { IdlePromoDto(imageUrl = it) },
    )

internal fun LocalizedText.toDto(): LocalizedTextDto = LocalizedTextDto(en = en, id = id)

private fun PaymentMethodConfig.toDto(): PaymentMethodConfigDto =
    PaymentMethodConfigDto(
        method = method.name,
        enabled = enabled,
        qrisMerchantIdMasked = qrisMerchantIdMasked,
        qrExpirySecondsAllowed = qrExpirySecondsAllowed,
        qrExpirySecondsDefault = qrExpirySecondsDefault,
        terminal = terminal?.let { TerminalDto(it.tid, it.connection) },
    )

private fun StaffPinHash.toDto(): StaffPinDto =
    StaffPinDto(algorithm = algorithm, iterations = iterations, salt = salt, hash = hash, version = version)
