package com.hitachi.kiosk.core.domain.model

/**
 * The merchant config (E5) and menu (E6) the kiosk sells from, always from the same sync (FR-028, TR-DAT-03).
 * Customer screens read it from the cache; it changes only between orders (A-29).
 */
data class Catalog(
    val config: MerchantConfig,
    val menu: Menu,
)
