package com.hitachi.kiosk.core.designsystem.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Draws [shadow] behind an element of [shape], like the design's CSS `box-shadow`. Docks and overlays use
 * `Modifier.kioskShadow(shape, KioskTheme.elevation.floating)` (PRD §11.1, DEV-004).
 */
fun Modifier.kioskShadow(
    shape: Shape,
    shadow: KioskShadow,
): Modifier =
    dropShadow(
        shape = shape,
        shadow =
            Shadow(
                radius = shadow.blurRadius,
                color = shadow.color,
                offset = DpOffset(0.dp, shadow.offsetY),
            ),
    )
