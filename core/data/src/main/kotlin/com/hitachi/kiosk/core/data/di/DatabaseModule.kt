package com.hitachi.kiosk.core.data.di

import android.content.Context
import androidx.room.Room
import com.hitachi.kiosk.core.data.db.KioskDatabase
import com.hitachi.kiosk.core.data.db.addKioskMigrations
import com.hitachi.kiosk.core.data.db.dao.CatalogDao
import com.hitachi.kiosk.core.data.db.dao.PendingOrderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The Room database (architecture §6, technical-design §6.1). Since 0.5.0 it holds `pending_order`, which must
 * survive an upgrade, so every schema change has a real `Migration` and there is no destructive fallback, not even
 * for the cache tables (TR-DAT-04, TR-REL-09).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): KioskDatabase =
        Room
            .databaseBuilder(context, KioskDatabase::class.java, KioskDatabase.NAME)
            .addKioskMigrations()
            .build()

    @Provides
    fun catalogDao(database: KioskDatabase): CatalogDao = database.catalogDao()

    @Provides
    fun pendingOrderDao(database: KioskDatabase): PendingOrderDao = database.pendingOrderDao()
}
