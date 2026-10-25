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

    /** The trash can of a Your order "−" at quantity 1 (`Payment.dc.html`), stroke 1.8. */
    val Trash: ImageVector =
        lineIcon(
            "Trash",
            "M5 7h14M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3",
            strokeWidth = 1.8f,
        )

    /** The QRIS payment method tile, stroke 1.8. */
    val QrCode: ImageVector =
        lineIcon(
            "QrCode",
            "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2v2h-2z" +
                "M18 14h2v2h-2zM14 18h2v2h-2zM18 18h2v2h-2zM16 16h2v2h-2z",
            strokeWidth = 1.8f,
        )

    /** The Card payment method tile, stroke 1.8. */
    val Card: ImageVector =
        lineIcon(
            "Card",
            "M3 6.5A1.5 1.5 0 0 1 4.5 5h15A1.5 1.5 0 0 1 21 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5z" +
                "M3 9.5h18M6.5 15h4",
            strokeWidth = 1.8f,
        )

    /** A struck-through card: "No payment methods right now" (`S_Payment_noMethods`), stroke 1.8. */
    val CardOff: ImageVector =
        lineIcon(
            "CardOff",
            "M3 6.5A1.5 1.5 0 0 1 4.5 5h15A1.5 1.5 0 0 1 21 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5z" +
                "M3 9.5h18M4 4l16 16",
            strokeWidth = 1.8f,
        )

    /** The warning triangle of the price-changed and unavailable banners (`S_Payment_*`), stroke 2. */
    val Warning: ImageVector =
        lineIcon(
            "Warning",
            "M12 8v5M12 16.5v.5M10.3 4.6L3.5 17a2 2 0 0 0 1.7 3h13.6a2 2 0 0 0 1.7-3L13.7 4.6a2 2 0 0 0-3.4 0z",
            strokeWidth = 2f,
        )

    /** The price tag of the promo nudge banner (`S_Payment_promoNudge`), stroke 2. */
    val Tag: ImageVector = lineIcon("Tag", "M3 12V4h8l10 10-8 8L3 12zM7.5 8.5h.01", strokeWidth = 2f)

    /** The lighter paper bag of the "Your tray is empty" state (`S_Payment_emptyTray`), stroke 1.6. */
    val BagLight: ImageVector =
        lineIcon(
            "BagLight",
            "M5 8h14l-1.2 12.2a1 1 0 0 1-1 .8H7.2a1 1 0 0 1-1-.8L5 8zM9 8V6a3 3 0 0 1 6 0v2",
            strokeWidth = 1.6f,
        )

    /** "ⓘ" of notes (Order type single option): a circle at stroke 2 with an "i" at stroke 2.2. */
    val Info: ImageVector =
        lineIcon(
            "Info",
            "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z" to 2f,
            "M12 11v5M12 8h.01" to 2.2f,
        )

    /** The "!" in a circle of the Budget Plan "too many items" message (`S_BudgetPas_cartFull`). */
    val AlertCircle: ImageVector =
        lineIcon(
            "AlertCircle",
            "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z" to 2f,
            "M12 7.5v5.5M12 16.2h.01" to 2.4f,
        )

    /** "✕" of sheet close buttons (`BudgetPas.dc.html` Customize), stroke 2.2. */
    val Close: ImageVector = lineIcon("Close", "M6 6l12 12M18 6L6 18", strokeWidth = 2.2f)

    /** The sliders of the Budget Plan "Customize" row, stroke 2. */
    val Sliders: ImageVector = lineIcon("Sliders", "M4 7h9M17 7h3M4 17h3M11 17h9M13 4v6M7 14v6", strokeWidth = 2f)

    /** The two opposed arrows of the Budget Plan "Swap" pill, stroke 2. */
    val Swap: ImageVector = lineIcon("Swap", "M4 8h13l-3.5-3.5M20 16H7l3.5 3.5", strokeWidth = 2f)

    /** The counter-clockwise arrow of "Reset to suggested" (`BudgetPas.dc.html`), stroke 2.2. */
    val Reset: ImageVector = lineIcon("Reset", "M4 12a8 8 0 1 0 3-6.2M4 4v5h5", strokeWidth = 2.2f)

    /** The backspace key of the budget keypad, stroke 1.8. */
    val Backspace: ImageVector = lineIcon("Backspace", "M9 5h11v14H9l-6-7zM12 9l5 6M17 9l-5 6", strokeWidth = 1.8f)

    /** The padlock of "Secure payment" (`Checkout.dc.html`), stroke 2. */
    val Lock: ImageVector = lineIcon("Lock", "M6 11V8a6 6 0 0 1 12 0v3M5 11h14v10H5z", strokeWidth = 2f)

    /** The "✕" of the failure badge (`Checkout.dc.html`), stroke 2.4. */
    val Cross: ImageVector = lineIcon("Cross", "M7 7l10 10M17 7L7 17", strokeWidth = 2.4f)

    /** The circular arrow of "Try again" (`Checkout.dc.html`), stroke 2.2. */
    val Retry: ImageVector = lineIcon("Retry", "M20 12a8 8 0 1 1-2.4-5.7M20 4v4h-4", strokeWidth = 2.2f)

    /** The bobbing arrow under the card terminal (`Checkout.dc.html`), stroke 2. */
    val ArrowDown: ImageVector = lineIcon("ArrowDown", "M12 4v15M5 13l7 7 7-7", strokeWidth = 2f)

    /** The open ring of the offline failure badge, which turns (`S_Checkout_offline`), stroke 2.2. */
    val SpinnerArc: ImageVector = lineIcon("SpinnerArc", "M12 3a9 9 0 1 0 9 9", strokeWidth = 2.2f)
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
