package com.hitachi.kiosk.core.domain.model

// Read-only queries over a [Menu] that the customer screens share (FR-009–FR-011). Lists keep the menu's order.

/** The product with [id], or `null` when the menu has none. */
fun Menu.product(id: String): Product? = products.firstOrNull { it.id == id }

/** The combo with [id], or `null` when the menu has none. */
fun Menu.combo(id: String): Combo? = combos.firstOrNull { it.id == id }

/** Categories in their `sort` order (FR-009). */
fun Menu.sortedCategories(): List<Category> = categories.sortedBy { it.sort }

/** The available products of [categoryId]. Sold-out products are hidden (FR-009, A-09). */
fun Menu.visibleProducts(categoryId: String): List<Product> =
    products.filter { it.categoryId == categoryId && it.available }

/** The available products of the "Popular" list, in `featuredProductIds` order (FR-009, A-09). */
fun Menu.featuredProducts(): List<Product> = featuredProductIds.mapNotNull { product(it) }.filter { it.available }

/** The available combos of the Combo screen (FR-011). */
fun Menu.availableCombos(): List<Combo> = combos.filter { it.available }

/** [product]'s modifier groups in the product's order. An unknown group ID is skipped. */
fun Menu.groupsOf(product: Product): List<ModifierGroup> =
    product.modifierGroupIds.mapNotNull { id -> modifierGroups.firstOrNull { it.id == id } }

/** The placeholder tone of [product]'s category, or `null` when the category has none (NFR-013). */
fun Menu.toneOf(product: Product): String? = categories.firstOrNull { it.id == product.categoryId }?.tone

/**
 * Whether [product] needs a choice before it can be added: some required group has no default that satisfies it.
 * Such a product shows "Choose ›" on the Menu and opens Item detail instead of quick add (FR-009, A-08). A
 * required group with a default does not, because quick add uses the defaults (FR-010).
 */
fun Menu.needsChoice(product: Product): Boolean = groupsOf(product).any { !it.isSatisfiedByDefaults() }

/** Whether the group's default options already meet its minimum (FR-010). A `LEVEL` group always does. */
fun ModifierGroup.isSatisfiedByDefaults(): Boolean =
    when (type) {
        ModifierType.LEVEL -> true
        ModifierType.SINGLE, ModifierType.MULTI -> options.count { it.isDefault } >= minimumChoices()
    }

/** How many options must be chosen: `min`, or one when the group is required without a `min` (api-spec E6). */
fun ModifierGroup.minimumChoices(): Int =
    when {
        type == ModifierType.LEVEL -> 0
        min != null -> min
        required -> 1
        else -> 0
    }
