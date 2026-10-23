package com.hitachi.kiosk.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The "Linen & Paprika" color tokens from PRD §11.1 (D-02, DEV-004). This is the only file in the project that
 * holds color literals (conventions §5); everything else reads [KioskColors] through `KioskTheme.colors`.
 */
object KioskPalette {
    /** `bg`: screen background, letterbox fill. */
    val Bg = Color(0xFFFAF8F5)

    /** `ink`: primary text, dark docks, selected pills. */
    val Ink = Color(0xFF171513)

    /** `muted`: secondary text. */
    val Muted = Color(0xFF6F6961)

    /** `line`: outline pills, progress tracks. */
    val Line = Color(0xFFE9E4DC)

    /** `surfaceAlt`: pills, segmented tracks, chips. */
    val SurfaceAlt = Color(0xFFF1EDE7)

    /** `cardBorder`: card outlines. */
    val CardBorder = Color(0xFFEFE9E0)

    /** `radioOff`: unselected radio and check borders. */
    val RadioOff = Color(0xFFD9D2C7)

    /** `success`: Open now, Save, Secure payment, not-charged note, toggles on. */
    val Success = Color(0xFF2F7A50)

    /** `successBg`: background of success chips and notes. */
    val SuccessBg = Color(0xFFEAF3EC)

    /** Second `successBg` value used by the design (PRD §11.1). */
    val SuccessBgAlt = Color(0xFFE6F0E9)

    /** Default merchant accent, "Paprika" (A-38). */
    val AccentPaprika = Color(0xFFC8552B)

    /** Accent option, green (A-38). */
    val AccentForest = Color(0xFF2F5D4A)

    /** Accent option, blue (A-38). */
    val AccentIndigo = Color(0xFF3A4C7A)

    /** Accent option, ink (A-38). */
    val AccentInk = Color(0xFF171513)

    /** `dockMuted`: secondary text on dark docks. */
    val DockMuted = Color(0xFFB9B2A8)

    /** Second `dockMuted` value used by the design (PRD §11.1). */
    val DockMutedAlt = Color(0xFFB5AEA4)

    /** `dockTile`: payment method tiles on the dark dock. */
    val DockTile = Color(0xFF211E1B)

    /** `dockTileBorder`: payment method tile outline. */
    val DockTileBorder = Color(0xFF2E2B27)

    /** `dockDisabled`: background of the disabled dock ("Add an item to pay"). */
    val DockDisabled = Color(0xFF26231F)

    /** Text on [DockDisabled]. */
    val DockDisabledText = Color(0xFF8F887E)

    /** `surface`: cards, rows, sheets. */
    val Surface = Color(0xFFFFFFFF)

    /** The floating elevation's shadow color, `rgba(23,21,19,.10)` (PRD §11.1). */
    val FloatingShadow = Ink.copy(alpha = 0.10f)

    /** The accent the merchant config may choose from, default first (A-38). */
    val AccentOptions: List<Color> = listOf(AccentPaprika, AccentForest, AccentIndigo, AccentInk)
}
