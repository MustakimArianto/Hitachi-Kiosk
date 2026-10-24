package com.hitachi.kiosk.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * The bound merchant's logo URL (config `logoUrl`), set once at the theme root like the accent (A-38, architecture
 * §12). `null` before a merchant is bound or when the config has no logo.
 */
val LocalMerchantLogoUrl = staticCompositionLocalOf<String?> { null }

/**
 * The merchant logo over a brand tile (`BrandMarkTile`, `BrandLockup`, `QrCenterMark`): Coil loads it from the cache or
 * the network. It draws nothing without a URL, so the design's ink tile with the accent dot stays until the logo has
 * loaded, and when it fails (NFR-013). The logo is square and fills the tile.
 */
@Composable
fun BoxScope.MerchantLogoImage() {
    val url = LocalMerchantLogoUrl.current ?: return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
    )
}
