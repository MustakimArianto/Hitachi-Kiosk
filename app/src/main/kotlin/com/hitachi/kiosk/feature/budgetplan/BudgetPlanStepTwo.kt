package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.RollingText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animateOnEnter
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.planner.PlanKind
import com.hitachi.kiosk.ui.MenuPhoto

/** Test tag of a plan card (`UNDER` / `OVER`). */
fun budgetPlanCardTag(side: PlanSide): String = "budget-plan-card:${side.name}"

/** Test tag of the hero plan card (one plan). */
const val BUDGET_PLAN_HERO_TAG = "budget-plan-hero"

/** Test tags of the collapsible rows. */
const val BUDGET_PLAN_INCLUDED_TAG = "budget-plan-included"
const val BUDGET_PLAN_PRICE_TAG = "budget-plan-price"

/** Test tags of the options card. */
const val BUDGET_PLAN_SIDES_TAG = "budget-plan-sides"
const val BUDGET_PLAN_DESSERTS_TAG = "budget-plan-desserts"
const val BUDGET_PLAN_OVER_TAG = "budget-plan-over"
const val BUDGET_PLAN_RESET_TAG = "budget-plan-reset"

/** Test tag of an item's "Swap" button. */
fun budgetPlanSwapTag(target: SwapTarget): String = "budget-plan-swap:${target.pickIndex}"

/** Entrance delays of the step 2 blocks (`fadeUp 700ms`): options @320, rows @380 / @440. */
private const val OPTIONS_DELAY_MILLIS = 320
private const val HERO_DELAY_MILLIS = 260
private const val INCLUDED_DELAY_MILLIS = 380
private const val PRICE_DELAY_MILLIS = 440

/** The collapsible rows open over 620 ms (`grid-template-rows 620ms`); the chevron turns over 500 ms. */
private const val EXPAND_MILLIS = 620
private const val CHEVRON_MILLIS = 500
private const val HALF_TURN = 180f

/** The hero card's shadow: `0 24px 48px` at 8 % for an exact match, 6 % otherwise. */
private const val EXACT_SHADOW_ALPHA = 0.08f
private const val HERO_SHADOW_ALPHA = 0.06f

/** A swapped item slides in from 28 dpx to the right over 420 ms (`swapA`). */
private const val SWAP_IN_MILLIS = 420
private const val SWAP_IN_PX = 28

/** Letter spacing in em. */
private const val HERO_TITLE_TRACKING_EM = -0.6f / 40
private const val HERO_TOTAL_TRACKING_EM = -3.4f / 112
private const val HERO_TOTAL_LINE_EM = 128f / 112
private const val ITEM_NAME_TRACKING_EM = -0.3f / 30
private const val CARD_TITLE_TRACKING_EM = -0.6f / 36
private const val CARD_TOTAL_TRACKING_EM = -1.6f / 52
private const val TOTAL_TRACKING_EM = -1f / 40
private const val TIGHT_LINE_EM = 1.1f
private const val ITEM_LINE_EM = 1.15f
private const val NOTE_LINE_EM = 1.4f

/**
 * Step 2 "Your plan" (FR-032, D-35): the two plan cards or the hero card, the options, "What's included" (open, with
 * a Swap button per meal) and "Price details".
 */
@Composable
internal fun StepTwo(
    state: BudgetPlanUiState,
    plan: PlanUi,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    Column {
        if (plan.cards.isNotEmpty()) {
            PlanCards(plan.cards, state.people, onEvent)
        } else {
            HeroCard(plan = plan, people = state.people, modifier = Modifier.padding(top = 56.dpx))
        }
        OptionsCard(plan.options, onEvent)
        val items = pluralStringResource(R.plurals.budget_plan_items, plan.itemCount, plan.itemCount)
        CollapsibleRow(
            title = stringResource(R.string.budget_plan_whats_included),
            value = items,
            expanded = state.includedExpanded,
            onToggle = { onEvent(BudgetPlanEvent.ToggleIncluded) },
            delayMillis = INCLUDED_DELAY_MILLIS,
            tag = BUDGET_PLAN_INCLUDED_TAG,
            modifier = Modifier.padding(top = 24.dpx),
        ) {
            IncludedBody(plan, onEvent)
        }
        CollapsibleRow(
            title = stringResource(R.string.budget_plan_price_details),
            value = plan.price.total,
            expanded = state.priceExpanded,
            onToggle = { onEvent(BudgetPlanEvent.TogglePriceDetails) },
            delayMillis = PRICE_DELAY_MILLIS,
            tag = BUDGET_PLAN_PRICE_TAG,
            modifier = Modifier.padding(top = 16.dpx),
        ) {
            PriceBody(plan.price)
        }
    }
}

/** "Within your budget" and "A little extra": two selectable cards (`S_BudgetPas_twoPlans`). */
@Composable
private fun PlanCards(
    cards: List<PlanCardUi>,
    people: Int,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val description = stringResource(R.string.budget_plan_plans)
    Column(
        modifier = Modifier.padding(top = 56.dpx).selectableGroup().semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        cards.forEachIndexed { index, card ->
            PlanCard(card, people, delayMillis = HERO_DELAY_MILLIS - 140 + index * 80, onEvent = onEvent)
        }
    }
}

@Composable
private fun PlanCard(
    card: PlanCardUi,
    people: Int,
    delayMillis: Int,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val enter = animateEntrance(FADE_UP_MILLIS, delayMillis)
    val shape = KioskTheme.shapes.card
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val over = card.side == PlanSide.OVER
    val peopleText = pluralStringResource(R.plurals.budget_plan_people, people, people)
    val itemsText = pluralStringResource(R.plurals.budget_plan_items, card.items, card.items)
    Row(
        modifier =
            Modifier
                .fadeUp(enter, UP_DISTANCE.dpx)
                .pressScale(scale)
                .fillMaxWidth()
                .kioskShadow(
                    shape,
                    KioskTheme.elevation.floating.copy(
                        color = colors.ink.copy(alpha = if (card.selected) EXACT_SHADOW_ALPHA else 0f),
                    ),
                ).clip(shape)
                .background(colors.surface)
                .border(
                    if (card.selected) 4.dpx else 2.dpx,
                    if (card.selected) colors.accent else colors.cardBorder,
                    shape,
                ).selectable(
                    selected = card.selected,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = { onEvent(BudgetPlanEvent.SelectPlan(card.side)) },
                ).testTag(budgetPlanCardTag(card.side))
                .padding(start = 32.dpx, top = 36.dpx, end = 36.dpx, bottom = 36.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dpx),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dpx)
                    .border(3.dpx, if (card.selected) colors.accent else colors.radioOff, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (card.selected) Box(Modifier.size(28.dpx).background(colors.accent, CircleShape))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dpx)) {
            KioskText(
                text = stringResource(if (over) R.string.budget_plan_card_over else R.string.budget_plan_card_under),
                style =
                    typography.section.copy(
                        fontSize = 36.spx,
                        letterSpacing = CARD_TITLE_TRACKING_EM.em,
                        lineHeight = TIGHT_LINE_EM.em,
                    ),
            )
            KioskText(
                text = stringResource(R.string.budget_plan_meta, peopleText, itemsText),
                style = typography.meta,
                color = colors.muted,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dpx)) {
            RollingText(
                text = card.total,
                style =
                    typography.display.copy(
                        fontSize = 52.spx,
                        letterSpacing = CARD_TOTAL_TRACKING_EM.em,
                        lineHeight =
                            (
                                64f /
                                    52f
                            ).em,
                    ),
                durationMillis = AMOUNT_ROLL_MILLIS,
            )
            KioskText(
                text =
                    stringResource(
                        if (over) R.string.budget_plan_badge_over else R.string.budget_plan_badge_under,
                        card.delta,
                    ),
                style = typography.meta.copy(fontWeight = FontWeight.W700),
                color = if (over) colors.ink else colors.success,
                modifier =
                    Modifier
                        .background(
                            if (over) colors.surfaceAlt else colors.successBg,
                            KioskTheme.shapes.pill,
                        ).padding(horizontal = 20.dpx, vertical = 8.dpx),
            )
        }
    }
}

/** The tag, title and badge texts and the badge colors of a hero card (FR-032 table). */
private class HeroLook(
    val tag: String,
    val title: String,
    val badge: String,
    val badgeBg: Color,
    val badgeFg: Color,
)

@Composable
private fun heroLook(hero: HeroUi): HeroLook {
    val colors = KioskTheme.colors
    return when (hero.kind) {
        PlanKind.EXACT -> {
            HeroLook(
                stringResource(R.string.budget_plan_tag_exact),
                stringResource(R.string.budget_plan_hero_exact),
                stringResource(R.string.budget_plan_badge_exact, hero.total),
                colors.success,
                colors.surface,
            )
        }

        PlanKind.UNDER -> {
            HeroLook(
                stringResource(R.string.budget_plan_tag_under),
                stringResource(R.string.budget_plan_hero_under),
                stringResource(R.string.budget_plan_badge_under, hero.delta),
                colors.successBg,
                colors.success,
            )
        }

        PlanKind.OVER -> {
            HeroLook(
                stringResource(R.string.budget_plan_tag_over),
                stringResource(R.string.budget_plan_hero_over),
                stringResource(R.string.budget_plan_badge_over, hero.delta),
                colors.surfaceAlt,
                colors.ink,
            )
        }

        PlanKind.TOO_LOW -> {
            HeroLook(
                stringResource(R.string.budget_plan_tag_too_low, hero.delta),
                stringResource(R.string.budget_plan_hero_too_low),
                stringResource(R.string.budget_plan_badge_too_low, hero.delta),
                colors.shortBg,
                colors.shortText,
            )
        }

        PlanKind.MORE_THAN_ENOUGH -> {
            HeroLook(
                stringResource(R.string.budget_plan_tag_generous),
                stringResource(R.string.budget_plan_hero_generous, hero.total),
                stringResource(R.string.budget_plan_badge_generous, hero.delta),
                colors.successBg,
                colors.success,
            )
        }
    }
}

/** The badge text of [hero], also the Customize sheet's summary. */
@Composable
internal fun heroBadge(hero: HeroUi): String = heroLook(hero).badge

@Composable
private fun HeroCard(
    plan: PlanUi,
    people: Int,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val enter = animateEntrance(KioskMotion.BudgetPlan.HERO_CARD_RISE_MILLIS, HERO_DELAY_MILLIS)
    val look = heroLook(plan.hero)
    val exact = plan.hero.kind == PlanKind.EXACT
    val shape = RoundedCornerShape(48.dpx)
    val shadow =
        KioskTheme.elevation.floating.copy(
            color = colors.ink.copy(alpha = if (exact) EXACT_SHADOW_ALPHA else HERO_SHADOW_ALPHA),
        )
    val peopleText = pluralStringResource(R.plurals.budget_plan_people, people, people)
    val itemsText = pluralStringResource(R.plurals.budget_plan_items, plan.itemCount, plan.itemCount)
    Column(
        modifier =
            modifier
                .fadeUp(enter, UP_DISTANCE.dpx)
                .fillMaxWidth()
                .kioskShadow(shape, shadow)
                .clip(shape)
                .background(colors.surface)
                .border(if (exact) 4.dpx else 2.dpx, if (exact) colors.success else colors.cardBorder, shape)
                .semantics(mergeDescendants = true) {}
                .testTag(BUDGET_PLAN_HERO_TAG)
                .padding(start = 44.dpx, end = 44.dpx, top = 48.dpx, bottom = 44.dpx),
        verticalArrangement = Arrangement.spacedBy(8.dpx),
    ) {
        KioskText(
            text = look.tag,
            style = typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
            color = colors.muted,
        )
        KioskText(
            text = look.title,
            style =
                typography.section.copy(
                    fontSize = 40.spx,
                    letterSpacing = HERO_TITLE_TRACKING_EM.em,
                    lineHeight = TIGHT_LINE_EM.em,
                ),
        )
        RollingText(
            text = plan.hero.total,
            style =
                typography.display.copy(
                    fontSize = 112.spx,
                    letterSpacing = HERO_TOTAL_TRACKING_EM.em,
                    lineHeight = HERO_TOTAL_LINE_EM.em,
                ),
            durationMillis = AMOUNT_ROLL_MILLIS,
            modifier = Modifier.padding(top = 8.dpx),
        )
        KioskText(
            text = look.badge,
            style = typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
            color = look.badgeFg,
            modifier =
                Modifier
                    .padding(top = 16.dpx)
                    .background(look.badgeBg, KioskTheme.shapes.pill)
                    .padding(horizontal = 24.dpx, vertical = 12.dpx),
        )
        KioskText(
            text = stringResource(R.string.budget_plan_meta, peopleText, itemsText),
            style = typography.body.copy(fontSize = 28.spx),
            color = colors.muted,
            modifier = Modifier.padding(top = 16.dpx),
        )
        plan.notes.forEach { note ->
            KioskText(
                text = noteText(note),
                style =
                    typography.meta.copy(
                        fontSize = 26.spx,
                        fontWeight = FontWeight.W400,
                        lineHeight = NOTE_LINE_EM.em,
                    ),
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun noteText(note: PlanNote): String =
    when (note) {
        PlanNote.TooLow -> stringResource(R.string.budget_plan_note_too_low)
        PlanNote.Generous -> stringResource(R.string.budget_plan_note_generous)
        is PlanNote.NoOverPlan -> stringResource(R.string.budget_plan_note_no_over, note.percent)
        PlanNote.OverOff -> stringResource(R.string.budget_plan_note_over_off)
        PlanNote.KidsMenuUnavailable -> stringResource(R.string.budget_plan_note_kids_menu)
    }

/**
 * "What's included" and "Price details" (FR-032): a 112 dpx white row with [value] and a turning chevron; the body
 * opens below it. Collapsed by default (TC-171).
 */
@Suppress("LongParameterList")
@Composable
private fun CollapsibleRow(
    title: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    delayMillis: Int,
    tag: String,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    val colors = KioskTheme.colors
    val reduce = LocalReduceMotion.current
    val enter = animateEntrance(FADE_UP_MILLIS, delayMillis)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) HALF_TURN else 0f,
        animationSpec = motionSpec(tween(CHEVRON_MILLIS, easing = KioskEasing.Spring)),
        label = "chevron",
    )
    val shape = RoundedCornerShape(36.dpx)
    Column(modifier = modifier.fadeUp(enter, UP_DISTANCE.dpx)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .pressable(onClick = onToggle)
                    .semantics {
                        if (expanded) collapse { onToggle().let { true } } else expand { onToggle().let { true } }
                    }.testTag(tag)
                    .heightIn(min = 112.dpx)
                    .clip(shape)
                    .background(colors.surface)
                    .border(2.dpx, colors.cardBorder, shape)
                    .padding(horizontal = 36.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dpx),
        ) {
            KioskText(
                text = title,
                style = KioskTheme.typography.body.copy(fontSize = 32.spx, fontWeight = FontWeight.W600),
                modifier = Modifier.weight(1f),
            )
            KioskText(
                text = value,
                style = KioskTheme.typography.meta.copy(fontSize = 28.spx),
                color = colors.muted,
                maxLines = 1,
            )
            KioskIcon(
                icon = KioskIcons.ChevronDown,
                tint = colors.ink,
                modifier = Modifier.size(36.dpx).graphicsLayer { rotationZ = rotation },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter =
                if (reduce) {
                    EnterTransition.None
                } else {
                    expandVertically(
                        tween(EXPAND_MILLIS, easing = KioskEasing.Emphasized),
                    )
                },
            exit =
                if (reduce) {
                    ExitTransition.None
                } else {
                    shrinkVertically(
                        tween(EXPAND_MILLIS, easing = KioskEasing.Emphasized),
                    )
                },
        ) {
            Box(Modifier.padding(top = 16.dpx)) { body() }
        }
    }
}

@Composable
private fun IncludedBody(
    plan: PlanUi,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val shape = KioskTheme.shapes.card
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(KioskTheme.colors.surface)
                .border(2.dpx, KioskTheme.colors.cardBorder, shape)
                .padding(start = 32.dpx, end = 32.dpx, top = 4.dpx, bottom = 16.dpx),
    ) {
        plan.groups.forEach { group ->
            Column(modifier = Modifier.padding(top = 20.dpx)) {
                GroupLabel(group.label, Modifier.padding(bottom = 4.dpx))
                group.items.forEach { item ->
                    IncludedRow(
                        item = item,
                        justSwapped = item.swap != null && item.swap == plan.lastSwap,
                        swapSerial = plan.swapSerial,
                        onSwap = { onEvent(BudgetPlanEvent.Swap(it)) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun GroupLabel(
    label: PlanGroupLabel,
    modifier: Modifier = Modifier,
) {
    KioskText(
        text =
            when (label) {
                is PlanGroupLabel.Adult -> {
                    stringResource(R.string.budget_plan_group_adult, label.number)
                }

                is PlanGroupLabel.Child -> {
                    stringResource(
                        if (label.kidFriendly) {
                            R.string.budget_plan_group_child_kid
                        } else {
                            R.string.budget_plan_group_child
                        },
                        label.number,
                    )
                }

                is PlanGroupLabel.Sharing -> {
                    stringResource(R.string.budget_plan_group_sharing, label.first, label.second)
                }

                PlanGroupLabel.Shared -> {
                    stringResource(R.string.budget_plan_group_shared)
                }
            },
        style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W600),
        color = KioskTheme.colors.muted,
        modifier = modifier,
    )
}

@Composable
internal fun itemMeta(meta: PlanItemMeta): String =
    when (meta) {
        PlanItemMeta.MainAndDrink -> stringResource(R.string.budget_plan_meta_main_drink)
        is PlanItemMeta.Meal -> meta.includes ?: stringResource(R.string.budget_plan_meta_meal)
        is PlanItemMeta.MealForTwo -> meta.includes ?: stringResource(R.string.budget_plan_meta_meal_for_two)
        PlanItemMeta.Side -> stringResource(R.string.budget_plan_meta_side)
        PlanItemMeta.Dessert -> stringResource(R.string.budget_plan_meta_dessert)
        is PlanItemMeta.Twice -> stringResource(R.string.budget_plan_swap_separate_meta, meta.pair)
    }

@Composable
private fun IncludedRow(
    item: PlanItemUi,
    justSwapped: Boolean,
    swapSerial: Int,
    onSwap: (SwapTarget) -> Unit,
) {
    val colors = KioskTheme.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 128.dpx).padding(vertical = 16.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dpx),
        ) {
            MenuPhoto(
                imageUrl = item.imageUrl,
                tone = item.tone,
                contentDescription = null,
                shape = KioskTheme.shapes.small,
                modifier = Modifier.size(80.dpx),
            )
            // A new key per swap replays the slide-in on the swapped item only (`swapA` / `swapB`).
            key(if (justSwapped) swapSerial else -1) {
                val slide = animateOnEnter(tween(SWAP_IN_MILLIS, easing = KioskEasing.Emphasized))
                val distance = SWAP_IN_PX.dpx
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .graphicsLayer {
                                if (justSwapped) {
                                    alpha = slide.value
                                    translationX = distance.toPx() * (1f - slide.value)
                                }
                            },
                    verticalArrangement = Arrangement.spacedBy(2.dpx),
                ) {
                    KioskText(
                        text = item.name,
                        style =
                            KioskTheme.typography.body.copy(
                                fontWeight = FontWeight.W600,
                                letterSpacing = ITEM_NAME_TRACKING_EM.em,
                                lineHeight = ITEM_LINE_EM.em,
                            ),
                    )
                    KioskText(
                        text = itemMeta(item.meta),
                        style = KioskTheme.typography.meta.copy(fontSize = 24.spx),
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            KioskText(
                text = item.price,
                style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
                maxLines = 1,
            )
            item.swap?.let { SwapButton(item.name, onClick = { onSwap(it) }, tag = budgetPlanSwapTag(it)) }
        }
        RowDivider()
    }
}

@Composable
private fun SwapButton(
    name: String,
    onClick: () -> Unit,
    tag: String,
) {
    val colors = KioskTheme.colors
    val description = stringResource(R.string.budget_plan_swap_description, name)
    Row(
        modifier =
            Modifier
                .pressable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .testTag(tag)
                .height(96.dpx)
                .background(colors.surfaceAlt, RoundedCornerShape(32.dpx))
                .padding(start = 20.dpx, end = 24.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dpx),
    ) {
        KioskIcon(icon = KioskIcons.Swap, tint = colors.ink, modifier = Modifier.size(32.dpx))
        KioskText(
            text = stringResource(R.string.budget_plan_swap),
            style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W600),
            maxLines = 1,
        )
    }
}

/** "Add sides", "Add desserts", "Allow a little over budget" and "Reset to suggested" (`S_BudgetPas_optionsOff`). */
@Composable
private fun OptionsCard(
    options: PlanOptionsUi,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val enter = animateEntrance(FADE_UP_MILLIS, OPTIONS_DELAY_MILLIS)
    val shape = KioskTheme.shapes.card
    Column(
        modifier =
            Modifier
                .padding(top = 24.dpx)
                .fadeUp(enter, UP_DISTANCE.dpx)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(2.dpx, colors.cardBorder, shape)
                .padding(start = 36.dpx, end = 36.dpx, top = 32.dpx, bottom = 8.dpx),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dpx)) {
            OptionChip(
                label = stringResource(R.string.budget_plan_opt_sides),
                on = options.includeSides,
                onChange = { onEvent(BudgetPlanEvent.SetIncludeSides(it)) },
                tag = BUDGET_PLAN_SIDES_TAG,
            )
            OptionChip(
                label = stringResource(R.string.budget_plan_opt_desserts),
                on = options.includeDesserts,
                onChange = { onEvent(BudgetPlanEvent.SetIncludeDesserts(it)) },
                tag = BUDGET_PLAN_DESSERTS_TAG,
            )
        }
        Box(Modifier.padding(top = 20.dpx)) { RowDivider() }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 128.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dpx),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dpx)) {
                KioskText(
                    text = stringResource(R.string.budget_plan_over_title),
                    style = KioskTheme.typography.body.copy(fontSize = 32.spx, fontWeight = FontWeight.W600),
                )
                KioskText(
                    text = stringResource(R.string.budget_plan_over_hint, options.overCapPercent),
                    style = KioskTheme.typography.meta,
                    color = colors.muted,
                )
            }
            PlanSwitch(
                checked = options.allowOver,
                description = stringResource(R.string.budget_plan_over_title),
                onChange = { onEvent(BudgetPlanEvent.SetAllowOver(it)) },
                modifier = Modifier.testTag(BUDGET_PLAN_OVER_TAG),
            )
        }
        if (options.canReset) {
            RowDivider()
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 112.dpx)
                        .pressable(onClick = { onEvent(BudgetPlanEvent.ResetPlan) })
                        .testTag(BUDGET_PLAN_RESET_TAG)
                        .padding(horizontal = 8.dpx),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dpx),
            ) {
                KioskIcon(icon = KioskIcons.Reset, tint = colors.shortText, modifier = Modifier.size(36.dpx))
                KioskText(
                    text = stringResource(R.string.budget_plan_reset),
                    style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W700),
                    color = colors.shortText,
                    maxLines = 1,
                )
            }
        } else {
            Box(Modifier.height(16.dpx))
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
) {
    val colors = KioskTheme.colors
    val spec = motionSpec(tween<Color>(SELECT_COLOR_MILLIS, easing = KioskEasing.Emphasized))
    val background by animateColorAsState(if (on) colors.ink else colors.surfaceAlt, spec, label = "opt-bg")
    val foreground by animateColorAsState(if (on) colors.bg else colors.ink, spec, label = "opt-fg")
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            Modifier
                .pressScale(scale)
                .height(96.dpx)
                .clip(KioskTheme.shapes.pill)
                .background(background)
                .toggleable(
                    value = on,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = onChange,
                ).testTag(tag)
                .padding(start = if (on) 24.dpx else 36.dpx, end = 36.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dpx),
    ) {
        if (on) KioskIcon(icon = KioskIcons.Check, tint = colors.bg, modifier = Modifier.size(36.dpx))
        KioskText(
            text = label,
            style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W600),
            color = foreground,
            maxLines = 1,
        )
    }
}

@Composable
private fun PriceBody(price: PriceDetailsUi) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val line = KioskTheme.typography.body.copy(fontSize = 28.spx)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(2.dpx, colors.cardBorder, shape)
                .padding(horizontal = 36.dpx, vertical = 32.dpx),
        verticalArrangement = Arrangement.spacedBy(14.dpx),
    ) {
        PriceLine(stringResource(R.string.budget_plan_subtotal), price.subtotal)
        price.discount?.let { discount ->
            val label =
                price.promoLabel?.let { stringResource(R.string.budget_plan_promo_label, it) }
                    ?: stringResource(R.string.budget_plan_promo)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KioskText(
                    text = label,
                    style = line.copy(fontWeight = FontWeight.W600),
                    color = colors.accent,
                    modifier = Modifier.weight(1f),
                )
                KioskText(
                    text = stringResource(R.string.budget_plan_discount, discount),
                    style = line.copy(fontWeight = FontWeight.W700),
                    color = colors.accent,
                )
            }
        }
        PriceLine(price.taxLabel ?: stringResource(R.string.budget_plan_tax), price.tax)
        price.rounding?.let { PriceLine(stringResource(R.string.budget_plan_rounding), it) }
        price.comboSavings?.let { savings ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KioskText(
                    text = stringResource(R.string.budget_plan_combo_savings),
                    style = line.copy(fontWeight = FontWeight.W600),
                    color = colors.success,
                    modifier = Modifier.weight(1f),
                )
                KioskText(
                    text = stringResource(R.string.budget_plan_discount, savings),
                    style = line.copy(fontWeight = FontWeight.W700),
                    color = colors.success,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dpx).background(colors.cardBorder))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KioskText(
                text = stringResource(R.string.budget_plan_total),
                style = KioskTheme.typography.button,
            )
            KioskText(
                text = price.total,
                style =
                    KioskTheme.typography.section.copy(
                        fontSize = 40.spx,
                        fontWeight = FontWeight.W800,
                        letterSpacing = TOTAL_TRACKING_EM.em,
                    ),
            )
        }
    }
}

@Composable
private fun PriceLine(
    label: String,
    amount: String,
) {
    val line = KioskTheme.typography.body.copy(fontSize = 28.spx)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        KioskText(text = label, style = line, color = KioskTheme.colors.muted, modifier = Modifier.weight(1f))
        KioskText(text = amount, style = line.copy(fontWeight = FontWeight.W600))
    }
}
