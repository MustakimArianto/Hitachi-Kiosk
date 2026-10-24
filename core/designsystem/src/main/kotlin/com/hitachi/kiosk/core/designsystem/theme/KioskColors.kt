package com.hitachi.kiosk.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The color roles of PRD §11.1 (DEV-004). Light theme only (TR-DSP-05). [accent] comes from merchant config
 * `accentColor` (A-38) through `KioskTheme(accent = …)` and equals [LocalAccent].
 */
@Immutable
data class KioskColors(
    val bg: Color = KioskPalette.Bg,
    val surface: Color = KioskPalette.Surface,
    val surfaceAlt: Color = KioskPalette.SurfaceAlt,
    val ink: Color = KioskPalette.Ink,
    val muted: Color = KioskPalette.Muted,
    val line: Color = KioskPalette.Line,
    val cardBorder: Color = KioskPalette.CardBorder,
    val radioOff: Color = KioskPalette.RadioOff,
    val success: Color = KioskPalette.Success,
    val successBg: Color = KioskPalette.SuccessBg,
    val successBgAlt: Color = KioskPalette.SuccessBgAlt,
    val accent: Color = KioskPalette.AccentPaprika,
    val dockMuted: Color = KioskPalette.DockMuted,
    val dockMutedAlt: Color = KioskPalette.DockMutedAlt,
    val dockTile: Color = KioskPalette.DockTile,
    val dockTileBorder: Color = KioskPalette.DockTileBorder,
    val dockDisabled: Color = KioskPalette.DockDisabled,
    val dockDisabledText: Color = KioskPalette.DockDisabledText,
    val floatingShadow: Color = KioskPalette.FloatingShadow,
    val raisedShadow: Color = KioskPalette.RaisedShadow,
    val errorBg: Color = KioskPalette.ErrorBg,
    val errorBorder: Color = KioskPalette.ErrorBorder,
    val errorText: Color = KioskPalette.ErrorText,
    val closedDot: Color = KioskPalette.ClosedDot,
    val skeletonHighlight: Color = KioskPalette.SkeletonHighlight,
    val photoTone: Color = KioskPalette.PhotoTone,
    val warningBg: Color = KioskPalette.WarningBg,
    val warningBorder: Color = KioskPalette.WarningBorder,
    val warningTrack: Color = KioskPalette.WarningTrack,
    val warningAccent: Color = KioskPalette.WarningAccent,
    val warningText: Color = KioskPalette.WarningText,
    val alert: Color = KioskPalette.Alert,
    val dockControl: Color = KioskPalette.DockControl,
    val dockTrack: Color = KioskPalette.DockTrack,
    val dockSubtle: Color = KioskPalette.DockSubtle,
    val dockChipText: Color = KioskPalette.DockChipText,
    val dockRingOff: Color = KioskPalette.DockRingOff,
    val shortText: Color = KioskPalette.ShortText,
    val shortBg: Color = KioskPalette.ShortBg,
    val rowDivider: Color = KioskPalette.RowDivider,
    val switchOff: Color = KioskPalette.SwitchOff,
    val sheetScrim: Color = KioskPalette.SheetScrim,
)

/** The [KioskColors] of the current [KioskTheme] (DEV-004). */
val LocalKioskColors = staticCompositionLocalOf { KioskColors() }

/** The merchant accent color (A-38), set by [KioskTheme]. Defaults to [KioskPalette.AccentPaprika]. */
val LocalAccent = staticCompositionLocalOf { KioskPalette.AccentPaprika }
