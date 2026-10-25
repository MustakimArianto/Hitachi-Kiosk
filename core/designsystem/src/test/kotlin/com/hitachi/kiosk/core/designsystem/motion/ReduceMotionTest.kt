package com.hitachi.kiosk.core.designsystem.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** DEV-004 / NFR-009 / TR-ACC-05: reduce motion collapses every spec to snap(), off leaves it untouched. */
class ReduceMotionTest {
    private val tween =
        tween<Float>(
            durationMillis = KioskMotion.Splash.MARK_IN_MILLIS,
            delayMillis = KioskMotion.Splash.MARK_IN_DELAY_MILLIS,
            easing = KioskEasing.Emphasized,
        )

    @Test
    fun `a tween becomes a zero delay snap when reduce motion is on`() {
        val reduced = tween.reducedIf(reduce = true)

        assertThat(reduced).isInstanceOf(SnapSpec::class.java)
        assertThat((reduced as SnapSpec).delay).isEqualTo(0)
    }

    @Test
    fun `a spring passed as an animation spec becomes snap when reduce motion is on`() {
        val spring: AnimationSpec<Float> = spring()

        assertThat(spring.reducedIf(reduce = true)).isInstanceOf(SnapSpec::class.java)
    }

    @Test
    fun `a finite spec stays finite when reduced`() {
        val finite: FiniteAnimationSpec<Float> = tween
        val reduced: FiniteAnimationSpec<Float> = finite.reducedIf(reduce = true)

        assertThat(reduced).isInstanceOf(SnapSpec::class.java)
    }

    @Test
    fun `specs are returned untouched when reduce motion is off`() {
        val spring: AnimationSpec<Float> = spring()

        assertThat(tween.reducedIf(reduce = false)).isSameInstanceAs(tween)
        assertThat(spring.reducedIf(reduce = false)).isSameInstanceAs(spring)
    }

    @Test
    fun `the named easings use the design curves`() {
        assertThat(KioskEasing.Emphasized).isEqualTo(CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
        assertThat(KioskEasing.Press).isEqualTo(CubicBezierEasing(0.2f, 0f, 0f, 1f))
        assertThat(KioskEasing.Spring).isEqualTo(CubicBezierEasing(0.3f, 1.15f, 0.4f, 1f))
        assertThat(KioskEasing.Bell).isEqualTo(CubicBezierEasing(0.3f, 1.25f, 0.4f, 1f))
        assertThat(KioskEasing.InOut).isEqualTo(CubicBezierEasing(0.65f, 0f, 0.35f, 1f))
        assertThat(KioskEasing.Fly).isEqualTo(CubicBezierEasing(0.55f, 0f, 0.75f, 0.2f))
    }

    @Test
    fun `the splash timings match the motion inventory`() {
        with(KioskMotion.Splash) {
            assertThat(listOf(MARK_IN_MILLIS, MARK_IN_DELAY_MILLIS, BASE_IN_MILLIS, BASE_IN_DELAY_MILLIS))
                .containsExactly(800, 100, 600, 450)
                .inOrder()
            assertThat(listOf(BELL_UP_MILLIS, BELL_UP_DELAY_MILLIS, WAVE_DRAW_MILLIS, WAVE_DRAW_DELAY_MILLIS))
                .containsExactly(700, 650, 800, 1100)
                .inOrder()
            assertThat(listOf(ECHO_MILLIS, ECHO_DELAY_MILLIS, TITLE_RISE_MILLIS, TITLE_RISE_DELAY_MILLIS))
                .containsExactly(1400, 1900, 900, 900)
                .inOrder()
            assertThat(listOf(TITLE_RISE_SECOND_DELAY_MILLIS, TAGLINE_UP_MILLIS, TAGLINE_UP_DELAY_MILLIS))
                .containsExactly(1000, 800, 1350)
                .inOrder()
            assertThat(listOf(BAR_FILL_MILLIS, BAR_FILL_DELAY_MILLIS, AUTO_ADVANCE_MILLIS))
                .containsExactly(2200, 1600, 3900)
                .inOrder()
        }
    }
}
