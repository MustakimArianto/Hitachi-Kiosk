package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.model.Activation
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

/**
 * A scriptable [ActivationRepository] (NFR-012). [stored] is what [load] finds on "disk", after [loadDelay];
 * [activateScript] answers [activate], which moves [state] to [DeviceState.Activated] on success. Calls are recorded
 * in [activations] and [loadCount].
 */
class FakeActivationRepository(
    var stored: DeviceState = DeviceState.NotActivated,
) : ActivationRepository {
    private val mutableState = MutableStateFlow<DeviceState>(DeviceState.NotActivated)
    override val state: StateFlow<DeviceState> = mutableState.asStateFlow()

    val activateScript = Script<Activation>(Outcome.Success(SampleData.activation))

    private val recorded = mutableListOf<DeviceIdentity>()

    /** Identities passed to [activate], in order. */
    val activations: List<DeviceIdentity> get() = synchronized(recorded) { recorded.toList() }

    /** How long [load] takes, to test the splash's 10 s cap (FR-001). */
    var loadDelay: Duration = Duration.ZERO

    var loadCount: Int = 0
        private set

    override suspend fun load(): DeviceState {
        loadCount++
        delay(loadDelay)
        mutableState.value = stored
        return stored
    }

    override suspend fun activate(identity: DeviceIdentity): Outcome<Activation> {
        synchronized(recorded) { recorded += identity }
        val outcome = activateScript.next()
        if (outcome is Outcome.Success) {
            stored = DeviceState.Activated(SampleData.kioskDevice.copy(deviceId = outcome.value.deviceId))
            mutableState.value = stored
        }
        return outcome
    }

    /** Moves [state] as the data layer does after a bind, Log out or HTTP 401. */
    fun emit(state: DeviceState) {
        stored = state
        mutableState.value = state
    }
}
