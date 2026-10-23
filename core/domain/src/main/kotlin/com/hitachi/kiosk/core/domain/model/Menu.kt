package com.hitachi.kiosk.core.domain.model

/**
 * The full menu from E6 (FR-009–FR-011, FR-032). Promo banners in the contract are ignored: the final design has
 * no promo carousel (api-spec E6).
 */
data class Menu(
    val menuVersion: String,
    val categories: List<Category>,
    val combosCategory: CombosCategory?,
    val featuredProductIds: List<String>,
    val products: List<Product>,
    val modifierGroups: List<ModifierGroup>,
    val combos: List<Combo>,
    val comboSlots: ComboSlots,
)

data class Category(
    val id: String,
    val name: LocalizedText,
    val sort: Int,
    val tone: String?,
)

/** Where the "Meals" pill sits among the categories. */
data class CombosCategory(
    val name: LocalizedText,
    val sort: Int,
)

data class Product(
    val id: String,
    val categoryId: String,
    val name: LocalizedText,
    val description: LocalizedText?,
    val price: Rupiah,
    val kcal: Int?,
    val badge: Badge?,
    val tags: LocalizedText?,
    val imageUrl: String?,
    val available: Boolean,
    val modifierGroupIds: List<String>,
    val role: ProductRole,
    val kidFriendly: Boolean,
    val servesAdults: Int,
    val shareable: Boolean,
    val plannerWeight: Int,
)

data class Badge(
    val type: BadgeType,
    val label: LocalizedText?,
)

enum class BadgeType { NEW, BEST_SELLER, SPICY, SHARE, LOCAL }

/** Role of a product in a Budget Plan (FR-032). */
enum class ProductRole { MAIN, SIDE, DRINK, DESSERT }

data class ModifierGroup(
    val id: String,
    val name: LocalizedText,
    val type: ModifierType,
    val required: Boolean,
    val min: Int?,
    val max: Int?,
    val options: List<ModifierOption>,
)

/** `SINGLE` = radio, `MULTI` = checkboxes, `LEVEL` = None / Regular / Extra per option (api-spec E6). */
enum class ModifierType { SINGLE, MULTI, LEVEL }

data class ModifierOption(
    val id: String,
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val note: LocalizedText? = null,
    val priceDelta: Rupiah = Rupiah.Zero,
    val isDefault: Boolean = false,
    val imageUrl: String? = null,
    val levels: List<LevelPrice> = emptyList(),
    val defaultLevel: Level? = null,
)

data class LevelPrice(
    val level: Level,
    val priceDelta: Rupiah,
)

enum class Level { NONE, REGULAR, EXTRA }

data class Combo(
    val id: String,
    val name: LocalizedText,
    val shortName: LocalizedText?,
    val includes: LocalizedText?,
    val price: Rupiah,
    val separatePrice: Rupiah,
    val serves: Int,
    val imageUrl: String?,
    val tone: String?,
    val available: Boolean,
)

/** Side and drink choices shared by every combo, plus the large-size upcharge (FR-011). */
data class ComboSlots(
    val sides: List<ComboSlotOption>,
    val drinks: List<ComboSlotOption>,
    val sizeLargeUpcharge: Rupiah,
)

data class ComboSlotOption(
    val id: String,
    val name: LocalizedText,
    val upcharge: Rupiah,
    val imageUrl: String?,
)
