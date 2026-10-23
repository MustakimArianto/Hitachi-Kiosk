package com.hitachi.kiosk.core.domain.setup

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.BindResult
import com.hitachi.kiosk.core.domain.repository.MerchantRepository

/**
 * "Log in" on Sign in (FR-003): activates the device and, when the dashboard already assigned a merchant
 * (`boundMerchantId`, api-spec E1), binds it straight away so the kiosk skips Choose a restaurant.
 *
 * Returns [DeviceState.Activated] (go to SCR-03) or [DeviceState.Bound] (go to SCR-04). If the pre-assigned
 * merchant cannot be bound now (closed, or the binding or menu download fails), the device stays activated and staff
 * finish on SCR-03.
 */
class ActivateKiosk(
    private val activation: ActivationRepository,
    private val merchants: MerchantRepository,
) {
    suspend operator fun invoke(identity: DeviceIdentity): Outcome<DeviceState> {
        val activated =
            when (val outcome = activation.activate(identity)) {
                is Outcome.Failure -> return outcome
                is Outcome.Success -> outcome.value
            }
        val device = activation.state.value
        val preassigned =
            activated.boundMerchantId
                ?.let { id -> activated.merchants.firstOrNull { it.id == id } }
                // A closed or coming-soon merchant cannot be bound (A-03); SCR-03 shows why.
                ?.takeIf { it.isBindable }
                ?: return Outcome.Success(device)
        return when (val bound = merchants.bind(preassigned)) {
            is BindResult.Bound -> {
                Outcome.Success(bound.state)
            }

            is BindResult.BindFailed, is BindResult.MenuDownloadFailed -> {
                KioskLog.w("preassigned_binding_failed", "merchantId" to preassigned.id)
                Outcome.Success(device)
            }
        }
    }
}
