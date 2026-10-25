package com.hitachi.kiosk.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hitachi.kiosk.core.data.db.dao.CatalogDao
import com.hitachi.kiosk.core.data.db.dao.PendingOrderDao
import com.hitachi.kiosk.core.data.db.entity.CategoryEntity
import com.hitachi.kiosk.core.data.db.entity.ComboEntity
import com.hitachi.kiosk.core.data.db.entity.ComboSlotOptionEntity
import com.hitachi.kiosk.core.data.db.entity.ConfigEntity
import com.hitachi.kiosk.core.data.db.entity.FeaturedEntity
import com.hitachi.kiosk.core.data.db.entity.MenuMetaEntity
import com.hitachi.kiosk.core.data.db.entity.ModifierGroupEntity
import com.hitachi.kiosk.core.data.db.entity.ModifierLevelEntity
import com.hitachi.kiosk.core.data.db.entity.ModifierOptionEntity
import com.hitachi.kiosk.core.data.db.entity.PendingOrderEntity
import com.hitachi.kiosk.core.data.db.entity.ProductEntity
import com.hitachi.kiosk.core.data.db.entity.ProductModifierGroupEntity

/**
 * The app database (technical-design §6.1), schemas exported to `core/data/schemas/`. Version 1 (0.3.0) held the
 * catalog cache; version 2 (0.5.0) adds `pending_order` ([MIGRATION_1_2]); version 3 (0.6.0) adds
 * `modifier_group.purpose` ([MIGRATION_2_3]). `pending_order` never migrates
 * destructively, so every schema change from here on needs a real `Migration` (TR-DAT-04).
 */
@Database(
    version = 3,
    exportSchema = true,
    entities = [
        ConfigEntity::class,
        MenuMetaEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        ProductModifierGroupEntity::class,
        ModifierGroupEntity::class,
        ModifierOptionEntity::class,
        ModifierLevelEntity::class,
        ComboEntity::class,
        ComboSlotOptionEntity::class,
        FeaturedEntity::class,
        PendingOrderEntity::class,
    ],
)
internal abstract class KioskDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun pendingOrderDao(): PendingOrderDao

    companion object {
        const val NAME = "kiosk.db"
    }
}
