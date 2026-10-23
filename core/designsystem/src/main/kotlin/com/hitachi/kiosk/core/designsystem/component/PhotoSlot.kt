package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.hitachi.kiosk.core.designsystem.theme.KioskPalette
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/** Below this luminance a requested tone is replaced by [KioskPalette.PhotoToneFallback] (`Photo.dc.html`). */
private const val MIN_TONE_LUMINANCE = 0.7f
private const val LUMA_RED = 0.299f
private const val LUMA_GREEN = 0.587f
private const val LUMA_BLUE = 0.114f

/**
 * The placeholder tone of `Photo.dc.html`: [tone] itself when it is light enough (luma ≥ 0.7, weights
 * 0.299 / 0.587 / 0.114), otherwise [KioskPalette.PhotoToneFallback], so an empty slot never looks dark (NFR-013).
 */
fun photoPlaceholderTone(tone: Color): Color {
    val luma = LUMA_RED * tone.red + LUMA_GREEN * tone.green + LUMA_BLUE * tone.blue
    return if (luma < MIN_TONE_LUMINANCE) KioskPalette.PhotoToneFallback else tone
}

/**
 * The photo slot of `Photo.dc.html` (PRD §11.3, NFR-013): a [shape]-clipped area filled with the placeholder tone
 * of [tone], with the image drawn by [content] on top. When there is no image, or it fails to load, the plain tone
 * shows (the design's "Photo" caption is a design-only label, PRD §4.1 C11). Size it with [modifier].
 *
 * `:app` fills [content] with Coil's `AsyncImage`, so this module depends on Compose only (technical-design §3).
 *
 * @param contentDescription what the photo shows, or `null` when the surrounding element already describes it.
 */
@Composable
fun PhotoSlot(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = KioskTheme.shapes.large,
    tone: Color = KioskTheme.colors.photoTone,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val described =
        if (contentDescription != null) {
            Modifier.semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(photoPlaceholderTone(tone))
                .then(described),
        content = content,
    )
}

@Preview(widthDp = 320, heightDp = 320)
@Composable
private fun PhotoSlotPreview() {
    KioskTheme {
        PhotoSlot(contentDescription = null, modifier = Modifier.size(272.dpx))
    }
}
