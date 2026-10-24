package com.hitachi.kiosk.feature.combo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.BackPill
import com.hitachi.kiosk.core.designsystem.component.CartPill
import com.hitachi.kiosk.core.designsystem.component.HeaderPillStyle
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.RollingText
import com.hitachi.kiosk.core.designsystem.component.SelectionDot
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Combo
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Common
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
import com.hitachi.kiosk.core.domain.cart.ComboMissing
import com.hitachi.kiosk.navigation.screenTag
import com.hitachi.kiosk.ui.MenuPhoto

/** Test tag of the "Menu" back pill. */
const val COMBO_BACK_TAG = "combo-back"

/** Test tag of the "My order" pill. */
const val COMBO_CART_TAG = "combo-cart"

/** Test tag of "Add combo". */
const val COMBO_ADD_TAG = "combo-add"

/** Test tag of "More sides (n)" / "Show fewer". */
const val COMBO_MORE_SIDES_TAG = "combo-more-sides"

/** Test tag of "More drinks (n)" / "Show fewer". */
const val COMBO_MORE_DRINKS_TAG = "combo-more-drinks"

/** Test tag of the Regular size segment. */
const val COMBO_REGULAR_TAG = "combo-size-regular"

/** Test tag of the Large size segment. */
const val COMBO_LARGE_TAG = "combo-size-large"

/** Test tag of a combo card. */
fun comboCardTag(id: String): String = "combo:$id"

/** Test tag of a side or drink card. */
fun comboSlotTag(id: String): String = "combo-slot:$id"

/** The header fades in over 500 ms; the subtitle fades up 700 @80; the title rises 900 @80 (`Combo.dc.html`). */
private const val HEADER_FADE_MILLIS = 500
private const val SUBTITLE_UP_MILLIS = 700
private const val TITLE_RISE_MILLIS = 900
private const val TITLE_DELAY_MILLIS = 80

/** Combo cards and the sections fade up over 700 ms; Side @480, Drink @540, Size @560. */
private const val LIST_UP_MILLIS = 700
private const val SIDE_DELAY_MILLIS = 480
private const val DRINK_DELAY_MILLIS = 540
private const val SIZE_DELAY_MILLIS = 560

/** Side and drink cards fade up over 500 ms when they appear. */
private const val SLOT_UP_MILLIS = 500

/** The dock rises from 140 % of its height, 800 ms @300 (PRD §7.1). */
private const val DOCK_IN_MILLIS = 800
private const val DOCK_IN_DELAY_MILLIS = 300
private const val DOCK_IN_FRACTION = 1.4f

/** The button colors change over 360 ms; rings and labels over 320 / 300 ms. */
private const val BUTTON_COLOR_MILLIS = 360
private const val RING_MILLIS = 320
private const val LABEL_COLOR_MILLIS = 300

/** The cart badge bumps to 1.2 over 460 ms (`bumpA`, `Combo.dc.html`). */
private const val CART_BUMP_SCALE = 1.2f
private const val CART_BUMP_MILLIS = 460

/** Sold-out side and drink cards are drawn at 45 % (`S_Combo_soldOut`). */
private const val SOLD_OUT_ALPHA = 0.45f

/** The fade above the dock: transparent to `bg` at 55 %. */
private const val FADE_HEIGHT_PX = 320
private const val FADE_SOLID_AT = 0.55f

private const val HALF_TURN = 180f
private const val SECTION_TITLE_EM = -0.02f
private const val TITLE_EM = -0.03f
private const val TOTAL_EM = -0.018f
private const val NAME_LINE_EM = 1.15f
private const val START_SCALE = 0.4f
private const val START_ROTATION = -45f

/**
 * SCR-07 Combo (`Combo.dc.html`, `S_Combo_*`, FR-011, DEV-030). Draws [state] exactly: header pills, the title, the
 * combo radio list, the Side, Drink and Size choices, and the dark dock.
 */
@Composable
fun ComboScreen(
    state: ComboUiState,
    onEvent: (ComboEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(screenTag("SCR-07")),
    ) {
        if (!state.loaded) return@Box
        Header(state = state, onEvent = onEvent)
        TitleBlock(Modifier.padding(start = 72.dpx, end = 72.dpx, top = 208.dpx))
        Body(state = state, onEvent = onEvent, modifier = Modifier.padding(top = 376.dpx))
        BottomFade(Modifier.align(Alignment.BottomCenter))
        if (state.summary != null) {
            Dock(state = state, onEvent = onEvent, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Header(
    state: ComboUiState,
    onEvent: (ComboEvent) -> Unit,
) {
    val fade = animateEntrance(HEADER_FADE_MILLIS)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 72.dpx, end = 72.dpx, top = 48.dpx)
                .fadeIn(fade),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackPill(
            text = stringResource(R.string.customize_menu_pill),
            onClick = { onEvent(ComboEvent.BackToMenu) },
            style = HeaderPillStyle.Flat,
            modifier = Modifier.testTag(COMBO_BACK_TAG),
        )
        CartPill(
            text = stringResource(R.string.customize_my_order_pill),
            count = state.cartCount,
            contentDescription =
                pluralStringResource(R.plurals.customize_my_order_description, state.cartCount, state.cartCount),
            onClick = { onEvent(ComboEvent.ViewOrder) },
            style = HeaderPillStyle.Flat,
            bumps = state.cartBumps,
            bumpScale = CART_BUMP_SCALE,
            bumpMillis = CART_BUMP_MILLIS,
            modifier = Modifier.testTag(COMBO_CART_TAG),
        )
    }
}

@Composable
private fun TitleBlock(modifier: Modifier = Modifier) {
    val subtitle = animateEntrance(SUBTITLE_UP_MILLIS, TITLE_DELAY_MILLIS)
    val title = animateEntrance(TITLE_RISE_MILLIS, TITLE_DELAY_MILLIS)
    Column(modifier = modifier) {
        KioskText(
            text = stringResource(R.string.combo_subtitle),
            style = KioskTheme.typography.meta,
            color = KioskTheme.colors.muted,
            modifier = Modifier.padding(bottom = 12.dpx).fadeUp(subtitle, Common.UP_DISTANCE_PX.dpx),
        )
        Box(Modifier.clipToBounds().padding(bottom = 6.dpx)) {
            KioskText(
                text = stringResource(R.string.combo_title),
                style = KioskTheme.typography.title.copy(letterSpacing = TITLE_EM.em, lineHeight = 1.em),
                modifier = Modifier.riseIn(title),
                cssLineBox = true,
            )
        }
    }
}

@Composable
private fun Body(
    state: ComboUiState,
    onEvent: (ComboEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listDescription = stringResource(R.string.combo_title)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 72.dpx, end = 72.dpx, top = 24.dpx, bottom = 280.dpx),
    ) {
        Column(
            modifier = Modifier.selectableGroup().semantics { contentDescription = listDescription },
            verticalArrangement = Arrangement.spacedBy(16.dpx),
        ) {
            state.combos.forEachIndexed { index, combo ->
                val enter =
                    animateEntrance(
                        LIST_UP_MILLIS,
                        Combo.LIST_FADE_UP_DELAY_MILLIS + index * Combo.LIST_FADE_UP_STAGGER_MILLIS,
                    )
                ComboCard(
                    combo = combo,
                    onClick = { onEvent(ComboEvent.SelectCombo(combo.id)) },
                    modifier = Modifier.fadeUp(enter, Common.UP_DISTANCE_PX.dpx).testTag(comboCardTag(combo.id)),
                )
            }
        }
        if (state.summary == null) return@Column
        SlotSection(
            title = stringResource(R.string.combo_side),
            section = state.sides,
            moreLabel = stringResource(R.string.combo_more_sides, state.sides.hiddenCount),
            moreTag = COMBO_MORE_SIDES_TAG,
            delayMillis = SIDE_DELAY_MILLIS,
            onPick = { onEvent(ComboEvent.SelectSide(it)) },
            onToggleMore = { onEvent(ComboEvent.ToggleMoreSides) },
            modifier = Modifier.padding(top = 56.dpx),
        )
        SlotSection(
            title = stringResource(R.string.combo_drink),
            section = state.drinks,
            moreLabel = stringResource(R.string.combo_more_drinks, state.drinks.hiddenCount),
            moreTag = COMBO_MORE_DRINKS_TAG,
            delayMillis = DRINK_DELAY_MILLIS,
            onPick = { onEvent(ComboEvent.SelectDrink(it)) },
            onToggleMore = { onEvent(ComboEvent.ToggleMoreDrinks) },
            modifier = Modifier.padding(top = 56.dpx),
        )
        SizeSection(
            large = state.large,
            largeUpcharge = state.largeUpcharge,
            onSelect = { onEvent(ComboEvent.SelectLarge(it)) },
            modifier = Modifier.padding(top = 64.dpx),
        )
    }
}

@Composable
private fun ComboCard(
    combo: ComboCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource, pressedScale = KioskMotion.Press.CARD_SCALE)
    val ring by animateColorAsState(
        targetValue = if (combo.selected) colors.accent else colors.cardBorder,
        animationSpec = motionSpec(tween(RING_MILLIS, easing = KioskEasing.Emphasized)),
        label = "combo-ring",
    )
    val radio by animateColorAsState(
        targetValue = if (combo.selected) colors.accent else colors.radioOff,
        animationSpec = motionSpec(tween(LABEL_COLOR_MILLIS)),
        label = "combo-radio",
    )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .pressScale(scale)
                .heightIn(min = 160.dpx)
                .clip(shape)
                .background(colors.surface)
                .border(if (combo.selected) 3.dpx else 2.dpx, ring, shape)
                .selectable(
                    selected = combo.selected,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(start = 16.dpx, end = 32.dpx, top = 16.dpx, bottom = 16.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dpx),
    ) {
        MenuPhoto(
            imageUrl = combo.imageUrl,
            tone = combo.tone,
            contentDescription = null,
            modifier = Modifier.size(width = 176.dpx, height = 128.dpx),
            shape = KioskTheme.shapes.medium,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dpx)) {
            KioskText(text = combo.name, style = KioskTheme.typography.item)
            combo.includes?.let {
                KioskText(
                    text = it,
                    style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W400),
                    color = colors.muted,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dpx)) {
            KioskText(text = combo.price, style = KioskTheme.typography.button, maxLines = 1)
            combo.saving?.let {
                KioskText(
                    text = stringResource(R.string.combo_save, it),
                    style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W600),
                    color = colors.success,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier.size(52.dpx).border(3.dpx, radio, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (combo.selected) {
                val pop = animateOnEnter(tween(Combo.RADIO_DOT_MILLIS, easing = KioskEasing.Pop))
                Box(
                    Modifier
                        .size(26.dpx)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }.background(colors.accent, CircleShape),
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SlotSection(
    title: String,
    section: SlotSectionUi,
    moreLabel: String,
    moreTag: String,
    delayMillis: Int,
    onPick: (String) -> Unit,
    onToggleMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enter = animateEntrance(LIST_UP_MILLIS, delayMillis)
    Column(
        modifier =
            modifier
                .fadeUp(enter, Common.UP_DISTANCE_PX.dpx)
                .semantics { contentDescription = title },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KioskText(
                text = title,
                style = KioskTheme.typography.section.copy(letterSpacing = SECTION_TITLE_EM.em),
                modifier = Modifier.weight(1f),
            )
            if (section.missing) RequiredTag()
        }
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(24.dpx)) {
            section.visible.chunked(VISIBLE_SLOT_OPTIONS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dpx)) {
                    row.forEach { option ->
                        SlotCard(
                            option = option,
                            tone = section.tone,
                            onClick = { onPick(option.id) },
                            modifier = Modifier.weight(1f).testTag(comboSlotTag(option.id)),
                        )
                    }
                    repeat(VISIBLE_SLOT_OPTIONS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (section.hiddenCount > 0) {
            MoreButton(
                label = if (section.expanded) stringResource(R.string.combo_show_fewer) else moreLabel,
                expanded = section.expanded,
                onClick = onToggleMore,
                modifier = Modifier.padding(top = 16.dpx).testTag(moreTag),
            )
        }
    }
}

@Composable
private fun RequiredTag() {
    Box(
        modifier =
            Modifier
                .height(48.dpx)
                .background(KioskTheme.colors.accent, KioskTheme.shapes.pill)
                .padding(horizontal = 20.dpx),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = stringResource(R.string.customize_required),
            style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W700),
            color = KioskTheme.colors.surface,
            maxLines = 1,
        )
    }
}

@Composable
private fun SlotCard(
    option: SlotOptionUi,
    tone: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = RoundedCornerShape(36.dpx)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val enter = animateEntrance(SLOT_UP_MILLIS)
    val ring by animateColorAsState(
        targetValue = if (option.selected) colors.ink else colors.cardBorder,
        animationSpec = motionSpec(tween(RING_MILLIS, easing = KioskEasing.Emphasized)),
        label = "slot-ring",
    )
    Box(
        modifier =
            modifier
                .fadeUp(enter, Common.UP_DISTANCE_PX.dpx)
                .pressScale(scale)
                .graphicsLayer { alpha = if (option.available) 1f else SOLD_OUT_ALPHA }
                .heightIn(min = 232.dpx)
                .clip(shape)
                .background(colors.surface)
                .border(if (option.selected) 3.dpx else 2.dpx, ring, shape)
                .selectable(
                    selected = option.selected,
                    enabled = option.available,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dpx, end = 20.dpx, top = 28.dpx, bottom = 20.dpx),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dpx),
        ) {
            MenuPhoto(
                imageUrl = option.imageUrl,
                tone = tone,
                contentDescription = null,
                modifier = Modifier.size(96.dpx),
                shape = CircleShape,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dpx),
            ) {
                KioskText(
                    text = option.name,
                    style =
                        KioskTheme.typography.body.copy(
                            fontSize = 30.spx,
                            fontWeight = FontWeight.W600,
                            lineHeight = NAME_LINE_EM.em,
                        ),
                    textAlign = TextAlign.Center,
                )
                KioskText(
                    text =
                        when {
                            !option.available -> stringResource(R.string.combo_sold_out)
                            option.upcharge != null -> option.upcharge
                            else -> stringResource(R.string.combo_included)
                        },
                    style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W400),
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        SelectionDot(
            selected = option.selected,
            fill = colors.ink,
            size = 40.dpx,
            checkSize = 24.dpx,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dpx, end = 16.dpx),
        )
    }
}

@Composable
private fun MoreButton(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) HALF_TURN else 0f,
        animationSpec = motionSpec(tween(KioskMotion.BudgetPlan.COLLAPSIBLE_ROW_MILLIS, easing = KioskEasing.Spring)),
        label = "more-chevron",
    )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .pressScale(scale)
                .height(96.dpx)
                .clip(KioskTheme.shapes.large)
                .background(colors.surfaceAlt)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics {
                    if (expanded) collapse { onClick().let { true } } else expand { onClick().let { true } }
                },
        horizontalArrangement = Arrangement.spacedBy(12.dpx, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KioskText(
            text = label,
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
            maxLines = 1,
        )
        KioskIcon(
            icon = KioskIcons.ChevronDown,
            tint = colors.ink,
            modifier = Modifier.size(32.dpx).graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun SizeSection(
    large: Boolean,
    largeUpcharge: String,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val enter = animateEntrance(LIST_UP_MILLIS, SIZE_DELAY_MILLIS)
    val position by animateFloatAsState(
        targetValue = if (large) 1f else 0f,
        animationSpec = motionSpec(tween(Combo.SIZE_INDICATOR_MILLIS, easing = KioskEasing.Spring)),
        label = "size-indicator",
    )
    val sizeTitle = stringResource(R.string.combo_size)
    Column(
        modifier = modifier.fadeUp(enter, Common.UP_DISTANCE_PX.dpx),
        verticalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        KioskText(text = sizeTitle, style = KioskTheme.typography.section.copy(letterSpacing = SECTION_TITLE_EM.em))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(104.dpx)
                    .background(colors.surfaceAlt, RoundedCornerShape(52.dpx))
                    .padding(8.dpx)
                    .selectableGroup()
                    .semantics { contentDescription = sizeTitle },
        ) {
            val indicatorShape = RoundedCornerShape(44.dpx)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(HALF)
                        .height(88.dpx)
                        .graphicsLayer { translationX = size.width * position }
                        .dropShadow(
                            indicatorShape,
                            Shadow(
                                radius = 6.dpx,
                                color = colors.ink.copy(alpha = 0.06f),
                                offset = DpOffset(0.dpx, 2.dpx),
                            ),
                        ).background(colors.surface, indicatorShape),
            )
            Row {
                SizeSegment(
                    label = stringResource(R.string.combo_size_regular),
                    selected = !large,
                    onClick = { onSelect(false) },
                    modifier = Modifier.weight(1f).testTag(COMBO_REGULAR_TAG),
                )
                SizeSegment(
                    label = stringResource(R.string.combo_size_large_upcharge, largeUpcharge),
                    selected = large,
                    onClick = { onSelect(true) },
                    modifier = Modifier.weight(1f).testTag(COMBO_LARGE_TAG),
                )
            }
        }
    }
}

private const val HALF = 0.5f

@Composable
private fun SizeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (selected) KioskTheme.colors.ink else KioskTheme.colors.muted,
        animationSpec = motionSpec(tween(LABEL_COLOR_MILLIS)),
        label = "size-label",
    )
    Box(
        modifier =
            modifier
                .height(88.dpx)
                .selectable(
                    selected = selected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = label,
            style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W600),
            color = color,
            maxLines = 1,
        )
    }
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
private fun summaryText(summary: ComboSummaryUi): String =
    when (summary.missing) {
        ComboMissing.SIDE_AND_DRINK -> {
            stringResource(R.string.combo_summary_pick_both, summary.shortName)
        }

        ComboMissing.SIDE -> {
            stringResource(R.string.combo_summary_pick_side, summary.shortName)
        }

        ComboMissing.DRINK -> {
            stringResource(R.string.combo_summary_pick_drink, summary.shortName)
        }

        null -> {
            stringResource(
                R.string.combo_summary,
                summary.shortName,
                summary.side.orEmpty(),
                summary.drink.orEmpty(),
                stringResource(if (summary.large) R.string.combo_size_large else R.string.combo_size_regular),
            )
        }
    }

@Composable
private fun Dock(
    state: ComboUiState,
    onEvent: (ComboEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.dock
    val enter = animateEntrance(DOCK_IN_MILLIS, DOCK_IN_DELAY_MILLIS)
    Row(
        modifier =
            modifier
                .padding(start = 72.dpx, end = 72.dpx, bottom = 48.dpx)
                .graphicsLayer { translationY = size.height * DOCK_IN_FRACTION * (1f - enter.value) }
                .fillMaxWidth()
                .height(168.dpx)
                .kioskShadow(shape, KioskTheme.elevation.floating)
                .background(colors.ink, shape)
                .padding(start = 40.dpx, end = 16.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dpx)) {
            state.summary?.let {
                KioskText(
                    text = summaryText(it),
                    style = KioskTheme.typography.meta,
                    color = colors.dockMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RollingText(
                text = state.total,
                style = KioskTheme.typography.section.copy(letterSpacing = TOTAL_EM.em),
                durationMillis = Combo.TOTAL_ROLL_MILLIS,
                color = colors.bg,
            )
        }
        AddComboButton(cta = state.cta, onClick = { onEvent(ComboEvent.AddCombo) })
    }
}

@Composable
private fun AddComboButton(
    cta: ComboCta,
    onClick: () -> Unit,
) {
    val colors = KioskTheme.colors
    val ready = cta == ComboCta.ADD || cta == ComboCta.ADDED
    val background by animateColorAsState(
        targetValue =
            when (cta) {
                ComboCta.ADD -> colors.accent
                ComboCta.ADDED -> colors.bg
                else -> KioskTheme.colors.dockControl
            },
        animationSpec = motionSpec(tween(BUTTON_COLOR_MILLIS, easing = KioskEasing.Emphasized)),
        label = "add-combo-bg",
    )
    val foreground by animateColorAsState(
        targetValue =
            when (cta) {
                ComboCta.ADD -> colors.surface
                ComboCta.ADDED -> colors.ink
                else -> colors.closedDot
            },
        animationSpec = motionSpec(tween(BUTTON_COLOR_MILLIS)),
        label = "add-combo-fg",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            Modifier
                .pressScale(scale)
                .width(360.dpx)
                .height(136.dpx)
                .clip(RoundedCornerShape(36.dpx))
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = ready,
                    role = Role.Button,
                    onClick = onClick,
                ).testTag(COMBO_ADD_TAG),
        horizontalArrangement = Arrangement.spacedBy(14.dpx, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cta == ComboCta.ADDED) {
            val pop = animateOnEnter(tween(CHECK_IN_MILLIS, easing = KioskEasing.Pop))
            KioskIcon(
                icon = KioskIcons.Check,
                tint = colors.accent,
                modifier =
                    Modifier.size(42.dpx).graphicsLayer {
                        val p = pop.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = START_SCALE + (1f - START_SCALE) * p
                        scaleY = scaleX
                        rotationZ = START_ROTATION * (1f - p)
                    },
            )
        }
        KioskText(
            text =
                stringResource(
                    when (cta) {
                        ComboCta.ADD -> R.string.combo_add
                        ComboCta.ADDED -> R.string.combo_added
                        ComboCta.PICK_SIDE_AND_DRINK -> R.string.combo_pick_both
                        ComboCta.CHOOSE_SIDE -> R.string.combo_choose_side
                        ComboCta.CHOOSE_DRINK -> R.string.combo_choose_drink
                    },
                ),
            style = KioskTheme.typography.button,
            color = foreground,
            maxLines = 1,
        )
    }
}

/** The Added check pops in over 420 ms (`checkIn 420ms`). */
private const val CHECK_IN_MILLIS = 420

@Composable
private fun ComboPreview(state: ComboUiState) {
    KioskTheme(reduceMotion = true) {
        DesignFrame { ComboScreen(state = state, onEvent = {}) }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun ComboDefaultPreview() = ComboPreview(ComboSamples.Default)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun ComboRequiredMissingPreview() = ComboPreview(ComboSamples.RequiredMissing)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun ComboAddedPreview() = ComboPreview(ComboSamples.Default.copy(cta = ComboCta.ADDED))
