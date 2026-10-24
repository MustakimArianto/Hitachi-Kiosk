package com.hitachi.kiosk.feature.order

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.BackPill
import com.hitachi.kiosk.core.designsystem.component.HeaderPillStyle
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.KioskToast
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.motion.riseIn
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.OrderType

/** Test tag of the Your order root; the edge-walk test finds screens by it. */
const val YOUR_ORDER_SCREEN_TAG = "screen:SCR-10"

/** Test tag of the header "Add more" pill. */
const val YOUR_ORDER_ADD_MORE_TAG = "your-order-add-more"

/** Test tag of "N items" next to the title. */
const val YOUR_ORDER_COUNT_TAG = "your-order-count"

/** Test tag of "Back to menu" in the empty tray. */
const val YOUR_ORDER_BACK_TO_MENU_TAG = "your-order-back-to-menu"

/** Test tag of the banner's button ("Review" or "Remove it"). */
const val YOUR_ORDER_BANNER_ACTION_TAG = "your-order-banner-action"

/** Test tag of the Budget Plan tag row. */
const val YOUR_ORDER_PLAN_TAG = "your-order-plan"

/** Top of the step label and title, and of the order list, in design px. */
private const val HEADING_TOP_PX = 208
private const val LIST_TOP_PX = 384

/** The list fades out over its last 40 dpx (`mask-image`). */
private const val LIST_FADE_PX = 40

/** Design timings of the entrance that the motion inventory does not name (`Payment.dc.html`). */
private const val HEADER_FADE_MILLIS = 500
private const val STEP_FADE_DELAY_MILLIS = 80
private const val TITLE_RISE_MILLIS = 800
private const val TITLE_RISE_DELAY_MILLIS = 80
private const val DASH_MILLIS = 500
private const val DASH_DELAY_MILLIS = 200
private const val DASH_STAGGER_MILLIS = 120
private const val BLOCK_UP_MILLIS = 500
private const val PROGRESS_MILLIS = 500
private const val BANNER_SPINNER_MILLIS = 1100

/** The dashes grow from their left edge (`transform-origin: left`). */
private val LeftCentre = TransformOrigin(pivotFractionX = 0f, pivotFractionY = 0.5f)

private const val TITLE_TRACKING_EM = -0.03f
private const val TITLE_LINE_HEIGHT_EM = 1.04f
private const val EMPTY_TITLE_TRACKING_EM = -0.02f
private const val BANNER_LINE_HEIGHT_EM = 1.2f

/**
 * SCR-10 Your order (`Payment.dc.html` and `S_Payment_priceChanged`, `S_Payment_itemUnavailable`,
 * `S_Payment_offline`, `S_Payment_noMethods`, `S_Payment_emptyTray`, `S_Payment_promoNudge`; FR-012, FR-013, FR-015;
 * DEV-037, DEV-037a, DEV-038). Draws [state] exactly: header, step label and title, the scrolling list (Budget Plan
 * row, the one banner, the lines or the empty tray) with its fade, and the summary panel.
 */
@Composable
fun YourOrderScreen(
    state: YourOrderUiState,
    onEvent: (YourOrderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(YOUR_ORDER_SCREEN_TAG),
    ) {
        OrderHeader(orderType = state.orderType, onAddMore = { onEvent(YourOrderEvent.AddMore) })
        OrderHeading(
            itemCount = state.itemCount,
            modifier = Modifier.padding(start = 72.dpx, end = 72.dpx, top = HEADING_TOP_PX.dpx),
        )
        if (state.loaded) {
            Column(modifier = Modifier.fillMaxSize().padding(top = LIST_TOP_PX.dpx)) {
                OrderList(state = state, onEvent = onEvent, modifier = Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.height(24.dpx))
                SummaryPanel(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.padding(start = 72.dpx, end = 72.dpx, bottom = 48.dpx),
                )
            }
        }
        if (state.showMaxToast) {
            KioskToast(
                text = stringResource(R.string.max_per_item_toast),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun OrderHeader(
    orderType: OrderType,
    onAddMore: () -> Unit,
) {
    val colors = KioskTheme.colors
    val fade = animateEntrance(HEADER_FADE_MILLIS, easing = KioskEasing.Ease)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 72.dpx, end = 72.dpx, top = 48.dpx)
                .height(96.dpx)
                .fadeIn(fade),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BackPill(
            text = stringResource(R.string.your_order_add_more),
            onClick = onAddMore,
            style = HeaderPillStyle.Flat,
            modifier = Modifier.testTag(YOUR_ORDER_ADD_MORE_TAG),
        )
        Row(
            modifier =
                Modifier
                    .height(96.dpx)
                    .border(2.dpx, colors.line, KioskTheme.shapes.pill)
                    .padding(horizontal = 36.dpx)
                    .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dpx),
        ) {
            val takeaway = orderType == OrderType.TAKEAWAY
            KioskIcon(
                icon = if (takeaway) KioskIcons.Bag else KioskIcons.DineIn,
                tint = colors.ink,
                modifier = Modifier.size(36.dpx),
            )
            KioskText(
                text =
                    stringResource(
                        if (takeaway) R.string.menu_order_type_takeaway else R.string.menu_order_type_dine_in,
                    ),
                style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
                maxLines = 1,
            )
        }
    }
}

/** "Step 2 of 3 · Review & pay", the "Your order" title and "N items" (FR-012 AC3). */
@Composable
private fun OrderHeading(
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val step = animateEntrance(HEADER_FADE_MILLIS, STEP_FADE_DELAY_MILLIS, KioskEasing.Ease)
    val title = animateEntrance(TITLE_RISE_MILLIS, TITLE_RISE_DELAY_MILLIS)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dpx)) {
        Row(
            modifier = Modifier.fadeIn(step),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dpx),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dpx)) {
                StepDash(colors.accent, DASH_DELAY_MILLIS)
                StepDash(colors.accent, DASH_DELAY_MILLIS + DASH_STAGGER_MILLIS)
                StepDash(colors.line, delayMillis = null)
            }
            KioskText(
                text = stringResource(R.string.your_order_step),
                style = KioskTheme.typography.meta,
                color = colors.muted,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(modifier = Modifier.weight(1f, fill = false).clipToBounds()) {
                KioskText(
                    text = stringResource(R.string.your_order_title),
                    style =
                        KioskTheme.typography.title.copy(
                            letterSpacing = TITLE_TRACKING_EM.em,
                            lineHeight = TITLE_LINE_HEIGHT_EM.em,
                        ),
                    modifier = Modifier.riseIn(title),
                    maxLines = 1,
                    cssLineBox = true,
                )
            }
            KioskText(
                text = pluralStringResource(R.plurals.your_order_item_count, itemCount, itemCount),
                style = KioskTheme.typography.meta,
                color = colors.muted,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 14.dpx).testTag(YOUR_ORDER_COUNT_TAG),
            )
        }
    }
}

/** One 44 × 8 dpx step dash; an accent dash grows in from the left after [delayMillis] (`dash`). */
@Composable
private fun StepDash(
    color: Color,
    delayMillis: Int?,
) {
    val grow = if (delayMillis == null) null else animateEntrance(DASH_MILLIS, delayMillis)
    Box(
        modifier =
            Modifier
                .size(44.dpx, 8.dpx)
                .graphicsLayer {
                    transformOrigin = LeftCentre
                    scaleX = grow?.value ?: 1f
                }.background(color, RoundedCornerShape(4.dpx)),
    )
}

/** The scrolling list: the Budget Plan row, the banner, then the lines or the empty tray. Fades out at its bottom. */
@Composable
private fun OrderList(
    state: YourOrderUiState,
    onEvent: (YourOrderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .fadeBottom(LIST_FADE_PX.dpx)
                .verticalScroll(rememberScrollState())
                .padding(start = 72.dpx, end = 72.dpx, bottom = 24.dpx),
    ) {
        state.planTag?.let { PlanRow(it, Modifier.padding(bottom = 24.dpx)) }
        state.banner?.let { OrderBanner(it, onEvent, Modifier.padding(bottom = 24.dpx)) }
        if (state.empty) {
            EmptyTray(onBackToMenu = { onEvent(YourOrderEvent.BackToMenu) })
        } else {
            state.lines.forEachIndexed { index, line ->
                key(line.lineId) { OrderLineRow(line = line, index = index, onEvent = onEvent) }
            }
        }
    }
}

/** The content fades to transparent over its last [height] (`mask-image: linear-gradient(...)`). */
private fun Modifier.fadeBottom(height: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = height.toPx()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        0f to Color.Black,
                        1f to Color.Transparent,
                        startY = size.height - fade,
                        endY = size.height,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }

/** "Budget Plan · 2 adults · 1 child · Rp 150.000" (FR-012, A-40). */
@Composable
private fun PlanRow(
    plan: PlanTagUi,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val entrance = animateEntrance(BLOCK_UP_MILLIS)
    val separator = stringResource(R.string.your_order_plan_separator)
    val people =
        buildList {
            add(pluralStringResource(R.plurals.your_order_plan_adults, plan.adults, plan.adults))
            if (plan.children > 0) {
                add(pluralStringResource(R.plurals.your_order_plan_children, plan.children, plan.children))
            }
            add(plan.budget)
        }
    Row(
        modifier =
            modifier
                .fadeUp(entrance, KioskMotion.Common.UP_DISTANCE_PX.dpx)
                .fillMaxWidth()
                .heightIn(min = 96.dpx)
                .background(colors.surfaceAlt, KioskTheme.shapes.large)
                .padding(horizontal = 32.dpx)
                .semantics(mergeDescendants = true) {}
                .testTag(YOUR_ORDER_PLAN_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dpx),
    ) {
        Box(Modifier.size(16.dpx).background(colors.accent, CircleShape))
        KioskText(
            text = stringResource(R.string.your_order_plan_title),
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        KioskText(
            text = people.joinToString(separator),
            style = KioskTheme.typography.meta,
            color = colors.muted,
            maxLines = 1,
        )
    }
}

/**
 * The banner of the list (`bannerStyle`): a white strip with a round icon, the title, an optional progress bar
 * (promo nudge) and an optional button ("Review", "Remove it"). Price and availability banners get an accent
 * outline. Announced politely as a status (NFR-007).
 */
@Composable
private fun OrderBanner(
    banner: OrderBannerUi,
    onEvent: (YourOrderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.large
    val entrance = animateEntrance(BLOCK_UP_MILLIS)
    val warn = banner is OrderBannerUi.PriceChanged || banner is OrderBannerUi.ItemUnavailable
    Row(
        modifier =
            modifier
                .fadeUp(entrance, KioskMotion.Common.UP_DISTANCE_PX.dpx)
                .fillMaxWidth()
                .heightIn(min = 128.dpx)
                .background(colors.surface, shape)
                .border(2.dpx, if (warn) colors.accent else colors.line, shape)
                .padding(horizontal = 28.dpx, vertical = 20.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        BannerIcon(banner)
        Column(
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(14.dpx),
        ) {
            KioskText(
                text = bannerTitle(banner),
                style =
                    KioskTheme.typography.body.copy(
                        fontWeight = FontWeight.W700,
                        lineHeight = BANNER_LINE_HEIGHT_EM.em,
                    ),
            )
            if (banner is OrderBannerUi.PromoNudge) PromoProgress(banner.progress)
        }
        when (banner) {
            OrderBannerUi.PriceChanged -> {
                BannerButton(R.string.your_order_banner_review) { onEvent(YourOrderEvent.ReviewPrices) }
            }

            is OrderBannerUi.ItemUnavailable -> {
                BannerButton(R.string.your_order_banner_remove_it) { onEvent(YourOrderEvent.RemoveUnavailable) }
            }

            OrderBannerUi.Offline, is OrderBannerUi.PromoNudge -> {
                Unit
            }
        }
    }
}

@Composable
private fun bannerTitle(banner: OrderBannerUi): String =
    when (banner) {
        OrderBannerUi.Offline -> {
            stringResource(R.string.offline_banner)
        }

        OrderBannerUi.PriceChanged -> {
            stringResource(R.string.your_order_banner_price_changed)
        }

        is OrderBannerUi.ItemUnavailable -> {
            pluralStringResource(R.plurals.your_order_banner_unavailable, banner.count, banner.count)
        }

        is OrderBannerUi.PromoNudge -> {
            stringResource(R.string.your_order_banner_promo, banner.remaining, banner.discount)
        }
    }

@Composable
private fun BannerIcon(banner: OrderBannerUi) {
    val colors = KioskTheme.colors
    val spin = banner == OrderBannerUi.Offline
    Box(
        modifier = Modifier.size(72.dpx).background(if (spin) colors.surfaceAlt else colors.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (banner) {
            OrderBannerUi.Offline -> {
                ArcSpinner(color = colors.muted, modifier = Modifier.size(40.dpx))
            }

            OrderBannerUi.PriceChanged, is OrderBannerUi.ItemUnavailable -> {
                KioskIcon(icon = KioskIcons.Warning, tint = colors.surface, modifier = Modifier.size(40.dpx))
            }

            is OrderBannerUi.PromoNudge -> {
                KioskIcon(icon = KioskIcons.Tag, tint = colors.surface, modifier = Modifier.size(40.dpx))
            }
        }
    }
}

/**
 * The offline banner's spinner: a three-quarter ring (`M12 3a9 9 0 1 0 9 9`, stroke 2.4 in a 24 viewBox) turning
 * once per 1.1 s, linearly. Still with reduce motion (NFR-009).
 */
@Composable
private fun ArcSpinner(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val rotation =
        if (reduce) {
            null
        } else {
            rememberInfiniteTransition(label = "banner-spinner").animateFloat(
                initialValue = 0f,
                targetValue = FULL_TURN,
                animationSpec = infiniteRepeatable(tween(BANNER_SPINNER_MILLIS, easing = LinearEasing)),
                label = "banner-spinner-turn",
            )
        }
    Canvas(modifier = modifier.graphicsLayer { rotationZ = rotation?.value ?: 0f }) {
        val unit = size.minDimension / ICON_VIEWBOX
        val radius = ARC_RADIUS * unit
        drawArc(
            color = color,
            // From 3 o'clock clockwise round to 12 o'clock: the SVG arc's three quarters.
            startAngle = 0f,
            sweepAngle = ARC_SWEEP,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = ARC_STROKE * unit, cap = StrokeCap.Round),
        )
    }
}

private const val ICON_VIEWBOX = 24f
private const val ARC_STROKE = 2.4f
private const val ARC_RADIUS = 9f
private const val ARC_SWEEP = 270f
private const val FULL_TURN = 360f

/** The 12 dpx progress bar of the promo nudge; its fill eases to the new width over 500 ms. */
@Composable
private fun PromoProgress(progress: Float) {
    val colors = KioskTheme.colors
    val fill by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        motionSpec(tween(PROGRESS_MILLIS, easing = KioskEasing.Emphasized)),
        label = "promo-progress",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(12.dpx)
                .background(colors.line, KioskTheme.shapes.pill),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fill)
                    .height(12.dpx)
                    .background(colors.accent, KioskTheme.shapes.pill),
        )
    }
}

@Composable
private fun BannerButton(
    label: Int,
    onClick: () -> Unit,
) {
    val colors = KioskTheme.colors
    val text = stringResource(label)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            Modifier
                .height(88.dpx)
                .pressScale(scale)
                .background(colors.ink, KioskTheme.shapes.medium)
                .orderButton(text, YOUR_ORDER_BANNER_ACTION_TAG, interactionSource, onClick = onClick)
                .padding(horizontal = 40.dpx),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = text,
            style = KioskTheme.typography.button.copy(fontSize = 28.spx, letterSpacing = 0.em),
            color = colors.bg,
            maxLines = 1,
        )
    }
}

/** "Your tray is empty" / "Add something tasty to get started." / "Back to menu" (`S_Payment_emptyTray`). */
@Composable
private fun EmptyTray(onBackToMenu: () -> Unit) {
    val colors = KioskTheme.colors
    val entrance = animateEntrance(BLOCK_UP_MILLIS)
    Column(
        modifier =
            Modifier
                .fadeUp(entrance, KioskMotion.Common.UP_DISTANCE_PX.dpx)
                .fillMaxWidth()
                .heightIn(min = 520.dpx)
                .background(colors.surfaceAlt, KioskTheme.shapes.card)
                .padding(vertical = 40.dpx),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dpx, Alignment.CenterVertically),
    ) {
        KioskIcon(icon = KioskIcons.BagLight, tint = colors.muted, modifier = Modifier.size(88.dpx))
        KioskText(
            text = stringResource(R.string.your_order_empty_title),
            style = KioskTheme.typography.section.copy(letterSpacing = EMPTY_TITLE_TRACKING_EM.em),
        )
        KioskText(
            text = stringResource(R.string.your_order_empty_body),
            style = KioskTheme.typography.body,
            color = colors.muted,
        )
        val label = stringResource(R.string.your_order_back_to_menu)
        val interactionSource = remember { MutableInteractionSource() }
        val scale = animatePressScale(interactionSource)
        Box(
            modifier =
                Modifier
                    .padding(top = 8.dpx)
                    .height(112.dpx)
                    .pressScale(scale)
                    .background(colors.ink, KioskTheme.shapes.large)
                    .orderButton(label, YOUR_ORDER_BACK_TO_MENU_TAG, interactionSource, onClick = onBackToMenu)
                    .padding(horizontal = 56.dpx),
            contentAlignment = Alignment.Center,
        ) {
            KioskText(
                text = label,
                style = KioskTheme.typography.button.copy(fontSize = 32.spx, letterSpacing = 0.em),
                color = colors.bg,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun YourOrderPreview(state: YourOrderUiState) {
    KioskTheme(reduceMotion = true) {
        DesignFrame { YourOrderScreen(state = state, onEvent = {}) }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun YourOrderDefaultPreview() = YourOrderPreview(YourOrderSamples.Default)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun YourOrderDetailsPreview() = YourOrderPreview(YourOrderSamples.Default.copy(detailsOpen = true))

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun YourOrderPlanPreview() = YourOrderPreview(YourOrderSamples.Plan)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun YourOrderPriceChangedPreview() = YourOrderPreview(YourOrderSamples.PriceChanged)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun YourOrderItemUnavailablePreview() = YourOrderPreview(YourOrderSamples.ItemUnavailable)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun YourOrderOfflinePreview() = YourOrderPreview(YourOrderSamples.Offline)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun YourOrderNoMethodsPreview() = YourOrderPreview(YourOrderSamples.NoMethods)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun YourOrderEmptyTrayPreview() = YourOrderPreview(YourOrderSamples.EmptyTray)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun YourOrderPromoNudgePreview() = YourOrderPreview(YourOrderSamples.PromoNudge)
