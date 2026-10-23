package com.hitachi.kiosk.core.designsystem.unit

import androidx.compose.runtime.Immutable

/** Narrowest supported aspect ratio, 9:16 (1080 × 1920), as width / height (TR-DSP-02). */
const val MIN_ASPECT_RATIO: Float = DESIGN_WIDTH_PX.toFloat() / DESIGN_HEIGHT_PX

/** Widest supported aspect ratio, 10:16 (1200 × 1920), as width / height (TR-DSP-02). */
const val MAX_ASPECT_RATIO: Float = MAX_DESIGN_WIDTH_PX.toFloat() / DESIGN_HEIGHT_PX

/**
 * The area the app draws in, in dp, after clamping the window into the supported 9:16 to 10:16 range
 * (NFR-002, TR-DSP-02, A-35). The rest of the window is filled with `bg` by [DesignFrame].
 */
@Immutable
data class DesignFrameSize(
    /** Width of the frame in dp. */
    val widthDp: Float,
    /** Height of the frame in dp. */
    val heightDp: Float,
) {
    /** Design scale of this frame, `widthDp / 1080` (PRD §11.2). */
    val scale: Float get() = designScale(widthDp)
}

/**
 * Computes the design frame for a window of [windowWidthDp] × [windowHeightDp] (TR-DSP-02, TC-141).
 *
 * - Inside the supported range (aspect 9:16 to 10:16) the frame is the whole window.
 * - A taller window (aspect below 9:16) keeps its width and is letterboxed vertically to 9:16.
 * - A wider window (aspect above 10:16) keeps its height and is pillarboxed to 10:16.
 *
 * Both sizes must be positive; an unbounded side is clamped by the other one.
 */
fun designFrameSize(
    windowWidthDp: Float,
    windowHeightDp: Float,
): DesignFrameSize {
    require(windowWidthDp > 0f && windowHeightDp > 0f) { "Window size must be positive" }
    require(windowWidthDp.isFinite() || windowHeightDp.isFinite()) { "At least one side must be bounded" }
    val aspect = windowWidthDp / windowHeightDp
    return when {
        aspect < MIN_ASPECT_RATIO -> DesignFrameSize(windowWidthDp, windowWidthDp / MIN_ASPECT_RATIO)
        aspect > MAX_ASPECT_RATIO -> DesignFrameSize(windowHeightDp * MAX_ASPECT_RATIO, windowHeightDp)
        else -> DesignFrameSize(windowWidthDp, windowHeightDp)
    }
}
