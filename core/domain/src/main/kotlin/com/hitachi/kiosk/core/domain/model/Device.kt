package com.hitachi.kiosk.core.domain.model

import kotlin.time.Instant

/** What the kiosk sends to E1 to activate itself (FR-002, FR-003). */
data class ActivationRequest(
    val serialNumber: String,
    val model: String?,
    val osVersion: String?,
    val appVersion: String,
)

/** E1 result. [tokenExpiresAt] is `null` when the token never expires (api-spec E1). */
data class Activation(
    val deviceId: String,
    val deviceToken: String,
    val tokenExpiresAt: Instant?,
    val kioskCode: String,
    val kioskName: String,
    val boundMerchantId: String?,
    val merchants: List<Merchant>,
) {
    override fun toString(): String = "Activation(deviceId=$deviceId, kioskCode=$kioskCode, deviceToken=•••)"
}

/** A merchant this kiosk may serve (E1, E2; FR-004). */
data class Merchant(
    val id: String,
    val name: String,
    val cuisine: LocalizedText?,
    val logoUrl: String?,
    val photoUrl: String?,
    val status: MerchantStatus,
    val isOpen: Boolean,
)

/** Only [LIVE] merchants can be bound (api-spec E2). */
enum class MerchantStatus { LIVE, COMING_SOON }

/** E3 result (FR-004). */
data class Binding(
    val deviceId: String,
    val merchantId: String,
    val kioskName: String?,
    val boundAt: Instant?,
)

/** Kiosk operating mode from E5 (FR-005). Only [SINGLE] is used in 1.0. */
enum class KioskMode { SINGLE, FOOD_COURT }

/** The staff PIN as a salted hash; the plain PIN is never stored (FR-024, NFR-005). */
data class StaffPinHash(
    val algorithm: String,
    val iterations: Int?,
    val salt: String?,
    val hash: String,
    val version: Int,
) {
    override fun toString(): String = "StaffPinHash(algorithm=$algorithm, version=$version)"
}

/** E14 result (FR-027, FR-028). */
data class Health(
    val status: HealthStatus,
    val serverTime: Instant?,
    val api: HealthStatus,
    val database: HealthStatus,
    val paymentGateway: HealthStatus,
) {
    /** Checkout is only possible when the backend and the gateway are both healthy (api-spec E14). */
    val canTakePayments: Boolean get() = status == HealthStatus.OK && paymentGateway == HealthStatus.OK
}

enum class HealthStatus { OK, DEGRADED, DOWN }
