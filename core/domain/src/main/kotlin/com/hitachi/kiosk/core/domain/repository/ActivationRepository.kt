package com.hitachi.kiosk.core.domain.repository

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.model.Activation
import com.hitachi.kiosk.core.domain.model.DeviceState
import kotlinx.coroutines.flow.StateFlow

/**
 * Device activation and the locally stored device state (FR-001, FR-003, NFR-005). The device token lives in the
 * encrypted store; it never leaves the data layer.
 */
interface ActivationRepository {
    /**
     * The current device state. It starts as [DeviceState.NotActivated] until [load] has run, and drops back to
     * [DeviceState.NotActivated] after Log out or when the backend rejects the token (HTTP 401, TR-NET-06).
     */
    val state: StateFlow<DeviceState>

    /**
     * Reads the stored token, device and binding (FR-001). Storage that cannot be read or decrypted is wiped and
     * reported as [DeviceState.NotActivated]; it never throws (TR-REL-06).
     */
    suspend fun load(): DeviceState

    /**
     * E1 with [identity] (FR-003). On success the token is stored encrypted and the device record in plain storage,
     * and [state] becomes [DeviceState.Activated]. Not retried: staff tap "Try again" (technical-design §5.3).
     */
    suspend fun activate(identity: DeviceIdentity): Outcome<Activation>
}
