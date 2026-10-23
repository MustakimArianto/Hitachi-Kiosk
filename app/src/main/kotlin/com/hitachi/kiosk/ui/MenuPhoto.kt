package com.hitachi.kiosk.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.hitachi.kiosk.core.designsystem.component.PhotoSlot
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme

/**
 * A menu or promo photo (NFR-013, `Photo.dc.html`): the design-system [PhotoSlot] tone placeholder with the image
 * loaded by Coil from the memory or disk cache, or the network. A missing URL or a failed load leaves the plain tone.
 * Coil requests the image at the slot's size, so a full-resolution bitmap is never decoded (TR-PRF-05).
 *
 * @param tone the category or combo tone from the menu (`#RRGGBB`); `null` uses the default photo tone.
 * @param contentDescription what the photo shows, or `null` when the surrounding element already describes it.
 */
@Composable
fun MenuPhoto(
    imageUrl: String?,
    tone: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = KioskTheme.shapes.large,
) {
    PhotoSlot(
        contentDescription = contentDescription,
        modifier = modifier,
        shape = shape,
        tone = parseHexColor(tone) ?: KioskTheme.colors.photoTone,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
