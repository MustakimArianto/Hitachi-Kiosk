package com.hitachi.kiosk.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.hitachi.kiosk.core.data.prefs.DeviceStore
import com.hitachi.kiosk.core.data.prefs.EncryptedSecureStoreSerializer
import com.hitachi.kiosk.core.data.prefs.KeystoreAead
import com.hitachi.kiosk.core.data.prefs.SecureStore
import com.hitachi.kiosk.core.data.prefs.SecureTokenStore
import com.hitachi.kiosk.core.data.prefs.TokenStore
import com.hitachi.kiosk.core.domain.log.KioskLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The DataStore files of `:core:data` (architecture §6, technical-design §6.2–§6.3). DataStore allows one instance
 * per file and process, so the instances live at process level, outside the Hilt graph (which instrumented tests
 * rebuild for every test). DataStore does its I/O on its own background scope.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {
    @Provides
    @Singleton
    fun deviceStore(
        @ApplicationContext context: Context,
    ): DeviceStore = DeviceStore(context.deviceDataStore)

    @Provides
    @Singleton
    fun tokenStore(
        @ApplicationContext context: Context,
    ): TokenStore = SecureTokenStore(SecureDataStore.get(context))
}

/** `device.preferences_pb`. Unreadable content is replaced: FR-001 treats it as "not activated", never a crash. */
private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "device",
    corruptionHandler =
        ReplaceFileCorruptionHandler {
            KioskLog.e("device_store_corrupt")
            emptyPreferences()
        },
)

/** The one `secure.pb` DataStore of the process, encrypted with the Keystore-backed [KeystoreAead]. */
private object SecureDataStore {
    @Volatile
    private var instance: DataStore<SecureStore>? = null

    fun get(context: Context): DataStore<SecureStore> =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    private fun create(context: Context): DataStore<SecureStore> =
        DataStoreFactory.create(
            serializer = EncryptedSecureStoreSerializer { KeystoreAead.create(context) },
            corruptionHandler =
                ReplaceFileCorruptionHandler {
                    KioskLog.e("token_store_reset", "cause" to "corrupt")
                    SecureStore()
                },
            produceFile = { context.dataStoreFile("secure.pb") },
        )
}
