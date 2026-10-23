package com.hitachi.kiosk.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** E6 response. */
@Serializable
internal data class MenuDto(
    val menuVersion: String,
    val categories: List<CategoryDto> = emptyList(),
    val combosCategory: CombosCategoryDto? = null,
    val featuredProductIds: List<String> = emptyList(),
    /** Optional and ignored: the final design has no promo carousel (api-spec E6). Kept untyped so it cannot fail. */
    val promoBanners: JsonElement? = null,
    val products: List<ProductDto> = emptyList(),
    val modifierGroups: List<ModifierGroupDto> = emptyList(),
    val combos: List<ComboDto> = emptyList(),
    val comboSlots: ComboSlotsDto = ComboSlotsDto(),
)

@Serializable
internal data class CategoryDto(
    val id: String,
    val name: LocalizedTextDto,
    val sort: Int = 0,
    val tone: String? = null,
)

@Serializable
internal data class CombosCategoryDto(
    val name: LocalizedTextDto,
    val sort: Int = 0,
)

@Serializable
internal data class ProductDto(
    val id: String,
    val categoryId: String,
    val name: LocalizedTextDto,
    val description: LocalizedTextDto? = null,
    val price: Long,
    val kcal: Int? = null,
    val badge: BadgeDto? = null,
    val tags: LocalizedTextDto? = null,
    val imageUrl: String? = null,
    val available: Boolean,
    val modifierGroupIds: List<String> = emptyList(),
    val role: String? = null,
    val kidFriendly: Boolean = false,
    val servesAdults: Int = 1,
    val shareable: Boolean = false,
    val plannerWeight: Int = 0,
)

@Serializable
internal data class BadgeDto(
    val type: String? = null,
    val label: LocalizedTextDto? = null,
)

@Serializable
internal data class ModifierGroupDto(
    val id: String,
    val name: LocalizedTextDto,
    val type: String,
    val required: Boolean = false,
    val min: Int? = null,
    val max: Int? = null,
    val options: List<ModifierOptionDto> = emptyList(),
)

@Serializable
internal data class ModifierOptionDto(
    val id: String,
    val name: LocalizedTextDto,
    val description: LocalizedTextDto? = null,
    val note: LocalizedTextDto? = null,
    val priceDelta: Long = 0,
    @SerialName("default") val isDefault: Boolean = false,
    val imageUrl: String? = null,
    val levels: List<LevelPriceDto> = emptyList(),
    val defaultLevel: String? = null,
)

@Serializable
internal data class LevelPriceDto(
    val level: String,
    val priceDelta: Long = 0,
)

@Serializable
internal data class ComboDto(
    val id: String,
    val name: LocalizedTextDto,
    val shortName: LocalizedTextDto? = null,
    val includes: LocalizedTextDto? = null,
    val price: Long,
    val separatePrice: Long,
    val serves: Int = 1,
    val imageUrl: String? = null,
    val tone: String? = null,
    val available: Boolean,
)

@Serializable
internal data class ComboSlotsDto(
    @SerialName("SIDE") val sides: List<ComboSlotOptionDto> = emptyList(),
    @SerialName("DRINK") val drinks: List<ComboSlotOptionDto> = emptyList(),
    @SerialName("SIZE_LARGE_UPCHARGE") val sizeLargeUpcharge: Long = 0,
)

@Serializable
internal data class ComboSlotOptionDto(
    val id: String,
    val name: LocalizedTextDto,
    val upcharge: Long = 0,
    val imageUrl: String? = null,
    val available: Boolean = true,
)
