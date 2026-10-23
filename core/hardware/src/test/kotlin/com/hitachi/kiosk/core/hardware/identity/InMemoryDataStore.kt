package com.hitachi.kiosk.core.hardware.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/** A [DataStore] kept in memory, which can be made to fail like a broken file. */
class InMemoryDataStore : DataStore<Preferences> {
    var current: Preferences = emptyPreferences()
        private set

    /** When set, every read and write throws it. */
    var failure: IOException? = null

    override val data: Flow<Preferences> =
        flow {
            failure?.let { throw it }
            emit(current)
        }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        failure?.let { throw it }
        return transform(current).also { current = it }
    }
}
