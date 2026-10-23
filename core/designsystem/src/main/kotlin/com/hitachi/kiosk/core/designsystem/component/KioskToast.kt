package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** The toast fades up over 500 ms (`fadeUp 500ms`, `Menu.dc.html`). */
private const val TOAST_IN_MILLIS = 500

/**
 * The toast of PRD §11.3 ("Maximum 20 per item", FR-009, FR-010): a dark pill with an accent "!" badge and one line
 * of [text]. It fades up on entry and is announced at once, like the design's `role="alert"` (NFR-007). The caller
 * places it (centred, 240 dpx above the bottom in the design) and removes it after its display time.
 */
@Composable
fun KioskToast(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.pill
    val entrance = animateEntrance(TOAST_IN_MILLIS)
    Row(
        modifier =
            modifier
                .fadeUp(entrance, 24.dpx)
                .height(96.dpx)
                .kioskShadow(shape, KioskTheme.elevation.raised)
                .background(colors.ink, shape)
                .padding(start = 24.dpx, end = 40.dpx)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        Box(
            modifier = Modifier.size(52.dpx).background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.AlertBold, tint = colors.surface, modifier = Modifier.size(30.dpx))
        }
        KioskText(
            text = text,
            style = KioskTheme.typography.body.copy(fontSize = 30.spx, fontWeight = FontWeight.W600),
            color = colors.bg,
            maxLines = 1,
        )
    }
}

@Preview(widthDp = 1080, heightDp = 240)
@Composable
private fun KioskToastPreview() {
    KioskTheme(reduceMotion = true) {
        KioskToast(text = "Maximum 20 per item", modifier = Modifier.padding(72.dpx))
    }
}
