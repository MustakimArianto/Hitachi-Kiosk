package com.hitachi.kiosk.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.hitachi.kiosk.core.designsystem.unit.designPxToDp

/** Layout tokens of PRD §11.1 in design px (DEV-004). */
object KioskLayout {
    /** Side margin of every screen. */
    const val SIDE_MARGIN_PX: Int = 72

    /** Content width between the side margins, 1080 − 2 × 72. */
    const val CONTENT_WIDTH_PX: Int = 936

    /** Distance from the top of the screen to the header. */
    const val HEADER_TOP_PX: Int = 48

    /** Header height. */
    const val HEADER_HEIGHT_PX: Int = 96

    /** Spacing grid step (8-pt grid). */
    const val GRID_PX: Int = 8

    /** Minimum touch target (NFR-007). */
    const val MIN_TOUCH_TARGET_PX: Int = 96

    /** Floating elevation: vertical shadow offset (`0 24 48`). */
    const val FLOATING_OFFSET_YPX: Int = 24

    /** Floating elevation: shadow blur radius (`0 24 48`). */
    const val FLOATING_BLUR_PX: Int = 48
}

/** Layout tokens of PRD §11.1 converted with the design scale (DEV-004). Read through `KioskTheme.spacing`. */
@Immutable
data class KioskSpacing(
    /** 72 px side margin. */
    val sideMargin: Dp,
    /** 936 px content width. */
    val contentWidth: Dp,
    /** 48 px from the top to the header. */
    val headerTop: Dp,
    /** 96 px header height. */
    val headerHeight: Dp,
    /** 8 px grid step; multiply for larger gaps. */
    val grid: Dp,
    /** 96 px minimum touch target. */
    val minTouchTarget: Dp,
)

/** Builds [KioskSpacing] for a design [scale] (PRD §11.1–11.2, DEV-004). */
fun kioskSpacing(scale: Float = 1f): KioskSpacing =
    KioskSpacing(
        sideMargin = designPxToDp(KioskLayout.SIDE_MARGIN_PX, scale),
        contentWidth = designPxToDp(KioskLayout.CONTENT_WIDTH_PX, scale),
        headerTop = designPxToDp(KioskLayout.HEADER_TOP_PX, scale),
        headerHeight = designPxToDp(KioskLayout.HEADER_HEIGHT_PX, scale),
        grid = designPxToDp(KioskLayout.GRID_PX, scale),
        minTouchTarget = designPxToDp(KioskLayout.MIN_TOUCH_TARGET_PX, scale),
    )

/** The [KioskSpacing] of the current [KioskTheme] (DEV-004). */
val LocalKioskSpacing = staticCompositionLocalOf { kioskSpacing() }

/**
 * A drop shadow, as in the CSS `box-shadow: 0 offsetY blur color` of the design (PRD §11.1). Draw it with
 * `Modifier.dropShadow`.
 */
@Immutable
data class KioskShadow(
    /** Vertical offset. */
    val offsetY: Dp,
    /** Blur radius. */
    val blurRadius: Dp,
    /** Shadow color including its alpha. */
    val color: Color,
)

/**
 * The two elevations of PRD §11.1: flat (no shadow) and [floating], `0 24 48 rgba(23,21,19,.10)`, for docks
 * and overlays (DEV-004). Read through `KioskTheme.elevation`.
 */
@Immutable
data class KioskElevation(
    /** Docks and overlays. */
    val floating: KioskShadow,
)

/** Builds [KioskElevation] for a design [scale] (PRD §11.1, DEV-004). */
fun kioskElevation(
    scale: Float = 1f,
    colors: KioskColors = KioskColors(),
): KioskElevation =
    KioskElevation(
        floating =
            KioskShadow(
                offsetY = designPxToDp(KioskLayout.FLOATING_OFFSET_YPX, scale),
                blurRadius = designPxToDp(KioskLayout.FLOATING_BLUR_PX, scale),
                color = colors.floatingShadow,
            ),
    )
