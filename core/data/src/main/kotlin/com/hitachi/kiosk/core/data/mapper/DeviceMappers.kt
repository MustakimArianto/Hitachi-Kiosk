package com.hitachi.kiosk.core.data.mapper

import com.hitachi.kiosk.core.data.dto.ActivationDto
import com.hitachi.kiosk.core.data.dto.ActivationRequestDto
import com.hitachi.kiosk.core.data.dto.BindingDto
import com.hitachi.kiosk.core.data.dto.HealthDto
import com.hitachi.kiosk.core.data.dto.MerchantDto
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Activation
import com.hitachi.kiosk.core.domain.model.ActivationRequest
import com.hitachi.kiosk.core.domain.model.Binding
import com.hitachi.kiosk.core.domain.model.Health
import com.hitachi.kiosk.core.domain.model.HealthStatus
import com.hitachi.kiosk.core.domain.model.Merchant
import com.hitachi.kiosk.core.domain.model.MerchantStatus

internal fun ActivationRequest.toDto(): ActivationRequestDto =
    ActivationRequestDto(serialNumber = serialNumber, model = model, osVersion = osVersion, appVersion = appVersion)

internal fun ActivationDto.toDomain(): Activation =
    Activation(
        deviceId = deviceId,
        deviceToken = deviceToken,
        tokenExpiresAt = tokenExpiresAt.toInstantOrNull(),
        kioskCode = kioskCode,
        kioskName = kioskName,
        boundMerchantId = boundMerchantId,
        merchants = merchants.map { it.toDomain() },
    )

/** An unknown merchant status maps to `COMING_SOON`: such a merchant cannot be bound (api-spec E2). */
internal fun MerchantDto.toDomain(): Merchant =
    Merchant(
        id = id,
        name = name,
        cuisine = cuisine?.toDomain(),
        logoUrl = logoUrl,
        photoUrl = photoUrl,
        status =
            when (status) {
                "live" -> {
                    MerchantStatus.LIVE
                }

                "coming_soon" -> {
                    MerchantStatus.COMING_SOON
                }

                else -> {
                    KioskLog.w("api_enum_fallback", "field" to "merchant.status", "fallback" to "COMING_SOON")
                    MerchantStatus.COMING_SOON
                }
            },
        isOpen = isOpen,
        opensAt = opensAt.toInstantOrNull(),
    )

internal fun BindingDto.toDomain(): Binding =
    Binding(deviceId = deviceId, merchantId = merchantId, kioskName = kioskName, boundAt = boundAt.toInstantOrNull())

/** An unknown or missing health status counts as `DOWN`, so checkout is never offered on a guess (api-spec E14). */
internal fun HealthDto.toDomain(): Health {
    fun status(
        value: String?,
        field: String,
    ) = enumOr(value, HealthStatus.DOWN, field)
    return Health(
        status = status(status, "health.status"),
        serverTime = serverTime.toInstantOrNull(),
        api = status(components?.api, "health.components.api"),
        database = status(components?.database, "health.components.database"),
        paymentGateway = status(components?.paymentGateway, "health.components.paymentGateway"),
    )
}
