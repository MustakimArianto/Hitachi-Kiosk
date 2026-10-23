package com.hitachi.kiosk.core.data.repository

import com.hitachi.kiosk.core.data.api.DeviceCredentials
import com.hitachi.kiosk.core.data.api.InMemoryDeviceCredentialsStore
import com.hitachi.kiosk.core.data.prefs.DeviceStore
import com.hitachi.kiosk.core.data.prefs.TokenStore
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Activation
import com.hitachi.kiosk.core.domain.model.BoundMerchant
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.model.KioskDevice
import com.hitachi.kiosk.core.domain.model.KioskMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The one owner of the stored device state (FR-001, FR-003, FR-004): the encrypted token, the device record and the
 * binding, the in-memory credentials OkHttp reads, and the [state] flow. Activation, binding, Log out and the 401
 * handler all write through it, so the three always agree. Writes are serialized by a [Mutex].
 */
@Singleton
internal class DeviceSession
    @Inject
    constructor(
        private val tokenStore: TokenStore,
        private val deviceStore: DeviceStore,
        private val credentials: InMemoryDeviceCredentialsStore,
    ) {
        private val mutex = Mutex()
        private val mutableState = MutableStateFlow<DeviceState>(DeviceState.NotActivated)

        val state: StateFlow<DeviceState> = mutableState.asStateFlow()

        /** The activated device's ID, or `null` before activation. Never does I/O. */
        val deviceId: String? get() = credentials.current()?.deviceId

        /**
         * Reads the stored state (FR-001). A token without a device record, or the reverse, is an inconsistent
         * store: it is wiped and reported as not activated, like unreadable storage (TR-REL-06).
         */
        suspend fun load(): DeviceState =
            mutex.withLock {
                val loaded =
                    try {
                        read()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        KioskLog.e("device_state_unreadable", "type" to e.javaClass.simpleName)
                        clearLocked()
                        DeviceState.NotActivated
                    }
                mutableState.value = loaded
                loaded
            }

        /** Stores a new activation: token encrypted, device record plain, no binding (FR-003, NFR-005). */
        suspend fun saveActivation(activation: Activation): DeviceState.Activated =
            mutex.withLock {
                val device = KioskDevice(activation.deviceId, activation.kioskCode, activation.kioskName)
                tokenStore.write(activation.deviceToken)
                deviceStore.saveDevice(device)
                credentials.set(DeviceCredentials(device.deviceId, activation.deviceToken))
                DeviceState.Activated(device).also { mutableState.value = it }
            }

        /**
         * Stores the binding with the config's operating mode (FR-004, FR-005). Returns `null` when the device is no
         * longer activated, for example because a 401 wiped it during the bind.
         */
        suspend fun saveBinding(
            merchant: BoundMerchant,
            mode: KioskMode,
        ): DeviceState.Bound? =
            mutex.withLock {
                val device =
                    when (val current = mutableState.value) {
                        is DeviceState.Activated -> current.device
                        is DeviceState.Bound -> current.device
                        DeviceState.NotActivated -> return@withLock null
                    }
                deviceStore.saveBinding(merchant, mode)
                DeviceState.Bound(device, merchant, mode).also { mutableState.value = it }
            }

        /** Forgets the token, device and binding (Log out FR-004 AC3, HTTP 401 TR-NET-06). Never throws. */
        suspend fun wipe(reason: String) {
            mutex.withLock {
                KioskLog.i("device_state_wiped", "reason" to reason)
                clearLocked()
                mutableState.value = DeviceState.NotActivated
            }
        }

        private suspend fun read(): DeviceState {
            val token = tokenStore.read()
            val record = deviceStore.read()
            if (token == null || record == null) {
                if (token != null || record != null) {
                    KioskLog.w(
                        "device_state_inconsistent",
                        "hasToken" to (token != null),
                        "hasDevice" to (record != null),
                    )
                    clearLocked()
                }
                return DeviceState.NotActivated
            }
            credentials.set(DeviceCredentials(record.device.deviceId, token))
            return if (record.merchant != null && record.mode != null) {
                DeviceState.Bound(record.device, record.merchant, record.mode)
            } else {
                DeviceState.Activated(record.device)
            }
        }

        private suspend fun clearLocked() {
            credentials.clear()
            tokenStore.clear()
            try {
                deviceStore.clear()
            } catch (e: IOException) {
                KioskLog.e("device_store_clear_failed", "type" to e.javaClass.simpleName)
            }
        }
    }
