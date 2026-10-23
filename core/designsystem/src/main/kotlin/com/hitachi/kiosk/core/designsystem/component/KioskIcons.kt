package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/** The 24 × 24 viewBox every design icon is drawn in. */
private const val VIEWPORT = 24f

/**
 * The line icons of the design, from their SVG paths in the `.dc.html` files (24 × 24 viewBox, round caps). They are
 * drawn in one color and tinted by [KioskIcon].
 */
object KioskIcons {
    /** "→" in the Log in button and merchant cards: `M5 12h14M13 6l6 6-6 6`, stroke 2. */
    val ArrowRight: ImageVector = lineIcon("ArrowRight", "M5 12h14M13 6l6 6-6 6", strokeWidth = 2f)

    /** "!" in error and failure panels: `M12 7v6M12 17h.01`, stroke 2.4. */
    val Alert: ImageVector = lineIcon("Alert", "M12 7v6M12 17h.01", strokeWidth = 2.4f)

    /** The header "Log out" icon of Merchants, stroke 2. */
    val LogOut: ImageVector =
        lineIcon("LogOut", "M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l-5-5 5-5M5 12h11", strokeWidth = 2f)

    /** The shop front of the Merchants empty state, stroke 1.6. */
    val Store: ImageVector =
        lineIcon("Store", "M4 10l1.5-5h13L20 10M4 10v9h16v-9M4 10h16M9 19v-5h6v5", strokeWidth = 1.6f)
}

private fun lineIcon(
    name: String,
    pathData: String,
    strokeWidth: Float,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = VIEWPORT.dp,
            defaultHeight = VIEWPORT.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).addPath(
            pathData = addPathNodes(pathData),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

/**
 * Draws one of [KioskIcons] in [tint]. Icons are decorative (`aria-hidden` in the design): the control around them
 * carries the description (NFR-007). Size it with [modifier].
 */
@Composable
fun KioskIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(imageVector = icon, contentDescription = null, modifier = modifier, colorFilter = ColorFilter.tint(tint))
}

@Preview(widthDp = 400, heightDp = 100)
@Composable
private fun KioskIconsPreview() {
    KioskTheme {
        Row {
            listOf(KioskIcons.ArrowRight, KioskIcons.Alert, KioskIcons.LogOut, KioskIcons.Store).forEach {
                KioskIcon(icon = it, tint = KioskTheme.colors.ink, modifier = Modifier.size(80.dpx))
            }
        }
    }
}
