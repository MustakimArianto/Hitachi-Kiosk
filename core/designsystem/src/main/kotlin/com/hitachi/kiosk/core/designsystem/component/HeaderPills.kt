package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** Which look a header pill has: [Floating] over a photo (white, shadow) or [Flat] on the page background. */
enum class HeaderPillStyle { Floating, Flat }

/**
 * The "‹ Menu" back pill of the Item and Combo headers (`ItemDetail.dc.html`, `Combo.dc.html`): 96 dpx tall, a
 * chevron and [text]. [HeaderPillStyle.Floating] is white with the floating shadow (over the Item hero);
 * [HeaderPillStyle.Flat] sits on `surfaceAlt` (Combo). Scales while pressed (PRD §7.1).
 */
@Composable
fun BackPill(
    text: String,
    onClick: () -> Unit,
    style: HeaderPillStyle,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.pill
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val floating = style == HeaderPillStyle.Floating
    Row(
        modifier =
            modifier
                .pressScale(scale)
                .height(96.dpx)
                .then(if (floating) Modifier.kioskShadow(shape, KioskTheme.elevation.floating) else Modifier)
                .clip(shape)
                .background(if (floating) colors.surface else colors.surfaceAlt)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(start = 24.dpx, end = 34.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dpx),
    ) {
        KioskIcon(icon = KioskIcons.ChevronLeft, tint = colors.ink, modifier = Modifier.size(40.dpx))
        KioskText(
            text = text,
            style = KioskTheme.typography.body.copy(fontSize = 30.spx, fontWeight = FontWeight.W600),
            maxLines = 1,
        )
    }
}

/**
 * The "My order" pill with the accent count badge (`ItemDetail.dc.html`, `Combo.dc.html`). The whole pill is one
 * button described by [contentDescription] ("Your order, 4 items", NFR-007). Each time [bumps] grows, the badge
 * scales up to [bumpScale] and back over [bumpMillis] after [bumpDelayMillis] (the cart bump; none under reduce
 * motion). [HeaderPillStyle.Flat] draws a `line` outline instead of the white fill and shadow.
 */
@Suppress("LongParameterList")
@Composable
fun CartPill(
    text: String,
    count: Int,
    contentDescription: String,
    onClick: () -> Unit,
    style: HeaderPillStyle,
    modifier: Modifier = Modifier,
    bumps: Int = 0,
    bumpScale: Float = 1.2f,
    bumpMillis: Int = 460,
    bumpDelayMillis: Int = 0,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.pill
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            modifier
                .pressScale(scale)
                .height(96.dpx)
                .then(
                    if (style == HeaderPillStyle.Floating) {
                        Modifier.kioskShadow(shape, KioskTheme.elevation.floating).background(colors.surface, shape)
                    } else {
                        Modifier.border(2.dpx, colors.line, shape)
                    },
                ).clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).clearAndSetSemantics { this.contentDescription = contentDescription }
                .padding(start = 30.dpx, end = 14.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dpx),
    ) {
        KioskIcon(icon = KioskIcons.Bag, tint = colors.ink, modifier = Modifier.size(40.dpx))
        KioskText(
            text = text,
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
            maxLines = 1,
        )
        CountBadge(
            count = count,
            bumps = bumps,
            bumpScale = bumpScale,
            bumpMillis = bumpMillis,
            bumpDelayMillis = bumpDelayMillis,
            color = colors.accent,
        )
    }
}

@Composable
private fun CountBadge(
    count: Int,
    bumps: Int,
    bumpScale: Float,
    bumpMillis: Int,
    bumpDelayMillis: Int,
    color: Color,
) {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(bumps) {
        if (bumps == 0 || reduce) return@LaunchedEffect
        scale.snapTo(1f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec =
                keyframes {
                    durationMillis = bumpMillis
                    delayMillis = bumpDelayMillis
                    bumpScale at (bumpMillis * BUMP_PEAK_FRACTION).toInt()
                },
        )
    }
    Box(
        modifier =
            Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }.height(64.dpx)
                .widthIn(min = 64.dpx)
                .background(color, KioskTheme.shapes.pill)
                .padding(horizontal = 16.dpx),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = count.toString(),
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
            color = KioskTheme.colors.surface,
            maxLines = 1,
        )
    }
}

/** The bump peaks at 40 % of its duration (`bump` keyframes). */
private const val BUMP_PEAK_FRACTION = 0.4f

@Preview(widthDp = 1080, heightDp = 200)
@Composable
private fun HeaderPillsPreview() {
    KioskTheme(reduceMotion = true) {
        Row(
            modifier = Modifier.padding(48.dpx),
            horizontalArrangement = Arrangement.spacedBy(24.dpx),
        ) {
            BackPill(text = "Menu", onClick = {}, style = HeaderPillStyle.Floating)
            CartPill(
                text = "My order",
                count = 4,
                contentDescription = "Your order, 4 items",
                onClick = {},
                style = HeaderPillStyle.Flat,
            )
        }
    }
}
