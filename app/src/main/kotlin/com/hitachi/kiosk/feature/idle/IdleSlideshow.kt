package com.hitachi.kiosk.feature.idle

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Idle
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.reducedIf
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.ui.MenuPhoto
import kotlinx.coroutines.delay

/** Test tag of the slide on screen, with its index ("idle-slide:0"). */
fun idleSlideTag(index: Int): String = "idle-slide:$index"

/** The Ken Burns zoom of the design: `kbA` zooms out from 1.06 (even slides), `kbB` zooms in to 1.06 (odd slides). */
private const val KEN_BURNS_SCALE = 1.06f

/** A progress segment fills from its left edge (`transform-origin: left`). */
private val FILL_ORIGIN = TransformOrigin(pivotFractionX = 0f, pivotFractionY = 0.5f)

/**
 * Which slide shows and when the current one started (FR-006: 7 s per slide). [index] only moves forward on the
 * timer; [period] changes whenever a slide's 7 s start again (a new slide, or Idle coming back on screen), so the
 * progress dot restarts with it.
 */
@Stable
internal class SlideshowState(
    private val count: Int,
) {
    var shown by mutableIntStateOf(0)
        private set
    var period by mutableIntStateOf(0)
        private set

    fun startPeriod() {
        period++
    }

    fun next() {
        shown++
    }

    val index: Int get() = shown % count
}

/**
 * Advances the slideshow every [Idle.PROGRESS_MILLIS] while the screen is at least STARTED; it pauses when Idle is
 * not visible (FR-006 rule) and the current slide gets its full 7 s again when it returns. Slides advance under
 * reduce motion too; only their animations stop.
 */
@Composable
internal fun rememberSlideshow(count: Int): SlideshowState {
    val state = remember(count) { SlideshowState(count) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.startPeriod()
                delay(Idle.PROGRESS_MILLIS.toLong())
                state.next()
            }
        }
    }
    return state
}

/**
 * The full-screen promo slides (FR-006, `Idle.dc.html`): the slide at [index] of [slides] crossfades in over 1.4 s
 * and zooms slowly for 8 s (Ken Burns). Decorative: the slides have no description (`label=""` in the design).
 */
@Composable
internal fun IdleSlides(
    slides: List<IdleSlideUi>,
    index: Int,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = index,
        animationSpec = motionSpec(tween(Idle.SLIDE_CROSSFADE_MILLIS, easing = FastOutSlowInEasing)),
        modifier = modifier.fillMaxSize().clipToBounds(),
        label = "idleSlides",
    ) { shown ->
        slides.getOrNull(shown)?.let { slide -> IdleSlide(slide = slide, index = shown) }
    }
}

@Composable
private fun IdleSlide(
    slide: IdleSlideUi,
    index: Int,
) {
    val kenBurns = animateEntrance(Idle.KEN_BURNS_MILLIS, easing = KioskEasing.Linear)
    val zoomOut = index % 2 == 0
    MenuPhoto(
        imageUrl = slide.imageUrl,
        tone = null,
        contentDescription = null,
        shape = RectangleShape,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale =
                        if (zoomOut) {
                            lerp(KEN_BURNS_SCALE, 1f, kenBurns.value)
                        } else {
                            lerp(1f, KEN_BURNS_SCALE, kenBurns.value)
                        }
                    scaleX = scale
                    scaleY = scale
                }.testTag(idleSlideTag(index)),
    )
}

/**
 * The segmented progress pill of the header: one 56 × 8 dpx segment per slide; earlier segments are full, the current
 * one fills linearly over 7 s (`progA`/`progB`), later ones are empty.
 */
@Composable
internal fun SlideProgress(
    count: Int,
    index: Int,
    period: Int,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val reduce = LocalReduceMotion.current
    val fill = remember(period) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(fill) {
        fill.animateTo(1f, tween<Float>(Idle.PROGRESS_MILLIS, easing = KioskEasing.Linear).reducedIf(reduce))
    }
    Row(
        modifier =
            modifier
                .height(72.dpx)
                .kioskShadow(KioskTheme.shapes.pill, KioskTheme.elevation.floating)
                .background(colors.surface, KioskTheme.shapes.pill)
                .padding(horizontal = 28.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dpx),
    ) {
        val segmentShape = RoundedCornerShape(4.dpx)
        repeat(count) { i ->
            Box(
                modifier =
                    Modifier
                        .width(56.dpx)
                        .height(8.dpx)
                        .clip(segmentShape)
                        .background(colors.line),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(56.dpx)
                            .graphicsLayer {
                                transformOrigin = FILL_ORIGIN
                                scaleX =
                                    when {
                                        i < index -> 1f
                                        i == index -> fill.value
                                        else -> 0f
                                    }
                            }.background(colors.ink),
                )
            }
        }
    }
}
