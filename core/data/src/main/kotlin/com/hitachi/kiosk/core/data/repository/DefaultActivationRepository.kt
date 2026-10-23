package com.hitachi.kiosk.core.data.repository

import com.hitachi.kiosk.core.data.api.ApiConfig
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Activation
import com.hitachi.kiosk.core.domain.model.ActivationRequest
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** E1 activation and the stored device state (FR-001, FR-003, DEV-014). */
internal class DefaultActivationRepository
    @Inject
    constructor(
        private val backend: BackendApi,
        private val session: DeviceSession,
        private val config: ApiConfig,
    ) : ActivationRepository {
        override val state: StateFlow<DeviceState> = session.state

        override suspend fun load(): DeviceState = session.load()

        override suspend fun activate(identity: DeviceIdentity): Outcome<Activation> {
            val request =
                ActivationRequest(
                    serialNumber = identity.serialNumber,
                    model = identity.model,
                    osVersion = identity.osVersion,
                    appVersion = config.appVersion,
                )
            // E1 is not retried: staff tap "Try again" (technical-design §5.3).
            val outcome = backend.activate(request)
            if (outcome is Outcome.Success) {
                session.saveActivation(outcome.value)
                KioskLog.i(
                    "device_activated",
                    "deviceId" to outcome.value.deviceId,
                    "kioskCode" to outcome.value.kioskCode,
                )
            }
            return outcome
        }
    }
