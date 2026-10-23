package com.hitachi.kiosk.core.data.di

import android.content.Context
import androidx.room.Room
import com.hitachi.kiosk.core.data.db.KioskDatabase
import com.hitachi.kiosk.core.data.db.dao.CatalogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The Room database (architecture §6, technical-design §6.1). Before 1.0.0 only cache tables exist, so a schema
 * change may drop and re-download them (TR-DAT-04); from 0.5.0 `pending_order` needs real migrations.
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
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun catalogDao(database: KioskDatabase): CatalogDao = database.catalogDao()
}
