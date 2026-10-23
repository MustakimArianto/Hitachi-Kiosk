package com.hitachi.kiosk.core.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException

/** A [DeviceStore] on a fresh `device.preferences_pb` in [folder]. */
internal fun newDeviceStore(folder: File): DeviceStore =
    DeviceStore(PreferenceDataStoreFactory.create(produceFile = { File(folder, "device.preferences_pb") }))

/** A [TokenStore] in memory. [failReads] makes [read] throw, like a store that cannot be decrypted. */
internal class InMemoryTokenStore(
    var token: String? = null,
) : TokenStore {
    var failReads = false

    override suspend fun read(): String? {
        if (failReads) throw IOException("unreadable")
        return token
    }

    override suspend fun write(token: String) {
        this.token = token
    }

    override suspend fun clear() {
        token = null
    }
}
