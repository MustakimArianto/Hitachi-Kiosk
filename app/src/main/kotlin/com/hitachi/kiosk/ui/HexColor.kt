package com.hitachi.kiosk.ui

import androidx.compose.ui.graphics.Color

private const val RGB_DIGITS = 6
private const val HEX_RADIX = 16
private const val OPAQUE = 0xFF000000L

/**
 * A `#RRGGBB` color from merchant data (config `accentColor`, category and combo `tone`, A-38, NFR-013), or `null`
 * when [hex] is missing or malformed, so the caller falls back to the design token.
 */
fun parseHexColor(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    if (digits.length != RGB_DIGITS) return null
    val rgb = digits.toLongOrNull(HEX_RADIX) ?: return null
    return Color(OPAQUE or rgb)
}
