package com.hitachi.kiosk.core.hardware.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hitachi.kiosk.core.domain.log.KioskLog
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * The last serial number read (FR-002, technical-design §9.1), kept in `identity.preferences_pb`, a DataStore file
 * owned by `:core:hardware` so the module never touches a `:core:data` store (architecture §3, technical-design §6.2).
 * It is a fallback only: [IminDeviceIdentityProvider] re-reads the sources at every call. A storage failure is logged
 * and treated as an empty cache, never as an identity failure.
 */
class SerialCache internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** The cached serial number, or `null` when none is stored or the file cannot be read. */
    suspend fun read(): String? =
        try {
            dataStore.data.first()[SERIAL_NUMBER]
        } catch (e: IOException) {
            KioskLog.w("serial_cache_read_failed", error = e)
            null
        }

    /** Stores [serial], replacing any previous value. */
    suspend fun write(serial: String) {
        try {
            dataStore.edit { it[SERIAL_NUMBER] = serial }
        } catch (e: IOException) {
            KioskLog.w("serial_cache_write_failed", error = e)
        }
    }

    /** Removes the cached value (debug Developer options, TC-006). */
    suspend fun clear() {
        try {
            dataStore.edit { it.remove(SERIAL_NUMBER) }
        } catch (e: IOException) {
            KioskLog.w("serial_cache_clear_failed", error = e)
        }
    }

    companion object {
        /** DataStore file name, without the `.preferences_pb` suffix DataStore adds (technical-design §6.2). */
        const val FILE_NAME = "identity"

        internal val SERIAL_NUMBER = stringPreferencesKey("serialNumber")
    }
}
