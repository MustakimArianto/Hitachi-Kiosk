package com.hitachi.kiosk.feature.idle

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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Idle
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.riseIn
import com.hitachi.kiosk.core.designsystem.theme.KioskShadow
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** Test tag of the Idle root. */
const val IDLE_SCREEN_TAG = "screen:SCR-04"

/** Test tag of the full-screen "Tap to order" layer. */
const val IDLE_TAP_TAG = "idle-tap-to-order"

/** Test tag of the hidden staff corner. */
const val IDLE_STAFF_CORNER_TAG = "idle-staff-corner"

/** Test tag of the red issue dot. */
const val IDLE_ISSUE_DOT_TAG = "idle-issue-dot"

/** A continuous press this long on the staff corner opens the staff PIN (FR-006 AC3, A-04). */
const val STAFF_HOLD_MILLIS: Long = 2_000L

// Motion of `Idle.dc.html` that KioskMotion.Idle does not name yet.
private const val HEADER_FADE_MILLIS = 700
private const val SUBTITLE_RISE_DELAY_MILLIS = 360
private const val CTA_UP_MILLIS = 900
private const val CTA_UP_DELAY_MILLIS = 450
private const val CTA_UP_DISTANCE_PX = 32
private const val CTA_RING_DELAY_MILLIS = 1400
private const val ALERT_PULSE_LOOP_MILLIS = 2000

/** `ring` keyframes: opacity .5 → 0 while scaling to (1.05, 1.3). */
private const val CTA_RING_ALPHA = 0.5f
private const val CTA_RING_GROW_X = 0.05f
private const val CTA_RING_GROW_Y = 0.3f

/** `tap` keyframes: at 45 % the hand is 6 px lower and at .94 scale. */
private const val TAP_PRESS_AT = 0.45f
private const val TAP_DROP_PX = 6
private const val TAP_SHRINK = 0.06f

/** `alertPulse`: a 22 px halo at .5 alpha fading out. */
private const val ALERT_PULSE_PX = 22
private const val ALERT_PULSE_ALPHA = 0.5f

/** The sheet's upward shadow, `0 -24px 48px rgba(23,21,19,.06)`, and the CTA's accent shadow at .28. */
private const val SHEET_SHADOW_ALPHA = 0.06f
private const val CTA_SHADOW_ALPHA = 0.28f
private const val TIGHT_EM = -0.02f
private const val CTA_LINE_HEIGHT_EM = 1.15f

/**
 * SCR-04 Idle (`Idle.dc.html`, `S_Idle_issues`, FR-006, DEV-024): the promo slideshow under the merchant chip and
 * progress dots, the "Hungry?" sheet with the pulsing "Tap to order" button, and the hidden staff corner. A tap
 * anywhere except the corner starts an order; holding the corner for 2 s opens staff settings.
 */
@Composable
fun IdleScreen(
    state: IdleUiState,
    onEvent: (IdleEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val show = rememberSlideshow(state.slides.size)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(IDLE_SCREEN_TAG),
    ) {
        IdleSlides(slides = state.slides, index = show.index)
        // The design's full-screen link: the header and sheet above it take no touches, so they fall through.
        TapLayer(onTap = { onEvent(IdleEvent.TapToOrder) })
        IdleHeader(brandName = state.brandName) {
            SlideProgress(count = state.slides.size, index = show.index, period = show.period)
        }
        IdleSheet(Modifier.align(Alignment.BottomCenter))
        if (state.showIssueDot) {
            IssueDot(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 44.dpx, bottom = 44.dpx),
            )
        }
        StaffCorner(
            onHold = { onEvent(IdleEvent.StaffHold) },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun TapLayer(onTap: () -> Unit) {
    val label = stringResource(R.string.idle_tap_to_order)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onTap)
                .semantics { contentDescription = label }
                .testTag(IDLE_TAP_TAG),
    )
}

@Composable
private fun IdleHeader(
    brandName: String?,
    progress: @Composable () -> Unit,
) {
    val fade = animateEntrance(HEADER_FADE_MILLIS, easing = KioskEasing.Ease)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 72.dpx, end = 72.dpx, top = 48.dpx)
                .fadeIn(fade),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (brandName != null) {
            BrandChip(name = brandName, modifier = Modifier.weight(1f, fill = false))
        } else {
            Spacer(Modifier.weight(1f))
        }
        progress()
    }
}

/** The white merchant chip: the ink tile with the accent dot and the name (34/700). */
@Composable
private fun BrandChip(
    name: String,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val pill = KioskTheme.shapes.pill
    Row(
        modifier =
            modifier
                .padding(end = 24.dpx)
                .height(112.dpx)
                .kioskShadow(pill, KioskTheme.elevation.floating)
                .background(colors.surface, pill)
                .padding(start = 16.dpx, end = 40.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dpx),
    ) {
        Box(
            modifier =
                Modifier
                    .size(80.dpx)
                    .background(colors.ink, KioskTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(26.dpx).background(colors.accent, CircleShape))
        }
        KioskText(
            text = name,
            style = KioskTheme.typography.button,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IdleSheet(modifier: Modifier = Modifier) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val shape = RoundedCornerShape(topStart = 56.dpx, topEnd = 56.dpx)
    val sheet = animateEntrance(Idle.SHEET_UP_MILLIS, Idle.SHEET_UP_DELAY_MILLIS)
    val title = animateEntrance(Idle.TITLE_RISE_MILLIS, Idle.TITLE_RISE_DELAY_MILLIS)
    val subtitle = animateEntrance(Idle.TITLE_RISE_MILLIS, SUBTITLE_RISE_DELAY_MILLIS)
    val cta = animateEntrance(CTA_UP_MILLIS, CTA_UP_DELAY_MILLIS)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .height(620.dpx)
                .graphicsLayer { translationY = size.height * (1f - sheet.value) }
                .kioskShadow(
                    shape,
                    KioskShadow(
                        offsetY = (-24).dpx,
                        blurRadius = 48.dpx,
                        color = colors.ink.copy(alpha = SHEET_SHADOW_ALPHA),
                    ),
                ).background(colors.bg, shape)
                .padding(start = 72.dpx, end = 72.dpx, top = 64.dpx, bottom = 48.dpx),
    ) {
        Box(Modifier.clipToBounds()) {
            KioskText(
                text = stringResource(R.string.idle_title),
                style = typography.display.copy(lineHeight = 1.em),
                modifier = Modifier.riseIn(title),
                cssLineBox = true,
            )
        }
        Box(
            Modifier
                .padding(top = 12.dpx)
                .clipToBounds(),
        ) {
            KioskText(
                text = stringResource(R.string.idle_subtitle),
                style = typography.body.copy(fontSize = 34.spx, fontWeight = FontWeight.W500),
                color = colors.muted,
                modifier = Modifier.riseIn(subtitle),
            )
        }
        Spacer(Modifier.weight(1f))
        TapToOrderButton(Modifier.fadeUp(cta, CTA_UP_DISTANCE_PX.dpx))
        KioskText(
            text = stringResource(R.string.idle_helper),
            style = typography.meta,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dpx),
        )
    }
}

/**
 * The accent "Tap to order" button with its pulsing ring and nudging hand. It only shows the way: the full-screen
 * layer below takes the tap and carries the description, so the button is hidden from accessibility.
 */
@Composable
private fun TapToOrderButton(modifier: Modifier = Modifier) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.dock
    val reduce = LocalReduceMotion.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(168.dpx)
                .clearAndSetSemantics {},
    ) {
        if (!reduce) CtaRing(Modifier.matchParentSize())
        Row(
            modifier =
                Modifier
                    .matchParentSize()
                    .kioskShadow(
                        shape,
                        KioskShadow(
                            offsetY = 24.dpx,
                            blurRadius = 48.dpx,
                            color = colors.accent.copy(alpha = CTA_SHADOW_ALPHA),
                        ),
                    ).background(colors.accent, shape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dpx, Alignment.CenterHorizontally),
        ) {
            TapIcon()
            KioskText(
                text = stringResource(R.string.idle_tap_to_order),
                style =
                    KioskTheme.typography.title.copy(
                        fontSize = 52.spx,
                        letterSpacing = TIGHT_EM.em,
                        lineHeight = CTA_LINE_HEIGHT_EM.em,
                    ),
                color = colors.surface,
                maxLines = 1,
            )
        }
    }
}

/** The `ring` loop: an accent outline growing out of the button every 2.8 s, first after 1.4 s. */
@Composable
private fun CtaRing(modifier: Modifier = Modifier) {
    val ring by rememberInfiniteTransition(label = "ctaRing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(Idle.CTA_RING_LOOP_MILLIS, easing = KioskEasing.Press),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(CTA_RING_DELAY_MILLIS),
            ),
        label = "ctaRingProgress",
    )
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    alpha = CTA_RING_ALPHA * (1f - ring)
                    scaleX = 1f + CTA_RING_GROW_X * ring
                    scaleY = 1f + CTA_RING_GROW_Y * ring
                }.border(3.dpx, KioskTheme.colors.accent, KioskTheme.shapes.dock),
    )
}

/** The pointing hand of the button, pressing down every 1.8 s (`tap`); still under reduce motion. */
@Composable
private fun TapIcon() {
    val press = tapLoop()
    val drop = TAP_DROP_PX.dpx
    KioskIcon(
        icon = KioskIcons.Tap,
        tint = KioskTheme.colors.surface,
        modifier =
            Modifier
                .size(60.dpx)
                .graphicsLayer {
                    val p = press.value
                    translationY = drop.toPx() * p
                    scaleX = 1f - TAP_SHRINK * p
                    scaleY = 1f - TAP_SHRINK * p
                },
    )
}

@Composable
private fun tapLoop(): State<Float> =
    if (LocalReduceMotion.current) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "tap").animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = Idle.TAP_ICON_LOOP_MILLIS
                            0f at 0 using KioskEasing.EaseInOut
                            1f at (Idle.TAP_ICON_LOOP_MILLIS * TAP_PRESS_AT).toInt() using KioskEasing.EaseInOut
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "tapPress",
        )
    }

/**
 * The red dot of `S_Idle_issues` (A-31): a 32 dpx dot with a 4 dpx `bg` border, pulsing every 2 s. Announced as
 * "Staff attention needed". Touches pass through to the staff corner on top.
 */
@Composable
private fun IssueDot(modifier: Modifier = Modifier) {
    val colors = KioskTheme.colors
    val alert = colors.alert
    val label = stringResource(R.string.idle_issue_dot)
    val pulse = alertPulse()
    val spread = ALERT_PULSE_PX.dpx
    Box(
        modifier =
            modifier
                .size(32.dpx)
                .semantics {
                    contentDescription = label
                    liveRegion = LiveRegionMode.Polite
                }.drawBehind {
                    val p = pulse.value
                    if (p > 0f) {
                        drawCircle(
                            color = alert.copy(alpha = ALERT_PULSE_ALPHA * (1f - p)),
                            radius = size.minDimension / 2f + spread.toPx() * p,
                        )
                    }
                }.background(colors.bg, CircleShape)
                .padding(4.dpx)
                .background(alert, CircleShape)
                .testTag(IDLE_ISSUE_DOT_TAG),
    )
}

@Composable
private fun alertPulse(): State<Float> =
    if (LocalReduceMotion.current) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "alertPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(ALERT_PULSE_LOOP_MILLIS, easing = KioskEasing.Press),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "alertPulseProgress",
        )
    }

/**
 * The transparent 160 × 160 dpx staff corner (FR-006, A-04): a continuous 2 s press calls [onHold]; lifting the finger
 * or sliding out of the corner earlier cancels, and a short tap does nothing (it never starts an order). Accessibility
 * services get a long-click action instead.
 */
@Composable
private fun StaffCorner(
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.idle_staff_corner)
    val currentOnHold by rememberUpdatedState(onHold)
    Box(
        modifier =
            modifier
                .size(160.dpx)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    role = Role.Button
                    onLongClick {
                        currentOnHold()
                        true
                    }
                }.pointerInput(Unit) { detectHold(STAFF_HOLD_MILLIS) { currentOnHold() } }
                .testTag(IDLE_STAFF_CORNER_TAG),
    )
}

/** Calls [onHold] once a pointer has stayed down inside the element for [holdMillis]. Consumes every event. */
private suspend fun PointerInputScope.detectHold(
    holdMillis: Long,
    onHold: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        val endedEarly = withTimeoutOrNull(holdMillis) { awaitReleaseOrExit() }
        if (endedEarly == null) {
            onHold()
            consumeUntilUp()
        }
    }
}

/** Returns when every pointer is up or one has left the element (`pointerup` / `pointerleave`). */
private suspend fun AwaitPointerEventScope.awaitReleaseOrExit() {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
        val left = event.changes.any { it.isOutOfBounds(size, extendedTouchPadding) }
        if (left || event.changes.none { it.pressed }) return
    }
}

private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    do {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}

@Composable
private fun IdlePreview(state: IdleUiState) {
    KioskTheme(reduceMotion = true) {
        DesignFrame { IdleScreen(state = state, onEvent = {}) }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun IdleDefaultPreview() = IdlePreview(IdleSamples.Default)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun IdleIssuesPreview() = IdlePreview(IdleSamples.Issues)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun IdleNoPromosPreview() = IdlePreview(IdleSamples.NoPromos)
