package com.hitachi.kiosk.core.data.mapper

import com.hitachi.kiosk.core.data.dto.BadgeDto
import com.hitachi.kiosk.core.data.dto.ComboDto
import com.hitachi.kiosk.core.data.dto.ComboSlotOptionDto
import com.hitachi.kiosk.core.data.dto.LevelPriceDto
import com.hitachi.kiosk.core.data.dto.MenuDto
import com.hitachi.kiosk.core.data.dto.ModifierGroupDto
import com.hitachi.kiosk.core.data.dto.ModifierOptionDto
import com.hitachi.kiosk.core.data.dto.ProductDto
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Badge
import com.hitachi.kiosk.core.domain.model.BadgeType
import com.hitachi.kiosk.core.domain.model.Category
import com.hitachi.kiosk.core.domain.model.Combo
import com.hitachi.kiosk.core.domain.model.ComboSlotOption
import com.hitachi.kiosk.core.domain.model.ComboSlots
import com.hitachi.kiosk.core.domain.model.CombosCategory
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.LevelPrice
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.ModifierGroup
import com.hitachi.kiosk.core.domain.model.ModifierOption
import com.hitachi.kiosk.core.domain.model.ModifierPurpose
import com.hitachi.kiosk.core.domain.model.ModifierType
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.ProductRole

/**
 * E6 → [Menu]. `promoBanners` is dropped (api-spec E6). Unknown values are handled explicitly
 * (technical-design §5.1): a missing or unknown product role becomes `MAIN`, an unknown badge is not shown, and a
 * modifier group of an unknown type or a level of an unknown name is left out; each case logs a warning.
 */
internal fun MenuDto.toDomain(): Menu =
    Menu(
        menuVersion = menuVersion,
        categories = categories.map { Category(id = it.id, name = it.name.toDomain(), sort = it.sort, tone = it.tone) },
        combosCategory = combosCategory?.let { CombosCategory(name = it.name.toDomain(), sort = it.sort) },
        featuredProductIds = featuredProductIds,
        products = products.map { it.toDomain() },
        modifierGroups =
            modifierGroups.mapKnown<ModifierGroupDto, ModifierType, ModifierGroup>(
                "menu.modifierGroups.type",
                { it.type },
            ) { dto, type -> dto.toDomain(type) },
        combos = combos.map { it.toDomain() },
        comboSlots =
            ComboSlots(
                sides = comboSlots.sides.map { it.toDomain() },
                drinks = comboSlots.drinks.map { it.toDomain() },
                sizeLargeUpcharge = comboSlots.sizeLargeUpcharge.toRupiah(),
            ),
    )

internal fun ProductDto.toDomain(): Product =
    Product(
        id = id,
        categoryId = categoryId,
        name = name.toDomain(),
        description = description?.toDomain(),
        price = price.toRupiah(),
        kcal = kcal,
        badge = badge?.toDomain(),
        tags = tags?.toDomain(),
        imageUrl = imageUrl,
        available = available,
        modifierGroupIds = modifierGroupIds,
        role = enumOr(role, ProductRole.MAIN, "menu.products.role"),
        kidFriendly = kidFriendly,
        servesAdults = servesAdults,
        shareable = shareable,
        plannerWeight = plannerWeight,
    )

private fun BadgeDto.toDomain(): Badge? {
    val badgeType = enumOrNull<BadgeType>(type)
    if (badgeType == null) KioskLog.w("api_enum_dropped", "field" to "menu.products.badge.type")
    return badgeType?.let { Badge(type = it, label = label?.toDomain()) }
}

private fun ModifierGroupDto.toDomain(type: ModifierType): ModifierGroup =
    ModifierGroup(
        id = id,
        name = name.toDomain(),
        type = type,
        required = required,
        min = min,
        max = max,
        options = options.map { it.toDomain() },
        purpose = purpose?.let(::purposeOrNull),
    )

/** An unknown purpose is a plain group (api-spec E6, OQ-17); it logs the field name only. */
private fun purposeOrNull(value: String): ModifierPurpose? =
    enumOrNull<ModifierPurpose>(value).also {
        if (it == null) KioskLog.w("api_enum_dropped", "field" to "menu.modifierGroups.purpose")
    }

private fun ModifierOptionDto.toDomain(): ModifierOption =
    ModifierOption(
        id = id,
        name = name.toDomain(),
        description = description?.toDomain(),
        note = note?.toDomain(),
        priceDelta = priceDelta.toRupiah(),
        isDefault = isDefault,
        imageUrl = imageUrl,
        levels =
            levels.mapKnown<LevelPriceDto, Level, LevelPrice>("menu.options.levels.level", { it.level }) { dto, level ->
                LevelPrice(level = level, priceDelta = dto.priceDelta.toRupiah())
            },
        defaultLevel = defaultLevel?.let { enumOr(it, Level.REGULAR, "menu.options.defaultLevel") },
    )

private fun ComboDto.toDomain(): Combo =
    Combo(
        id = id,
        name = name.toDomain(),
        shortName = shortName?.toDomain(),
        includes = includes?.toDomain(),
        price = price.toRupiah(),
        separatePrice = separatePrice.toRupiah(),
        serves = serves,
        imageUrl = imageUrl,
        tone = tone,
        available = available,
    )

private fun ComboSlotOptionDto.toDomain(): ComboSlotOption =
    ComboSlotOption(
        id = id,
        name = name.toDomain(),
        upcharge = upcharge.toRupiah(),
        imageUrl = imageUrl,
        available = available,
    )
