package com.hitachi.kiosk.core.designsystem.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** DEV-004: the tokens match PRD §11.1. */
class KioskTokensTest {
    private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

    @Test
    fun `every color token has the PRD hex value`() {
        val c = KioskColors()

        val actual =
            mapOf(
                "bg" to c.bg,
                "ink" to c.ink,
                "muted" to c.muted,
                "line" to c.line,
                "surfaceAlt" to c.surfaceAlt,
                "cardBorder" to c.cardBorder,
                "radioOff" to c.radioOff,
                "success" to c.success,
                "successBg" to c.successBg,
                "successBgAlt" to c.successBgAlt,
                "accent" to c.accent,
                "dockMuted" to c.dockMuted,
                "dockMutedAlt" to c.dockMutedAlt,
                "dockTile" to c.dockTile,
                "dockTileBorder" to c.dockTileBorder,
                "dockDisabled" to c.dockDisabled,
                "dockDisabledText" to c.dockDisabledText,
                "surface" to c.surface,
                "errorBg" to c.errorBg,
                "errorBorder" to c.errorBorder,
                "errorText" to c.errorText,
                "closedDot" to c.closedDot,
                "skeletonHighlight" to c.skeletonHighlight,
                "photoTone" to c.photoTone,
                "warningBg" to c.warningBg,
                "warningBorder" to c.warningBorder,
                "warningTrack" to c.warningTrack,
                "warningAccent" to c.warningAccent,
                "warningText" to c.warningText,
                "alert" to c.alert,
                "dockControl" to c.dockControl,
                "dockTrack" to c.dockTrack,
                "dockSubtle" to c.dockSubtle,
            ).mapValues { it.value.hex() }

        assertThat(actual).containsExactlyEntriesIn(
            mapOf(
                "bg" to "#FAF8F5",
                "ink" to "#171513",
                "muted" to "#6F6961",
                "line" to "#E9E4DC",
                "surfaceAlt" to "#F1EDE7",
                "cardBorder" to "#EFE9E0",
                "radioOff" to "#D9D2C7",
                "success" to "#2F7A50",
                "successBg" to "#EAF3EC",
                "successBgAlt" to "#E6F0E9",
                "accent" to "#C8552B",
                "dockMuted" to "#B9B2A8",
                "dockMutedAlt" to "#B5AEA4",
                "dockTile" to "#211E1B",
                "dockTileBorder" to "#2E2B27",
                "dockDisabled" to "#26231F",
                "dockDisabledText" to "#8F887E",
                "surface" to "#FFFFFF",
                "errorBg" to "#FBEEE8",
                "errorBorder" to "#F0CDBF",
                "errorText" to "#4F4A44",
                "closedDot" to "#A39C92",
                "skeletonHighlight" to "#F6F3EE",
                "photoTone" to "#E9DED0",
                "warningBg" to "#FFF4DB",
                "warningBorder" to "#EBD39A",
                "warningTrack" to "#E2C877",
                "warningAccent" to "#8A6A12",
                "warningText" to "#4A3A08",
                "alert" to "#D6402B",
                "dockControl" to "#3A3632",
                "dockTrack" to "#2A2724",
                "dockSubtle" to "#CFC8BE",
            ),
        )
    }

    @Test
    fun `every color token is opaque except the shadows at 10 and 18 percent ink`() {
        val c = KioskColors()
        val opaque =
            listOf(
                c.bg,
                c.ink,
                c.muted,
                c.line,
                c.surfaceAlt,
                c.cardBorder,
                c.radioOff,
                c.success,
                c.successBg,
                c.successBgAlt,
                c.accent,
                c.dockMuted,
                c.dockMutedAlt,
                c.dockTile,
                c.dockTileBorder,
                c.dockDisabled,
                c.dockDisabledText,
                c.surface,
                c.errorBg,
                c.errorBorder,
                c.errorText,
                c.closedDot,
                c.skeletonHighlight,
                c.photoTone,
                c.warningBg,
                c.warningBorder,
                c.warningTrack,
                c.warningAccent,
                c.warningText,
                c.alert,
                c.dockControl,
                c.dockTrack,
                c.dockSubtle,
                KioskPalette.PhotoToneFallback,
            )

        assertThat(opaque.map { it.alpha }.toSet()).containsExactly(1f)
        assertThat(c.floatingShadow.hex()).isEqualTo("#171513")
        assertThat(c.floatingShadow.alpha).isWithin(0.002f).of(0.10f)
        assertThat(c.raisedShadow.hex()).isEqualTo("#171513")
        assertThat(c.raisedShadow.alpha).isWithin(0.002f).of(0.18f)
    }

    @Test
    fun `the photo slot fallback tone is E6DCD3`() {
        assertThat(KioskPalette.PhotoToneFallback.hex()).isEqualTo("#E6DCD3")
    }

    @Test
    fun `the accent options are paprika forest indigo and ink with paprika as default`() {
        assertThat(KioskPalette.AccentOptions.map { it.hex() })
            .containsExactly("#C8552B", "#2F5D4A", "#3A4C7A", "#171513")
            .inOrder()
        assertThat(KioskColors().accent).isEqualTo(KioskPalette.AccentPaprika)
    }

    @Test
    fun `the type scale matches the PRD sizes and weights at scale 1`() {
        val t = kioskTypography(scale = 1f, largeText = false)

        fun TextStyle.spec() = fontSize.value to fontWeight
        assertThat(t.display.spec()).isEqualTo(112f to FontWeight.W800)
        assertThat(t.title.spec()).isEqualTo(80f to FontWeight.W700)
        assertThat(t.section.spec()).isEqualTo(44f to FontWeight.W700)
        assertThat(t.item.spec()).isEqualTo(36f to FontWeight.W600)
        assertThat(t.body.spec()).isEqualTo(30f to FontWeight.W400)
        assertThat(t.button.spec()).isEqualTo(34f to FontWeight.W700)
        assertThat(t.meta.spec()).isEqualTo(26f to FontWeight.W500)
        listOf(t.display, t.title, t.section, t.item, t.body, t.button, t.meta).forEach {
            assertThat(it.fontSize).isEqualTo(it.fontSize.value.sp)
            assertThat(it.fontFamily).isEqualTo(SchibstedGrotesk)
            assertThat(it.fontSize.value).isAtLeast(KioskTypeScale.MIN_SIZE_PX.toFloat())
        }
    }

    @Test
    fun `headlines are tight between minus 0_02 and minus 0_035 em`() {
        val t = kioskTypography()

        assertThat(t.display.letterSpacing).isEqualTo((-0.035f).em)
        assertThat(t.title.letterSpacing).isEqualTo((-0.03f).em)
        assertThat(t.section.letterSpacing).isEqualTo((-0.02f).em)
    }

    @Test
    fun `type follows the design scale and the larger text multiplier`() {
        val half = kioskTypography(scale = 0.5f, largeText = false)
        val large = kioskTypography(scale = 1f, largeText = true)

        assertThat(half.display.fontSize.value).isEqualTo(56f)
        assertThat(large.display.fontSize.value).isWithin(0.001f).of(128.8f)
        assertThat(large.meta.fontSize.value).isWithin(0.001f).of(29.9f)
    }

    @Test
    fun `radii are 999 pill 48 dock 40 card and 32 28 24`() {
        val s = kioskShapes(scale = 1f)
        val density = Density(1f)
        val size = Size(2000f, 2000f)

        fun radius(shape: RoundedCornerShape): Float = shape.topStart.toPx(size, density)

        assertThat(s.pill).isEqualTo(RoundedCornerShape(CornerSize(50)))
        assertThat(radius(s.dock)).isEqualTo(48f)
        assertThat(radius(s.card)).isEqualTo(40f)
        assertThat(radius(s.large)).isEqualTo(32f)
        assertThat(radius(s.medium)).isEqualTo(28f)
        assertThat(radius(s.small)).isEqualTo(24f)
        assertThat(KioskRadius.PILL_PX).isEqualTo(999)
    }

    @Test
    fun `layout tokens are 72 margin 936 content 48 header top 96 header 8 grid 96 touch`() {
        val sp = kioskSpacing(scale = 1f)

        assertThat(listOf(sp.sideMargin, sp.contentWidth, sp.headerTop, sp.headerHeight, sp.grid, sp.minTouchTarget))
            .containsExactly(72.dp, 936.dp, 48.dp, 96.dp, 8.dp, 96.dp)
            .inOrder()
        assertThat(KioskLayout.CONTENT_WIDTH_PX + 2 * KioskLayout.SIDE_MARGIN_PX).isEqualTo(1080)
    }

    @Test
    fun `spacing and shapes scale with the design scale`() {
        val sp = kioskSpacing(scale = 0.5f)

        assertThat(sp.sideMargin).isEqualTo(36.dp)
        assertThat(sp.minTouchTarget).isEqualTo(48.dp)
    }

    @Test
    fun `the floating elevation is 0 24 48 at 10 percent ink`() {
        val floating = kioskElevation(scale = 1f).floating

        assertThat(floating.offsetY).isEqualTo(24.dp)
        assertThat(floating.blurRadius).isEqualTo(48.dp)
        assertThat(floating.color).isEqualTo(KioskColors().floatingShadow)
        assertThat(kioskElevation(scale = 0.5f).floating.blurRadius).isEqualTo(Dp(24f))
    }

    @Test
    fun `the material scheme mirrors the kiosk palette`() {
        val colors = KioskColors(accent = KioskPalette.AccentForest)
        val scheme = colors.toMaterialColorScheme()

        assertThat(scheme.primary).isEqualTo(KioskPalette.AccentForest)
        assertThat(scheme.background).isEqualTo(colors.bg)
        assertThat(scheme.surface).isEqualTo(colors.surface)
        assertThat(scheme.onSurface).isEqualTo(colors.ink)
        assertThat(scheme.outline).isEqualTo(colors.line)
    }
}
