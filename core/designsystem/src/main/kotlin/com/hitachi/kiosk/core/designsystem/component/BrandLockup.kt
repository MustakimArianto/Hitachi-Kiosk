package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/**
 * The header brand of Sign in, Merchants and the merchant screens (`Login.dc.html`, `Merchants.dc.html`): an 80 dpx
 * [BrandMarkTile], then [name] in the button style (34/700). Decorative tile; the name is the text. [logo] draws the
 * merchant's logo over the tile once it is loaded (the tile stays when there is none).
 */
@Composable
fun BrandLockup(
    name: String,
    modifier: Modifier = Modifier,
    logo: @Composable BoxScope.() -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        BrandMarkTile(
            modifier = Modifier.size(80.dpx),
            shape = RoundedCornerShape(24.dpx),
            dotSize = 26.dpx,
            logo = logo,
        )
        KioskText(text = name, style = KioskTheme.typography.button)
    }
}

/**
 * The brand mark of the design: an ink tile with an accent dot. [logo] is drawn over it, filling the tile, when a
 * merchant logo is available; until then, and when there is none or it fails to load, the tile stays (NFR-013).
 */
@Composable
fun BrandMarkTile(
    modifier: Modifier,
    shape: Shape,
    dotSize: Dp,
    logo: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier.clip(shape).background(KioskTheme.colors.ink),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(dotSize).background(KioskTheme.colors.accent, CircleShape))
        logo()
    }
}

@Preview(widthDp = 500, heightDp = 120)
@Composable
private fun BrandLockupPreview() {
    KioskTheme { BrandLockup(name = "Hitachi Kiosk") }
}
