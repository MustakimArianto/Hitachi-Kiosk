package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme

/** The logo is drawn in the 200 × 200 viewBox of the design's SVG. */
private const val VIEWBOX = 200f
private const val TILE_RADIUS = 60f
private const val TILE_START_SCALE = 0.6f
private const val TILE_START_ROTATION = -8f

private const val DOME_LEFT = 40f
private const val DOME_RIGHT = 160f
private const val DOME_BASELINE = 138f
private const val DOME_RADIUS = 60f
private const val BELL_START_SCALE_Y = 0.4f
private const val BELL_START_OFFSET = 24f

private const val BASE_TOP = 148f
private const val BASE_HEIGHT = 14f
private const val BASE_START_SCALE_X = 0.2f

/** The sound wave: an arc of radius 76 around the dome's base centre, from 220° over 100°. */
private const val WAVE_RADIUS = 76f
private const val WAVE_START_ANGLE = 220f
private const val WAVE_SWEEP = 100f
private const val WAVE_STROKE = 11f
private const val ECHO_STROKE = 9f
private const val ECHO_START_ALPHA = 0.6f
private const val ECHO_GROWTH = 0.5f

private val Center = Offset(VIEWBOX / 2f, VIEWBOX / 2f)
private val WaveCenter = Offset(VIEWBOX / 2f, DOME_BASELINE)

/**
 * The Hitachi Kiosk logo (`Main.dc.html`, `Login.dc.html`, `Merchants.dc.html`): an ink tile with a cream service
 * bell and an accent sound wave. Each part has an entrance progress (0 → 1, read while drawing, no recomposition);
 * the defaults draw the finished logo. [echo] is the wave that ripples out once after it has been drawn.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    tile: () -> Float = ONE,
    bell: () -> Float = ONE,
    base: () -> Float = ONE,
    wave: () -> Float = ONE,
    echo: () -> Float = ONE,
) {
    val ink = KioskTheme.colors.ink
    val cream = KioskTheme.colors.bg
    val accent = KioskTheme.colors.accent
    Canvas(modifier) {
        scale(size.width / VIEWBOX, pivot = Offset.Zero) {
            drawTile(ink, tile())
            drawBell(cream, bell())
            drawBase(cream, base())
            drawWave(accent, wave())
            drawEcho(accent, echo())
        }
    }
}

private val ONE: () -> Float = { 1f }

private fun DrawScope.drawTile(
    color: Color,
    p: Float,
) {
    withTransform({
        scale(TILE_START_SCALE + (1f - TILE_START_SCALE) * p, pivot = Center)
        rotate(TILE_START_ROTATION * (1f - p), pivot = Center)
    }) {
        drawRoundRect(color, size = Size(VIEWBOX, VIEWBOX), cornerRadius = CornerRadius(TILE_RADIUS), alpha = p)
    }
}

private fun DrawScope.drawBell(
    color: Color,
    p: Float,
) {
    val dome =
        Path().apply {
            moveTo(DOME_LEFT, DOME_BASELINE)
            arcTo(
                Rect(DOME_LEFT, DOME_BASELINE - DOME_RADIUS, DOME_RIGHT, DOME_BASELINE + DOME_RADIUS),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            close()
        }
    withTransform({
        translate(top = BELL_START_OFFSET * (1f - p))
        scale(1f, BELL_START_SCALE_Y + (1f - BELL_START_SCALE_Y) * p, pivot = WaveCenter)
    }) {
        drawPath(dome, color, alpha = p)
    }
}

private fun DrawScope.drawBase(
    color: Color,
    p: Float,
) {
    withTransform({
        scale(
            BASE_START_SCALE_X + (1f - BASE_START_SCALE_X) * p,
            1f,
            pivot =
                Offset(
                    Center.x,
                    BASE_TOP + BASE_HEIGHT / 2f,
                ),
        )
    }) {
        drawRoundRect(
            color,
            topLeft = Offset(DOME_LEFT, BASE_TOP),
            size = Size(DOME_RIGHT - DOME_LEFT, BASE_HEIGHT),
            cornerRadius = CornerRadius(BASE_HEIGHT / 2f),
            alpha = p,
        )
    }
}

private fun waveArc(): Path =
    Path().apply {
        addArc(
            Rect(
                WaveCenter.x - WAVE_RADIUS,
                WaveCenter.y - WAVE_RADIUS,
                WaveCenter.x + WAVE_RADIUS,
                WaveCenter.y + WAVE_RADIUS,
            ),
            WAVE_START_ANGLE,
            WAVE_SWEEP,
        )
    }

private fun DrawScope.drawWave(
    color: Color,
    p: Float,
) {
    if (p <= 0f) return
    val full = waveArc()
    val path =
        if (p >= 1f) {
            full
        } else {
            val measure = PathMeasure().apply { setPath(full, false) }
            Path().also { measure.getSegment(0f, measure.length * p, it, true) }
        }
    drawPath(path, color, style = Stroke(width = WAVE_STROKE, cap = StrokeCap.Round))
}

private fun DrawScope.drawEcho(
    color: Color,
    p: Float,
) {
    if (p <= 0f || p >= 1f) return
    withTransform({ scale(1f + ECHO_GROWTH * p, pivot = WaveCenter) }) {
        drawPath(
            waveArc(),
            color,
            alpha = ECHO_START_ALPHA * (1f - p),
            style = Stroke(width = ECHO_STROKE, cap = StrokeCap.Round),
        )
    }
}

@Preview(widthDp = 240, heightDp = 240)
@Composable
private fun BrandMarkPreview() {
    KioskTheme { BrandMark(Modifier.size(240.dp)) }
}
