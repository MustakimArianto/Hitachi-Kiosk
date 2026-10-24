package com.hitachi.kiosk.feature.merchant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskSpinner
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.PhotoSlot
import com.hitachi.kiosk.core.designsystem.component.SkeletonBlock
import com.hitachi.kiosk.core.designsystem.component.rememberShimmerPhase
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Common
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Merchants
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.theme.outerRing
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** Letter spacing of merchant names (−0.02 em) and their line height. */
private const val NAME_EM = -0.02f
private const val NAME_LINE_HEIGHT_EM = 1.05f

/** Alpha of the "Open now" pulse ring at its start (`rgba(47,122,80,.45)`). */
private const val PULSE_START_ALPHA = 0.45f

/** Alpha of the spinner track on the accent arrow circle. */
private const val SPINNER_TRACK_ALPHA = 0.35f

/** The live merchant cards and every coming-soon row (FR-004). */
@Composable
internal fun MerchantsList(
    content: MerchantsContent.Loaded,
    onSelect: (String) -> Unit,
) {
    val binding = content.bindingMerchantId != null
    content.live.forEachIndexed { index, merchant ->
        val enter =
            animateEntrance(
                Merchants.CARD_UP_MILLIS,
                Merchants.CARD_UP_DELAY_MILLIS + index * Merchants.SOON_ROWS_STAGGER_MILLIS,
            )
        val cardModifier = Modifier.fadeUp(enter, Common.UP_DISTANCE_PX.dpx)
        if (merchant.isOpen) {
            OpenMerchantCard(
                merchant = merchant,
                enabled = !binding,
                isBinding = merchant.id == content.bindingMerchantId,
                onClick = { onSelect(merchant.id) },
                modifier = cardModifier,
            )
        } else {
            ClosedMerchantCard(merchant = merchant, modifier = cardModifier)
        }
    }
    if (content.comingSoon.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dpx)) {
            val label = animateEntrance(Merchants.SOON_LABEL_UP_MILLIS, Merchants.SOON_LABEL_UP_DELAY_MILLIS)
            KioskText(
                text = stringResource(R.string.merchants_coming_soon),
                style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W600),
                color = KioskTheme.colors.muted,
                modifier = Modifier.fadeUp(label, Common.UP_DISTANCE_PX.dpx),
            )
            content.comingSoon.forEachIndexed { index, row ->
                val enter =
                    animateEntrance(
                        Merchants.SOON_ROWS_UP_MILLIS,
                        Merchants.SOON_ROWS_DELAY_MILLIS + index * Merchants.SOON_ROWS_STAGGER_MILLIS,
                    )
                ComingSoonRow(row = row, modifier = Modifier.fadeUp(enter, Common.UP_DISTANCE_PX.dpx))
            }
        }
    }
}

@Composable
private fun OpenMerchantCard(
    merchant: MerchantCardUi,
    enabled: Boolean,
    isBinding: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val description = stringResource(R.string.merchants_card_open_description, merchant.name)
    val tag = merchantCardTag(merchant.id)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource, KioskMotion.Press.CARD_SCALE, KioskMotion.Press.CARD_MILLIS)
    MerchantCardLayout(
        merchant = merchant,
        modifier =
            modifier
                .pressScale(scale)
                .kioskShadow(shape, KioskTheme.elevation.floating)
                .outerRing(3.dpx, colors.accent, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ).clearAndSetSemantics {
                    testTag = tag
                    contentDescription = description
                    role = Role.Button
                    if (enabled) {
                        onClick {
                            onClick()
                            true
                        }
                    } else {
                        disabled()
                    }
                },
        badge = { OpenNowBadge() },
        arrow = {
            ArrowCircle(background = colors.accent) {
                if (isBinding) {
                    KioskSpinner(
                        color = colors.surface,
                        trackColor = colors.surface.copy(alpha = SPINNER_TRACK_ALPHA),
                        modifier = Modifier.size(48.dpx),
                    )
                } else {
                    val nudge = arrowNudge()
                    KioskIcon(
                        icon = KioskIcons.ArrowRight,
                        tint = colors.surface,
                        modifier = Modifier.size(48.dpx).graphicsLayer { translationX = nudge.value * density },
                    )
                }
            }
        },
    )
}

@Composable
private fun ClosedMerchantCard(
    merchant: MerchantCardUi,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val badgeText =
        merchant.opensAt?.let { stringResource(R.string.merchants_closed_opens, it) }
            ?: stringResource(R.string.merchants_closed)
    val description = stringResource(R.string.merchants_card_closed_description, merchant.name, badgeText)
    val tag = merchantCardTag(merchant.id)
    MerchantCardLayout(
        merchant = merchant,
        modifier =
            modifier
                .outerRing(2.dpx, colors.line, shape)
                .clearAndSetSemantics {
                    testTag = tag
                    contentDescription = description
                    disabled()
                },
        badge = {
            StatusChip(text = badgeText, background = colors.surfaceAlt, foreground = colors.muted) {
                Box(Modifier.size(12.dpx).background(colors.closedDot, CircleShape))
            }
        },
        arrow = {
            ArrowCircle(background = colors.line) {
                KioskIcon(icon = KioskIcons.ArrowRight, tint = colors.muted, modifier = Modifier.size(48.dpx))
            }
        },
    )
}

/** The 320 dpx card: photo with logo, name, cuisine, [badge], and the [arrow] circle. */
@Composable
private fun MerchantCardLayout(
    merchant: MerchantCardUi,
    badge: @Composable () -> Unit,
    arrow: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 320.dpx)
                .background(colors.surface, KioskTheme.shapes.card)
                .padding(start = 24.dpx, top = 24.dpx, bottom = 24.dpx, end = 40.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dpx),
    ) {
        MerchantPhoto(merchant)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dpx),
        ) {
            KioskText(
                text = merchant.name,
                style =
                    KioskTheme.typography.title.copy(
                        fontSize = 48.spx,
                        letterSpacing = NAME_EM.em,
                        lineHeight = NAME_LINE_HEIGHT_EM.em,
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            KioskText(
                text = merchant.cuisine,
                style = KioskTheme.typography.body.copy(fontSize = 28.spx),
                color = colors.muted,
            )
            badge()
        }
        arrow()
    }
}

/**
 * The 272 dpx picture box of a live card. The merchant logo fills the whole box when there is one (PO, 2026-10-25,
 * `ui-design/photo-guidelines.md` §5). Without a logo it is the design's cover photo with the initial chip on it. The
 * initial shows until the logo loads, and stays when it fails (NFR-013).
 */
@Composable
private fun MerchantPhoto(merchant: MerchantCardUi) {
    val logoDescription = stringResource(R.string.merchants_logo_description, merchant.name)
    val initial = merchant.name.take(1).uppercase()
    val logoUrl = merchant.logoUrl
    if (logoUrl != null) {
        PhotoSlot(
            contentDescription = logoDescription,
            modifier = Modifier.size(272.dpx),
            shape = KioskTheme.shapes.large,
        ) {
            KioskText(
                text = initial,
                style = KioskTheme.typography.display.copy(fontSize = 96.spx),
                modifier = Modifier.align(Alignment.Center),
            )
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        return
    }
    Box {
        PhotoSlot(
            contentDescription = null,
            modifier = Modifier.size(272.dpx),
            shape = KioskTheme.shapes.large,
        ) {
            merchant.photoUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .offset(16.dpx, 16.dpx)
                    .size(72.dpx)
                    .clip(RoundedCornerShape(22.dpx))
                    .background(KioskTheme.colors.surface)
                    .semantics {
                        contentDescription = logoDescription
                        role = Role.Image
                    },
            contentAlignment = Alignment.Center,
        ) {
            KioskText(text = initial, style = KioskTheme.typography.section.copy(fontSize = 36.spx))
        }
    }
}

@Composable
private fun OpenNowBadge() {
    val colors = KioskTheme.colors
    val pulse = openNowPulse()
    val spread = Merchants.OPEN_NOW_PULSE_PX.dpx
    StatusChip(
        text = stringResource(R.string.merchants_open_now),
        background = colors.successBg,
        foreground = colors.success,
    ) {
        Box(
            modifier =
                Modifier
                    .size(12.dpx)
                    .drawBehind {
                        val p = pulse.value
                        if (p > 0f) {
                            drawCircle(
                                color = colors.success.copy(alpha = PULSE_START_ALPHA * (1f - p)),
                                radius = size.minDimension / 2f + spread.toPx() * p,
                            )
                        }
                    }.background(colors.success, CircleShape),
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    background: Color,
    foreground: Color,
    dot: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .height(56.dpx)
                .background(background, KioskTheme.shapes.pill)
                .padding(horizontal = 22.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dpx),
    ) {
        dot()
        KioskText(
            text = text,
            style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W600),
            color = foreground,
            maxLines = 1,
        )
    }
}

@Composable
private fun ArrowCircle(
    background: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.size(112.dpx).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The `pulse` loop of the "Open now" dot (1.8 s, Press easing); 0 when reduce motion is on. */
@Composable
private fun openNowPulse(): State<Float> =
    if (LocalReduceMotion.current) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(Merchants.OPEN_NOW_PULSE_LOOP_MILLIS, easing = KioskEasing.Press),
                    RepeatMode.Restart,
                ),
            label = "pulseProgress",
        )
    }

/** The `nudge` loop of the arrow, in design px (0 → 8 → 0, 1.8 s ease-in-out after 1.4 s); 0 when reduced. */
@Composable
private fun arrowNudge(): State<Float> {
    val scale = 1.dpx.value
    return if (LocalReduceMotion.current) {
        remember { mutableFloatStateOf(0f) }
    } else {
        val px = Merchants.ARROW_NUDGE_PX * scale
        rememberInfiniteTransition(label = "nudge").animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = Merchants.ARROW_NUDGE_LOOP_MILLIS
                            0f at 0 using KioskEasing.EaseInOut
                            px at Merchants.ARROW_NUDGE_LOOP_MILLIS / 2 using KioskEasing.EaseInOut
                        },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(Merchants.ARROW_NUDGE_DELAY_MILLIS),
                ),
            label = "nudgeOffset",
        )
    }
}

@Composable
private fun ComingSoonRow(
    row: ComingSoonRowUi,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val description = stringResource(R.string.merchants_soon_row_description, row.name, row.cuisine)
    val tag = comingSoonRowTag(row.id)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 176.dpx)
                .background(colors.surfaceAlt, KioskTheme.shapes.card)
                .clearAndSetSemantics {
                    testTag = tag
                    contentDescription = description
                    disabled()
                }.padding(start = 24.dpx, end = 32.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dpx),
    ) {
        Box(
            modifier = Modifier.size(88.dpx).background(colors.surface, KioskTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            KioskText(
                text = row.initial,
                style = KioskTheme.typography.section.copy(fontSize = 40.spx),
                color = colors.muted,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dpx)) {
            KioskText(
                text = row.name,
                style = KioskTheme.typography.item,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            KioskText(text = row.cuisine, style = KioskTheme.typography.meta, color = colors.muted)
        }
        Box(
            modifier =
                Modifier
                    .height(64.dpx)
                    .background(colors.surface, KioskTheme.shapes.pill)
                    .padding(horizontal = 26.dpx),
            contentAlignment = Alignment.Center,
        ) {
            KioskText(
                text = stringResource(R.string.merchants_soon_pill),
                style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W600),
                color = colors.muted,
            )
        }
    }
}

/** "Loading restaurants…": one big skeleton card and two compact rows with the shimmer (`S_Merchants_skeleton`). */
@Composable
internal fun MerchantsSkeleton() {
    val colors = KioskTheme.colors
    val phase = rememberShimmerPhase()
    val description = stringResource(R.string.merchants_loading_description)
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(320.dpx)
                    .background(colors.surface, KioskTheme.shapes.card)
                    .padding(24.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dpx),
        ) {
            SkeletonBlock(phase, KioskTheme.shapes.large, colors.surfaceAlt, colors.bg, Modifier.size(272.dpx))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dpx)) {
                SkeletonBlock(
                    phase,
                    RoundedCornerShape(16.dpx),
                    colors.surfaceAlt,
                    colors.bg,
                    Modifier.fillMaxWidth(SKELETON_TITLE_FRACTION).height(52.dpx),
                )
                SkeletonBlock(
                    phase,
                    RoundedCornerShape(12.dpx),
                    colors.surfaceAlt,
                    colors.bg,
                    Modifier.fillMaxWidth(SKELETON_META_FRACTION).height(32.dpx),
                )
                SkeletonBlock(
                    phase,
                    KioskTheme.shapes.pill,
                    colors.surfaceAlt,
                    colors.bg,
                    Modifier.width(200.dpx).height(56.dpx),
                )
            }
        }
        SkeletonRow(nameFraction = SKELETON_ROW1_NAME, typeFraction = SKELETON_ROW1_TYPE, phase = phase)
        SkeletonRow(nameFraction = SKELETON_ROW2_NAME, typeFraction = SKELETON_ROW2_TYPE, phase = phase)
        KioskText(
            text = stringResource(R.string.merchants_loading),
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx),
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dpx),
        )
    }
}

private const val SKELETON_TITLE_FRACTION = 0.7f
private const val SKELETON_META_FRACTION = 0.45f
private const val SKELETON_ROW1_NAME = 0.5f
private const val SKELETON_ROW1_TYPE = 0.3f
private const val SKELETON_ROW2_NAME = 0.42f
private const val SKELETON_ROW2_TYPE = 0.26f

@Composable
private fun SkeletonRow(
    nameFraction: Float,
    typeFraction: Float,
    phase: State<Float>,
) {
    val colors = KioskTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(176.dpx)
                .background(colors.surfaceAlt, KioskTheme.shapes.card)
                .padding(start = 24.dpx, end = 32.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dpx),
    ) {
        SkeletonBlock(phase, KioskTheme.shapes.medium, colors.line, colors.skeletonHighlight, Modifier.size(104.dpx))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dpx)) {
            SkeletonBlock(
                phase,
                RoundedCornerShape(12.dpx),
                colors.line,
                colors.skeletonHighlight,
                Modifier.fillMaxWidth(nameFraction).height(36.dpx),
            )
            SkeletonBlock(
                phase,
                RoundedCornerShape(10.dpx),
                colors.line,
                colors.skeletonHighlight,
                Modifier.fillMaxWidth(typeFraction).height(26.dpx),
            )
        }
    }
}

/** Looks of the buttons inside the empty card and the failure panel. */
internal enum class PanelButtonStyle { Accent, Dark, Outline }

/** A 32 dpx-radius panel button ("Retry", "Refresh list", "Log out"). Height comes from [modifier]. */
@Composable
internal fun PanelButton(
    text: String,
    onClick: () -> Unit,
    style: PanelButtonStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val look =
        when (style) {
            PanelButtonStyle.Accent -> Modifier.background(colors.accent, shape)
            PanelButtonStyle.Dark -> Modifier.background(colors.ink, shape)
            PanelButtonStyle.Outline -> Modifier.border(2.dpx, colors.ink, shape)
        }
    val textStyle =
        when (style) {
            PanelButtonStyle.Accent -> KioskTheme.typography.button
            PanelButtonStyle.Dark -> KioskTheme.typography.button.copy(fontSize = 32.spx)
            PanelButtonStyle.Outline -> KioskTheme.typography.body.copy(fontWeight = FontWeight.W600)
        }
    Box(
        modifier =
            modifier
                .pressScale(scale)
                .clip(shape)
                .then(look)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = if (style == PanelButtonStyle.Outline) 48.dpx else 56.dpx),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = text,
            style = textStyle,
            color = if (style == PanelButtonStyle.Outline) colors.ink else colors.surface,
            maxLines = 1,
        )
    }
}

/** `role="alert"`: the panel's text is read out when it appears. */
internal fun Modifier.alertSemantics(): Modifier =
    semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }
