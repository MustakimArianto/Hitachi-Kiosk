package com.hitachi.kiosk.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

/** Every row of one cached catalog (technical-design §6.1). */
internal data class CatalogRows(
    val config: ConfigEntity,
    val meta: MenuMetaEntity,
    val categories: List<CategoryEntity>,
    val products: List<ProductEntity>,
    val productGroups: List<ProductModifierGroupEntity>,
    val groups: List<ModifierGroupEntity>,
    val options: List<ModifierOptionEntity>,
    val levels: List<ModifierLevelEntity>,
    val combos: List<ComboEntity>,
    val slotOptions: List<ComboSlotOptionEntity>,
    val featured: List<FeaturedEntity>,
)

/**
 * Reads and replaces the catalog cache. [replaceAll] and [readAll] are single transactions, so E5 and E6 always land
 * and load together, and a failed write keeps the previous catalog (TR-DAT-03, TR-REL-07).
 */
@Dao
@Suppress("TooManyFunctions") // One query per table keeps the transaction explicit.
internal abstract class CatalogDao {
    /** Deletes the cache and writes [rows] in one transaction. */
    @Transaction
    open suspend fun replaceAll(rows: CatalogRows) {
        deleteAll()
        insertConfig(rows.config)
        insertMeta(rows.meta)
        insertCategories(rows.categories)
        insertProducts(rows.products)
        insertProductGroups(rows.productGroups)
        insertGroups(rows.groups)
        insertOptions(rows.options)
        insertLevels(rows.levels)
        insertCombos(rows.combos)
        insertSlotOptions(rows.slotOptions)
        insertFeatured(rows.featured)
    }

    /** The cached catalog, or `null` when the cache is empty or holds only half of it. */
    @Transaction
    open suspend fun readAll(): CatalogRows? {
        val config = config() ?: return null
        val meta = meta(config.merchantId) ?: return null
        return CatalogRows(
            config = config,
            meta = meta,
            categories = categories(),
            products = products(),
            productGroups = productGroups(),
            groups = groups(),
            options = options(),
            levels = levels(),
            combos = combos(),
            slotOptions = slotOptions(),
            featured = featured(),
        )
    }

    /** Empties the cache (Log out, HTTP 401: FR-004 AC3). */
    @Transaction
    open suspend fun deleteAll() {
        deleteConfig()
        deleteMeta()
        deleteCategories()
        deleteProducts()
        deleteProductGroups()
        deleteGroups()
        deleteOptions()
        deleteLevels()
        deleteCombos()
        deleteSlotOptions()
        deleteFeatured()
    }

    @Query("SELECT etag FROM config WHERE merchantId = :merchantId")
    abstract suspend fun configEtag(merchantId: String): String?

    @Query("SELECT etag FROM menu_meta WHERE merchantId = :merchantId")
    abstract suspend fun menuEtag(merchantId: String): String?

    @Query("SELECT * FROM config LIMIT 1")
    protected abstract suspend fun config(): ConfigEntity?

    @Query("SELECT * FROM menu_meta WHERE merchantId = :merchantId")
    protected abstract suspend fun meta(merchantId: String): MenuMetaEntity?

    @Query("SELECT * FROM category ORDER BY position")
    protected abstract suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM product ORDER BY position")
    protected abstract suspend fun products(): List<ProductEntity>

    @Query("SELECT * FROM product_modifier_group ORDER BY productId, position")
    protected abstract suspend fun productGroups(): List<ProductModifierGroupEntity>

    @Query("SELECT * FROM modifier_group ORDER BY position")
    protected abstract suspend fun groups(): List<ModifierGroupEntity>

    @Query("SELECT * FROM modifier_option ORDER BY groupId, position")
    protected abstract suspend fun options(): List<ModifierOptionEntity>

    @Query("SELECT * FROM modifier_level ORDER BY groupId, optionId, position")
    protected abstract suspend fun levels(): List<ModifierLevelEntity>

    @Query("SELECT * FROM combo ORDER BY position")
    protected abstract suspend fun combos(): List<ComboEntity>

    @Query("SELECT * FROM combo_slot_option ORDER BY slot, position")
    protected abstract suspend fun slotOptions(): List<ComboSlotOptionEntity>

    @Query("SELECT * FROM featured ORDER BY position")
    protected abstract suspend fun featured(): List<FeaturedEntity>

    @Insert
    protected abstract suspend fun insertConfig(row: ConfigEntity)

    @Insert
    protected abstract suspend fun insertMeta(row: MenuMetaEntity)

    @Insert
    protected abstract suspend fun insertCategories(rows: List<CategoryEntity>)

    @Insert
    protected abstract suspend fun insertProducts(rows: List<ProductEntity>)

    @Insert
    protected abstract suspend fun insertProductGroups(rows: List<ProductModifierGroupEntity>)

    @Insert
    protected abstract suspend fun insertGroups(rows: List<ModifierGroupEntity>)

    @Insert
    protected abstract suspend fun insertOptions(rows: List<ModifierOptionEntity>)

    @Insert
    protected abstract suspend fun insertLevels(rows: List<ModifierLevelEntity>)

    @Insert
    protected abstract suspend fun insertCombos(rows: List<ComboEntity>)

    @Insert
    protected abstract suspend fun insertSlotOptions(rows: List<ComboSlotOptionEntity>)

    @Insert
    protected abstract suspend fun insertFeatured(rows: List<FeaturedEntity>)

    @Query("DELETE FROM config")
    protected abstract suspend fun deleteConfig()

    @Query("DELETE FROM menu_meta")
    protected abstract suspend fun deleteMeta()

    @Query("DELETE FROM category")
    protected abstract suspend fun deleteCategories()

    @Query("DELETE FROM product")
    protected abstract suspend fun deleteProducts()

    @Query("DELETE FROM product_modifier_group")
    protected abstract suspend fun deleteProductGroups()

    @Query("DELETE FROM modifier_group")
    protected abstract suspend fun deleteGroups()

    @Query("DELETE FROM modifier_option")
    protected abstract suspend fun deleteOptions()

    @Query("DELETE FROM modifier_level")
    protected abstract suspend fun deleteLevels()

    @Query("DELETE FROM combo")
    protected abstract suspend fun deleteCombos()

    @Query("DELETE FROM combo_slot_option")
    protected abstract suspend fun deleteSlotOptions()

    @Query("DELETE FROM featured")
    protected abstract suspend fun deleteFeatured()
}
