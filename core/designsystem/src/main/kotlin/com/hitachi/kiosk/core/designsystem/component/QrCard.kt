package com.hitachi.kiosk.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

private const val CARD_WIDTH_PX = 720
private const val CARD_PAD_V_PX = 36
private const val CARD_PAD_H_PX = 48
private const val CARD_GAP_PX = 24
private const val CARD_BORDER_PX = 2
private const val TITLE_SIZE_PX = 36
private const val EXPIRY_SIZE_PX = 28
private const val FOOTER_GAP_PX = 14
private const val ENTER_RISE_PX = 40
private const val ENTER_SCALE_FROM = 0.96f
private const val SHADOW_ALPHA = 0.06f

// The design's logo mark: a 104 box with a 28 radius, an 80 ink tile with a 22 radius and a 26 accent dot.
private const val MARK_TILE_FRACTION = 80f / 104f
private const val MARK_DOT_FRACTION = 26f / 80f
private const val MARK_RADIUS_PERCENT = 27

/**
 * The QRIS card of Checkout (`Checkout.dc.html`, FR-017): a white 720 dpx card with a header row ([title] left,
 * [caption] right), the QR in the middle and the expiry ring with [expiryText] under it. There is no scan line, no
 * instruction list and no step indicator (D-30). The card rises in with the design's `qrIn` entrance
 * (900 ms after 220 ms; instant with reduce motion).
 *
 * The QR itself comes in through [qr] so this module stays free of the QR encoder; the app passes its `QrCode`.
 *
 * @param title the method name, e.g. "QRIS".
 * @param caption merchant and kiosk, e.g. "Urban Grill · Kiosk 3".
 * @param expiryText the already formatted line next to the ring, e.g. "Code expires in 5:00". Build the time with
 *   [formatCountdown].
 * @param remainingSeconds seconds left on the code, from the server expiry (NFR-017).
 * @param totalSeconds the code's full lifetime; the ring is `remainingSeconds / totalSeconds` full.
 * @param qr the QR, 480 dpx square.
 */
@Composable
fun QrCard(
    title: String,
    caption: String,
    expiryText: String,
    remainingSeconds: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
    qr: @Composable () -> Unit,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.dock
    val enter = animateEntrance(KioskMotion.Checkout.QR_IN_MILLIS, KioskMotion.Checkout.QR_IN_DELAY_MILLIS)
    val shadow = KioskTheme.elevation.floating.let { it.copy(color = colors.ink.copy(alpha = SHADOW_ALPHA)) }
    val rise = ENTER_RISE_PX.dpx
    Column(
        modifier =
            modifier
                .width(CARD_WIDTH_PX.dpx)
                .graphicsLayer {
                    val p = enter.value
                    alpha = p
                    translationY = rise.toPx() * (1f - p)
                    val scale = ENTER_SCALE_FROM + (1f - ENTER_SCALE_FROM) * p
                    scaleX = scale
                    scaleY = scale
                }.kioskShadow(shape, shadow)
                .background(colors.surface, shape)
                .border(CARD_BORDER_PX.dpx, colors.cardBorder, shape)
                .padding(horizontal = CARD_PAD_H_PX.dpx, vertical = CARD_PAD_V_PX.dpx),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CARD_GAP_PX.dpx),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KioskText(
                text = title,
                style = KioskTheme.typography.item.copy(fontSize = TITLE_SIZE_PX.spx, fontWeight = FontWeight.W800),
            )
            KioskText(text = caption, style = KioskTheme.typography.meta, color = colors.muted)
        }
        qr()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FOOTER_GAP_PX.dpx),
        ) {
            ExpiryRing(remainingSeconds = remainingSeconds, totalSeconds = totalSeconds)
            KioskText(
                text = expiryText,
                style = KioskTheme.typography.body.copy(fontSize = EXPIRY_SIZE_PX.spx, fontWeight = FontWeight.W600),
            )
        }
    }
}

/**
 * The merchant mark that sits in the middle of the QR (`Checkout.dc.html`): a white rounded chip, an ink tile and
 * an [accent] dot. It fills whatever space it is given (the QR gives it at most 20 % of its width, TD-07) and
 * scales its radii and inner tile with it. Pass your own content to the QR's centre slot instead for a logo image.
 */
@Composable
fun QrCenterMark(
    modifier: Modifier = Modifier,
    accent: Color = KioskTheme.colors.accent,
) {
    val colors = KioskTheme.colors
    val chip = RoundedCornerShape(percent = MARK_RADIUS_PERCENT)
    Box(
        modifier = modifier.clip(chip).background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(MARK_TILE_FRACTION).clip(chip).background(colors.ink),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize(MARK_DOT_FRACTION).clip(CircleShape).background(accent))
        }
    }
}

@Preview(widthDp = 800, heightDp = 900)
@Composable
private fun QrCardPreview() {
    KioskTheme(reduceMotion = true) {
        QrCard(
            title = "QRIS",
            caption = "Urban Grill · Kiosk 3",
            // The sentence is a string resource of the app (`checkout_qr_expires_in`); the preview shows the time only.
            expiryText = formatCountdown(300),
            remainingSeconds = 300,
            totalSeconds = 300,
        ) {
            Box(Modifier.size(480.dp).background(KioskTheme.colors.surfaceAlt), contentAlignment = Alignment.Center) {
                QrCenterMark(Modifier.size(96.dp))
            }
        }
    }
}
