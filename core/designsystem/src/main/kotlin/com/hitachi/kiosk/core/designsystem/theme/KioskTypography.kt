package com.hitachi.kiosk.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.core.designsystem.R
import com.hitachi.kiosk.core.designsystem.unit.designPxToSp

/**
 * Schibsted Grotesk 400–800, bundled under `res/font` (SIL OFL, `licenses/SchibstedGrotesk-OFL.txt`)
 * (PRD §11.1, DEV-004).
 */
val SchibstedGrotesk: FontFamily =
    FontFamily(
        Font(R.font.schibsted_grotesk_regular, FontWeight.W400),
        Font(R.font.schibsted_grotesk_medium, FontWeight.W500),
        Font(R.font.schibsted_grotesk_semibold, FontWeight.W600),
        Font(R.font.schibsted_grotesk_bold, FontWeight.W700),
        Font(R.font.schibsted_grotesk_extrabold, FontWeight.W800),
    )

/**
 * One step of the type scale in design px (PRD §11.1). Letter spacing and line height are relative to the
 * font size (`em`), as in the design (headlines −0.02 to −0.035 em).
 */
@Immutable
data class KioskTypeSpec(
    /** Font size in design px. */
    val sizePx: Int,
    /** Font weight. */
    val weight: FontWeight,
    /** Letter spacing in em. */
    val letterSpacingEm: Float,
    /** Line height as a multiple of the font size. */
    val lineHeightEm: Float,
)

/** The type scale of PRD §11.1 in design px (DEV-004). Nothing is below 24 px. */
object KioskTypeScale {
    /** display 112/800. */
    val Display = KioskTypeSpec(sizePx = 112, weight = FontWeight.W800, letterSpacingEm = -0.035f, lineHeightEm = 0.98f)

    /** title 80/700. */
    val Title = KioskTypeSpec(sizePx = 80, weight = FontWeight.W700, letterSpacingEm = -0.03f, lineHeightEm = 1.02f)

    /** section 44/700 (56 px line height in the design). */
    val Section =
        KioskTypeSpec(
            sizePx = 44,
            weight = FontWeight.W700,
            letterSpacingEm = -0.02f,
            lineHeightEm =
                56f / 44f,
        )

    /** item 36/600. */
    val Item = KioskTypeSpec(sizePx = 36, weight = FontWeight.W600, letterSpacingEm = -0.01f, lineHeightEm = 1.15f)

    /** body 30/400. */
    val Body = KioskTypeSpec(sizePx = 30, weight = FontWeight.W400, letterSpacingEm = 0f, lineHeightEm = 1.4f)

    /** button 34/700. */
    val Button = KioskTypeSpec(sizePx = 34, weight = FontWeight.W700, letterSpacingEm = -0.01f, lineHeightEm = 1.15f)

    /** meta 26/500. */
    val Meta = KioskTypeSpec(sizePx = 26, weight = FontWeight.W500, letterSpacingEm = 0f, lineHeightEm = 1.3f)

    /** Smallest text size allowed anywhere in the UI, in design px (PRD §11.1). */
    const val MIN_SIZE_PX: Int = 24
}

/**
 * The text styles of PRD §11.1, already converted with the design scale and the Larger text multiplier
 * (PRD §11.2, DEV-004). Read them through `KioskTheme.typography`.
 */
@Immutable
data class KioskTypography(
    val display: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val item: TextStyle,
    val body: TextStyle,
    val button: TextStyle,
    val meta: TextStyle,
)

/**
 * Builds [KioskTypography] for a design [scale] and the Larger text setting [largeText] (PRD §11.1–11.2,
 * TR-DSP-04, DEV-004).
 */
fun kioskTypography(
    scale: Float = 1f,
    largeText: Boolean = false,
): KioskTypography {
    fun style(spec: KioskTypeSpec) =
        TextStyle(
            fontFamily = SchibstedGrotesk,
            fontWeight = spec.weight,
            fontSize = designPxToSp(spec.sizePx, scale, largeText),
            letterSpacing = spec.letterSpacingEm.em,
            lineHeight = spec.lineHeightEm.em,
            lineHeightStyle =
                LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
        )
    return KioskTypography(
        display = style(KioskTypeScale.Display),
        title = style(KioskTypeScale.Title),
        section = style(KioskTypeScale.Section),
        item = style(KioskTypeScale.Item),
        body = style(KioskTypeScale.Body),
        button = style(KioskTypeScale.Button),
        meta = style(KioskTypeScale.Meta),
    )
}

/** The [KioskTypography] of the current [KioskTheme] (DEV-004). */
val LocalKioskTypography = staticCompositionLocalOf { kioskTypography() }
