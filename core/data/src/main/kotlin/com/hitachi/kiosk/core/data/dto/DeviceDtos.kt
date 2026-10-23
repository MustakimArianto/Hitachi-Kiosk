package com.hitachi.kiosk.core.data.dto

import kotlinx.serialization.Serializable

/** E1 request. */
@Serializable
internal data class ActivationRequestDto(
    val serialNumber: String,
    val model: String? = null,
    val osVersion: String? = null,
    val appVersion: String,
)

/** E1 response. */
@Serializable
internal data class ActivationDto(
    val deviceId: String,
    val deviceToken: String,
    val tokenExpiresAt: String? = null,
    val kioskCode: String,
    val kioskName: String,
    val boundMerchantId: String? = null,
    val merchants: List<MerchantDto> = emptyList(),
) {
    override fun toString(): String = "ActivationDto(deviceId=$deviceId, kioskCode=$kioskCode, deviceToken=•••)"
}

/** A merchant in E1 and E2. `status`: `live` | `coming_soon`. */
@Serializable
internal data class MerchantDto(
    val id: String,
    val name: String,
    val cuisine: LocalizedTextDto? = null,
    val logoUrl: String? = null,
    val photoUrl: String? = null,
    val status: String? = null,
    val isOpen: Boolean = false,
)

/** E2 response. */
@Serializable
internal data class MerchantsDto(
    val merchants: List<MerchantDto> = emptyList(),
)

/** E3 request. */
@Serializable
internal data class BindRequestDto(
    val merchantId: String,
)

/** E3 response. */
@Serializable
internal data class BindingDto(
    val deviceId: String,
    val merchantId: String,
    val kioskName: String? = null,
    val boundAt: String? = null,
)

/** E13 request. Sent over TLS only and never logged (NFR-006). */
@Serializable
internal data class ChangeStaffPinRequestDto(
    val currentPin: String,
    val newPin: String,
) {
    override fun toString(): String = "ChangeStaffPinRequestDto(•••)"
}

/** E13 response. */
@Serializable
internal data class StaffPinResponseDto(
    val staffPin: StaffPinDto,
)

/** E14 response. Status values: `OK` | `DEGRADED` | `DOWN`. */
@Serializable
internal data class HealthDto(
    val status: String? = null,
    val serverTime: String? = null,
    val components: HealthComponentsDto? = null,
)

@Serializable
internal data class HealthComponentsDto(
    val api: String? = null,
    val database: String? = null,
    val paymentGateway: String? = null,
)
