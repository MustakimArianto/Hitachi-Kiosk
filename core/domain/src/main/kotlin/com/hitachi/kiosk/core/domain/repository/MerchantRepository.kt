package com.hitachi.kiosk.core.domain.repository

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.model.Merchant

/** Choosing the merchant this kiosk serves, and Log out (FR-004). Needs an activated device. */
interface MerchantRepository {
    /** E2, retried on transient errors (technical-design §5.3). */
    suspend fun merchants(): Outcome<List<Merchant>>

    /**
     * Binds the kiosk to [merchant] (FR-004): E3 with one idempotency key for this attempt, then the first config
     * and menu download. The binding is stored only when both succeed, so a restart never opens Idle without a menu
     * (FR-004 edge case).
     */
    suspend fun bind(merchant: Merchant): BindResult

    /**
     * Log out (FR-004 AC3): E4 when the backend is reachable, then the token, device and binding are wiped locally
     * whatever E4 answered, so the kiosk always returns to Sign in.
     */
    suspend fun logOut()
}

/** The outcome of [MerchantRepository.bind]; each failure has its own copy on SCR-03 (FR-004). */
sealed interface BindResult {
    data class Bound(
        val state: DeviceState.Bound,
    ) : BindResult

    /** E3 failed: "This kiosk could not be linked to {merchant}…". */
    data class BindFailed(
        val error: DomainError,
    ) : BindResult

    /** E3 succeeded but config or menu did not download: "The menu for {merchant} did not finish downloading…". */
    data class MenuDownloadFailed(
        val error: DomainError,
    ) : BindResult
}
