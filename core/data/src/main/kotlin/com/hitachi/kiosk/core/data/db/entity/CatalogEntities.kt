package com.hitachi.kiosk.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// The catalog cache of technical-design §6.1. Money columns are INTEGER Rupiah (TR-DAT-01). `{en, id}` texts are
// two columns each (`…En`, `…Id`); every list keeps the E6 order in a `position` column. The menu tables hold one
// merchant's menu at a time and are replaced together in one transaction (TR-DAT-03).

/** E5, stored whole as its JSON body: it is read whole and never queried by field (technical-design §6.1). */
@Entity(tableName = "config")
internal data class ConfigEntity(
    @PrimaryKey val merchantId: String,
    val etag: String?,
    val json: String,
    val fetchedAtMillis: Long,
)

/** E6 metadata: version, ETag and the menu-level fields. */
@Entity(tableName = "menu_meta")
internal data class MenuMetaEntity(
    @PrimaryKey val merchantId: String,
    val menuVersion: String,
    val etag: String?,
    val fetchedAtMillis: Long,
    val combosNameEn: String?,
    val combosNameId: String?,
    val combosSort: Int?,
    val sizeLargeUpcharge: Long,
)

@Entity(tableName = "category")
internal data class CategoryEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val sort: Int,
    val nameEn: String?,
    val nameId: String?,
    val tone: String?,
)

@Entity(tableName = "product")
internal data class ProductEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val categoryId: String,
    val nameEn: String?,
    val nameId: String?,
    val descriptionEn: String?,
    val descriptionId: String?,
    val price: Long,
    val kcal: Int?,
    val badgeType: String?,
    val badgeLabelEn: String?,
    val badgeLabelId: String?,
    val tagsEn: String?,
    val tagsId: String?,
    val imageUrl: String?,
    val available: Boolean,
    val role: String,
    val kidFriendly: Boolean,
    val servesAdults: Int,
    val shareable: Boolean,
    val plannerWeight: Int,
)

/** Which modifier groups a product shows, in order. */
@Entity(tableName = "product_modifier_group", primaryKeys = ["productId", "groupId"])
internal data class ProductModifierGroupEntity(
    val productId: String,
    val groupId: String,
    val position: Int,
)

@Entity(tableName = "modifier_group")
internal data class ModifierGroupEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val nameEn: String?,
    val nameId: String?,
    val type: String,
    val required: Boolean,
    val min: Int?,
    val max: Int?,
    /** `MEAL_UPGRADE` or `null` (api-spec E6 `purpose`, added in schema version 3). */
    val purpose: String? = null,
)

/** Option IDs are unique within their group only (api-spec E6). */
@Entity(tableName = "modifier_option", primaryKeys = ["groupId", "id"])
internal data class ModifierOptionEntity(
    val groupId: String,
    val id: String,
    val position: Int,
    val nameEn: String?,
    val nameId: String?,
    val descriptionEn: String?,
    val descriptionId: String?,
    val noteEn: String?,
    val noteId: String?,
    val priceDelta: Long,
    val isDefault: Boolean,
    val imageUrl: String?,
    val defaultLevel: String?,
)

/** The None / Regular / Extra prices of a `LEVEL` option. */
@Entity(tableName = "modifier_level", primaryKeys = ["groupId", "optionId", "level"])
internal data class ModifierLevelEntity(
    val groupId: String,
    val optionId: String,
    val level: String,
    val position: Int,
    val priceDelta: Long,
)

@Entity(tableName = "combo")
internal data class ComboEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val nameEn: String?,
    val nameId: String?,
    val shortNameEn: String?,
    val shortNameId: String?,
    val includesEn: String?,
    val includesId: String?,
    val price: Long,
    val separatePrice: Long,
    val serves: Int,
    val imageUrl: String?,
    val tone: String?,
    val available: Boolean,
)

/** A combo side or drink; [slot] is `SIDE` or `DRINK`. */
@Entity(tableName = "combo_slot_option", primaryKeys = ["slot", "id"])
internal data class ComboSlotOptionEntity(
    val slot: String,
    val id: String,
    val position: Int,
    val nameEn: String?,
    val nameId: String?,
    val upcharge: Long,
    val imageUrl: String?,
    val available: Boolean,
)

/** The "Popular" list, in order. */
@Entity(tableName = "featured")
internal data class FeaturedEntity(
    @PrimaryKey val position: Int,
    val productId: String,
)
