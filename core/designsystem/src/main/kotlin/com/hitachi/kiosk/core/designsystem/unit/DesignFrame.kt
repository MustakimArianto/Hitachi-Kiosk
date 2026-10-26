package com.hitachi.kiosk.core.designsystem.unit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.ProvideScaledTokens

/**
 * Lays out the app in the design frame (NFR-002, TR-DSP-02, DEV-003): the window clamped to the supported 9:16
 * to 10:16 range by [designFrameSize], centred, with the rest filled with `bg` (letterbox or pillarbox). Inside,
 * [LocalDesignScale] is `frameWidthDp / 1080`, so [dpx] and [spx] reproduce the 1080 × 1920 design at any
 * density, and the theme's type, shapes and spacing are rebuilt for that scale. Use it once, directly inside
 * [KioskTheme]. The frame sits above the navigation bar (the iMin panel keeps it visible), so bottom docks and
 * buttons are never covered.
 */
@Composable
fun DesignFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val frame = designFrameSize(maxWidth.value, maxHeight.value)
        Box(Modifier.size(frame.widthDp.dp, frame.heightDp.dp)) {
            ProvideScaledTokens(scale = frame.scale, content = content)
        }
    }
}

@Preview(widthDp = 540, heightDp = 960)
@Composable
private fun DesignFramePreview() {
    KioskTheme {
        DesignFrame {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 72.dpx)
                    .background(KioskTheme.colors.surfaceAlt),
            )
        }
    }
}

@Preview(widthDp = 540, heightDp = 1170)
@Composable
private fun DesignFrameLetterboxPreview() {
    KioskTheme {
        DesignFrame {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(KioskTheme.colors.surface),
            )
        }
    }
}
