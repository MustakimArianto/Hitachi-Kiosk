package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
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
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.planner.PlanKind
import com.hitachi.kiosk.ui.MenuPhoto

/** Test tag of a "Just under" / "Just over" segment. */
fun budgetPlanSegmentTag(side: PlanSide): String = "budget-plan-segment:${side.name}"

/** Test tag of the hero plan card. */
const val BUDGET_PLAN_HERO_TAG = "budget-plan-hero"

/** Test tags of the collapsible rows and the Customize row. */
const val BUDGET_PLAN_INCLUDED_TAG = "budget-plan-included"
const val BUDGET_PLAN_PRICE_TAG = "budget-plan-price"
const val BUDGET_PLAN_CUSTOMIZE_TAG = "budget-plan-customize"

/** Entrance delays of the step 2 blocks (`fadeUp 700ms`): control @200, hero @260, rows @340 / @400 / @460. */
private const val OPTIONS_DELAY_MILLIS = 200
private const val HERO_DELAY_MILLIS = 260
private const val INCLUDED_DELAY_MILLIS = 340
private const val PRICE_DELAY_MILLIS = 400
private const val CUSTOMIZE_DELAY_MILLIS = 460

/** The collapsible rows open over 620 ms (`grid-template-rows 620ms`); the chevron turns over 500 ms. */
private const val EXPAND_MILLIS = 620
private const val CHEVRON_MILLIS = 500
private const val HALF_TURN = 180f

/** The hero card's shadow: `0 24px 48px` at 8 % for an exact match, 6 % otherwise. */
private const val EXACT_SHADOW_ALPHA = 0.08f
private const val HERO_SHADOW_ALPHA = 0.06f

/** Letter spacing in em: hero title 40 px (−0.6 px), hero total 112 px (−3.4 px), item names 30 px (−0.3 px). */
private const val HERO_TITLE_TRACKING_EM = -0.6f / 40
private const val HERO_TOTAL_TRACKING_EM = -3.4f / 112
private const val HERO_TOTAL_LINE_EM = 128f / 112
private const val ITEM_NAME_TRACKING_EM = -0.3f / 30
private const val TOTAL_TRACKING_EM = -1f / 40
private const val TIGHT_LINE_EM = 1.1f
private const val ITEM_LINE_EM = 1.15f
private const val NOTE_LINE_EM = 1.4f

/** Step 2 "Your plan" (FR-032): the segmented control, the hero card, the collapsible rows and "Customize". */
@Composable
internal fun StepTwo(
    state: BudgetPlanUiState,
    plan: PlanUi,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    Column {
        if (plan.options.isNotEmpty()) PlanOptions(plan.options, onEvent)
        HeroCard(
            plan = plan,
            people = state.people,
            modifier = Modifier.padding(top = if (plan.options.isEmpty()) 56.dpx else 32.dpx),
        )
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
            IncludedBody(plan.groups)
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
        CustomizeRow(onClick = { onEvent(BudgetPlanEvent.OpenCustomize) })
    }
}

@Composable
private fun PlanOptions(
    options: List<PlanOptionUi>,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val enter = animateEntrance(FADE_UP_MILLIS, OPTIONS_DELAY_MILLIS)
    SegmentedControl(
        items = options,
        selected = { it.selected },
        onSelect = { onEvent(BudgetPlanEvent.SelectPlan(it.side)) },
        description = stringResource(R.string.budget_plan_options),
        segmentHeight = 104.dpx,
        trackShape = RoundedCornerShape(56.dpx),
        segmentShape = RoundedCornerShape(48.dpx),
        tag = { budgetPlanSegmentTag(it.side) },
        modifier = Modifier.padding(top = 56.dpx).fadeUp(enter, UP_DISTANCE.dpx),
    ) { option, _ ->
        KioskText(
            text =
                stringResource(
                    if (option.side ==
                        PlanSide.UNDER
                    ) {
                        R.string.budget_plan_just_under
                    } else {
                        R.string.budget_plan_just_over
                    },
                ),
            style = KioskTheme.typography.body.copy(fontWeight = FontWeight.W700),
            maxLines = 1,
        )
        KioskText(
            text = option.total,
            style = KioskTheme.typography.meta.copy(fontSize = 24.spx),
            color = KioskTheme.colors.muted,
            maxLines = 1,
        )
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
                stringResource(R.string.budget_plan_hero_generous),
                stringResource(R.string.budget_plan_badge_generous),
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
        PlanNote.Strict -> stringResource(R.string.budget_plan_note_strict)
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
private fun IncludedBody(groups: List<PlanGroupUi>) {
    val shape = KioskTheme.shapes.card
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(KioskTheme.colors.surface)
                .border(2.dpx, KioskTheme.colors.cardBorder, shape)
                .padding(start = 32.dpx, end = 32.dpx, top = 4.dpx, bottom = 24.dpx),
    ) {
        groups.forEach { group ->
            Column(modifier = Modifier.padding(top = 20.dpx)) {
                GroupLabel(group.label, Modifier.padding(bottom = 4.dpx))
                group.items.forEach { item -> IncludedRow(item) }
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
                    stringResource(R.string.budget_plan_group_child, label.number)
                }

                is PlanGroupLabel.Sharing -> {
                    stringResource(
                        R.string.budget_plan_group_sharing,
                        label.first,
                        label.second,
                    )
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
        PlanItemMeta.Main -> {
            stringResource(R.string.budget_plan_meta_main)
        }

        PlanItemMeta.KidMain -> {
            stringResource(R.string.budget_plan_meta_kid_main)
        }

        PlanItemMeta.Drink -> {
            stringResource(R.string.budget_plan_meta_drink)
        }

        is PlanItemMeta.Meal -> {
            meta.includes?.let { stringResource(R.string.budget_plan_meta_meal_includes, it) }
                ?: stringResource(R.string.budget_plan_meta_meal)
        }

        is PlanItemMeta.MealForTwo -> {
            meta.includes?.let { stringResource(R.string.budget_plan_meta_meal_for_two_includes, it) }
                ?: stringResource(R.string.budget_plan_meta_meal_for_two)
        }

        PlanItemMeta.Side -> {
            stringResource(R.string.budget_plan_meta_side)
        }

        PlanItemMeta.Dessert -> {
            stringResource(R.string.budget_plan_meta_dessert)
        }
    }

@Composable
private fun IncludedRow(item: PlanItemUi) {
    val colors = KioskTheme.colors
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dpx).padding(vertical = 12.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dpx),
        ) {
            MenuPhoto(
                imageUrl = item.imageUrl,
                tone = item.tone,
                contentDescription = null,
                shape = KioskTheme.shapes.small,
                modifier = Modifier.size(80.dpx),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dpx)) {
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
            KioskText(
                text = item.price,
                style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
                maxLines = 1,
            )
        }
        RowDivider()
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

@Composable
private fun CustomizeRow(onClick: () -> Unit) {
    val colors = KioskTheme.colors
    val enter = animateEntrance(FADE_UP_MILLIS, CUSTOMIZE_DELAY_MILLIS)
    val shape = RoundedCornerShape(36.dpx)
    Row(
        modifier =
            Modifier
                .padding(top = 16.dpx)
                .fadeUp(enter, UP_DISTANCE.dpx)
                .fillMaxWidth()
                .pressable(onClick = onClick)
                .testTag(BUDGET_PLAN_CUSTOMIZE_TAG)
                .heightIn(min = 112.dpx)
                .clip(shape)
                .background(colors.surfaceAlt)
                .padding(horizontal = 36.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        KioskIcon(icon = KioskIcons.Sliders, tint = colors.ink, modifier = Modifier.size(44.dpx))
        KioskText(
            text = stringResource(R.string.budget_plan_customize),
            style = KioskTheme.typography.body.copy(fontSize = 32.spx, fontWeight = FontWeight.W700),
            modifier = Modifier.weight(1f),
        )
        KioskText(
            text = stringResource(R.string.budget_plan_customize_hint),
            style = KioskTheme.typography.meta,
            color = colors.muted,
            maxLines = 1,
        )
    }
}
