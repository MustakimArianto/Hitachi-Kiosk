package com.hitachi.kiosk.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.BoundMerchant
import com.hitachi.kiosk.core.domain.model.KioskDevice
import com.hitachi.kiosk.core.domain.model.KioskMode
import kotlinx.coroutines.flow.first

/** What `device.preferences_pb` holds: the device from E1 and, once bound, the merchant and its mode. */
internal data class DeviceRecord(
    val device: KioskDevice,
    val merchant: BoundMerchant?,
    val mode: KioskMode?,
)

/**
 * The non-secret device state in `device.preferences_pb` (architecture §6, technical-design §6.2): the device ID,
 * kiosk code and name from E1, the bound merchant (FR-004) and the operating mode from config (FR-005). The token
 * is not here; it lives in the encrypted [TokenStore].
 */
internal class DeviceStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** The stored record, or `null` when the kiosk was never activated or the record is incomplete. */
    suspend fun read(): DeviceRecord? {
        val prefs = dataStore.data.first()
        val deviceId = prefs[DEVICE_ID]
        val kioskCode = prefs[KIOSK_CODE]
        val kioskName = prefs[KIOSK_NAME]
        if (deviceId == null || kioskCode == null || kioskName == null) return null
        val merchantId = prefs[MERCHANT_ID]
        val merchantName = prefs[MERCHANT_NAME]
        val merchant = if (merchantId != null && merchantName != null) BoundMerchant(merchantId, merchantName) else null
        return DeviceRecord(
            device = KioskDevice(deviceId = deviceId, kioskCode = kioskCode, kioskName = kioskName),
            merchant = merchant,
            mode = prefs[KIOSK_MODE]?.toKioskMode(),
        )
    }

    /** Stores a freshly activated device. Any earlier binding is removed (FR-003). */
    suspend fun saveDevice(device: KioskDevice) {
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[DEVICE_ID] = device.deviceId
            prefs[KIOSK_CODE] = device.kioskCode
            prefs[KIOSK_NAME] = device.kioskName
        }
    }

    /** Stores the binding and the config's operating mode together (FR-004, FR-005). */
    suspend fun saveBinding(
        merchant: BoundMerchant,
        mode: KioskMode,
    ) {
        dataStore.edit { prefs ->
            prefs[MERCHANT_ID] = merchant.id
            prefs[MERCHANT_NAME] = merchant.name
            prefs[KIOSK_MODE] = mode.name
        }
    }

    /** Removes everything (Log out, HTTP 401, unreadable storage). */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun String.toKioskMode(): KioskMode? =
        KioskMode.entries.firstOrNull { it.name == this }
            ?: null.also { KioskLog.w("device_store_unknown_mode", "mode" to this) }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("deviceId")
        val KIOSK_CODE = stringPreferencesKey("kioskCode")
        val KIOSK_NAME = stringPreferencesKey("kioskName")
        val MERCHANT_ID = stringPreferencesKey("merchantId")
        val MERCHANT_NAME = stringPreferencesKey("merchantName")
        val KIOSK_MODE = stringPreferencesKey("kioskMode")
    }
}
