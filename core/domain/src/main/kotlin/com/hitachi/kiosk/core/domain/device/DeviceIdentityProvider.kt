package com.hitachi.kiosk.core.domain.device

import com.hitachi.kiosk.core.domain.Outcome

/** Reads the kiosk's serial number and model for activation (FR-002). The source is decided in ADR-0002. */
fun interface DeviceIdentityProvider {
    /** Fails with a [com.hitachi.kiosk.core.domain.error.DomainError.Hardware] when no serial number is readable. */
    suspend fun identify(): Outcome<DeviceIdentity>
}

data class DeviceIdentity(
    val serialNumber: String,
    val model: String?,
    val osVersion: String?,
)
