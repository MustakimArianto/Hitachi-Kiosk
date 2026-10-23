package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** Line height of the banner text (`line-height: 1.2`). */
private const val BANNER_LINE_HEIGHT_EM = 1.2f

/** One turn of the banner spinner (`spinM 900ms linear`). */
private const val BANNER_SPINNER_MILLIS = 900

/**
 * The amber status banner of PRD §11.3 (`S_Menu_offline`, FR-028): a 112 dpx rounded strip with a spinner and
 * [text], for example "Ordering is paused — reconnecting…". It fades up on entry and is announced politely as a
 * status (NFR-007). Size its width with [modifier].
 */
@Composable
fun OfflineBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = RoundedCornerShape(36.dpx)
    val entrance = animateEntrance(KioskMotion.Common.PANEL_UP_MILLIS)
    Row(
        modifier =
            modifier
                .fadeUp(entrance, KioskMotion.Common.UP_DISTANCE_PX.dpx)
                .fillMaxWidth()
                .height(112.dpx)
                .kioskShadow(shape, KioskTheme.elevation.floating)
                .background(colors.warningBg, shape)
                .border(2.dpx, colors.warningBorder, shape)
                .padding(horizontal = 36.dpx)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        KioskSpinner(
            color = colors.warningAccent,
            trackColor = colors.warningTrack,
            modifier = Modifier.size(44.dpx),
            periodMillis = BANNER_SPINNER_MILLIS,
        )
        KioskText(
            text = text,
            style =
                KioskTheme.typography.body.copy(
                    fontSize = 30.spx,
                    fontWeight = FontWeight.W600,
                    lineHeight = BANNER_LINE_HEIGHT_EM.em,
                ),
            color = colors.warningText,
        )
    }
}

@Preview(widthDp = 1080, heightDp = 240)
@Composable
private fun OfflineBannerPreview() {
    KioskTheme(reduceMotion = true) {
        OfflineBanner(text = "Ordering is paused — reconnecting…", modifier = Modifier.padding(72.dpx))
    }
}
