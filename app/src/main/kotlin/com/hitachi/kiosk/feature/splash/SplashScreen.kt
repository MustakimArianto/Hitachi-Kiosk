package com.hitachi.kiosk.feature.splash

import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Splash
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animateOnEnter
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.riseIn
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** `markIn`: scale .6 → 1, rotate −8° → 0. */
private const val MARK_START_SCALE = 0.6f
private const val MARK_START_ROTATION = -8f

/** `dotIn`: 0 → 1.25 at 70 % → 1. */
private const val DOT_OVERSHOOT = 1.25f
private const val DOT_OVERSHOOT_AT = 0.7f

/** `ringOut`: opacity .5 → 0, scale 1 → 1.9. */
private const val RING_START_ALPHA = 0.5f
private const val RING_GROWTH = 0.9f

/** Test tag of the brand mark, which opens Developer options after 5 taps in debug builds (DEV-009). */
const val SPLASH_MARK_TAG = "brand-mark"

/** Test tag of the Splash root. */
const val SPLASH_SCREEN_TAG = "screen:SCR-01"

/**
 * SCR-01 Splash (`Main.dc.html`, FR-001, DEV-010). Plays the fixed brand animation of PRD §11.4; with reduce motion
 * on it shows the final frame at once (FR-001 AC3). It never navigates by itself: the route does, through
 * `StartupRouter`.
 *
 * @param onMarkClick a tap on the brand mark (five taps open Developer options in debug builds).
 */
@Composable
fun SplashScreen(
    onMarkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .testTag(SPLASH_SCREEN_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.offset(y = (-40).dpx),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(64.dpx),
        ) {
            SplashMark(onClick = onMarkClick)
            SplashTitle()
            SplashBar()
        }
        val caption = animateEntrance(Splash.CAPTION_FADE_MILLIS, Splash.CAPTION_FADE_DELAY_MILLIS, KioskEasing.Ease)
        KioskText(
            text = stringResource(R.string.splash_preparing),
            style = KioskTheme.typography.meta.copy(fontSize = 24.spx),
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 96.dpx)
                    .fadeIn(caption),
        )
    }
}

@Composable
private fun SplashMark(onClick: () -> Unit) {
    val colors = KioskTheme.colors
    val markShape = RoundedCornerShape(60.dpx)
    val description = stringResource(R.string.splash_brand_mark_description)
    val mark = animateEntrance(Splash.MARK_IN_MILLIS, Splash.MARK_IN_DELAY_MILLIS)
    val ring = animateEntrance(Splash.RING_OUT_MILLIS, Splash.RING_OUT_DELAY_MILLIS)
    val dot =
        animateOnEnter(
            keyframes {
                durationMillis = Splash.DOT_IN_MILLIS
                delayMillis = Splash.DOT_IN_DELAY_MILLIS
                0f at 0 using KioskEasing.Spring
                DOT_OVERSHOOT at (Splash.DOT_IN_MILLIS * DOT_OVERSHOOT_AT).toInt() using KioskEasing.Spring
            },
        )
    Box(
        modifier =
            Modifier
                .size(200.dpx)
                .semantics { contentDescription = description }
                .clickable(interactionSource = null, indication = null, onClick = onClick)
                .testTag(SPLASH_MARK_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val p = ring.value
                        alpha = RING_START_ALPHA * (1f - p)
                        scaleX = 1f + RING_GROWTH * p
                        scaleY = 1f + RING_GROWTH * p
                    }.border(3.dpx, colors.accent, markShape),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val p = mark.value
                        alpha = p
                        val scale = MARK_START_SCALE + (1f - MARK_START_SCALE) * p
                        scaleX = scale
                        scaleY = scale
                        rotationZ = MARK_START_ROTATION * (1f - p)
                    }.background(colors.ink, markShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(64.dpx)
                        .graphicsLayer {
                            scaleX = dot.value
                            scaleY = dot.value
                        }.background(colors.accent, CircleShape),
            )
        }
    }
}

@Composable
private fun SplashTitle() {
    val colors = KioskTheme.colors
    val display = KioskTheme.typography.display
    val first = animateEntrance(Splash.TITLE_RISE_MILLIS, Splash.TITLE_RISE_DELAY_MILLIS)
    val second = animateEntrance(Splash.TITLE_RISE_MILLIS, Splash.TITLE_RISE_SECOND_DELAY_MILLIS)
    val tagline = animateEntrance(Splash.TAGLINE_UP_MILLIS, Splash.TAGLINE_UP_DELAY_MILLIS)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        Row(
            modifier =
                Modifier
                    .clipToBounds()
                    .padding(start = 8.dpx, end = 8.dpx, bottom = 12.dpx),
            horizontalArrangement = Arrangement.spacedBy(28.dpx),
        ) {
            KioskText(
                text = stringResource(R.string.splash_title_brand),
                style = display,
                modifier = Modifier.riseIn(first),
                maxLines = 1,
                cssLineBox = true,
            )
            KioskText(
                text = stringResource(R.string.splash_title_product),
                style = display.copy(fontWeight = FontWeight.W500),
                color = colors.muted,
                modifier = Modifier.riseIn(second),
                maxLines = 1,
                cssLineBox = true,
            )
        }
        KioskText(
            text = stringResource(R.string.splash_tagline),
            style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W500),
            color = colors.muted,
            modifier = Modifier.fadeUp(tagline, 24.dpx),
        )
    }
}

@Composable
private fun SplashBar() {
    val colors = KioskTheme.colors
    val track = animateEntrance(Splash.BAR_TRACK_FADE_MILLIS, Splash.BAR_TRACK_FADE_DELAY_MILLIS, KioskEasing.Ease)
    val fill = animateEntrance(Splash.BAR_FILL_MILLIS, Splash.BAR_FILL_DELAY_MILLIS, KioskEasing.InOut)
    val barShape = RoundedCornerShape(3.dpx)
    Box(
        modifier =
            Modifier
                .width(360.dpx)
                .height(6.dpx)
                .fadeIn(track)
                .clip(barShape)
                .background(colors.line),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(pivotFractionX = 0f, pivotFractionY = 0.5f)
                        scaleX = fill.value
                    }.background(colors.accent),
        )
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun SplashScreenPreview() {
    KioskTheme(reduceMotion = true) {
        DesignFrame { SplashScreen(onMarkClick = {}) }
    }
}
