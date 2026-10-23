package com.hitachi.kiosk.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.hitachi.kiosk.core.designsystem.unit.designPxToDp

/** Corner radii of PRD §11.1 in design px (DEV-004). */
object KioskRadius {
    /** Pills: 999 px in the design, a fully rounded end. */
    const val PILL_PX: Int = 999

    /** Docks. */
    const val DOCK_PX: Int = 48

    /** Cards. */
    const val CARD_PX: Int = 40

    /** Larger inner elements. */
    const val LARGE_PX: Int = 32

    /** Medium inner elements. */
    const val MEDIUM_PX: Int = 28

    /** Small inner elements. */
    const val SMALL_PX: Int = 24
}

/** The corner shapes of PRD §11.1, converted with the design scale (DEV-004). Read through `KioskTheme.shapes`. */
@Immutable
data class KioskShapes(
    /** Fully rounded ends (999 px in the design). */
    val pill: RoundedCornerShape,
    /** 48 px: bottom docks. */
    val dock: RoundedCornerShape,
    /** 40 px: cards. */
    val card: RoundedCornerShape,
    /** 32 px. */
    val large: RoundedCornerShape,
    /** 28 px. */
    val medium: RoundedCornerShape,
    /** 24 px. */
    val small: RoundedCornerShape,
)

/** Builds [KioskShapes] for a design [scale] (PRD §11.1–11.2, DEV-004). */
fun kioskShapes(scale: Float = 1f): KioskShapes {
    fun rounded(px: Int) = RoundedCornerShape(designPxToDp(px, scale))
    return KioskShapes(
        pill = RoundedCornerShape(percent = 50),
        dock = rounded(KioskRadius.DOCK_PX),
        card = rounded(KioskRadius.CARD_PX),
        large = rounded(KioskRadius.LARGE_PX),
        medium = rounded(KioskRadius.MEDIUM_PX),
        small = rounded(KioskRadius.SMALL_PX),
    )
}

/** The [KioskShapes] of the current [KioskTheme] (DEV-004). */
val LocalKioskShapes = staticCompositionLocalOf { kioskShapes() }
