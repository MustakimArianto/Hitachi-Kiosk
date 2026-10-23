package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/**
 * The header brand of Sign in and Merchants (`Login.dc.html`, `Merchants.dc.html`): an 80 dpx ink tile with the
 * accent dot, then [name] in the button style (34/700). Decorative tile; the name is the text.
 */
@Composable
fun BrandLockup(
    name: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        Box(
            modifier =
                Modifier
                    .size(80.dpx)
                    .background(KioskTheme.colors.ink, RoundedCornerShape(24.dpx)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(26.dpx).background(KioskTheme.colors.accent, CircleShape))
        }
        KioskText(text = name, style = KioskTheme.typography.button)
    }
}

@Preview(widthDp = 500, heightDp = 120)
@Composable
private fun BrandLockupPreview() {
    KioskTheme { BrandLockup(name = "Hitachi Kiosk") }
}
