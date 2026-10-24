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

    /** "→" on dark docks and the Plan by budget row (Menu "View order"), stroke 2.2. */
    val ArrowRightBold: ImageVector = lineIcon("ArrowRightBold", "M5 12h14M13 6l6 6-6 6", strokeWidth = 2.2f)

    /** The "!" of the "Maximum 20 per item" toast, stroke 2.6. */
    val AlertBold: ImageVector = lineIcon("AlertBold", "M12 7v6M12 17h.01", strokeWidth = 2.6f)

    /** The pointing hand of the Idle "Tap to order" button, stroke 1.8. */
    val Tap: ImageVector =
        lineIcon(
            "Tap",
            "M9 11V5.5a1.5 1.5 0 0 1 3 0V11m0-1.5a1.5 1.5 0 0 1 3 0V12m0-1a1.5 1.5 0 0 1 3 0v4.5a5.5 5.5 0 0 1-5.5 " +
                "5.5h-.8a5 5 0 0 1-3.9-1.9L5 15.4a1.5 1.5 0 0 1 2.3-1.9L9 15",
            strokeWidth = 1.8f,
        )

    /** A check mark (Order type, quick add, Added states), stroke 2.6. */
    val Check: ImageVector = lineIcon("Check", "M5 12.5l4.5 4.5L19 7.5", strokeWidth = 2.6f)

    /** The heavier check of selected option cards and the Added dock, stroke 3. */
    val CheckBold: ImageVector = lineIcon("CheckBold", "M5 12.5l4.5 4.5L19 7.5", strokeWidth = 3f)

    /** Fork and knife, the Dine in card, stroke 2. */
    val DineIn: ImageVector =
        lineIcon("DineIn", "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 21V3c-2 0-3.5 2.5-3.5 6v4H17", strokeWidth = 2f)

    /** A paper bag: the Takeaway card and the "My order" pill, stroke 2. */
    val Bag: ImageVector =
        lineIcon(
            "Bag",
            "M5 8h14l-1.2 12.2a1 1 0 0 1-1 .8H7.2a1 1 0 0 1-1-.8L5 8zM9 8V6a3 3 0 0 1 6 0v2",
            strokeWidth = 2f,
        )

    /** "+" of quick add and steppers, stroke 2.2. */
    val Plus: ImageVector = lineIcon("Plus", "M12 5v14M5 12h14", strokeWidth = 2.2f)

    /** "−" of steppers, stroke 2.2. */
    val Minus: ImageVector = lineIcon("Minus", "M5 12h14", strokeWidth = 2.2f)

    /** The covered dish of the Menu empty category, stroke 1.6. */
    val Cloche: ImageVector =
        lineIcon("Cloche", "M4 12h16M6 12c0-4 2.7-7 6-7s6 3 6 7M5 12l1 6h12l1-6", strokeWidth = 1.6f)

    /** "↗" of the Meals pill, stroke 2. */
    val ArrowUpRight: ImageVector = lineIcon("ArrowUpRight", "M7 17L17 7M9 7h8v8", strokeWidth = 2f)

    /** Two people, the "Plan by budget" row, stroke 1.8. */
    val People: ImageVector =
        lineIcon(
            "People",
            "M8.5 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM2.5 19.5c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5M16.5 11a2.5 2.5 0 1 0 0-5" +
                "M17.5 14.2c2.3.4 4 2.3 4 5.3",
            strokeWidth = 1.8f,
        )

    /** "›" of the "Choose" pill, stroke 2.4. */
    val ChevronRight: ImageVector = lineIcon("ChevronRight", "M9 6l6 6-6 6", strokeWidth = 2.4f)

    /** "‹" of the "Menu" back pill, stroke 2. */
    val ChevronLeft: ImageVector = lineIcon("ChevronLeft", "M15 5l-7 7 7 7", strokeWidth = 2f)

    /** "⌄" of collapsible rows and "More sides", stroke 2.2. */
    val ChevronDown: ImageVector = lineIcon("ChevronDown", "M6 9l6 6 6-6", strokeWidth = 2.2f)

    /** "ⓘ" of notes (Order type single option): a circle at stroke 2 with an "i" at stroke 2.2. */
    val Info: ImageVector =
        lineIcon(
            "Info",
            "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z" to 2f,
            "M12 11v5M12 8h.01" to 2.2f,
        )
}

private fun lineIcon(
    name: String,
    pathData: String,
    strokeWidth: Float,
): ImageVector = lineIcon(name, pathData to strokeWidth)

/** An icon of several stroked paths, each with its own stroke width. */
private fun lineIcon(
    name: String,
    vararg paths: Pair<String, Float>,
): ImageVector =
    paths
        .fold(
            ImageVector.Builder(
                name = name,
                defaultWidth = VIEWPORT.dp,
                defaultHeight = VIEWPORT.dp,
                viewportWidth = VIEWPORT,
                viewportHeight = VIEWPORT,
            ),
        ) { builder, (pathData, strokeWidth) ->
            builder.addPath(
                pathData = addPathNodes(pathData),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()

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
