package com.hitachi.kiosk.core.hardware.di

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.hitachi.kiosk.core.hardware.identity.BuildSerialSource
import com.hitachi.kiosk.core.hardware.identity.DeviceBuild
import com.hitachi.kiosk.core.hardware.identity.SERIAL_PROPERTY_KEYS
import com.hitachi.kiosk.core.hardware.identity.SerialCache
import com.hitachi.kiosk.core.hardware.identity.SerialSources
import com.hitachi.kiosk.core.hardware.identity.SystemPropertySerialSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `identity.preferences_pb` (technical-design §6.2). A process-wide delegate, not one DataStore per Hilt component:
 * instrumented tests rebuild the component in one process, and two DataStores on one file fail.
 */
private val Context.identityDataStore: DataStore<Preferences> by preferencesDataStore(name = SerialCache.FILE_NAME)

/**
 * Hardware implementations (architecture §10). `:app` binds the domain ports to them, so debug builds can wrap them
 * (technical-design §4.3).
 */
@Module
@InstallIn(SingletonComponent::class)
object HardwareModule {
    /** The serial-number sources in the order they are tried (FR-002, ADR-0002). */
    @Provides
    fun serialSources(
        @ApplicationContext context: Context,
    ): SerialSources =
        SerialSources(
            SERIAL_PROPERTY_KEYS.map { SystemPropertySerialSource(it) } + BuildSerialSource.forDevice(context),
        )

    @Provides
    @Singleton
    fun serialCache(
        @ApplicationContext context: Context,
    ): SerialCache = SerialCache(context.identityDataStore)

    @Provides
    fun deviceBuild(): DeviceBuild = DeviceBuild(model = Build.MODEL, osVersion = Build.VERSION.RELEASE)
}
