package com.hitachi.kiosk.core.designsystem.unit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Width of the design artboard in design px (PRD §11.2, NFR-002). */
const val DESIGN_WIDTH_PX: Int = 1080

/** Height of the design artboard in design px (PRD §11.2, NFR-002). */
const val DESIGN_HEIGHT_PX: Int = 1920

/** Widest supported display, 1200 × 1920 (10:16), in design px (TR-DSP-02). */
const val MAX_DESIGN_WIDTH_PX: Int = 1200

/** Text multiplier applied by [spx] when Larger text is on (PRD §11.2, FR-026, TR-DSP-04). */
const val LARGE_TEXT_MULTIPLIER: Float = 1.15f

/**
 * Number of dp per design px: `frameWidthDp / 1080` (PRD §11.2, TR-DSP-01). Provided by [DesignFrame]; the
 * default of `1f` is only right for a 1080 dp wide window (for example a 1080 × 1920 mdpi preview).
 */
val LocalDesignScale = staticCompositionLocalOf { 1f }

/** Whether the staff "Larger text" setting is on, so [spx] multiplies by [LARGE_TEXT_MULTIPLIER] (FR-026). */
val LocalLargeText = staticCompositionLocalOf { false }

/** A size in design px converted with [LocalDesignScale] (PRD §11.2, TR-DSP-01, DEV-003). */
val Int.dpx: Dp
    @Composable
    @ReadOnlyComposable
    get() = designPxToDp(this, LocalDesignScale.current)

/**
 * A text size in design px converted with [LocalDesignScale], times [LARGE_TEXT_MULTIPLIER] when [LocalLargeText]
 * is on (PRD §11.2, TR-DSP-01, TR-DSP-04, DEV-003).
 */
val Int.spx: TextUnit
    @Composable
    @ReadOnlyComposable
    get() = designPxToSp(this, LocalDesignScale.current, LocalLargeText.current)

/** The design scale for a frame [frameWidthDp] wide: `frameWidthDp / 1080` (PRD §11.2, DEV-003). */
fun designScale(frameWidthDp: Float): Float = frameWidthDp / DESIGN_WIDTH_PX

/** Converts [designPx] to dp at [scale] outside composition (PRD §11.2, DEV-003). */
fun designPxToDp(
    designPx: Int,
    scale: Float,
): Dp = (designPx * scale).dp

/** Converts a text size of [designPx] to sp at [scale], applying [LARGE_TEXT_MULTIPLIER] when [largeText] (DEV-003). */
fun designPxToSp(
    designPx: Int,
    scale: Float,
    largeText: Boolean,
): TextUnit = (designPx * scale * textMultiplier(largeText)).sp

/** `1.15` when [largeText] is on, otherwise `1` (PRD §11.2, FR-026). */
fun textMultiplier(largeText: Boolean): Float = if (largeText) LARGE_TEXT_MULTIPLIER else 1f
