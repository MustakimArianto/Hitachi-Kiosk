package com.hitachi.kiosk.inactivity

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animateOnEnter
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.motion.reducedIf
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.navigation.screenTag

/** Test tag of the overlay root (the scrim that continues on any tap), in the `screen:` form of the screen tags. */
val STILL_THERE_TAG: String = screenTag("SCR-13")

/** Test tag of the countdown ring. */
const val STILL_THERE_RING_TAG = "still-there-ring"

// StillThere.dc.html: scrim rgba(23,21,19,.48) with a 6 px backdrop blur. API 30 cannot blur what is drawn below, so
// its scrim is darker to keep the card readable over the busy Menu.
private const val SCRIM_ALPHA = 0.48f
private const val SCRIM_ALPHA_NO_BLUR = 0.62f
private const val BLUR_PX = 6

// Card: inset 72 px, top 460 px, radius 56 px; cardIn 800 ms @120 from translateY(80px) scale(.94).
private const val CARD_TOP_PX = 460
private const val CARD_RADIUS_PX = 56
private const val CARD_IN_MILLIS = 800
private const val CARD_IN_DELAY_MILLIS = 120
private const val CARD_IN_DISTANCE_PX = 80
private const val CARD_IN_SCALE = 0.94f

// Ring: 280 px, r 124, stroke 16; ringIn 900 ms @260 from scale(.7) rotate(-20deg).
private const val RING_PX = 280
private const val RING_RADIUS_PX = 124
private const val RING_STROKE_PX = 16
private const val RING_IN_MILLIS = 900
private const val RING_IN_DELAY_MILLIS = 260
private const val RING_IN_SCALE = 0.7f
private const val RING_IN_ROTATION = -20f
private const val RING_START_ANGLE = -90f
private const val FULL_SWEEP = 360f

// Number: 96 px / 104 px, -3 px; numA/numB 420 ms from translateY(-50%) on every tick.
private const val NUMBER_SIZE_PX = 96
private const val NUMBER_LINE_PX = 104
private const val NUMBER_TRACKING_PX = -3
private const val NUMBER_TICK_MILLIS = 420
private const val NUMBER_TICK_SHIFT = 0.5f
private const val SECONDS_LABEL_PX = 24

// Last 5 s: urgent pulse 600 ms (1 → 1.06 at 30 % → 1, Pop) and halo 900 ms (opacity .5 → 0, scale 1 → 1.35, Press).
private const val PULSE_MILLIS = 600
private const val PULSE_PEAK_MILLIS = 180
private const val PULSE_SCALE = 1.06f
private const val HALO_MILLIS = 900
private const val HALO_ALPHA = 0.5f
private const val HALO_SCALE = 1.35f
private const val HALO_BORDER_PX = 4

// Text and buttons: fadeUp 700 ms @380 / @440 / @520.
private const val TEXT_UP_MILLIS = 700
private const val TITLE_UP_DELAY_MILLIS = 380
private const val BODY_UP_DELAY_MILLIS = 440
private const val ACTIONS_UP_DELAY_MILLIS = 520
private const val BODY_MAX_WIDTH_PX = 680

/**
 * Draws [content] (the current screen) and, while [state] is [StillThereState.Showing], blurs it and lays the SCR-13
 * overlay over it (`StillThere.dc.html`). The blur needs API 31; on API 30 the overlay's darker scrim stands in.
 */
@Composable
fun StillThereHost(
    state: StillThereState,
    onContinue: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val showing = state as? StillThereState.Showing
    val blur = remember { Animatable(0f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(showing != null) {
        if (showing != null) {
            blur.animateTo(
                1f,
                tween<Float>(KioskMotion.StillThere.SCRIM_IN_MILLIS, easing = KioskEasing.Emphasized).reducedIf(reduce),
            )
        } else {
            blur.snapTo(0f)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        val radius = BLUR_PX.dpx * blur.value
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (showing != null && radius > 0.dpx) Modifier.blur(radius) else Modifier),
        ) {
            content()
        }
        if (showing != null) {
            StillThereOverlay(state = showing, onContinue = onContinue, onStartOver = onStartOver)
        }
    }
}

/**
 * SCR-13 "Still there?" (FR-023, `StillThere.dc.html`): a scrim over the current screen and a card with the countdown
 * ring, "Still there?", the cancellation notice, "Continue ordering" and "Start over". A tap anywhere outside the
 * buttons continues too (AC2). The ring pulses in the last 5 s; under reduce motion nothing moves.
 */
@Composable
fun StillThereOverlay(
    state: StillThereState.Showing,
    onContinue: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val title = stringResource(R.string.still_there_title)
    val scrimIn = animateEntrance(KioskMotion.StillThere.SCRIM_IN_MILLIS)
    val cardIn = animateEntrance(CARD_IN_MILLIS, CARD_IN_DELAY_MILLIS)
    val scrimAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SCRIM_ALPHA else SCRIM_ALPHA_NO_BLUR
    val tapAnywhere = stringResource(R.string.still_there_tap_anywhere)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(STILL_THERE_TAG)
                .semantics { paneTitle = title }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = tapAnywhere,
                    onClick = onContinue,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .fadeIn(scrimIn)
                    .background(colors.ink.copy(alpha = scrimAlpha)),
        )
        val cardShape = RoundedCornerShape(CARD_RADIUS_PX.dpx)
        val cardDistance = CARD_IN_DISTANCE_PX.dpx
        Column(
            modifier =
                Modifier
                    .padding(start = 72.dpx, end = 72.dpx, top = CARD_TOP_PX.dpx)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = cardIn.value
                        alpha = p
                        translationY = cardDistance.toPx() * (1f - p)
                        val scale = CARD_IN_SCALE + (1f - CARD_IN_SCALE) * p
                        scaleX = scale
                        scaleY = scale
                    }.kioskShadow(cardShape, KioskTheme.elevation.floating)
                    .clip(cardShape)
                    .background(colors.surface)
                    .padding(start = 72.dpx, end = 72.dpx, top = 80.dpx, bottom = 64.dpx),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CountdownRing(state)
            Spacer(Modifier.height(56.dpx))
            KioskText(
                text = title,
                style = KioskTheme.typography.title.copy(lineHeight = 1.em),
                textAlign = TextAlign.Center,
                cssLineBox = true,
                modifier =
                    Modifier
                        .fadeUp(
                            animateEntrance(TEXT_UP_MILLIS, TITLE_UP_DELAY_MILLIS),
                            KioskMotion.Common.UP_DISTANCE_PX.dpx,
                        ).semantics { heading() },
            )
            Spacer(Modifier.height(20.dpx))
            KioskText(
                text = pluralStringResource(R.plurals.still_there_body, state.secondsLeft, state.secondsLeft),
                style = KioskTheme.typography.body,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .widthIn(max = BODY_MAX_WIDTH_PX.dpx)
                        .fadeUp(
                            animateEntrance(TEXT_UP_MILLIS, BODY_UP_DELAY_MILLIS),
                            KioskMotion.Common.UP_DISTANCE_PX.dpx,
                        ).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(56.dpx))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fadeUp(
                            animateEntrance(TEXT_UP_MILLIS, ACTIONS_UP_DELAY_MILLIS),
                            KioskMotion.Common.UP_DISTANCE_PX.dpx,
                        ),
                verticalArrangement = Arrangement.spacedBy(16.dpx),
            ) {
                ContinueButton(onContinue)
                StartOverButton(onStartOver)
            }
        }
    }
}

@Composable
private fun CountdownRing(state: StillThereState.Showing) {
    val colors = KioskTheme.colors
    val ringIn = animateEntrance(RING_IN_MILLIS, RING_IN_DELAY_MILLIS)
    val fraction = state.secondsLeft.toFloat() / StillThereState.COUNTDOWN_SECONDS
    val sweep = remember { Animatable(fraction) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(fraction) {
        sweep.animateTo(
            fraction,
            tween<Float>(KioskMotion.StillThere.COUNTDOWN_STEP_MILLIS, easing = KioskEasing.Linear).reducedIf(reduce),
        )
    }
    val description =
        pluralStringResource(R.plurals.still_there_countdown_description, state.secondsLeft, state.secondsLeft)
    Box(
        modifier =
            Modifier
                .size(RING_PX.dpx)
                .testTag(STILL_THERE_RING_TAG)
                .clearAndSetSemantics { contentDescription = description }
                .graphicsLayer {
                    val p = ringIn.value
                    alpha = p
                    val scale = RING_IN_SCALE + (1f - RING_IN_SCALE) * p
                    scaleX = scale
                    scaleY = scale
                    rotationZ = RING_IN_ROTATION * (1f - p)
                },
        contentAlignment = Alignment.Center,
    ) {
        // A new key per second restarts the one-shot pulse and halo, like the design's alternating A/B keyframes.
        key(state.secondsLeft) {
            if (state.countdownLow) Halo()
            val pulse = if (state.countdownLow) animatePulse() else null
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = pulse?.value ?: 1f
                            scaleX = scale
                            scaleY = scale
                        },
                contentAlignment = Alignment.Center,
            ) {
                RingArcs(sweep = { sweep.value }, track = colors.surfaceAlt, accent = colors.accent)
                RingNumber(state.secondsLeft)
            }
        }
    }
}

@Composable
private fun RingArcs(
    sweep: () -> Float,
    track: Color,
    accent: Color,
) {
    val radius = RING_RADIUS_PX.dpx
    val stroke = RING_STROKE_PX.dpx
    Canvas(modifier = Modifier.fillMaxSize()) {
        val r = radius.toPx()
        val topLeft = Offset(center.x - r, center.y - r)
        val arcSize = Size(r * 2, r * 2)
        drawCircle(color = track, radius = r, style = Stroke(width = stroke.toPx()))
        val fraction = sweep()
        if (fraction > 0f) {
            drawArc(
                color = accent,
                startAngle = RING_START_ANGLE,
                sweepAngle = FULL_SWEEP * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun RingNumber(secondsLeft: Int) {
    // The first number comes in with the ring; every later one drops in (numA / numB).
    val tick =
        animateOnEnter(
            if (secondsLeft < StillThereState.COUNTDOWN_SECONDS) {
                tween(NUMBER_TICK_MILLIS, easing = KioskEasing.Emphasized)
            } else {
                tween(0)
            },
        )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.height(NUMBER_LINE_PX.dpx).clipToBounds(), contentAlignment = Alignment.Center) {
            KioskText(
                text = secondsLeft.toString(),
                style =
                    KioskTheme.typography.display.copy(
                        fontSize = NUMBER_SIZE_PX.spx,
                        lineHeight = NUMBER_LINE_PX.spx,
                        letterSpacing = NUMBER_TRACKING_PX.spx,
                        fontWeight = FontWeight.W800,
                        fontFeatureSettings = "tnum",
                    ),
                cssLineBox = true,
                modifier =
                    Modifier.graphicsLayer {
                        val p = tick.value
                        alpha = p
                        translationY = -size.height * NUMBER_TICK_SHIFT * (1f - p)
                    },
            )
        }
        KioskText(
            text = stringResource(R.string.still_there_seconds),
            style = KioskTheme.typography.meta.copy(fontSize = SECONDS_LABEL_PX.spx, fontWeight = FontWeight.W600),
            color = KioskTheme.colors.muted,
        )
    }
}

/** The urgent pulse: 1 → 1.06 at 30 % → 1 (`urgentA` / `urgentB`); its value is the scale. */
@Composable
private fun animatePulse(): State<Float> =
    animateOnEnter(
        keyframes {
            durationMillis = PULSE_MILLIS
            1f at 0 using KioskEasing.Pop
            PULSE_SCALE at PULSE_PEAK_MILLIS using KioskEasing.Pop
            1f at PULSE_MILLIS
        },
    )

@Composable
private fun Halo() {
    val halo = animateEntrance(HALO_MILLIS, easing = KioskEasing.Press)
    val accent = KioskTheme.colors.accent
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = halo.value
                    alpha = HALO_ALPHA * (1f - p)
                    val scale = 1f + (HALO_SCALE - 1f) * p
                    scaleX = scale
                    scaleY = scale
                }.border(HALO_BORDER_PX.dpx, accent, CircleShape),
    )
}

@Composable
private fun ContinueButton(onContinue: () -> Unit) {
    val colors = KioskTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(152.dpx)
                .pressScale(scale)
                .clip(KioskTheme.shapes.card)
                .background(colors.accent)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onContinue,
                ),
        horizontalArrangement = Arrangement.spacedBy(16.dpx, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KioskText(
            text = stringResource(R.string.still_there_continue),
            style = KioskTheme.typography.button,
            color = colors.surface,
        )
        KioskIcon(icon = KioskIcons.ArrowRightBold, tint = colors.surface, modifier = Modifier.size(40.dpx))
    }
}

@Composable
private fun StartOverButton(onStartOver: () -> Unit) {
    val colors = KioskTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(104.dpx)
                .pressScale(scale)
                .clip(KioskTheme.shapes.pill)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onStartOver,
                ),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = stringResource(R.string.still_there_start_over),
            style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W600),
            color = colors.muted,
        )
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun StillThereOverlayPreview() {
    KioskTheme(reduceMotion = true) {
        DesignFrame {
            StillThereHost(
                state = StillThereState.Showing(StillThereState.COUNTDOWN_SECONDS),
                onContinue = {},
                onStartOver = {},
            ) {
                Box(Modifier.fillMaxSize().background(KioskTheme.colors.bg))
            }
        }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun StillThereOverlayCountdownLowPreview() {
    KioskTheme(reduceMotion = true) {
        DesignFrame {
            StillThereHost(
                state = StillThereState.Showing(StillThereState.COUNTDOWN_LOW_SECONDS),
                onContinue = {},
                onStartOver = {},
            ) {
                Box(Modifier.fillMaxSize().background(KioskTheme.colors.bg))
            }
        }
    }
}
