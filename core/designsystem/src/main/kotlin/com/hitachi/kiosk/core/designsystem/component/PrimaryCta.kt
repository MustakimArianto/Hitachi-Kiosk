package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** Alpha of the white arrow tile on the button (`rgba(255,255,255,.18)`). */
private const val TILE_ALPHA = 0.18f

/** Alpha of the spinner track (`rgba(255,255,255,.35)`). */
private const val SPINNER_TRACK_ALPHA = 0.35f

/**
 * The primary CTA of PRD §11.3 (`Login.dc.html` bottom dock): a 152 dpx accent button with [text] on the left and an
 * arrow tile on the right, which shows a spinner while [busy]. It scales to 0.96 while pressed (PRD §7.1).
 *
 * @param enabled whether taps reach [onClick]; also exposed as disabled semantics when false.
 * @param locked draws the disabled look (`line` background, `muted` text) for a button that cannot be used at all,
 *   for example when the serial number cannot be read. A busy button is disabled but keeps the accent look.
 */
@Composable
fun PrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    locked: Boolean = false,
) {
    val colors = KioskTheme.colors
    val background = if (locked) colors.line else colors.accent
    val foreground = if (locked) colors.muted else colors.surface
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(152.dpx)
                .pressScale(scale)
                .clip(KioskTheme.shapes.card)
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(start = 56.dpx, end = 24.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        KioskText(
            text = text,
            style = KioskTheme.typography.button.copy(fontSize = 36.spx),
            color = foreground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier =
                Modifier
                    .size(104.dpx)
                    .background(colors.surface.copy(alpha = TILE_ALPHA), KioskTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                KioskSpinner(
                    color = colors.surface,
                    trackColor = colors.surface.copy(alpha = SPINNER_TRACK_ALPHA),
                    modifier = Modifier.size(44.dpx),
                )
            } else {
                KioskIcon(icon = KioskIcons.ArrowRight, tint = foreground, modifier = Modifier.size(44.dpx))
            }
        }
    }
}

@Preview(widthDp = 1080, heightDp = 600)
@Composable
private fun PrimaryCtaPreview() {
    KioskTheme(reduceMotion = true) {
        Column(Modifier.padding(72.dpx), verticalArrangement = Arrangement.spacedBy(24.dpx)) {
            PrimaryCta(text = "Log in", onClick = {})
            PrimaryCta(text = "Signing in…", onClick = {}, enabled = false, busy = true)
            PrimaryCta(text = "Log in", onClick = {}, enabled = false, locked = true)
        }
    }
}
