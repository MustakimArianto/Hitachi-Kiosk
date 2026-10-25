package com.hitachi.kiosk.core.data.mapper

import com.hitachi.kiosk.core.data.db.dao.CatalogRows
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
import com.hitachi.kiosk.core.data.dto.MerchantConfigDto
import com.hitachi.kiosk.core.domain.model.Badge
import com.hitachi.kiosk.core.domain.model.BadgeType
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.Category
import com.hitachi.kiosk.core.domain.model.Combo
import com.hitachi.kiosk.core.domain.model.ComboSlotOption
import com.hitachi.kiosk.core.domain.model.ComboSlots
import com.hitachi.kiosk.core.domain.model.CombosCategory
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.LevelPrice
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.ModifierGroup
import com.hitachi.kiosk.core.domain.model.ModifierOption
import com.hitachi.kiosk.core.domain.model.ModifierPurpose
import com.hitachi.kiosk.core.domain.model.ModifierType
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah
import kotlinx.serialization.json.Json

// Catalog ↔ Room rows (technical-design §6.1). The rows are written by this module only, so their enum names are
// always known values; a row that does not map throws, and the store treats the cache as empty (TR-REL-07).

private const val SLOT_SIDE = "SIDE"
private const val SLOT_DRINK = "DRINK"

/** ETags and fetch time stored beside a catalog (TR-NET-09). */
internal data class CatalogStamp(
    val configEtag: String?,
    val menuEtag: String?,
    val fetchedAtMillis: Long,
)

internal fun Catalog.toRows(
    stamp: CatalogStamp,
    json: Json,
): CatalogRows {
    val merchantId = config.merchantId
    return CatalogRows(
        config =
            ConfigEntity(
                merchantId = merchantId,
                etag = stamp.configEtag,
                json = json.encodeToString(MerchantConfigDto.serializer(), config.toDto()),
                fetchedAtMillis = stamp.fetchedAtMillis,
            ),
        meta = menu.toMetaEntity(merchantId, stamp),
        categories =
            menu.categories.mapIndexed { i, category ->
                CategoryEntity(category.id, i, category.sort, category.name.en, category.name.id, category.tone)
            },
        products = menu.products.mapIndexed { i, product -> product.toEntity(i) },
        productGroups =
            menu.products.flatMap { product ->
                product.modifierGroupIds.mapIndexed { i, groupId -> ProductModifierGroupEntity(product.id, groupId, i) }
            },
        groups =
            menu.modifierGroups.mapIndexed { i, group ->
                ModifierGroupEntity(
                    group.id,
                    i,
                    group.name.en,
                    group.name.id,
                    group.type.name,
                    group.required,
                    group.min,
                    group.max,
                    group.purpose?.name,
                )
            },
        options =
            menu.modifierGroups.flatMap { group ->
                group.options.mapIndexed { i, option -> option.toEntity(group.id, i) }
            },
        levels =
            menu.modifierGroups.flatMap { group ->
                group.options.flatMap { option ->
                    option.levels.mapIndexed { i, price ->
                        ModifierLevelEntity(group.id, option.id, price.level.name, i, price.priceDelta.value)
                    }
                }
            },
        combos = menu.combos.mapIndexed { i, combo -> combo.toEntity(i) },
        slotOptions =
            menu.comboSlots.sides.mapIndexed { i, side -> side.toEntity(SLOT_SIDE, i) } +
                menu.comboSlots.drinks.mapIndexed { i, drink -> drink.toEntity(SLOT_DRINK, i) },
        featured = menu.featuredProductIds.mapIndexed { i, id -> FeaturedEntity(i, id) },
    )
}

private fun Menu.toMetaEntity(
    merchantId: String,
    stamp: CatalogStamp,
): MenuMetaEntity =
    MenuMetaEntity(
        merchantId = merchantId,
        menuVersion = menuVersion,
        etag = stamp.menuEtag,
        fetchedAtMillis = stamp.fetchedAtMillis,
        combosNameEn = combosCategory?.name?.en,
        combosNameId = combosCategory?.name?.id,
        combosSort = combosCategory?.sort,
        sizeLargeUpcharge = comboSlots.sizeLargeUpcharge.value,
    )

internal fun CatalogRows.toCatalog(json: Json): Catalog =
    Catalog(
        config = json.decodeFromString(MerchantConfigDto.serializer(), config.json).toDomain(),
        menu = toMenu(),
    )

private fun CatalogRows.toMenu(): Menu {
    val groupIdsByProduct = productGroups.groupBy({ it.productId }, { it.groupId })
    val optionsByGroup = options.groupBy { it.groupId }
    val levelsByOption = levels.groupBy { it.groupId to it.optionId }
    return Menu(
        menuVersion = meta.menuVersion,
        categories = categories.map { Category(it.id, text(it.nameEn, it.nameId), it.sort, it.tone) },
        combosCategory =
            meta.combosSort?.let { sort -> CombosCategory(text(meta.combosNameEn, meta.combosNameId), sort) },
        featuredProductIds = featured.map { it.productId },
        products = products.map { it.toDomain(groupIdsByProduct[it.id].orEmpty()) },
        modifierGroups =
            groups.map { group ->
                ModifierGroup(
                    id = group.id,
                    name = text(group.nameEn, group.nameId),
                    type = enumValueOf<ModifierType>(group.type),
                    required = group.required,
                    min = group.min,
                    max = group.max,
                    purpose = enumOrNull<ModifierPurpose>(group.purpose),
                    options =
                        optionsByGroup[group.id].orEmpty().map { option ->
                            option.toDomain(levelsByOption[group.id to option.id].orEmpty())
                        },
                )
            },
        combos = combos.map { it.toDomain() },
        comboSlots =
            ComboSlots(
                sides = slotOptions.filter { it.slot == SLOT_SIDE }.map { it.toDomain() },
                drinks = slotOptions.filter { it.slot == SLOT_DRINK }.map { it.toDomain() },
                sizeLargeUpcharge = Rupiah(meta.sizeLargeUpcharge),
            ),
    )
}

private fun Product.toEntity(position: Int): ProductEntity =
    ProductEntity(
        id = id,
        position = position,
        categoryId = categoryId,
        nameEn = name.en,
        nameId = name.id,
        descriptionEn = description?.en,
        descriptionId = description?.id,
        price = price.value,
        kcal = kcal,
        badgeType = badge?.type?.name,
        badgeLabelEn = badge?.label?.en,
        badgeLabelId = badge?.label?.id,
        tagsEn = tags?.en,
        tagsId = tags?.id,
        imageUrl = imageUrl,
        available = available,
        role = role.name,
        kidFriendly = kidFriendly,
        servesAdults = servesAdults,
        shareable = shareable,
        plannerWeight = plannerWeight,
    )

private fun ProductEntity.toDomain(groupIds: List<String>): Product =
    Product(
        id = id,
        categoryId = categoryId,
        name = text(nameEn, nameId),
        description = textOrNull(descriptionEn, descriptionId),
        price = Rupiah(price),
        kcal = kcal,
        badge = badgeType?.let { Badge(enumValueOf<BadgeType>(it), textOrNull(badgeLabelEn, badgeLabelId)) },
        tags = textOrNull(tagsEn, tagsId),
        imageUrl = imageUrl,
        available = available,
        modifierGroupIds = groupIds,
        role = enumValueOf<ProductRole>(role),
        kidFriendly = kidFriendly,
        servesAdults = servesAdults,
        shareable = shareable,
        plannerWeight = plannerWeight,
    )

private fun ModifierOption.toEntity(
    groupId: String,
    position: Int,
): ModifierOptionEntity =
    ModifierOptionEntity(
        groupId = groupId,
        id = id,
        position = position,
        nameEn = name.en,
        nameId = name.id,
        descriptionEn = description?.en,
        descriptionId = description?.id,
        noteEn = note?.en,
        noteId = note?.id,
        priceDelta = priceDelta.value,
        isDefault = isDefault,
        imageUrl = imageUrl,
        defaultLevel = defaultLevel?.name,
    )

private fun ModifierOptionEntity.toDomain(levels: List<ModifierLevelEntity>): ModifierOption =
    ModifierOption(
        id = id,
        name = text(nameEn, nameId),
        description = textOrNull(descriptionEn, descriptionId),
        note = textOrNull(noteEn, noteId),
        priceDelta = Rupiah(priceDelta),
        isDefault = isDefault,
        imageUrl = imageUrl,
        levels = levels.map { LevelPrice(enumValueOf<Level>(it.level), Rupiah(it.priceDelta)) },
        defaultLevel = defaultLevel?.let { enumValueOf<Level>(it) },
    )

private fun Combo.toEntity(position: Int): ComboEntity =
    ComboEntity(
        id = id,
        position = position,
        nameEn = name.en,
        nameId = name.id,
        shortNameEn = shortName?.en,
        shortNameId = shortName?.id,
        includesEn = includes?.en,
        includesId = includes?.id,
        price = price.value,
        separatePrice = separatePrice.value,
        serves = serves,
        imageUrl = imageUrl,
        tone = tone,
        available = available,
    )

private fun ComboEntity.toDomain(): Combo =
    Combo(
        id = id,
        name = text(nameEn, nameId),
        shortName = textOrNull(shortNameEn, shortNameId),
        includes = textOrNull(includesEn, includesId),
        price = Rupiah(price),
        separatePrice = Rupiah(separatePrice),
        serves = serves,
        imageUrl = imageUrl,
        tone = tone,
        available = available,
    )

private fun ComboSlotOption.toEntity(
    slot: String,
    position: Int,
): ComboSlotOptionEntity =
    ComboSlotOptionEntity(slot, id, position, name.en, name.id, upcharge.value, imageUrl, available)

private fun ComboSlotOptionEntity.toDomain(): ComboSlotOption =
    ComboSlotOption(id, text(nameEn, nameId), Rupiah(upcharge), imageUrl, available)

private fun text(
    en: String?,
    id: String?,
) = LocalizedText(en = en, id = id)

/** An optional text: `null` when both languages are missing. */
private fun textOrNull(
    en: String?,
    id: String?,
): LocalizedText? = if (en == null && id == null) null else LocalizedText(en = en, id = id)
