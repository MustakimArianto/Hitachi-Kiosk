package com.hitachi.kiosk.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hitachi.kiosk.core.data.db.dao.CatalogDao
import com.hitachi.kiosk.core.data.db.entity.CategoryEntity
import com.hitachi.kiosk.core.data.db.entity.ComboEntity
import com.hitachi.kiosk.core.data.db.entity.ComboSlotOptionEntity
import com.hitachi.kiosk.core.data.db.entity.ConfigEntity
import com.hitachi.kiosk.core.data.db.entity.FeaturedEntity
import com.hitachi.kiosk.core.data.db.entity.MenuMetaEntity
import com.hitachi.kiosk.core.data.db.entity.ModifierGroupEntity
import com.hitachi.kiosk.core.data.db.entity.ModifierLevelEntity
import com.hitachi.kiosk.core.data.db.entity.ModifierOptionEntity
import com.hitachi.kiosk.core.data.db.entity.ProductEntity
import com.hitachi.kiosk.core.data.db.entity.ProductModifierGroupEntity

/**
 * The app database (technical-design §6.1): version 1 at 0.3.0, schema exported to `core/data/schemas/`. Until
 * 1.0.0 a schema change of the cache tables may migrate destructively; `pending_order` (0.5.0) never does
 * (TR-DAT-04).
 */
@Database(
    version = 1,
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
    ],
)
internal abstract class KioskDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        const val NAME = "kiosk.db"
    }
}
