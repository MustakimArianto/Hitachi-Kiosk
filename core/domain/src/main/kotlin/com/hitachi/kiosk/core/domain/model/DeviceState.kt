package com.hitachi.kiosk.core.domain.model

/** The kiosk as the backend registered it, from E1 (FR-003). Not secret, so it is stored in plain DataStore. */
data class KioskDevice(
    val deviceId: String,
    val kioskCode: String,
    val kioskName: String,
)

/** The merchant this kiosk serves (FR-004). Exactly one at a time. */
data class BoundMerchant(
    val id: String,
    val name: String,
)

/**
 * Where the kiosk is in its setup, which decides the start screen (FR-001): Sign in, Choose a restaurant or Idle.
 */
sealed interface DeviceState {
    /** No device token, or the stored one could not be read (FR-001 corrupt storage) → SCR-02. */
    data object NotActivated : DeviceState

    /** Activated by E1 but not bound to a merchant → SCR-03. */
    data class Activated(
        val device: KioskDevice,
    ) : DeviceState

    /** Bound, with the merchant's config and menu downloaded once → SCR-04. [mode] comes from config (FR-005). */
    data class Bound(
        val device: KioskDevice,
        val merchant: BoundMerchant,
        val mode: KioskMode,
    ) : DeviceState
}
