package com.hitachi.kiosk.feature.ordertype

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.BrandLockup
import com.hitachi.kiosk.core.designsystem.component.BrandMarkTile
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Common
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.riseIn
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.ui.MerchantLogoImage
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.OrderType as Motion

/** Test tag of the Order type root. */
const val ORDER_TYPE_SCREEN_TAG = "screen:SCR-05"

/** Test tag of the single-option note. */
const val ORDER_TYPE_NOTE_TAG = "order-type-note"

// Motion of `OrderType.dc.html` that KioskMotion.OrderType does not name yet.
private const val WELCOME_RISE_MILLIS = 700
private const val WELCOME_RISE_DELAY_MILLIS = 80
private const val BRAND_RISE_MILLIS = 900
private const val BRAND_RISE_DELAY_MILLIS = 140
private const val QUESTION_UP_MILLIS = 700
private const val QUESTION_UP_DELAY_MILLIS = 260
private const val NOTE_UP_MILLIS = 700
private const val NOTE_UP_DELAY_MILLIS = 520
private const val FOOTER_FADE_MILLIS = 600
private const val FOOTER_FADE_DELAY_MILLIS = 900

/** `heroIn`: the logo tile settles from 1.05. */
private const val LOGO_FROM_SCALE = 1.05f
private const val BRAND_LINE_HEIGHT_EM = 0.98f
private const val QUESTION_LINE_HEIGHT_EM = 1.1f
private const val TIGHT_EM = -0.02f

/**
 * SCR-05 Order type (`OrderType.dc.html`, `S_OrderType_singleOption`, FR-007, DEV-025). Text and logo only: no photo,
 * no hero image and no language control (D-30, D-29). The brand header, the large logo mark, "Welcome to {merchant}",
 * the question and one full-width card per offered type; with one type the note says which is not offered.
 */
@Composable
fun OrderTypeScreen(
    state: OrderTypeUiState,
    onEvent: (OrderTypeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(ORDER_TYPE_SCREEN_TAG),
    ) {
        OrderTypeHeader(brandName = state.brandName)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dpx, end = 72.dpx, top = 280.dpx),
        ) {
            Welcome(brandName = state.brandName)
            Question(single = state.options.size == 1)
            OrderTypeCards(state = state, onPick = { onEvent(OrderTypeEvent.Pick(it)) })
            state.unavailable?.let { UnavailableNote(it) }
        }
        Footer(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun OrderTypeHeader(brandName: String) {
    val fade = animateEntrance(Common.HEADER_FADE_MILLIS, easing = KioskEasing.Ease)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 72.dpx, end = 72.dpx, top = 48.dpx)
                .height(96.dpx)
                .fadeIn(fade),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLockup(name = brandName, logo = { MerchantLogoImage() })
    }
}

/** The logo mark, "Welcome to" and the merchant name, read as one heading. */
@Composable
private fun Welcome(brandName: String) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val logo = animateEntrance(Motion.LOGO_TILE_MILLIS, Motion.LOGO_TILE_DELAY_MILLIS)
    val welcome = animateEntrance(WELCOME_RISE_MILLIS, WELCOME_RISE_DELAY_MILLIS)
    val brand = animateEntrance(BRAND_RISE_MILLIS, BRAND_RISE_DELAY_MILLIS)
    val welcomeText = stringResource(R.string.order_type_welcome)
    val heading = stringResource(R.string.order_type_welcome_description, brandName)
    Column(verticalArrangement = Arrangement.spacedBy(16.dpx)) {
        BrandMarkTile(
            modifier =
                Modifier
                    .padding(bottom = 40.dpx)
                    .size(168.dpx)
                    .graphicsLayer {
                        val p = logo.value
                        alpha = p
                        val scale = LOGO_FROM_SCALE + (1f - LOGO_FROM_SCALE) * p
                        scaleX = scale
                        scaleY = scale
                    },
            shape = KioskTheme.shapes.dock,
            dotSize = 56.dpx,
            logo = { MerchantLogoImage() },
        )
        Column(
            modifier =
                Modifier.semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = heading
                },
            verticalArrangement = Arrangement.spacedBy(16.dpx),
        ) {
            Box(Modifier.clipToBounds()) {
                KioskText(
                    text = welcomeText,
                    style = typography.meta,
                    color = colors.muted,
                    modifier = Modifier.riseIn(welcome),
                )
            }
            Box(
                Modifier
                    .clipToBounds()
                    .padding(bottom = 8.dpx),
            ) {
                KioskText(
                    text = brandName,
                    style = typography.display.copy(fontSize = 96.spx, lineHeight = BRAND_LINE_HEIGHT_EM.em),
                    modifier = Modifier.riseIn(brand),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    cssLineBox = true,
                )
            }
        }
    }
}

@Composable
private fun Question(single: Boolean) {
    val up = animateEntrance(QUESTION_UP_MILLIS, QUESTION_UP_DELAY_MILLIS)
    KioskText(
        text = stringResource(if (single) R.string.order_type_question_single else R.string.order_type_question),
        style =
            KioskTheme.typography.title.copy(
                fontSize = 52.spx,
                letterSpacing = TIGHT_EM.em,
                lineHeight = QUESTION_LINE_HEIGHT_EM.em,
            ),
        modifier =
            Modifier
                .padding(top = 96.dpx, bottom = 40.dpx)
                .fadeUp(up, Common.UP_DISTANCE_PX.dpx)
                .semantics { heading() },
    )
}

@Composable
private fun OrderTypeCards(
    state: OrderTypeUiState,
    onPick: (OrderType) -> Unit,
) {
    val group = stringResource(R.string.order_type_group)
    Column(
        modifier =
            Modifier
                .selectableGroup()
                .semantics { contentDescription = group },
        verticalArrangement = Arrangement.spacedBy(32.dpx),
    ) {
        state.options.forEachIndexed { i, orderType ->
            val delay = if (i == 0) Motion.CARDS_IN_DELAY_MILLIS else Motion.CARDS_IN_SECOND_DELAY_MILLIS
            OrderTypeCard(
                orderType = orderType,
                selected = state.picked == orderType,
                dimmed = state.picked != null && state.picked != orderType,
                entrance = animateEntrance(Motion.CARDS_IN_MILLIS, delay),
                onClick = { onPick(orderType) },
            )
        }
    }
}

/** "Takeaway isn't offered at this restaurant right now." (or the Dine in one) under a single card (A-05). */
@Composable
private fun UnavailableNote(unavailable: OrderType) {
    val colors = KioskTheme.colors
    val up = animateEntrance(NOTE_UP_MILLIS, NOTE_UP_DELAY_MILLIS)
    Row(
        modifier =
            Modifier
                .padding(top = 32.dpx)
                .fadeUp(up, Common.UP_DISTANCE_PX.dpx)
                .testTag(ORDER_TYPE_NOTE_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dpx),
    ) {
        KioskIcon(icon = KioskIcons.Info, tint = colors.muted, modifier = Modifier.size(36.dpx))
        KioskText(
            text =
                stringResource(
                    when (unavailable) {
                        OrderType.DINE_IN -> R.string.order_type_dine_in_unavailable
                        OrderType.TAKEAWAY -> R.string.order_type_takeaway_unavailable
                    },
                ),
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W500),
            color = colors.muted,
        )
    }
}

@Composable
private fun Footer(modifier: Modifier = Modifier) {
    val fade = animateEntrance(FOOTER_FADE_MILLIS, FOOTER_FADE_DELAY_MILLIS, KioskEasing.Ease)
    KioskText(
        text = stringResource(R.string.order_type_footer),
        style = KioskTheme.typography.meta.copy(fontSize = 24.spx),
        color = KioskTheme.colors.muted,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 56.dpx)
                .fadeIn(fade),
    )
}

@Composable
private fun OrderTypePreview(state: OrderTypeUiState) {
    KioskTheme(reduceMotion = true) {
        DesignFrame { OrderTypeScreen(state = state, onEvent = {}) }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun OrderTypeDefaultPreview() = OrderTypePreview(OrderTypeSamples.Default)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun OrderTypeSingleOptionPreview() = OrderTypePreview(OrderTypeSamples.SingleOption)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun OrderTypeSelectedPreview() = OrderTypePreview(OrderTypeSamples.Selected)
