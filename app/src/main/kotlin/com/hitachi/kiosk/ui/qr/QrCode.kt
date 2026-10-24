package com.hitachi.kiosk.ui.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import com.google.zxing.common.BitMatrix
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/** Size of the QR on Checkout in design px (FR-017, `Checkout.dc.html`). */
const val QR_SIZE_PX: Int = 480

/**
 * The centre mark may cover at most 20 % of the QR's width, about 4 % of its area (TD-07). Level H corrects up to
 * 30 % damage, so the code still decodes (TC-079).
 */
const val QR_MARK_MAX_FRACTION: Float = 0.2f

/**
 * A QR code for [content], the QRIS `qrString` (FR-017, DEV-048, TD-07). It encodes once per [content] with ZXing at
 * level H, then draws the dark modules as rectangles on a canvas with a whole number of pixels per module, so
 * nothing is scaled. The code is dark ink on white with a quiet zone, whatever the theme.
 *
 * If [content] cannot be encoded (blank or too long) the square stays blank, [onEncodeFailed] is called once per
 * content, and a warning without the content goes to `KioskLog`: a QR string is payment data (NFR-006). Nothing
 * crashes.
 *
 * @param content the string to encode.
 * @param modifier applied to the square.
 * @param size width and height of the square; 480 dpx on Checkout.
 * @param contentDescription what a screen reader says, e.g. "Payment QR code"; `null` leaves the QR unannounced.
 * @param onEncodeFailed called when [content] could not be encoded, so the caller can fail the payment.
 * @param centerMark drawn over the middle of the code in a box of [QR_MARK_MAX_FRACTION] of the width. Use
 *   `QrCenterMark` or a merchant logo; it fills the box it is given.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = QR_SIZE_PX.dpx,
    contentDescription: String? = null,
    onEncodeFailed: () -> Unit = {},
    centerMark: @Composable () -> Unit = {},
) {
    val matrix = remember(content) { QrEncoder.encode(content) }
    if (matrix == null) {
        LaunchedEffect(content) { onEncodeFailed() }
    }
    val dark = KioskTheme.colors.ink
    val light = KioskTheme.colors.surface
    val semantics =
        if (contentDescription == null) {
            Modifier
        } else {
            Modifier.clearAndSetSemantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
        }
    Box(
        modifier =
            modifier
                .size(size)
                .then(semantics)
                .background(light)
                .drawWithCache {
                    val path = matrix?.let { modulesPath(it, this.size.minDimension.toInt()) }
                    onDrawBehind { path?.let { drawModules(it, dark) } }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size * QR_MARK_MAX_FRACTION), propagateMinConstraints = true) { centerMark() }
    }
}

private fun DrawScope.drawModules(
    path: Path,
    color: Color,
) = drawPath(path = path, color = color)

/**
 * One rectangle per horizontal run of dark modules, on whole pixels (TD-07). The matrix includes its quiet zone, so
 * the light border is simply not drawn. `null` when the canvas is smaller than one pixel per module.
 */
private fun modulesPath(
    matrix: BitMatrix,
    canvasPx: Int,
): Path? {
    val layout = QrLayout.of(matrix.width, canvasPx) ?: return null
    val path = Path()
    for (y in 0 until matrix.height) {
        var x = 0
        while (x < matrix.width) {
            if (!matrix.get(x, y)) {
                x++
                continue
            }
            val start = x
            while (x < matrix.width && matrix.get(x, y)) x++
            path.addRect(
                Rect(
                    left = (layout.offsetPx + start * layout.modulePx).toFloat(),
                    top = (layout.offsetPx + y * layout.modulePx).toFloat(),
                    right = (layout.offsetPx + x * layout.modulePx).toFloat(),
                    bottom = (layout.offsetPx + (y + 1) * layout.modulePx).toFloat(),
                ),
            )
        }
    }
    return path
}
