package com.hitachi.kiosk.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.LocalDesignScale
import com.hitachi.kiosk.core.designsystem.unit.dpx
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DEV-004: a specimen of the type scale, palette, radii and floating elevation, plus theme wiring checks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w540dp-h960dp-xhdpi")
class KioskThemeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the theme specimen matches the golden`() {
        composeRule.setContent { KioskTheme { DesignFrame { Specimen() } } }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/KioskTheme_specimen.png")
    }

    @Test
    fun `the theme specimen with larger text matches the golden`() {
        composeRule.setContent { KioskTheme(largeText = true) { DesignFrame { Specimen() } } }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/KioskTheme_specimen_largeText.png")
    }

    @Test
    fun `the design frame rescales typography and spacing for a 540 dp window`() {
        var scale = 0f
        var typography: KioskTypography? = null
        var spacing: KioskSpacing? = null
        composeRule.setContent {
            KioskTheme {
                DesignFrame {
                    scale = LocalDesignScale.current
                    typography = KioskTheme.typography
                    spacing = KioskTheme.spacing
                }
            }
        }
        composeRule.waitForIdle()

        assertThat(scale).isEqualTo(0.5f)
        assertThat(typography?.display?.fontSize?.value).isEqualTo(56f)
        assertThat(spacing?.sideMargin?.value).isEqualTo(36f)
    }

    @Test
    fun `the theme provides the merchant accent and collapses motion when reduce motion is on`() {
        var accent = Color.Unspecified
        var reduced: AnimationSpec<Float>? = null
        var normal: AnimationSpec<Float>? = null
        val spec = tween<Float>(durationMillis = 300)
        composeRule.setContent {
            KioskTheme(accent = KioskPalette.AccentIndigo, reduceMotion = true) {
                accent = KioskTheme.colors.accent
                reduced = motionSpec(spec)
            }
            KioskTheme(reduceMotion = false) { normal = motionSpec(spec) }
        }
        composeRule.waitForIdle()

        assertThat(accent).isEqualTo(KioskPalette.AccentIndigo)
        assertThat(reduced).isInstanceOf(SnapSpec::class.java)
        assertThat(normal).isSameInstanceAs(spec)
    }
}

@Composable
private fun Specimen() {
    val t = KioskTheme.typography
    val c = KioskTheme.colors
    val s = KioskTheme.shapes
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = KioskTheme.spacing.sideMargin, vertical = KioskTheme.spacing.headerTop),
        verticalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        listOf(
            "Display" to t.display,
            "Title" to t.title,
            "Section" to t.section,
            "Item" to t.item,
            "Body text" to t.body,
            "Button" to t.button,
            "Meta" to t.meta,
        ).forEach { (label, style) -> KioskText(text = label, style = style) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dpx)) {
            listOf(c.accent, c.ink, c.muted, c.line, c.surfaceAlt, c.success, c.successBg, c.dockTile)
                .forEach { Box(Modifier.size(96.dpx).background(it, s.small)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dpx)) {
            listOf(s.small, s.medium, s.large, s.card, s.dock, s.pill).forEach {
                Box(Modifier.size(136.dpx).background(c.surface, it).border(2.dpx, c.cardBorder, it))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dpx)
                .kioskShadow(s.dock, KioskTheme.elevation.floating)
                .background(c.ink, s.dock),
        )
    }
}
