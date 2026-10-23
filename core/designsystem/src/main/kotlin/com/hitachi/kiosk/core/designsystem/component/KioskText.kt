package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme

/**
 * The text primitive for feature code (PRD §11.3, architecture §12): draws [text] in a `KioskTheme.typography`
 * [style] and [color], without going through Material 3.
 */
@Composable
fun KioskText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = KioskTheme.colors.ink,
) {
    BasicText(text = text, modifier = modifier, style = style.merge(color = color))
}
