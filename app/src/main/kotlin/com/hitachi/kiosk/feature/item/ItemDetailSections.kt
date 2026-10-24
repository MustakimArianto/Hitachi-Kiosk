package com.hitachi.kiosk.feature.item

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.SelectionDot
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.ui.MenuPhoto

/** Option rings change over 320 ms (`transition: box-shadow 320ms`). */
private const val RING_MILLIS = 320

/** Tags and labels change color over 300 ms. */
private const val COLOR_MILLIS = 300

/** The switch knob slides over 500 ms with the spring easing. */
private const val KNOB_MILLIS = 500

/** The level indicator slides over 550 ms with the spring easing (PRD §7.1 segmented controls). */
private const val LEVEL_INDICATOR_MILLIS = 550

/** Width of one None / Regular / Extra segment, in design px. */
private const val LEVEL_SEGMENT_PX = 147

/** Padding of the None / Regular / Extra track around its segments, in design px. */
private const val LEVEL_TRACK_PADDING_PX = 8

private const val SECTION_TITLE_EM = -0.02f

/** Test tag of a collapsible row's header. */
fun itemCollapsibleTag(groupId: String): String = "item-collapsible:$groupId"

/** Test tag of an option card or row. */
fun itemOptionTag(
    groupId: String,
    optionId: String,
): String = "item-option:$groupId:$optionId"

/** Test tag of a switch row. */
fun itemSwitchTag(groupId: String): String = "item-switch:$groupId"

/** Test tag of one level segment. */
fun itemLevelTag(
    optionId: String,
    level: Level,
): String = "item-level:$optionId:${level.name}"

/** The ring of a selectable card: 3 dpx accent when selected, 2 dpx `cardBorder` otherwise. */
@Composable
private fun Modifier.optionRing(
    selected: Boolean,
    shape: RoundedCornerShape,
): Modifier {
    val colors = KioskTheme.colors
    val color by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.cardBorder,
        animationSpec = motionSpec(tween(RING_MILLIS, easing = KioskEasing.Emphasized)),
        label = "ring",
    )
    val width by animateDpAsState(
        targetValue = if (selected) 3.dpx else 2.dpx,
        animationSpec = motionSpec(tween(RING_MILLIS, easing = KioskEasing.Emphasized)),
        label = "ring-width",
    )
    return border(width, color, shape)
}

@Composable
internal fun ItemSection(
    section: ItemSectionUi,
    tone: String?,
    onEvent: (ItemDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        is ItemSectionUi.Choice -> ChoiceSection(section, onEvent, modifier)
        is ItemSectionUi.Switch -> SwitchRow(section, tone, onEvent, modifier)
        is ItemSectionUi.Levels -> LevelsSection(section, onEvent, modifier)
        is ItemSectionUi.Extras -> ExtrasSection(section, tone, onEvent, modifier)
    }
}

@Composable
private fun ChoiceSection(
    section: ItemSectionUi.Choice,
    onEvent: (ItemDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dpx)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dpx, Alignment.Start),
        ) {
            KioskText(
                text = section.title,
                style = KioskTheme.typography.section.copy(letterSpacing = SECTION_TITLE_EM.em),
                modifier = Modifier.weight(1f),
            )
            section.requiredTag?.let { RequiredTagPill(it) }
        }
        Column(
            modifier =
                Modifier.selectableGroup().semantics { contentDescription = section.title },
            verticalArrangement = Arrangement.spacedBy(24.dpx),
        ) {
            section.options.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dpx)) {
                    pair.forEach { option ->
                        ChoiceCard(
                            option = option,
                            onClick = { onEvent(ItemDetailEvent.SelectOption(section.groupId, option.id)) },
                            modifier = Modifier.weight(1f).testTag(itemOptionTag(section.groupId, option.id)),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RequiredTagPill(tag: RequiredTag) {
    val colors = KioskTheme.colors
    val missing = tag == RequiredTag.MISSING
    val background by animateColorAsState(
        targetValue = if (missing) colors.accent else colors.surfaceAlt,
        animationSpec = motionSpec(tween(COLOR_MILLIS)),
        label = "tag-bg",
    )
    val foreground by animateColorAsState(
        targetValue = if (missing) colors.surface else colors.muted,
        animationSpec = motionSpec(tween(COLOR_MILLIS)),
        label = "tag-fg",
    )
    Box(
        modifier =
            Modifier
                .height(56.dpx)
                .background(background, KioskTheme.shapes.pill)
                .padding(horizontal = 24.dpx),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = stringResource(if (missing) R.string.customize_required else R.string.item_required_done),
            style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W700),
            color = foreground,
            maxLines = 1,
        )
    }
}

/** "Three patties · +Rp 6.000", or whichever part exists. */
@Composable
private fun detailLine(
    description: String?,
    price: String?,
): String? =
    when {
        description != null && price != null -> stringResource(R.string.item_option_detail, description, price)
        else -> description ?: price
    }

@Composable
private fun ChoiceCard(
    option: ChoiceOptionUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            modifier
                .pressScale(scale)
                .heightIn(min = 136.dpx)
                .clip(shape)
                .background(colors.surface)
                .optionRing(option.selected, shape)
                .selectable(
                    selected = option.selected,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(start = 32.dpx, end = 28.dpx, top = 16.dpx, bottom = 16.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dpx),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dpx)) {
            KioskText(
                text = option.name,
                style = KioskTheme.typography.button.copy(fontWeight = FontWeight.W600),
            )
            detailLine(option.description, option.priceDelta)?.let {
                KioskText(
                    text = it,
                    style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W400),
                    color = colors.muted,
                )
            }
        }
        SelectionDot(selected = option.selected, fill = colors.accent, size = 48.dpx, checkSize = 28.dpx)
    }
}

@Composable
private fun SwitchRow(
    section: ItemSectionUi.Switch,
    tone: String?,
    onEvent: (ItemDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val track by animateColorAsState(
        targetValue = if (section.checked) colors.accent else colors.radioOff,
        animationSpec = motionSpec(tween(RING_MILLIS)),
        label = "track",
    )
    val knob by animateFloatAsState(
        targetValue = if (section.checked) 1f else 0f,
        animationSpec = motionSpec(tween(KNOB_MILLIS, easing = KioskEasing.Spring)),
        label = "knob",
    )
    val knobTravel = 48.dpx
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .pressScale(scale)
                .heightIn(min = 160.dpx)
                .clip(shape)
                .background(colors.surface)
                .optionRing(section.checked, shape)
                .toggleable(
                    value = section.checked,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Switch,
                    onValueChange = { on ->
                        onEvent(
                            ItemDetailEvent.SelectOption(
                                section.groupId,
                                if (on) section.onOptionId else section.offOptionId,
                            ),
                        )
                    },
                ).testTag(itemSwitchTag(section.groupId))
                .padding(start = 16.dpx, end = 32.dpx, top = 16.dpx, bottom = 16.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        MenuPhoto(
            imageUrl = section.imageUrl,
            tone = tone,
            contentDescription = null,
            modifier = Modifier.size(128.dpx),
            shape = KioskTheme.shapes.medium,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dpx)) {
            KioskText(
                text = section.title,
                style = KioskTheme.typography.item.copy(fontWeight = FontWeight.W700),
            )
            detailLine(section.description, section.priceDelta)?.let {
                KioskText(
                    text = it,
                    style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W400),
                    color = colors.muted,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .width(112.dpx)
                    .height(64.dpx)
                    .background(track, RoundedCornerShape(32.dpx)),
        ) {
            Box(
                modifier =
                    Modifier
                        .offset(x = 6.dpx, y = 6.dpx)
                        .graphicsLayer { translationX = knobTravel.toPx() * knob }
                        .size(52.dpx)
                        .dropShadow(
                            CircleShape,
                            Shadow(
                                radius = 6.dpx,
                                color = colors.ink.copy(alpha = 0.2f),
                                offset = DpOffset(0.dpx, 2.dpx),
                            ),
                        ).background(colors.surface, CircleShape),
            )
        }
    }
}

/** The white collapsible card with a title, a one-line summary and a turning chevron (PRD §11.3). */
@Composable
private fun CollapsibleCard(
    groupId: String,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val reduce = LocalReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) HALF_TURN else 0f,
        animationSpec = motionSpec(tween(KioskMotion.BudgetPlan.COLLAPSIBLE_ROW_MILLIS, easing = KioskEasing.Spring)),
        label = "chevron",
    )
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(2.dpx, colors.cardBorder, shape),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .pressScale(scale)
                    .heightIn(min = 128.dpx)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onToggle,
                    ).semantics {
                        if (expanded) collapse { onToggle().let { true } } else expand { onToggle().let { true } }
                    }.testTag(itemCollapsibleTag(groupId))
                    .padding(horizontal = 32.dpx, vertical = 16.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dpx),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dpx)) {
                KioskText(text = title, style = KioskTheme.typography.button)
                KioskText(
                    text = summary,
                    style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W400),
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.size(64.dpx).background(colors.surfaceAlt, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                KioskIcon(
                    icon = KioskIcons.ChevronDown,
                    tint = colors.ink,
                    modifier = Modifier.size(36.dpx).graphicsLayer { rotationZ = rotation },
                )
            }
        }
        val spec = tween<Float>(KioskMotion.BudgetPlan.COLLAPSIBLE_ROW_MILLIS, easing = KioskEasing.Emphasized)
        AnimatedVisibility(
            visible = expanded,
            enter =
                if (reduce) {
                    EnterTransition.None
                } else {
                    expandVertically(
                        tween(KioskMotion.BudgetPlan.COLLAPSIBLE_ROW_MILLIS, easing = KioskEasing.Emphasized),
                    ) +
                        fadeIn(spec)
                },
            exit =
                if (reduce) {
                    ExitTransition.None
                } else {
                    shrinkVertically(
                        tween(KioskMotion.BudgetPlan.COLLAPSIBLE_ROW_MILLIS, easing = KioskEasing.Emphasized),
                    ) +
                        fadeOut(spec)
                },
        ) {
            Column {
                Box(Modifier.fillMaxWidth().height(2.dpx).background(colors.surfaceAlt))
                body()
            }
        }
    }
}

private const val HALF_TURN = 180f

@Composable
private fun levelChange(change: LevelChangeUi): String =
    stringResource(
        when (change.level) {
            Level.NONE -> R.string.item_level_change_none
            Level.REGULAR -> R.string.item_level_change_regular
            Level.EXTRA -> R.string.item_level_change_extra
        },
        change.name,
    )

@Composable
private fun LevelsSection(
    section: ItemSectionUi.Levels,
    onEvent: (ItemDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(R.string.customize_summary_separator)
    val summary =
        if (section.changes.isEmpty()) {
            stringResource(R.string.item_customize_no_changes)
        } else {
            section.changes.map { levelChange(it) }.joinToString(separator)
        }
    CollapsibleCard(
        groupId = section.groupId,
        title = section.title,
        summary = summary,
        expanded = section.expanded,
        onToggle = { onEvent(ItemDetailEvent.ToggleExpanded(section.groupId)) },
        modifier = modifier,
    ) {
        Column(Modifier.padding(start = 32.dpx, end = 24.dpx, bottom = 8.dpx)) {
            section.rows.forEachIndexed { index, row ->
                LevelRow(
                    row = row,
                    onPick = { onEvent(ItemDetailEvent.SetLevel(section.groupId, row.optionId, it)) },
                )
                if (index < section.rows.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(2.dpx).background(KioskTheme.colors.surfaceAlt))
                }
            }
        }
    }
}

@Composable
private fun LevelRow(
    row: LevelRowUi,
    onPick: (Level) -> Unit,
) {
    val colors = KioskTheme.colors
    val indicatorColor by animateColorAsState(
        targetValue =
            when (row.level) {
                Level.NONE -> colors.surface
                Level.REGULAR -> colors.ink
                Level.EXTRA -> colors.accent
            },
        animationSpec = motionSpec(tween(RING_MILLIS)),
        label = "level-color",
    )
    val position by animateFloatAsState(
        targetValue = row.level.ordinal.toFloat(),
        animationSpec = motionSpec(tween(LEVEL_INDICATOR_MILLIS, easing = KioskEasing.Spring)),
        label = "level-position",
    )
    val groupDescription = stringResource(R.string.item_level_group_description, row.name)
    val segment = LEVEL_SEGMENT_PX.dpx
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 128.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dpx)) {
            KioskText(
                text = row.name,
                style = KioskTheme.typography.body.copy(fontSize = 32.spx, fontWeight = FontWeight.W600),
            )
            row.note?.let {
                KioskText(
                    text = it,
                    style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W400),
                    color = colors.muted,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .width((LEVEL_SEGMENT_PX * Level.entries.size + 2 * LEVEL_TRACK_PADDING_PX).dpx)
                    .height(88.dpx)
                    .background(colors.surfaceAlt, RoundedCornerShape(44.dpx))
                    .padding(LEVEL_TRACK_PADDING_PX.dpx)
                    .selectableGroup()
                    .semantics { contentDescription = groupDescription },
        ) {
            val indicatorShape = RoundedCornerShape(36.dpx)
            Box(
                modifier =
                    Modifier
                        .graphicsLayer { translationX = segment.toPx() * position }
                        .width(LEVEL_SEGMENT_PX.dpx)
                        .height(72.dpx)
                        .dropShadow(
                            indicatorShape,
                            Shadow(
                                radius = 6.dpx,
                                color = colors.ink.copy(alpha = 0.06f),
                                offset = DpOffset(0.dpx, 2.dpx),
                            ),
                        ).background(indicatorColor, indicatorShape),
            )
            Row {
                Level.entries.forEach { level ->
                    LevelSegment(
                        label = stringResource(level.label()),
                        selected = row.level == level,
                        selectedColor = if (level == Level.NONE) colors.ink else colors.surface,
                        onClick = { onPick(level) },
                        modifier = Modifier.testTag(itemLevelTag(row.optionId, level)),
                    )
                }
            }
        }
    }
}

private fun Level.label(): Int =
    when (this) {
        Level.NONE -> R.string.item_level_none
        Level.REGULAR -> R.string.item_level_regular
        Level.EXTRA -> R.string.item_level_extra
    }

@Composable
private fun LevelSegment(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (selected) selectedColor else KioskTheme.colors.muted,
        animationSpec = motionSpec(tween(COLOR_MILLIS)),
        label = "segment-label",
    )
    Box(
        modifier =
            modifier
                .width(LEVEL_SEGMENT_PX.dpx)
                .height(72.dpx)
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
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun ExtrasSection(
    section: ItemSectionUi.Extras,
    tone: String?,
    onEvent: (ItemDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(R.string.customize_summary_separator)
    val summary =
        if (section.chosenNames.isEmpty()) {
            stringResource(R.string.item_extras_none_added)
        } else {
            (section.chosenNames + listOfNotNull(section.chosenTotal)).joinToString(separator)
        }
    CollapsibleCard(
        groupId = section.groupId,
        title = section.title,
        summary = summary,
        expanded = section.expanded,
        onToggle = { onEvent(ItemDetailEvent.ToggleExpanded(section.groupId)) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dpx, end = 16.dpx, top = 16.dpx, bottom = 24.dpx),
            verticalArrangement = Arrangement.spacedBy(12.dpx),
        ) {
            section.options.forEach { option ->
                ExtraRow(
                    option = option,
                    tone = tone,
                    onToggle = { onEvent(ItemDetailEvent.SelectOption(section.groupId, option.id)) },
                    modifier = Modifier.testTag(itemOptionTag(section.groupId, option.id)),
                )
            }
        }
    }
}

@Composable
private fun ExtraRow(
    option: ExtraOptionUi,
    tone: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .pressScale(scale)
                .graphicsLayer { alpha = if (option.enabled) 1f else DISABLED_ALPHA }
                .heightIn(min = 120.dpx)
                .clip(shape)
                .background(colors.bg)
                .optionRing(option.selected, shape)
                .toggleable(
                    value = option.selected,
                    enabled = option.enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ).padding(start = 16.dpx, end = 32.dpx, top = 16.dpx, bottom = 16.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dpx),
    ) {
        MenuPhoto(
            imageUrl = option.imageUrl,
            tone = tone,
            contentDescription = null,
            modifier = Modifier.size(88.dpx),
            shape = KioskTheme.shapes.small,
        )
        KioskText(
            text = option.name,
            style = KioskTheme.typography.body.copy(fontSize = 32.spx, fontWeight = FontWeight.W600),
            modifier = Modifier.weight(1f),
        )
        KioskText(
            text = option.price,
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
            color = colors.muted,
            maxLines = 1,
        )
        SelectionDot(selected = option.selected, fill = colors.accent, size = 48.dpx, checkSize = 28.dpx)
    }
}

/** An extra that cannot be added because the group is at its `max` (api-spec E6). */
private const val DISABLED_ALPHA = 0.45f
