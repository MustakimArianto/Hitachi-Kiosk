package com.hitachi.kiosk.feature.item

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.BackPill
import com.hitachi.kiosk.core.designsystem.component.CartPill
import com.hitachi.kiosk.core.designsystem.component.HeaderPillStyle
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.KioskToast
import com.hitachi.kiosk.core.designsystem.component.RollingText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Common
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Item
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animateOnEnter
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.motion.riseIn
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.navigation.screenTag
import com.hitachi.kiosk.ui.MenuPhoto

/** Test tag of the "Menu" back pill. */
const val ITEM_BACK_TAG = "item-back"

/** Test tag of the "My order" pill. */
const val ITEM_CART_TAG = "item-cart"

/** Test tag of the dock's "−". */
const val ITEM_DECREASE_TAG = "item-decrease"

/** Test tag of the dock's "+". */
const val ITEM_INCREASE_TAG = "item-increase"

/** Test tag of the dock's quantity. */
const val ITEM_QUANTITY_TAG = "item-quantity"

/** Test tag of "Add to order". */
const val ITEM_ADD_TAG = "item-add"

/** Test tag of "Keep ordering" on the Added dock. */
const val ITEM_KEEP_ORDERING_TAG = "item-keep-ordering"

/** Test tag of "View order" on the Added dock. */
const val ITEM_VIEW_ORDER_TAG = "item-view-order"

/** Test tag of "Back to menu" on the sold-out banner. */
const val ITEM_SOLD_OUT_BACK_TAG = "item-sold-out-back"

private const val HERO_HEIGHT_PX = 560
private const val SHEET_TOP_PX = 500
private const val SHEET_RISE_PX = 320
private const val HERO_START_SCALE = 1.08f

/** `rgba(250,248,245,.62)` over a sold-out hero. */
private const val SOLD_OUT_SCRIM_ALPHA = 0.62f

/** The header pills fade in (`fadeIn 500ms @100 / @160`). */
private const val PILL_FADE_MILLIS = 500
private const val BACK_PILL_DELAY_MILLIS = 100
private const val CART_PILL_DELAY_MILLIS = 160

/** The cart badge bumps to 1.25 over 520 ms, 760 ms after the add (`bump`, `ItemDetail.dc.html`). */
private const val CART_BUMP_SCALE = 1.25f
private const val CART_BUMP_MILLIS = 520
private const val CART_BUMP_DELAY_MILLIS = 760

/** The info block (`ItemDetail.dc.html`): chips 600 @320, title 900 @300, price 600 @420, description 600 @480. */
private const val INFO_UP_MILLIS = 600
private const val CHIPS_DELAY_MILLIS = 320
private const val TITLE_RISE_MILLIS = 900
private const val TITLE_RISE_DELAY_MILLIS = 300
private const val PRICE_DELAY_MILLIS = 420
private const val DESCRIPTION_DELAY_MILLIS = 480

/** The first section (the required group) fades up at 500 ms, the next ones at the §11.4 delays. */
private const val FIRST_SECTION_DELAY_MILLIS = 500
private val SectionDelays =
    listOf(
        FIRST_SECTION_DELAY_MILLIS,
        Item.SECTIONS_FADE_UP_DELAY_MILLIS,
        Item.SECTIONS_FADE_UP_SECOND_DELAY_MILLIS,
        Item.SECTIONS_FADE_UP_THIRD_DELAY_MILLIS,
    )

/** The dock rises from 140 % of its height, 800 ms @300 (PRD §7.1). */
private const val DOCK_IN_MILLIS = 800
private const val DOCK_IN_DELAY_MILLIS = 300
private const val DOCK_IN_FRACTION = 1.4f

/** The Added check: ring out 800 ms to 1.8×, check in 460 ms. */
private const val RING_OUT_MILLIS = 800
private const val RING_OUT_SCALE = 0.8f
private const val RING_START_ALPHA = 0.8f
private const val CHECK_IN_MILLIS = 460
private val CheckPop = CubicBezierEasing(0.3f, 1.4f, 0.5f, 1f)

/** The flying thumbnail: from (380, 1700), 180 dpx, to +560 / −1640 at 15 %, fading over the last 20 %. */
private const val FLY_LEFT_PX = 380
private const val FLY_TOP_PX = 1700
private const val FLY_SIZE_PX = 180
private const val FLY_DX_PX = 560
private const val FLY_DY_PX = -1640
private const val FLY_END_SCALE = 0.15f
private const val FLY_FADE_START = 0.8f

/** The disabled "−" and CTA on the dark dock, `#3A3632`: `bg` at 15 % over `ink`. */
private const val DOCK_CONTROL_ALPHA = 0.15f

/** "+" fades to 40 % at the maximum quantity. */
private const val AT_MAX_ALPHA = 0.4f

/** The "View order" button is 1.1× the width of "Keep ordering". */
private const val VIEW_ORDER_WEIGHT = 1.1f

/** The background fade above the dock: transparent to `bg` at 55 %. */
private const val FADE_HEIGHT_PX = 300
private const val FADE_SOLID_AT = 0.55f

private const val TITLE_LETTER_EM = -0.03f
private const val PRICE_LETTER_EM = -0.02f

@Composable
private fun dockControlColor(): Color =
    KioskTheme.colors.bg
        .copy(alpha = DOCK_CONTROL_ALPHA)
        .compositeOver(KioskTheme.colors.ink)

/**
 * SCR-08 Item detail (`ItemDetail.dc.html`, `S_ItemDetail_*`, FR-010, DEV-029). Draws [state] exactly: the photo hero,
 * the sheet with the info and the modifier groups, the header pills, the dock and the toast.
 */
@Composable
fun ItemDetailScreen(
    state: ItemDetailUiState,
    onEvent: (ItemDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(screenTag("SCR-08")),
    ) {
        if (!state.loaded) return@Box
        val tone = state.product?.tone
        Hero(imageUrl = state.product?.imageUrl, tone = tone, soldOut = state.soldOut)
        Sheet(state = state, onEvent = onEvent)
        HeaderPills(state = state, onEvent = onEvent)
        BottomFade(Modifier.align(Alignment.BottomCenter))
        if (state.showMaxToast) {
            KioskToast(
                text = stringResource(R.string.max_per_item_toast),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 240.dpx),
            )
        }
        Dock(state = state, onEvent = onEvent, modifier = Modifier.align(Alignment.BottomCenter))
        FlyingThumbnail(bumps = state.cartBumps, imageUrl = state.product?.imageUrl, tone = tone)
    }
}

@Composable
private fun Hero(
    imageUrl: String?,
    tone: String?,
    soldOut: Boolean,
) {
    val colors = KioskTheme.colors
    val zoom = animateEntrance(Item.HERO_SCALE_MILLIS)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HERO_HEIGHT_PX.dpx)
                .clipToBounds(),
    ) {
        MenuPhoto(
            imageUrl = imageUrl,
            tone = tone,
            contentDescription = null,
            shape = RectangleShape,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    val scale = HERO_START_SCALE + (1f - HERO_START_SCALE) * zoom.value
                    scaleX = scale
                    scaleY = scale
                },
        )
        if (soldOut) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(colors.bg.copy(alpha = SOLD_OUT_SCRIM_ALPHA))
                        .padding(start = 72.dpx, bottom = 100.dpx),
                contentAlignment = Alignment.BottomStart,
            ) {
                Box(
                    modifier =
                        Modifier
                            .height(72.dpx)
                            .background(colors.ink, KioskTheme.shapes.pill)
                            .padding(horizontal = 32.dpx),
                    contentAlignment = Alignment.Center,
                ) {
                    KioskText(
                        text = stringResource(R.string.item_sold_out_chip),
                        style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
                        color = colors.bg,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderPills(
    state: ItemDetailUiState,
    onEvent: (ItemDetailEvent) -> Unit,
) {
    val backFade = animateEntrance(PILL_FADE_MILLIS, BACK_PILL_DELAY_MILLIS)
    val cartFade = animateEntrance(PILL_FADE_MILLIS, CART_PILL_DELAY_MILLIS)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 72.dpx, end = 72.dpx, top = 48.dpx),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BackPill(
            text = stringResource(R.string.customize_menu_pill),
            onClick = { onEvent(ItemDetailEvent.BackToMenu) },
            style = HeaderPillStyle.Floating,
            modifier = Modifier.fadeIn(backFade).testTag(ITEM_BACK_TAG),
        )
        CartPill(
            text = stringResource(R.string.customize_my_order_pill),
            count = state.cartCount,
            contentDescription =
                pluralStringResource(R.plurals.customize_my_order_description, state.cartCount, state.cartCount),
            onClick = { onEvent(ItemDetailEvent.ViewOrder) },
            style = HeaderPillStyle.Floating,
            bumps = state.cartBumps,
            bumpScale = CART_BUMP_SCALE,
            bumpMillis = CART_BUMP_MILLIS,
            bumpDelayMillis = CART_BUMP_DELAY_MILLIS,
            modifier = Modifier.fadeIn(cartFade).testTag(ITEM_CART_TAG),
        )
    }
}

@Composable
private fun Sheet(
    state: ItemDetailUiState,
    onEvent: (ItemDetailEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val rise = animateEntrance(Item.SHEET_UP_MILLIS, Item.SHEET_UP_DELAY_MILLIS)
    val shape = RoundedCornerShape(topStart = 48.dpx, topEnd = 48.dpx)
    val riseDistance = SHEET_RISE_PX.dpx
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = SHEET_TOP_PX.dpx)
                .graphicsLayer { translationY = riseDistance.toPx() * (1f - rise.value) }
                .clip(shape)
                .background(colors.bg)
                .verticalScroll(rememberScrollState())
                .padding(start = 72.dpx, end = 72.dpx, top = 56.dpx, bottom = 300.dpx),
        verticalArrangement = Arrangement.spacedBy(48.dpx),
    ) {
        if (state.soldOut) SoldOutBanner(onBack = { onEvent(ItemDetailEvent.BackToMenu) })
        state.product?.let { ProductInfo(it) }
        state.sections.forEachIndexed { index, section ->
            val enter =
                animateEntrance(
                    Item.SECTIONS_FADE_UP_MILLIS,
                    SectionDelays[index.coerceAtMost(SectionDelays.lastIndex)],
                )
            ItemSection(
                section = section,
                tone = state.product?.tone,
                onEvent = onEvent,
                modifier = Modifier.fadeUp(enter, Common.UP_DISTANCE_PX.dpx),
            )
        }
    }
}

@Composable
private fun SoldOutBanner(onBack: () -> Unit) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.errorBg, shape)
                .border(2.dpx, colors.errorBorder, shape)
                .padding(horizontal = 36.dpx, vertical = 32.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dpx),
    ) {
        Box(
            modifier = Modifier.size(88.dpx).background(colors.accent, KioskTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.Alert, tint = colors.surface, modifier = Modifier.size(48.dpx))
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
            verticalArrangement = Arrangement.spacedBy(6.dpx),
        ) {
            KioskText(
                text = stringResource(R.string.item_no_longer_available),
                style = KioskTheme.typography.section.copy(fontSize = 38.spx, letterSpacing = PRICE_LETTER_EM.em),
            )
            KioskText(
                text = stringResource(R.string.item_sold_out_body),
                style = KioskTheme.typography.body.copy(fontSize = 28.spx, lineHeight = SOLD_OUT_BODY_LINE_EM.em),
                color = colors.errorText,
            )
        }
        Box(
            modifier =
                Modifier
                    .pressScale(scale)
                    .height(96.dpx)
                    .clip(KioskTheme.shapes.large)
                    .background(colors.ink)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onBack,
                    ).testTag(ITEM_SOLD_OUT_BACK_TAG)
                    .padding(horizontal = 32.dpx),
            contentAlignment = Alignment.Center,
        ) {
            KioskText(
                text = stringResource(R.string.item_back_to_menu),
                style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
                color = colors.bg,
                maxLines = 1,
            )
        }
    }
}

private const val SOLD_OUT_BODY_LINE_EM = 1.35f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductInfo(product: ItemProductUi) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val chips = animateEntrance(INFO_UP_MILLIS, CHIPS_DELAY_MILLIS)
    val title = animateEntrance(TITLE_RISE_MILLIS, TITLE_RISE_DELAY_MILLIS)
    val price = animateEntrance(INFO_UP_MILLIS, PRICE_DELAY_MILLIS)
    val description = animateEntrance(INFO_UP_MILLIS, DESCRIPTION_DELAY_MILLIS)
    Column(verticalArrangement = Arrangement.spacedBy(16.dpx)) {
        val chipTexts =
            listOfNotNull(
                product.badge?.let { it to true },
                product.kcal?.let { pluralStringResource(R.plurals.item_kcal, it, it) to false },
                product.tags?.let { it to false },
            )
        if (chipTexts.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fadeUp(chips, Common.UP_DISTANCE_PX.dpx),
                horizontalArrangement = Arrangement.spacedBy(12.dpx),
                verticalArrangement = Arrangement.spacedBy(12.dpx),
            ) {
                chipTexts.forEach { (text, accent) -> InfoChip(text = text, accent = accent) }
            }
        }
        Box(
            modifier =
                Modifier
                    .padding(top = 8.dpx)
                    .clipToBounds()
                    .padding(bottom = 6.dpx),
        ) {
            KioskText(
                text = product.name,
                style = typography.title.copy(letterSpacing = TITLE_LETTER_EM.em, lineHeight = 1.em),
                modifier = Modifier.riseIn(title),
            )
        }
        KioskText(
            text = product.price,
            style = typography.title.copy(fontSize = 48.spx, letterSpacing = PRICE_LETTER_EM.em),
            modifier = Modifier.fadeUp(price, Common.UP_DISTANCE_PX.dpx),
        )
        product.description?.let {
            KioskText(
                text = it,
                style = typography.body,
                color = colors.muted,
                modifier =
                    Modifier
                        .padding(top = 8.dpx)
                        .widthIn(max = 860.dpx)
                        .fadeUp(description, Common.UP_DISTANCE_PX.dpx),
            )
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    accent: Boolean,
) {
    val colors = KioskTheme.colors
    KioskText(
        text = text,
        style =
            KioskTheme.typography.meta.copy(
                fontSize = 24.spx,
                fontWeight = if (accent) FontWeight.W700 else FontWeight.W600,
            ),
        color = if (accent) colors.accent else colors.muted,
        modifier =
            Modifier
                .background(colors.surfaceAlt, KioskTheme.shapes.pill)
                .padding(horizontal = 20.dpx, vertical = 10.dpx),
    )
}

@Composable
private fun BottomFade(modifier: Modifier = Modifier) {
    val bg = KioskTheme.colors.bg
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(FADE_HEIGHT_PX.dpx)
                .background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), FADE_SOLID_AT to bg, 1f to bg)),
    )
}

@Composable
private fun Dock(
    state: ItemDetailUiState,
    onEvent: (ItemDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.dock
    val enter = animateEntrance(DOCK_IN_MILLIS, DOCK_IN_DELAY_MILLIS)
    Box(
        modifier =
            modifier
                .padding(start = 72.dpx, end = 72.dpx, bottom = 48.dpx)
                .graphicsLayer { translationY = size.height * DOCK_IN_FRACTION * (1f - enter.value) }
                .fillMaxWidth()
                .height(168.dpx)
                .kioskShadow(shape, KioskTheme.elevation.floating)
                .background(colors.ink, shape)
                .padding(horizontal = 16.dpx),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (state.dock) {
            ItemDock.EDITING -> EditingDock(state, onEvent)
            ItemDock.ADDED -> AddedDock(onEvent)
        }
    }
}

@Composable
private fun EditingDock(
    state: ItemDetailUiState,
    onEvent: (ItemDetailEvent) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dpx)) {
        QuantityStepper(state, onEvent)
        AddButton(state, onAdd = { onEvent(ItemDetailEvent.AddToOrder) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuantityStepper(
    state: ItemDetailUiState,
    onEvent: (ItemDetailEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val description = stringResource(R.string.item_quantity)
    Row(
        modifier =
            Modifier
                .height(136.dpx)
                .background(colors.dockTileBorder, RoundedCornerShape(36.dpx))
                .padding(12.dpx)
                .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dpx),
    ) {
        val decreaseBg by animateColorAsState(
            targetValue = if (state.canDecrease) colors.bg else dockControlColor(),
            animationSpec = motionSpec(tween(STEPPER_COLOR_MILLIS)),
            label = "decrease-bg",
        )
        StepperButton(
            icon = KioskIcons.Minus,
            background = decreaseBg,
            tint = if (state.canDecrease) colors.ink else colors.dockDisabledText,
            description = stringResource(R.string.item_decrease_quantity),
            enabled = state.canDecrease,
            onClick = { onEvent(ItemDetailEvent.DecreaseQuantity) },
            modifier = Modifier.testTag(ITEM_DECREASE_TAG),
        )
        Box(
            modifier =
                Modifier
                    .size(width = 72.dpx, height = 80.dpx)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(ITEM_QUANTITY_TAG),
            contentAlignment = Alignment.Center,
        ) {
            RollingText(
                text = state.quantity.toString(),
                style = KioskTheme.typography.section.copy(fontWeight = FontWeight.W700),
                durationMillis = Item.QTY_ROLL_MILLIS,
                color = colors.bg,
                direction = state.quantityRoll,
            )
        }
        StepperButton(
            icon = KioskIcons.Plus,
            background = colors.bg,
            tint = colors.ink,
            description = stringResource(R.string.item_increase_quantity),
            enabled = true,
            onClick = { onEvent(ItemDetailEvent.IncreaseQuantity) },
            modifier =
                Modifier
                    .graphicsLayer { alpha = if (state.atMaxQuantity) AT_MAX_ALPHA else 1f }
                    .testTag(ITEM_INCREASE_TAG),
        )
    }
}

private const val STEPPER_COLOR_MILLIS = 300

@Suppress("LongParameterList")
@Composable
private fun StepperButton(
    icon: ImageVector,
    background: Color,
    tint: Color,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            modifier
                .pressScale(scale)
                .size(112.dpx)
                .clip(KioskTheme.shapes.medium)
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        KioskIcon(icon = icon, tint = tint, modifier = Modifier.size(40.dpx))
    }
}

@Composable
private fun AddButton(
    state: ItemDetailUiState,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val enabled = state.cta == ItemCta.Add
    val background by animateColorAsState(
        targetValue = if (enabled) colors.accent else dockControlColor(),
        animationSpec = motionSpec(tween(STEPPER_COLOR_MILLIS)),
        label = "add-bg",
    )
    val foreground = if (enabled) colors.surface else colors.closedDot
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val label =
        when (val cta = state.cta) {
            ItemCta.Add -> {
                stringResource(R.string.item_add_to_order)
            }

            ItemCta.Unavailable -> {
                stringResource(R.string.item_no_longer_available)
            }

            is ItemCta.ChooseFirst -> {
                if (cta.prompt) {
                    stringResource(R.string.item_cta_to_continue, cta.groupName)
                } else {
                    stringResource(R.string.item_cta_choose_to_continue, cta.groupName)
                }
            }
        }
    Row(
        modifier =
            modifier
                .pressScale(scale)
                .height(136.dpx)
                .clip(RoundedCornerShape(36.dpx))
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onAdd,
                ).testTag(ITEM_ADD_TAG)
                .padding(horizontal = 36.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dpx),
    ) {
        KioskText(
            text = label,
            style = KioskTheme.typography.button,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (enabled) {
            RollingText(
                text = state.total,
                style = KioskTheme.typography.body.copy(fontSize = 32.spx, fontWeight = FontWeight.W600),
                durationMillis = Item.PRICE_ROLL_MILLIS,
                color = foreground,
            )
        }
    }
}

@Composable
private fun AddedDock(onEvent: (ItemDetailEvent) -> Unit) {
    val swap = animateEntrance(Item.SWAP_IN_MILLIS)
    Row(
        modifier = Modifier.fadeUp(swap, Common.UP_DISTANCE_PX.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dpx),
    ) {
        AddedCheck(Modifier.padding(start = 16.dpx))
        DockButton(
            text = stringResource(R.string.item_keep_ordering),
            filled = false,
            onClick = { onEvent(ItemDetailEvent.BackToMenu) },
            modifier = Modifier.weight(1f).testTag(ITEM_KEEP_ORDERING_TAG),
        )
        DockButton(
            text = stringResource(R.string.item_view_order),
            filled = true,
            onClick = { onEvent(ItemDetailEvent.ViewOrder) },
            modifier = Modifier.weight(VIEW_ORDER_WEIGHT).testTag(ITEM_VIEW_ORDER_TAG),
        )
    }
}

@Composable
private fun AddedCheck(modifier: Modifier = Modifier) {
    val colors = KioskTheme.colors
    val ring = animateOnEnter(tween(RING_OUT_MILLIS, easing = KioskEasing.Press))
    val check = animateOnEnter(tween(CHECK_IN_MILLIS, easing = CheckPop))
    val description = stringResource(R.string.item_added)
    Box(
        modifier =
            modifier
                .size(104.dpx)
                .background(colors.accent, CircleShape)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (!LocalReduceMotion.current) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = 1f + RING_OUT_SCALE * ring.value
                            scaleX = scale
                            scaleY = scale
                            alpha = RING_START_ALPHA * (1f - ring.value)
                        }.border(3.dpx, colors.accent, CircleShape),
            )
        }
        KioskIcon(
            icon = KioskIcons.Check,
            tint = colors.surface,
            modifier =
                Modifier.size(52.dpx).graphicsLayer {
                    val p = check.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = CHECK_START_SCALE + (1f - CHECK_START_SCALE) * p
                    scaleY = scaleX
                    rotationZ = CHECK_START_ROTATION * (1f - p)
                },
        )
    }
}

private const val CHECK_START_SCALE = 0.4f
private const val CHECK_START_ROTATION = -45f

@Composable
private fun DockButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = RoundedCornerShape(36.dpx)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            modifier
                .pressScale(scale)
                .height(136.dpx)
                .clip(shape)
                .then(if (filled) Modifier.background(colors.bg) else Modifier.border(2.dpx, dockControlColor(), shape))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = text,
            style =
                if (filled) {
                    KioskTheme.typography.button
                } else {
                    KioskTheme.typography.body.copy(fontSize = 32.spx, fontWeight = FontWeight.W600)
                },
            color = if (filled) colors.ink else colors.bg,
            maxLines = 1,
        )
    }
}

/** The thumbnail that flies to the "My order" pill after each add (`fly 820ms`, [KioskEasing.Fly]). */
@Composable
private fun FlyingThumbnail(
    bumps: Int,
    imageUrl: String?,
    tone: String?,
) {
    if (bumps == 0 || LocalReduceMotion.current) return
    val progress = remember(bumps) { Animatable(0f) }
    LaunchedEffect(bumps) {
        progress.animateTo(1f, tween(Item.FLY_MILLIS, easing = KioskEasing.Fly))
    }
    if (progress.value >= 1f) return
    val dx = FLY_DX_PX.dpx
    val dy = FLY_DY_PX.dpx
    MenuPhoto(
        imageUrl = imageUrl,
        tone = tone,
        contentDescription = null,
        shape = CircleShape,
        modifier =
            Modifier
                .offset(x = FLY_LEFT_PX.dpx, y = FLY_TOP_PX.dpx)
                .graphicsLayer {
                    val p = progress.value
                    translationX = dx.toPx() * p
                    translationY = dy.toPx() * p
                    val scale = 1f - (1f - FLY_END_SCALE) * p
                    scaleX = scale
                    scaleY = scale
                    alpha = if (p < FLY_FADE_START) 1f else (1f - p) / (1f - FLY_FADE_START)
                }.size(FLY_SIZE_PX.dpx)
                .kioskShadow(CircleShape, KioskTheme.elevation.raised),
    )
}

@Composable
private fun ItemDetailPreview(state: ItemDetailUiState) {
    KioskTheme(reduceMotion = true) {
        DesignFrame { ItemDetailScreen(state = state, onEvent = {}) }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun ItemDetailDefaultPreview() = ItemDetailPreview(ItemDetailSamples.Default)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun ItemDetailRequiredMissingPreview() = ItemDetailPreview(ItemDetailSamples.RequiredMissing)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun ItemDetailSoldOutPreview() = ItemDetailPreview(ItemDetailSamples.SoldOut)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun ItemDetailAddedPreview() = ItemDetailPreview(ItemDetailSamples.Default.copy(dock = ItemDock.ADDED))
