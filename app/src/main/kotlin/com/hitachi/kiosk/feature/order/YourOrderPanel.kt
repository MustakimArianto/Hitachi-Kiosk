package com.hitachi.kiosk.feature.order

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.RollingText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.PaymentMethod

/** Test tag of the "Total · Details" toggle. */
const val YOUR_ORDER_DETAILS_TAG = "your-order-details"

/** Test tag of the large Total. */
const val YOUR_ORDER_TOTAL_TAG = "your-order-total"

/** Test tag of the Pay button, enabled or blocked. */
const val YOUR_ORDER_PAY_TAG = "your-order-pay"

/** Test tag of "No payment methods right now". */
const val YOUR_ORDER_NO_METHODS_TAG = "your-order-no-methods"

/** Test tag of a payment method tile. */
fun yourOrderMethodTag(method: PaymentMethod): String = "your-order-method:${method.name.lowercase()}"

/** The collapsed panel is 520 dpx; Details adds 48 dpx plus 46 dpx per row (`panelH`). */
private const val PANEL_HEIGHT_PX = 520
private const val DETAILS_BASE_PX = 48
private const val DETAILS_ROW_PX = 46

/** The panel and the list resize over 500 ms (`transition: height 500ms`). */
private const val PANEL_RESIZE_MILLIS = 500

/** The panel slides up from 110 % of its height (`panelUp`). */
private const val PANEL_UP_FRACTION = 1.1f

/** Details fade up over 400 ms; the chevron turns over 400 ms with the spring curve. */
private const val DETAILS_UP_MILLIS = 400
private const val CHEVRON_MILLIS = 400
private const val HALF_TURN = 180f

/** Tile colors change over 320 ms, the ring over 300 ms (`transition`). */
private const val TILE_COLOR_MILLIS = 320
private const val RING_COLOR_MILLIS = 300

/** The tile check pops in over 420 ms from 40 % turned −20° (`popIn`). */
private const val CHECK_POP_MILLIS = 420
private const val CHECK_POP_FROM = 0.4f
private const val CHECK_POP_PEAK = 1.1f
private const val CHECK_POP_PEAK_FRACTION = 0.7f
private const val CHECK_POP_TURN = -20f

/** The method note is drawn at 75 % (`opacity: .75`), the Pay arrow tile at white 16 %. */
private const val NOTE_ALPHA = 0.75f
private const val PAY_TILE_ALPHA = 0.16f

private const val TOTAL_TRACKING_EM = -0.03f
private const val TOTAL_LINE_HEIGHT_EM = 96f / 72f
private const val TILE_TITLE_LINE_HEIGHT_EM = 1.1f
private const val TILE_NOTE_LINE_HEIGHT_EM = 1.2f

/**
 * The dark summary panel of `Payment.dc.html` (FR-013 display, FR-015, DEV-038): the rolling Total with the
 * "Details" toggle (Subtotal, Promo, tax and Rounding), the payment method tiles or "No payment methods right now",
 * and Pay, which shows its blocking reason when it cannot be used. It grows with Details open, and the list above
 * shrinks to match (TC-173).
 */
@Composable
internal fun SummaryPanel(
    state: YourOrderUiState,
    onEvent: (YourOrderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.dock
    val entrance =
        animateEntrance(KioskMotion.YourOrder.PANEL_UP_MILLIS, KioskMotion.YourOrder.PANEL_UP_DELAY_MILLIS)
    val targetHeight =
        PANEL_HEIGHT_PX + if (state.detailsOpen) DETAILS_BASE_PX + DETAILS_ROW_PX * state.totals.detailRows else 0
    val height by animateDpAsState(
        targetHeight.dpx,
        motionSpec(tween(PANEL_RESIZE_MILLIS, easing = KioskEasing.Emphasized)),
        label = "panel-height",
    )
    Column(
        modifier =
            modifier
                .graphicsLayer { translationY = size.height * PANEL_UP_FRACTION * (1f - entrance.value) }
                .fillMaxWidth()
                .heightIn(min = height)
                .kioskShadow(shape, KioskTheme.elevation.floating)
                .background(colors.ink, shape)
                .padding(start = 48.dpx, end = 48.dpx, top = 40.dpx, bottom = 48.dpx),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            TotalRow(state, onEvent)
            if (state.detailsOpen) DetailsRows(state.totals)
            if (state.noMethods) {
                NoMethods(Modifier.padding(top = 24.dpx))
            } else {
                MethodTiles(
                    methods = state.methods,
                    selected = state.selectedMethod,
                    onSelect = { onEvent(YourOrderEvent.SelectMethod(it)) },
                    modifier = Modifier.padding(top = 24.dpx),
                )
            }
        }
        Spacer(Modifier.height(24.dpx))
        PayButton(pay = state.pay, onPay = { onEvent(YourOrderEvent.Pay) })
    }
}

@Composable
private fun TotalRow(
    state: YourOrderUiState,
    onEvent: (YourOrderEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val chevron by animateFloatAsState(
        if (state.detailsOpen) HALF_TURN else 0f,
        motionSpec(tween(CHEVRON_MILLIS, easing = KioskEasing.Spring)),
        label = "details-chevron",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier =
                Modifier
                    .height(96.dpx)
                    .pressScale(scale)
                    .orderButton(
                        stringResource(R.string.your_order_details_description),
                        YOUR_ORDER_DETAILS_TAG,
                        interactionSource,
                        onClick = { onEvent(YourOrderEvent.ToggleDetails) },
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dpx),
        ) {
            KioskText(
                text = stringResource(R.string.your_order_total),
                style = KioskTheme.typography.item.copy(letterSpacing = 0.em),
                color = colors.bg,
                maxLines = 1,
            )
            Row(
                modifier =
                    Modifier
                        .height(56.dpx)
                        .border(2.dpx, colors.dockControl, KioskTheme.shapes.pill)
                        .padding(start = 24.dpx, end = 20.dpx),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dpx),
            ) {
                KioskText(
                    text = stringResource(R.string.your_order_details),
                    style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W600),
                    color = colors.dockChipText,
                    maxLines = 1,
                )
                KioskIcon(
                    icon = KioskIcons.ChevronDown,
                    tint = colors.dockChipText,
                    modifier = Modifier.size(28.dpx).graphicsLayer { rotationZ = chevron },
                )
            }
        }
        RollingText(
            text = state.totals.total,
            style =
                KioskTheme.typography.display.copy(
                    fontSize = 72.spx,
                    letterSpacing = TOTAL_TRACKING_EM.em,
                    lineHeight = TOTAL_LINE_HEIGHT_EM.em,
                ),
            durationMillis = KioskMotion.YourOrder.TOTALS_ROLL_MILLIS,
            color = colors.bg,
            modifier =
                Modifier
                    .height(96.dpx)
                    .semantics(mergeDescendants = true) {}
                    .testTag(YOUR_ORDER_TOTAL_TAG),
        )
    }
}

/** Subtotal, Promo (when a promo applies), the tax label from config and Rounding (when non-zero). */
@Composable
private fun DetailsRows(totals: OrderTotalsUi) {
    val colors = KioskTheme.colors
    val entrance = animateEntrance(DETAILS_UP_MILLIS)
    Column(
        modifier =
            Modifier
                .padding(top = 8.dpx)
                .fadeUp(entrance, KioskMotion.Common.UP_DISTANCE_PX.dpx)
                .fillMaxWidth()
                .background(colors.dockTileBorder)
                .padding(top = 2.dpx)
                .background(colors.ink)
                .padding(top = 16.dpx),
        verticalArrangement = Arrangement.spacedBy(12.dpx),
    ) {
        DetailRow(stringResource(R.string.your_order_subtotal), totals.subtotal)
        if (totals.discount != null) {
            DetailRow(
                label = totals.promoLabel.orEmpty(),
                value = stringResource(R.string.your_order_discount_amount, totals.discount),
                promo = true,
            )
        }
        DetailRow(totals.taxLabel ?: stringResource(R.string.your_order_tax), totals.tax)
        if (totals.rounding != null) {
            DetailRow(
                stringResource(R.string.your_order_rounding),
                stringResource(R.string.your_order_rounding_amount, totals.rounding),
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    promo: Boolean = false,
) {
    val colors = KioskTheme.colors
    val text = KioskTheme.typography.body.copy(fontSize = 28.spx, lineHeight = 1.2.em)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 34.dpx).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dpx),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dpx),
        ) {
            if (promo) {
                Box(
                    modifier =
                        Modifier
                            .background(colors.accent, KioskTheme.shapes.pill)
                            .padding(horizontal = 14.dpx, vertical = 2.dpx),
                ) {
                    KioskText(
                        text = stringResource(R.string.your_order_promo),
                        style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W700),
                        color = colors.surface,
                        maxLines = 1,
                    )
                }
            }
            KioskText(
                text = label,
                style = text,
                color = if (promo) colors.bg else colors.dockMutedAlt,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        KioskText(text = value, style = text.copy(fontWeight = FontWeight.W600), color = colors.bg, maxLines = 1)
    }
}

/** "No payment methods right now" / "Please ask a team member for help." (`S_Payment_noMethods`, A-14). */
@Composable
private fun NoMethods(modifier: Modifier = Modifier) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.large
    val entrance = animateEntrance(KioskMotion.Common.PANEL_UP_MILLIS)
    Row(
        modifier =
            modifier
                .fadeUp(entrance, KioskMotion.Common.UP_DISTANCE_PX.dpx)
                .fillMaxWidth()
                .heightIn(min = 136.dpx)
                .background(colors.dockTile, shape)
                .border(2.dpx, colors.dockTileBorder, shape)
                .padding(horizontal = 32.dpx, vertical = 16.dpx)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }
                .testTag(YOUR_ORDER_NO_METHODS_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        KioskIcon(icon = KioskIcons.CardOff, tint = colors.bg, modifier = Modifier.size(48.dpx))
        Column(verticalArrangement = Arrangement.spacedBy(6.dpx)) {
            KioskText(
                text = stringResource(R.string.your_order_no_methods_title),
                style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W700, lineHeight = 1.2.em),
                color = colors.bg,
            )
            KioskText(
                text = stringResource(R.string.your_order_no_methods_body),
                style = KioskTheme.typography.meta,
                color = colors.dockMutedAlt,
            )
        }
    }
}

/** "How would you like to pay?": two wide tiles side by side, QRIS first (FR-015). */
@Composable
private fun MethodTiles(
    methods: List<PaymentMethod>,
    selected: PaymentMethod?,
    onSelect: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.your_order_methods_description)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = description }
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(16.dpx),
    ) {
        methods.forEachIndexed { index, method ->
            MethodTile(
                method = method,
                selected = method == selected,
                index = index,
                onClick = { onSelect(method) },
                modifier = Modifier.weight(1f),
            )
        }
        // The grid keeps two columns, so a single method keeps its half width (`repeat(2, 1fr)`).
        repeat(METHOD_COLUMNS - methods.size.coerceAtMost(METHOD_COLUMNS)) { Spacer(Modifier.weight(1f)) }
    }
}

private const val METHOD_COLUMNS = 2

@Composable
private fun MethodTile(
    method: PaymentMethod,
    selected: Boolean,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.large
    val entrance =
        animateEntrance(
            KioskMotion.YourOrder.METHOD_TILES_MILLIS,
            KioskMotion.YourOrder.METHOD_TILES_DELAY_MILLIS + index * KioskMotion.YourOrder.METHOD_TILES_STAGGER_MILLIS,
        )
    val tileSpec = motionSpec(tween<Color>(TILE_COLOR_MILLIS))
    val ringSpec = motionSpec(tween<Color>(RING_COLOR_MILLIS))
    val background by animateColorAsState(if (selected) colors.bg else colors.dockTile, tileSpec, label = "tile-bg")
    val foreground by animateColorAsState(if (selected) colors.ink else colors.bg, tileSpec, label = "tile-fg")
    val border by animateColorAsState(
        if (selected) colors.bg else colors.dockTileBorder,
        tileSpec,
        label = "tile-border",
    )
    val ringBorder by animateColorAsState(
        if (selected) colors.accent else colors.dockRingOff,
        ringSpec,
        label = "ring-border",
    )
    val ringFill by animateColorAsState(
        if (selected) colors.accent else colors.accent.copy(alpha = 0f),
        ringSpec,
        label = "ring-fill",
    )
    val (title, note, icon) =
        when (method) {
            PaymentMethod.QRIS -> {
                Triple(R.string.your_order_method_qris, R.string.your_order_method_qris_note, KioskIcons.QrCode)
            }

            PaymentMethod.CARD -> {
                Triple(R.string.your_order_method_card, R.string.your_order_method_card_note, KioskIcons.Card)
            }
        }
    val titleText = stringResource(title)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            modifier
                .fadeUp(entrance, KioskMotion.Common.UP_DISTANCE_PX.dpx)
                .height(112.dpx)
                .pressScale(scale)
                .background(background, shape)
                .border(2.dpx, border, shape)
                .orderButton(
                    titleText,
                    yourOrderMethodTag(method),
                    interactionSource,
                    role = Role.RadioButton,
                    selected = selected,
                    onClick = onClick,
                ).padding(horizontal = 24.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        KioskIcon(icon = icon, tint = foreground, modifier = Modifier.size(48.dpx))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dpx)) {
            KioskText(
                text = titleText,
                style =
                    KioskTheme.typography.button.copy(
                        fontSize = 32.spx,
                        letterSpacing = 0.em,
                        lineHeight = TILE_TITLE_LINE_HEIGHT_EM.em,
                    ),
                color = foreground,
                maxLines = 1,
            )
            KioskText(
                text = stringResource(note),
                style = KioskTheme.typography.meta.copy(fontSize = 24.spx, lineHeight = TILE_NOTE_LINE_HEIGHT_EM.em),
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(NOTE_ALPHA),
            )
        }
        Box(
            modifier =
                Modifier
                    .size(44.dpx)
                    .background(ringFill, CircleShape)
                    .border(2.dpx, ringBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) SelectedCheck()
        }
    }
}

/** The white check of the selected tile, popping in (`popIn`, spring curve); still with reduce motion. */
@Composable
private fun SelectedCheck() {
    val pop = animateEntrance(CHECK_POP_MILLIS, easing = KioskEasing.Spring)
    KioskIcon(
        icon = KioskIcons.CheckBold,
        tint = KioskTheme.colors.surface,
        modifier =
            Modifier.size(26.dpx).graphicsLayer {
                val t = pop.value
                val rise = (t / CHECK_POP_PEAK_FRACTION).coerceIn(0f, 1f)
                val settle = ((t - CHECK_POP_PEAK_FRACTION) / (1f - CHECK_POP_PEAK_FRACTION)).coerceIn(0f, 1f)
                val s =
                    if (t < CHECK_POP_PEAK_FRACTION) {
                        CHECK_POP_FROM + (CHECK_POP_PEAK - CHECK_POP_FROM) * rise
                    } else {
                        CHECK_POP_PEAK + (1f - CHECK_POP_PEAK) * settle
                    }
                alpha = rise
                scaleX = s
                scaleY = s
                rotationZ = CHECK_POP_TURN * (1f - t)
            },
    )
}

/** "Pay Rp X" (accent, with the arrow tile) or the disabled bar with the blocking reason (FR-015). */
@Composable
private fun PayButton(
    pay: PayUi,
    onPay: () -> Unit,
) {
    val colors = KioskTheme.colors
    val shape = RoundedCornerShape(36.dpx)
    val label = payLabel(pay)
    when (pay) {
        is PayUi.Ready -> {
            val interactionSource = remember { MutableInteractionSource() }
            val scale = animatePressScale(interactionSource)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(144.dpx)
                        .pressScale(scale)
                        .background(colors.accent, shape)
                        .orderButton(label, YOUR_ORDER_PAY_TAG, interactionSource, onClick = onPay)
                        .padding(start = 48.dpx, end = 20.dpx),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                KioskText(
                    text = label,
                    style = KioskTheme.typography.button.copy(fontSize = 36.spx, letterSpacing = 0.em),
                    color = colors.surface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier =
                        Modifier
                            .size(104.dpx)
                            .background(colors.surface.copy(alpha = PAY_TILE_ALPHA), KioskTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    KioskIcon(icon = KioskIcons.ArrowRightBold, tint = colors.surface, modifier = Modifier.size(46.dpx))
                }
            }
        }

        is PayUi.Blocked -> {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(144.dpx)
                        .background(colors.dockDisabled, shape)
                        .clearAndSetSemantics {
                            testTag = YOUR_ORDER_PAY_TAG
                            contentDescription = label
                            liveRegion = LiveRegionMode.Polite
                            disabled()
                        }.padding(horizontal = 32.dpx),
                contentAlignment = Alignment.Center,
            ) {
                KioskText(
                    text = label,
                    style = KioskTheme.typography.button.copy(fontSize = 32.spx, fontWeight = FontWeight.W600),
                    color = colors.dockDisabledText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun payLabel(pay: PayUi): String =
    when (pay) {
        is PayUi.Ready -> {
            stringResource(R.string.your_order_pay, pay.total)
        }

        is PayUi.Blocked -> {
            stringResource(
                when (pay.reason) {
                    PayBlock.EMPTY -> R.string.your_order_pay_empty
                    PayBlock.OFFLINE -> R.string.your_order_pay_offline
                    PayBlock.PRICE_CHANGED -> R.string.your_order_pay_price_changed
                    PayBlock.ITEM_UNAVAILABLE -> R.string.your_order_pay_item_unavailable
                    PayBlock.NO_METHODS -> R.string.your_order_pay_unavailable
                },
            )
        }
    }
