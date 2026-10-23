package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme

/**
 * The text primitive for feature code (PRD §11.3, architecture §12): draws [text] in a `KioskTheme.typography`
 * [style] and [color], without going through Material 3.
 *
 * @param textAlign alignment inside the text's width; `null` keeps the style's.
 * @param maxLines lines before the text is cut with [overflow].
 * @param cssLineBox for one-line headlines whose line height is below the font's own height (display .98, title 1):
 *   lays the line out [style]'s line height tall, centred, as a CSS line box does. Without it the line keeps the
 *   font's ascent and descent, which makes tight headlines taller than in the design.
 */
@Composable
fun KioskText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = KioskTheme.colors.ink,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    cssLineBox: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = if (cssLineBox) modifier.cssLineBox(style) else modifier,
        style = style.merge(color = color, textAlign = textAlign ?: style.textAlign),
        overflow = overflow,
        softWrap = true,
        maxLines = maxLines,
    )
}

/** Lays out one line [style]'s line height tall, centred vertically (see `KioskText(cssLineBox = true)`). */
private fun Modifier.cssLineBox(style: TextStyle): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val lineHeight = style.lineHeight
        val target =
            if (lineHeight.isEm) {
                (style.fontSize.toPx() * lineHeight.value).toInt()
            } else {
                lineHeight.roundToPx()
            }
        val height = target.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
    }
