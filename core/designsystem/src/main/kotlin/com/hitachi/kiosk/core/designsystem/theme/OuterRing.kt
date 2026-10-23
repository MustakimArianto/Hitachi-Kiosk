package com.hitachi.kiosk.core.designsystem.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp

/**
 * Draws a [width] ring of [color] just outside the element, like the design's spread-only `box-shadow: 0 0 0 Npx`
 * outlines (the selected merchant card, the empty-state card). [shape] is the element's own shape; the ring keeps
 * its corner radii. Put it before the element's background so the background covers the inner part.
 */
fun Modifier.outerRing(
    width: Dp,
    color: Color,
    shape: Shape,
): Modifier =
    drawBehind {
        val w = width.toPx()
        translate(left = -w, top = -w) {
            val ring = shape.createOutline(Size(size.width + 2 * w, size.height + 2 * w), layoutDirection, this)
            drawOutline(ring, color)
        }
    }
