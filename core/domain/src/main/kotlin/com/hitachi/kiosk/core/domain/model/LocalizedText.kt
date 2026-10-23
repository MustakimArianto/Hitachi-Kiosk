package com.hitachi.kiosk.core.domain.model

/**
 * Text the backend may send in English and Bahasa Indonesia (api-spec §2.3). The 1.0 UI is English only (D-29),
 * so [resolve] returns English and falls back to Indonesian only when English is missing.
 */
data class LocalizedText(
    val en: String? = null,
    val id: String? = null,
) {
    fun resolve(): String = en?.takeIf { it.isNotBlank() } ?: id.orEmpty()
}
