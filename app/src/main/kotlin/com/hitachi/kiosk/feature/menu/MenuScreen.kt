package com.hitachi.kiosk.feature.menu

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.BrandLockup
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.KioskToast
import com.hitachi.kiosk.core.designsystem.component.OfflineBanner
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Common
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
import com.hitachi.kiosk.core.domain.time.DayPart
import com.hitachi.kiosk.ui.MerchantLogoImage

/** Test tag of the Menu root; the edge-walk test finds screens by it. */
const val MENU_SCREEN_TAG = "screen:SCR-06"

/** Test tag of the header "Dine in · Change" pill. */
const val MENU_CHANGE_TAG = "menu-change"

/** Test tag of the header "Cancel" pill. */
const val MENU_CANCEL_TAG = "menu-cancel"

/** Test tag of the "Plan by budget" row. */
const val MENU_BUDGET_PLAN_TAG = "menu-budget-plan"

/** Test tag of the "Plan by budget" pill in the category bar. */
const val MENU_BUDGET_PILL_TAG = "menu-budget-pill"

/** Test tag of "See popular items" in the empty category state. */
const val MENU_SEE_POPULAR_TAG = "menu-see-popular"

/** Test tag of the "Popular" pill. */
const val MENU_POPULAR_PILL_TAG = "menu-pill:popular"

/** Test tag of the "Meals ↗" pill. */
const val MENU_MEALS_PILL_TAG = "menu-pill:meals"

/** Test tag of a category pill. */
fun menuCategoryPillTag(categoryId: String): String = "menu-pill:$categoryId"

/** Top of the category bar, in design px. */
private const val PILLS_TOP_PX = 400

/** Top of the scrolling content, in design px. */
private const val CONTENT_TOP_PX = 528

/** Bottom padding of the content, so the last row clears the dock and its fade (`padding-bottom: 360px`). */
private const val CONTENT_BOTTOM_PX = 360

/** Height of the linen fade above the dock, in design px, and where it turns 92 % and fully opaque. */
private const val FADE_HEIGHT_PX = 400
private const val FADE_MOSTLY_STOP = 0.48f
private const val FADE_MOSTLY_ALPHA = 0.92f
private const val FADE_SOLID_STOP = 0.62f

/** The banner and the toast sit 240 dpx above the bottom, over the dock's fade. */
private const val FLOATING_BOTTOM_PX = 240

/** Design timings of the Menu entrance that PRD §11.4 does not name (`Menu.dc.html`). */
private const val HEADER_FADE_MILLIS = 500
private const val GREETING_UP_MILLIS = 700
private const val TITLE_RISE_MILLIS = 900
private const val TITLE_DELAY_MILLIS = 80
private const val PILLS_DELAY_MILLIS = 160
private const val PILL_COLOR_MILLIS = 420
private const val HEADING_IN_MILLIS = 620
private const val EMPTY_UP_MILLIS = 700
private const val EMPTY_UP_DELAY_MILLIS = 160

/** The section heading rises from 60 % of its height (`headA`). */
private const val HEADING_RISE_FRACTION = 0.6f

/** Pills slide in from 32 dpx to the right (`pillIn`). */
private const val PILL_SLIDE_PX = 32

/** Letter spacings of the design in px, as em of their font size. */
private const val SECTION_TRACKING_EM = -0.9f / 44
private const val EMPTY_TITLE_TRACKING_EM = -0.02f
private const val EMPTY_TITLE_LINE_HEIGHT_EM = 1.1f
private const val SECTION_LINE_HEIGHT_EM = 1.15f

/**
 * SCR-06 Menu (`Menu.dc.html`, `S_Menu_emptyCategory`, `S_Menu_offline`; FR-009, DEV-027, DEV-028). Draws [state]
 * exactly: header, greeting, category pills, the Popular "Plan by budget" row, the 2-column grid (or the empty
 * category state), the linen fade, the offline banner or the "Maximum 20 per item" toast, and the cart dock.
 */
@Composable
fun MenuScreen(
    state: MenuUiState,
    onEvent: (MenuEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchors = remember { FlyAnchors() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .onPlaced { anchors.root = it }
                .testTag(MENU_SCREEN_TAG),
    ) {
        MenuHeader(
            brandName = state.brandName,
            orderType = state.orderType,
            onChange = { onEvent(MenuEvent.ChangeOrderType) },
            onCancel = { onEvent(MenuEvent.Cancel) },
        )
        MenuGreeting(dayPart = state.dayPart, modifier = Modifier.padding(start = 72.dpx, end = 72.dpx, top = 208.dpx))
        Row(
            modifier = Modifier.padding(top = PILLS_TOP_PX.dpx).fillMaxWidth().height(112.dpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.showPlanPill) {
                BudgetPlanPill(
                    onClick = { onEvent(MenuEvent.OpenBudgetPlan) },
                    modifier = Modifier.padding(start = 72.dpx),
                )
                Box(
                    Modifier
                        .padding(
                            start = 24.dpx,
                            end = 8.dpx,
                        ).size(2.dpx, 48.dpx)
                        .background(KioskTheme.colors.line),
                )
            }
            CategoryPills(
                pills = state.pills,
                onEvent = onEvent,
                startPadding = if (state.showPlanPill) 16.dpx else 72.dpx,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.loaded) {
            MenuContent(
                state = state,
                anchors = anchors,
                onEvent = onEvent,
                modifier = Modifier.padding(top = CONTENT_TOP_PX.dpx),
            )
        }
        DockFade(Modifier.align(Alignment.BottomCenter))
        if (state.offline) {
            OfflineBanner(
                text = stringResource(R.string.offline_banner),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 72.dpx, end = 72.dpx, bottom = FLOATING_BOTTOM_PX.dpx),
            )
        } else if (state.showMaxToast) {
            KioskToast(
                text = stringResource(R.string.max_per_item_toast),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = FLOATING_BOTTOM_PX.dpx),
            )
        }
        CartDock(
            dock = state.dock,
            addSerial = state.addSerial,
            viewOrderEnabled = state.viewOrderEnabled,
            onViewOrder = { onEvent(MenuEvent.ViewOrder) },
            onViewOrderPlaced = { anchors.viewOrder = it },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 72.dpx, end = 72.dpx, bottom = 48.dpx),
        )
        FlyToCart(state = state, anchors = anchors)
    }
}

@Composable
private fun MenuHeader(
    brandName: String,
    orderType: OrderType,
    onChange: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = KioskTheme.colors
    val fade = animateEntrance(HEADER_FADE_MILLIS)
    val pillText = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600)
    val orderLabel =
        stringResource(
            if (orderType ==
                OrderType.TAKEAWAY
            ) {
                R.string.menu_order_type_takeaway
            } else {
                R.string.menu_order_type_dine_in
            },
        )
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
        BrandLockup(name = brandName, logo = { MerchantLogoImage() })
        Row(horizontalArrangement = Arrangement.spacedBy(16.dpx)) {
            val changeDescription = stringResource(R.string.menu_change_description, orderLabel)
            OutlinePill(
                description = changeDescription,
                tag = MENU_CHANGE_TAG,
                onClick = onChange,
                padding = PaddingValues(start = 34.dpx, end = 14.dpx),
            ) {
                KioskText(text = orderLabel, style = pillText, color = colors.muted)
                Box(
                    modifier =
                        Modifier
                            .height(72.dpx)
                            .background(colors.surfaceAlt, KioskTheme.shapes.pill)
                            .padding(horizontal = 26.dpx),
                    contentAlignment = Alignment.Center,
                ) {
                    KioskText(
                        text = stringResource(R.string.menu_change),
                        style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W600),
                    )
                }
            }
            val cancel = stringResource(R.string.menu_cancel)
            OutlinePill(
                description = cancel,
                tag = MENU_CANCEL_TAG,
                onClick = onCancel,
                padding = PaddingValues(horizontal = 36.dpx),
            ) {
                KioskText(text = cancel, style = pillText)
            }
        }
    }
}

/** A 96 dpx header pill with a 2 dpx `line` outline. [padding] includes the outline. */
@Composable
private fun OutlinePill(
    description: String,
    tag: String,
    onClick: () -> Unit,
    padding: PaddingValues,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            Modifier
                .height(96.dpx)
                .pressScale(scale)
                .border(2.dpx, KioskTheme.colors.line, KioskTheme.shapes.pill)
                .kioskButton(description, tag, interactionSource, onClick = onClick)
                .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dpx),
    ) {
        content()
    }
}

@Composable
private fun MenuGreeting(
    dayPart: DayPart,
    modifier: Modifier = Modifier,
) {
    val greeting = animateEntrance(GREETING_UP_MILLIS, TITLE_DELAY_MILLIS)
    val title = animateEntrance(TITLE_RISE_MILLIS, TITLE_DELAY_MILLIS)
    val greetingText =
        stringResource(
            when (dayPart) {
                DayPart.MORNING -> R.string.menu_greeting_morning
                DayPart.AFTERNOON -> R.string.menu_greeting_afternoon
                DayPart.EVENING -> R.string.menu_greeting_evening
            },
        )
    Column(modifier = modifier) {
        KioskText(
            text = greetingText,
            style = KioskTheme.typography.meta,
            color = KioskTheme.colors.muted,
            modifier =
                Modifier
                    .padding(bottom = 12.dpx)
                    .fadeUp(greeting, Common.UP_DISTANCE_PX.dpx),
        )
        Box(
            modifier =
                Modifier
                    .clipToBounds()
                    .padding(bottom = 6.dpx),
        ) {
            KioskText(
                text = stringResource(R.string.menu_title),
                style = KioskTheme.typography.title.copy(lineHeight = 1.em),
                modifier = Modifier.riseIn(title),
                cssLineBox = true,
            )
        }
    }
}

@Composable
private fun CategoryPills(
    pills: List<MenuPillUi>,
    onEvent: (MenuEvent) -> Unit,
    startPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.menu_categories_description)
    // The active pill starts in view, as the design scrolls it to the left edge on mount.
    val activeIndex = pills.indexOfFirst { it.isActive }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeIndex)
    LazyRow(
        state = listState,
        modifier =
            modifier
                .fillMaxWidth()
                .height(112.dpx)
                .semantics { contentDescription = description },
        contentPadding = PaddingValues(start = startPadding, end = 72.dpx, top = 8.dpx, bottom = 8.dpx),
        horizontalArrangement = Arrangement.spacedBy(12.dpx),
    ) {
        itemsIndexed(pills, key = { _, pill -> pill.key }) { index, pill ->
            val entrance =
                animateEntrance(
                    KioskMotion.Menu.PILLS_IN_MILLIS,
                    PILLS_DELAY_MILLIS + index * KioskMotion.Menu.PILLS_STAGGER_MILLIS,
                )
            val slide = PILL_SLIDE_PX.dpx
            val enter =
                Modifier.graphicsLayer {
                    alpha = entrance.value
                    translationX = slide.toPx() * (1f - entrance.value)
                }
            when (pill) {
                is MenuPillUi.Popular -> {
                    CategoryPill(
                        name = stringResource(R.string.menu_category_popular),
                        active = pill.active,
                        tag = MENU_POPULAR_PILL_TAG,
                        onClick = { onEvent(MenuEvent.Select(MenuSelection.Popular)) },
                        modifier = enter,
                    )
                }

                is MenuPillUi.Category -> {
                    CategoryPill(
                        name = pill.name,
                        active = pill.active,
                        tag = menuCategoryPillTag(pill.id),
                        onClick = { onEvent(MenuEvent.Select(MenuSelection.Category(pill.id))) },
                        modifier = enter,
                    )
                }

                is MenuPillUi.Meals -> {
                    MealsPill(name = pill.name, onClick = { onEvent(MenuEvent.OpenCombos) }, modifier = enter)
                }
            }
        }
    }
}

private val MenuPillUi.key: String
    get() =
        when (this) {
            is MenuPillUi.Popular -> MENU_POPULAR_PILL_TAG
            is MenuPillUi.Category -> menuCategoryPillTag(id)
            is MenuPillUi.Meals -> MENU_MEALS_PILL_TAG
        }

private val MenuPillUi.isActive: Boolean
    get() =
        when (this) {
            is MenuPillUi.Popular -> active
            is MenuPillUi.Category -> active
            is MenuPillUi.Meals -> false
        }

@Composable
private fun CategoryPill(
    name: String,
    active: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val colorSpec =
        motionSpec(tween<androidx.compose.ui.graphics.Color>(PILL_COLOR_MILLIS, easing = KioskEasing.Emphasized))
    val background by animateColorAsState(if (active) colors.ink else colors.surfaceAlt, colorSpec, label = "pill-bg")
    val foreground by animateColorAsState(if (active) colors.bg else colors.ink, colorSpec, label = "pill-fg")
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            modifier
                .height(96.dpx)
                .pressScale(scale)
                .background(background, KioskTheme.shapes.pill)
                .kioskButton(name, tag, interactionSource, role = Role.Tab, selected = active, onClick = onClick)
                .padding(horizontal = 40.dpx),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(text = name, style = pillTextStyle(), color = foreground, maxLines = 1)
    }
}

/** The "Meals ↗" link pill to the Combo screen (FR-009, TC-035). */
@Composable
private fun MealsPill(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            modifier
                .height(96.dpx)
                .pressScale(scale)
                .background(colors.surfaceAlt, KioskTheme.shapes.pill)
                .kioskButton(name, MENU_MEALS_PILL_TAG, interactionSource, onClick = onClick)
                .padding(start = 40.dpx, end = 32.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dpx),
    ) {
        KioskText(text = name, style = pillTextStyle(), maxLines = 1)
        KioskIcon(icon = KioskIcons.ArrowUpRight, tint = colors.muted, modifier = Modifier.size(28.dpx))
    }
}

@Composable
private fun pillTextStyle() =
    KioskTheme.typography.button.copy(fontSize = 30.spx, fontWeight = FontWeight.W600, letterSpacing = 0.em)

/**
 * The scrolling part below the pills. Keyed by the selection, so a category switch replays the heading and card
 * entrances, and scrolled back to the top (FR-009).
 */
@Composable
private fun MenuContent(
    state: MenuUiState,
    anchors: FlyAnchors,
    onEvent: (MenuEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(state.selection) { scroll.scrollTo(0) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 72.dpx, end = 72.dpx, top = 24.dpx, bottom = CONTENT_BOTTOM_PX.dpx),
    ) {
        key(state.selection) {
            if (state.showBudgetPlan) {
                BudgetPlanRow(
                    onClick = { onEvent(MenuEvent.OpenBudgetPlan) },
                    modifier = Modifier.padding(bottom = 56.dpx),
                )
            }
            SectionHeading(
                title = state.sectionTitle ?: stringResource(R.string.menu_popular_heading),
                count = state.products.size,
            )
            if (state.products.isEmpty()) {
                EmptyCategory(onSeePopular = { onEvent(MenuEvent.Select(MenuSelection.Popular)) })
            } else {
                ProductGrid(state = state, anchors = anchors, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    count: Int,
) {
    val entrance = animateEntrance(HEADING_IN_MILLIS)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dpx)
                .clipToBounds(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        KioskText(
            text = title,
            style =
                KioskTheme.typography.section.copy(
                    letterSpacing = SECTION_TRACKING_EM.em,
                    lineHeight = SECTION_LINE_HEIGHT_EM.em,
                ),
            modifier =
                Modifier
                    .alignByBaseline()
                    .weight(1f, fill = false)
                    .graphicsLayer {
                        alpha = entrance.value
                        translationY = size.height * HEADING_RISE_FRACTION * (1f - entrance.value)
                    },
        )
        KioskText(
            text = pluralStringResource(R.plurals.menu_item_count, count, count),
            style = KioskTheme.typography.meta,
            color = KioskTheme.colors.muted,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** Two columns; the cards of a row share its height, like the design's CSS grid. */
@Composable
private fun ProductGrid(
    state: MenuUiState,
    anchors: FlyAnchors,
    onEvent: (MenuEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dpx)) {
        state.products.chunked(2).forEachIndexed { row, pair ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(24.dpx),
            ) {
                pair.forEachIndexed { column, product ->
                    key(product.id) {
                        ProductCard(
                            product = product,
                            index = row * 2 + column,
                            addEnabled = state.addEnabled,
                            onOpen = { onEvent(MenuEvent.OpenProduct(product.id)) },
                            onAdd = { onEvent(MenuEvent.QuickAdd(product.id)) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .onPlaced { anchors.cards[product.id] = it },
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** "Nothing here right now" when every item of the category is sold out (`S_Menu_emptyCategory`, A-33). */
@Composable
private fun EmptyCategory(onSeePopular: () -> Unit) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val entrance = animateEntrance(EMPTY_UP_MILLIS, EMPTY_UP_DELAY_MILLIS)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fadeUp(entrance, Common.UP_DISTANCE_PX.dpx)
                .background(colors.surface, shape)
                .border(2.dpx, colors.cardBorder, shape)
                .padding(horizontal = 50.dpx, vertical = 98.dpx),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        Box(
            modifier =
                Modifier
                    .size(144.dpx)
                    .background(
                        colors.surfaceAlt,
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(44.dpx),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.Cloche, tint = colors.muted, modifier = Modifier.size(72.dpx))
        }
        KioskText(
            text = stringResource(R.string.menu_empty_title),
            style =
                KioskTheme.typography.title.copy(
                    fontSize = 48.spx,
                    letterSpacing = EMPTY_TITLE_TRACKING_EM.em,
                    lineHeight = EMPTY_TITLE_LINE_HEIGHT_EM.em,
                ),
            textAlign = TextAlign.Center,
        )
        KioskText(
            text = stringResource(R.string.menu_empty_body),
            style = KioskTheme.typography.body,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 640.dpx),
        )
        val label = stringResource(R.string.menu_empty_action)
        val interactionSource = remember { MutableInteractionSource() }
        val scale = animatePressScale(interactionSource)
        Box(
            modifier =
                Modifier
                    .padding(top = 16.dpx)
                    .height(104.dpx)
                    .pressScale(scale)
                    .background(colors.ink, KioskTheme.shapes.large)
                    .kioskButton(label, MENU_SEE_POPULAR_TAG, interactionSource, onClick = onSeePopular)
                    .padding(horizontal = 48.dpx),
            contentAlignment = Alignment.Center,
        ) {
            KioskText(
                text = label,
                style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W700),
                color = colors.bg,
            )
        }
    }
}

/** The linen fade above the dock, so no card is cut hard against it (FR-009). */
@Composable
private fun DockFade(modifier: Modifier = Modifier) {
    val bg = KioskTheme.colors.bg
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(FADE_HEIGHT_PX.dpx)
                .background(
                    Brush.verticalGradient(
                        0f to bg.copy(alpha = 0f),
                        FADE_MOSTLY_STOP to bg.copy(alpha = FADE_MOSTLY_ALPHA),
                        FADE_SOLID_STOP to bg,
                        1f to bg,
                    ),
                ),
    )
}

@Composable
private fun MenuPreview(state: MenuUiState) {
    KioskTheme(reduceMotion = true) {
        DesignFrame { MenuScreen(state = state, onEvent = {}) }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun MenuPopularPreview() = MenuPreview(MenuSamples.Popular)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun MenuOfflinePreview() = MenuPreview(MenuSamples.Popular.copy(offline = true))

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun MenuEmptyCategoryPreview() = MenuPreview(MenuSamples.EmptyDesserts)
