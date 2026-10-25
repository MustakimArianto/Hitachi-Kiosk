package com.hitachi.kiosk.feature.complete

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/** Test tag of the decorative receipt printer. */
const val COMPLETE_PRINTER_TAG = "complete-printer"

// The receipt printer of Complete.dc.html (`printOk`), in design px. It is decorative: no printing happens in 0.6.0.
private const val PRINTER_LEFT_PX = 300
private const val PRINTER_TOP_PX = 1480
private const val PRINTER_WIDTH_PX = 480
private const val PRINTER_HEIGHT_PX = 24
private const val PRINTER_RADIUS_PX = 12
private const val PRINTER_UP_MILLIS = 600
private const val PRINTER_UP_DELAY_MILLIS = 1900
private const val PRINTER_UP_PX = 32

private const val SLOT_LEFT_PX = 340
private const val SLOT_TOP_PX = 1492
private const val RECEIPT_WIDTH_PX = 400
private const val RECEIPT_HEIGHT_PX = 150
private const val RECEIPT_BORDER_PX = 1
private const val RECEIPT_PAD_V_PX = 26
private const val RECEIPT_PAD_H_PX = 30
private const val RECEIPT_GAP_PX = 14

private const val TITLE_BAR_WIDTH = 0.46f
private const val TITLE_BAR_HEIGHT_PX = 10
private const val LINE_ONE_WIDTH = 0.86f
private const val LINE_TWO_WIDTH = 0.66f
private const val LINE_HEIGHT_PX = 8
private const val TOTAL_BAR_WIDTH = 0.30f
private const val TOTAL_BAR_HEIGHT_PX = 10

/** The printer bar and the receipt sliding out of it (`up`, `print`), on the shared Complete clock. */
@Composable
internal fun CompleteReceiptPrinter(
    time: State<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    Box(modifier = modifier.clearAndSetSemantics { testTag = COMPLETE_PRINTER_TAG }) {
        Box(
            modifier =
                Modifier
                    .offset(x = PRINTER_LEFT_PX.dpx, y = PRINTER_TOP_PX.dpx)
                    .size(PRINTER_WIDTH_PX.dpx, PRINTER_HEIGHT_PX.dpx)
                    .fadeUpWith(time, PRINTER_UP_DELAY_MILLIS, PRINTER_UP_MILLIS, PRINTER_UP_PX)
                    .background(colors.ink, RoundedCornerShape(PRINTER_RADIUS_PX.dpx)),
        )
        Box(
            modifier =
                Modifier
                    .offset(x = SLOT_LEFT_PX.dpx, y = SLOT_TOP_PX.dpx)
                    .size(RECEIPT_WIDTH_PX.dpx, RECEIPT_HEIGHT_PX.dpx)
                    .clipToBounds(),
        ) {
            Receipt(time = time, accent = colors.accent)
        }
    }
}

@Composable
private fun Receipt(
    time: State<Float>,
    accent: Color,
) {
    val colors = KioskTheme.colors
    Column(
        modifier =
            Modifier
                .size(RECEIPT_WIDTH_PX.dpx, RECEIPT_HEIGHT_PX.dpx)
                .graphicsLayer {
                    val p =
                        segment(
                            time.value,
                            KioskMotion.Complete.RECEIPT_PRINT_DELAY_MILLIS,
                            KioskMotion.Complete.RECEIPT_PRINT_MILLIS,
                            KioskEasing.Press,
                        )
                    translationY = -size.height * (1f - p)
                }.background(colors.surface)
                .border(RECEIPT_BORDER_PX.dpx, colors.cardBorder)
                .padding(horizontal = RECEIPT_PAD_H_PX.dpx, vertical = RECEIPT_PAD_V_PX.dpx),
        verticalArrangement = Arrangement.spacedBy(RECEIPT_GAP_PX.dpx),
    ) {
        ReceiptBar(TITLE_BAR_WIDTH, TITLE_BAR_HEIGHT_PX, colors.ink)
        ReceiptBar(LINE_ONE_WIDTH, LINE_HEIGHT_PX, colors.line)
        ReceiptBar(LINE_TWO_WIDTH, LINE_HEIGHT_PX, colors.line)
        ReceiptBar(TOTAL_BAR_WIDTH, TOTAL_BAR_HEIGHT_PX, accent, Modifier.align(Alignment.End))
    }
}

@Composable
private fun ReceiptBar(
    widthFraction: Float,
    heightPx: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth(widthFraction)
                .height(heightPx.dpx)
                .background(color, RoundedCornerShape((heightPx / 2).dpx)),
    )
}
