package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.RollDirection
import com.hitachi.kiosk.core.designsystem.component.RollingText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** Test tag of a quick budget chip. */
fun budgetPlanChipTag(amount: Long): String = "budget-plan-chip:$amount"

/** Test tag of the "Other" / "Edit" chip. */
const val BUDGET_PLAN_OTHER_TAG = "budget-plan-other"

/** Test tag of the budget amount. */
const val BUDGET_PLAN_BUDGET_TAG = "budget-plan-budget"

/** Test tags of the steppers' buttons and counts. */
const val BUDGET_PLAN_ADULTS_LESS_TAG = "budget-plan-adults-less"
const val BUDGET_PLAN_ADULTS_MORE_TAG = "budget-plan-adults-more"
const val BUDGET_PLAN_ADULTS_COUNT_TAG = "budget-plan-adults-count"
const val BUDGET_PLAN_CHILDREN_LESS_TAG = "budget-plan-children-less"
const val BUDGET_PLAN_CHILDREN_MORE_TAG = "budget-plan-children-more"
const val BUDGET_PLAN_CHILDREN_COUNT_TAG = "budget-plan-children-count"

/** Test tag of "Set group to 10". */
const val BUDGET_PLAN_FIT_GROUP_TAG = "budget-plan-fit-group"

/** Entrance delays of the step 1 cards (`fadeUp 700ms @220 / @300`, the group card @80). */
private const val BUDGET_CARD_DELAY_MILLIS = 220
private const val PEOPLE_CARD_DELAY_MILLIS = 300
private const val GROUP_CARD_DELAY_MILLIS = 80

/** The stepper counts roll over 380 ms. */
private const val COUNT_ROLL_MILLIS = 380

/** "+" fades to 30 % at its maximum, "−" to 40 % at its minimum. */
private const val INCREASE_DISABLED_ALPHA = 0.3f

/** Letter spacing in em: the 112 px amount (−3.4 px), the 40 px labels (−0.5 px), the 52 px group title (−1.4 px). */
private const val AMOUNT_TRACKING_EM = -3.4f / 112
private const val LABEL_TRACKING_EM = -0.5f / 40
private const val GROUP_TITLE_TRACKING_EM = -1.4f / 52
private const val AMOUNT_LINE_EM = 128f / 112
private const val COUNT_LINE_EM = 64f / 52
private const val GROUP_TITLE_LINE_EM = 1.05f

/** Step 1 "Your table" (FR-032): the budget card, the people card and, above the limit, the group card. */
@Composable
internal fun StepOne(
    state: BudgetPlanUiState,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    Column {
        BudgetCard(state, onEvent)
        PeopleCard(state, onEvent)
        if (state.groupTooLarge) GroupCard(state.maxGroupSize, onFit = { onEvent(BudgetPlanEvent.FitGroup) })
    }
}

/** The white step 1 card: 40 dpx radius and the `cardBorder` outline. */
@Composable
private fun Modifier.planCard(): Modifier {
    val shape = KioskTheme.shapes.card
    return fillMaxWidth()
        .clip(shape)
        .background(KioskTheme.colors.surface)
        .border(2.dpx, KioskTheme.colors.cardBorder, shape)
}

@Composable
private fun BudgetCard(
    state: BudgetPlanUiState,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val enter = animateEntrance(FADE_UP_MILLIS, BUDGET_CARD_DELAY_MILLIS)
    val description = stringResource(R.string.budget_plan_budget_group)
    Column(
        modifier =
            Modifier
                .padding(top = 72.dpx)
                .fadeUp(enter, UP_DISTANCE.dpx)
                .planCard()
                .padding(40.dpx),
    ) {
        KioskText(
            text = stringResource(R.string.budget_plan_your_budget),
            style = typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
            color = colors.muted,
        )
        RollingText(
            text = state.budget,
            style =
                typography.display.copy(
                    fontSize = 112.spx,
                    letterSpacing = AMOUNT_TRACKING_EM.em,
                    lineHeight = AMOUNT_LINE_EM.em,
                ),
            durationMillis = AMOUNT_ROLL_MILLIS,
            modifier =
                Modifier
                    .padding(top = 8.dpx)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(BUDGET_PLAN_BUDGET_TAG),
        )
        KioskText(
            text = stringResource(R.string.budget_plan_budget_caption),
            style = typography.meta,
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dpx),
        )
        Row(
            modifier =
                Modifier
                    .padding(top = 36.dpx)
                    .fillMaxWidth()
                    .selectableGroup()
                    .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(12.dpx),
        ) {
            state.chips.forEach { chip ->
                BudgetChip(
                    label =
                        chip.thousands?.let { stringResource(R.string.budget_plan_chip_thousands, it) }
                            ?: chip.formatted,
                    selected = chip.selected,
                    onClick = { if (!chip.selected) onEvent(BudgetPlanEvent.PickBudget(chip.amount)) },
                    modifier = Modifier.weight(1f).testTag(budgetPlanChipTag(chip.amount)),
                )
            }
            BudgetChip(
                label =
                    stringResource(
                        if (state.otherChip ==
                            OtherChip.EDIT
                        ) {
                            R.string.budget_plan_chip_edit
                        } else {
                            R.string.budget_plan_chip_other
                        },
                    ),
                selected = state.otherChip == OtherChip.EDIT,
                onClick = { onEvent(BudgetPlanEvent.OpenKeypad) },
                modifier = Modifier.weight(1f).testTag(BUDGET_PLAN_OTHER_TAG),
            )
        }
    }
}

@Composable
private fun BudgetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val spec = motionSpec(tween<Color>(SELECT_COLOR_MILLIS, easing = KioskEasing.Emphasized))
    val background by animateColorAsState(if (selected) colors.ink else colors.surfaceAlt, spec, label = "chip-bg")
    val foreground by animateColorAsState(if (selected) colors.bg else colors.ink, spec, label = "chip-fg")
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            modifier
                .pressScale(scale)
                .height(96.dpx)
                .clip(KioskTheme.shapes.pill)
                .background(background)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = label,
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
            color = foreground,
            maxLines = 1,
        )
    }
}

@Composable
private fun PeopleCard(
    state: BudgetPlanUiState,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val enter = animateEntrance(FADE_UP_MILLIS, PEOPLE_CARD_DELAY_MILLIS)
    val description = stringResource(R.string.budget_plan_who_is_eating)
    Column(
        modifier =
            Modifier
                .padding(top = 40.dpx)
                .fadeUp(enter, UP_DISTANCE.dpx)
                .planCard()
                .semantics { contentDescription = description }
                .padding(horizontal = 40.dpx, vertical = 8.dpx),
    ) {
        StepperRow(
            label = stringResource(R.string.budget_plan_adults),
            age = stringResource(R.string.budget_plan_adults_age),
            count = state.adults,
            roll = state.adultsRoll,
            canDecrease = state.canDecreaseAdults,
            canIncrease = state.canIncreaseAdults,
            onDecrease = { onEvent(BudgetPlanEvent.DecreaseAdults) },
            onIncrease = { onEvent(BudgetPlanEvent.IncreaseAdults) },
            tags = StepperTags(BUDGET_PLAN_ADULTS_LESS_TAG, BUDGET_PLAN_ADULTS_COUNT_TAG, BUDGET_PLAN_ADULTS_MORE_TAG),
        )
        StepperRow(
            label = stringResource(R.string.budget_plan_children),
            age = stringResource(R.string.budget_plan_children_age),
            count = state.children,
            roll = state.childrenRoll,
            canDecrease = state.canDecreaseChildren,
            canIncrease = state.canIncreaseChildren,
            onDecrease = { onEvent(BudgetPlanEvent.DecreaseChildren) },
            onIncrease = { onEvent(BudgetPlanEvent.IncreaseChildren) },
            tags =
                StepperTags(
                    BUDGET_PLAN_CHILDREN_LESS_TAG,
                    BUDGET_PLAN_CHILDREN_COUNT_TAG,
                    BUDGET_PLAN_CHILDREN_MORE_TAG,
                ),
        )
    }
}

private class StepperTags(
    val decrease: String,
    val count: String,
    val increase: String,
)

@Suppress("LongParameterList")
@Composable
private fun StepperRow(
    label: String,
    age: String,
    count: Int,
    roll: RollDirection,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    tags: StepperTags,
) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dpx).semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dpx),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dpx)) {
                KioskText(
                    text = label,
                    style = typography.section.copy(fontSize = 40.spx, letterSpacing = LABEL_TRACKING_EM.em),
                )
                KioskText(text = age, style = typography.meta, color = colors.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dpx)) {
                RoundStepButton(
                    icon = KioskIcons.Minus,
                    background = colors.surfaceAlt,
                    tint = colors.ink,
                    description = stringResource(R.string.budget_plan_fewer, label),
                    enabled = canDecrease,
                    disabledAlpha = DISABLED_ALPHA,
                    onClick = onDecrease,
                    modifier = Modifier.testTag(tags.decrease),
                )
                Box(
                    modifier =
                        Modifier
                            .width(88.dpx)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(tags.count),
                    contentAlignment = Alignment.Center,
                ) {
                    RollingText(
                        text = count.toString(),
                        style =
                            typography.section.copy(
                                fontSize = 52.spx,
                                lineHeight = COUNT_LINE_EM.em,
                                textAlign = TextAlign.Center,
                            ),
                        durationMillis = COUNT_ROLL_MILLIS,
                        direction = roll,
                    )
                }
                RoundStepButton(
                    icon = KioskIcons.Plus,
                    background = colors.ink,
                    tint = colors.bg,
                    description = stringResource(R.string.budget_plan_more, label),
                    enabled = canIncrease,
                    disabledAlpha = INCREASE_DISABLED_ALPHA,
                    onClick = onIncrease,
                    modifier = Modifier.testTag(tags.increase),
                )
            }
        }
        RowDivider()
    }
}

@Suppress("LongParameterList")
@Composable
private fun RoundStepButton(
    icon: ImageVector,
    background: Color,
    tint: Color,
    description: String,
    enabled: Boolean,
    disabledAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .disabledFade(enabled, disabledAlpha)
                .pressable(onClick = onClick, enabled = enabled)
                .size(96.dpx)
                .background(background, CircleShape)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        KioskIcon(icon = icon, tint = tint, modifier = Modifier.size(40.dpx))
    }
}

/** "Please order in smaller groups" (FR-032 AC11, `S_BudgetPas_group`). */
@Composable
private fun GroupCard(
    maxGroupSize: Int,
    onFit: () -> Unit,
) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val enter = animateEntrance(FADE_UP_MILLIS, GROUP_CARD_DELAY_MILLIS)
    Column(
        modifier =
            Modifier
                .padding(top = 32.dpx)
                .fadeUp(enter, UP_DISTANCE.dpx)
                .planCard()
                .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Assertive }
                .padding(40.dpx),
        verticalArrangement = Arrangement.spacedBy(12.dpx),
    ) {
        KioskText(
            text = stringResource(R.string.budget_plan_group_tag),
            style = typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W600),
            color = colors.shortText,
        )
        KioskText(
            text = stringResource(R.string.budget_plan_group_title),
            style =
                typography.section.copy(
                    fontSize = 52.spx,
                    fontWeight = FontWeight.W800,
                    letterSpacing = GROUP_TITLE_TRACKING_EM.em,
                    lineHeight = GROUP_TITLE_LINE_EM.em,
                ),
        )
        KioskText(
            text = pluralStringResource(R.plurals.budget_plan_group_body, maxGroupSize, maxGroupSize),
            style = typography.body,
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dpx),
        )
        SheetButton(
            onClick = onFit,
            background = colors.ink,
            shape = RoundedCornerShape(32.dpx),
            modifier = Modifier.padding(top = 16.dpx).height(96.dpx).testTag(BUDGET_PLAN_FIT_GROUP_TAG),
        ) {
            KioskText(
                text = stringResource(R.string.budget_plan_group_fit, maxGroupSize),
                style = typography.body.copy(fontWeight = FontWeight.W700),
                color = colors.bg,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 36.dpx),
            )
        }
    }
}
