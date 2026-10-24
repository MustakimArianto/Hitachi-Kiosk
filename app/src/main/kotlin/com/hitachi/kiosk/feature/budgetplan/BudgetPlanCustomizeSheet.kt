package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.animateOnEnter
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.planner.PlanTolerance

/** Test tag of the Customize sheet. */
const val BUDGET_PLAN_SHEET_TAG = "budget-plan-sheet"

/** Test tags of the sheet's controls. */
const val BUDGET_PLAN_SHEET_CLOSE_TAG = "budget-plan-sheet-close"
const val BUDGET_PLAN_SIDES_TAG = "budget-plan-sides"
const val BUDGET_PLAN_DESSERTS_TAG = "budget-plan-desserts"
const val BUDGET_PLAN_RESET_TAG = "budget-plan-reset"
const val BUDGET_PLAN_DONE_TAG = "budget-plan-done"

/** Test tag of a tolerance segment. */
fun budgetPlanToleranceTag(tolerance: PlanTolerance): String = "budget-plan-tolerance:${tolerance.name}"

/** Test tag of an item's "Swap" button. */
fun budgetPlanSwapTag(target: SwapTarget): String = "budget-plan-swap:${target.pickIndex}:${target.part.name}"

/** The sheet's top edge, in design px from the top of the screen (`top: 240px`). */
private const val SHEET_TOP_PX = 240

/** A swapped item slides in from 28 dpx to the right over 420 ms (`swapA`). */
private const val SWAP_IN_MILLIS = 420
private const val SWAP_IN_PX = 28

private const val SHEET_TITLE_TRACKING_EM = -1.4f / 52
private const val SHEET_TITLE_LINE_EM = 1.05f
private const val SWAP_TITLE_TRACKING_EM = -0.8f / 40
private const val ITEM_NAME_TRACKING_EM = -0.3f / 30
private const val ITEM_LINE_EM = 1.15f
private const val HINT_LINE_EM = 1.4f

/**
 * The Customize sheet (DEV-043a, FR-032, TC-170): the live total, the sides and desserts toggles, the over-budget
 * tolerance, "Swap" per item with automatic rebalancing, "Reset to suggested" and "Done".
 */
@Composable
internal fun CustomizeSheet(
    customize: CustomizeUi,
    plan: PlanUi,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val close = { onEvent(BudgetPlanEvent.CloseCustomize) }
    SheetOverlay(
        scrimDescription = stringResource(R.string.budget_plan_close),
        onDismiss = close,
        riseMillis = KioskMotion.BudgetPlan.CUSTOMIZE_SHEET_UP_MILLIS,
        sheetModifier = Modifier.fillMaxSize().padding(top = SHEET_TOP_PX.dpx).testTag(BUDGET_PLAN_SHEET_TAG),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SheetHeader(plan.hero, onClose = close)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 72.dpx, end = 72.dpx, top = 8.dpx, bottom = 24.dpx),
            ) {
                OptionsCard(customize, onEvent)
                SwapList(plan, onEvent)
            }
            SheetFooter(canReset = customize.canReset, onEvent = onEvent)
        }
    }
}

@Composable
private fun SheetHeader(
    hero: HeroUi,
    onClose: () -> Unit,
) {
    val colors = KioskTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(start = 72.dpx, end = 72.dpx, top = 48.dpx, bottom = 24.dpx),
        horizontalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dpx)) {
            KioskText(
                text = stringResource(R.string.budget_plan_sheet_title),
                style =
                    KioskTheme.typography.section.copy(
                        fontSize = 52.spx,
                        fontWeight = FontWeight.W800,
                        letterSpacing = SHEET_TITLE_TRACKING_EM.em,
                        lineHeight = SHEET_TITLE_LINE_EM.em,
                    ),
            )
            KioskText(
                text = stringResource(R.string.budget_plan_sheet_summary, hero.total, heroBadge(hero)),
                style = KioskTheme.typography.meta.copy(fontSize = 28.spx),
                color = colors.muted,
            )
        }
        val description = stringResource(R.string.budget_plan_close)
        Box(
            modifier =
                Modifier
                    .pressable(onClick = onClose)
                    .size(96.dpx)
                    .background(colors.surfaceAlt, CircleShape)
                    .semantics { contentDescription = description }
                    .testTag(BUDGET_PLAN_SHEET_CLOSE_TAG),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.Close, tint = colors.ink, modifier = Modifier.size(40.dpx))
        }
    }
}

@Composable
private fun OptionsCard(
    customize: CustomizeUi,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(2.dpx, colors.cardBorder, shape)
                .padding(horizontal = 36.dpx),
    ) {
        ToggleRow(
            label = stringResource(R.string.budget_plan_include_sides),
            hint = stringResource(R.string.budget_plan_include_sides_hint),
            checked = customize.includeSides,
            onChange = { onEvent(BudgetPlanEvent.SetIncludeSides(it)) },
            tag = BUDGET_PLAN_SIDES_TAG,
        )
        ToggleRow(
            label = stringResource(R.string.budget_plan_include_desserts),
            hint = stringResource(R.string.budget_plan_include_desserts_hint),
            checked = customize.includeDesserts,
            onChange = { onEvent(BudgetPlanEvent.SetIncludeDesserts(it)) },
            tag = BUDGET_PLAN_DESSERTS_TAG,
        )
        Column(modifier = Modifier.padding(top = 28.dpx, bottom = 32.dpx)) {
            KioskText(
                text = stringResource(R.string.budget_plan_over_budget),
                style = KioskTheme.typography.body.copy(fontSize = 32.spx, fontWeight = FontWeight.W600),
            )
            KioskText(
                text = stringResource(R.string.budget_plan_over_budget_hint),
                style = KioskTheme.typography.meta,
                color = colors.muted,
                modifier = Modifier.padding(top = 2.dpx, bottom = 20.dpx),
            )
            val strict = stringResource(R.string.budget_plan_strict)
            val upToCap = stringResource(R.string.budget_plan_up_to_cap, customize.overCapPercent)
            SegmentedControl(
                items = PlanTolerance.entries,
                selected = { it == customize.tolerance },
                onSelect = { if (it != customize.tolerance) onEvent(BudgetPlanEvent.SetTolerance(it)) },
                description = stringResource(R.string.budget_plan_tolerance),
                segmentHeight = 88.dpx,
                trackShape = RoundedCornerShape(52.dpx),
                segmentShape = RoundedCornerShape(44.dpx),
                tag = ::budgetPlanToleranceTag,
            ) { tolerance, _ ->
                KioskText(
                    text = if (tolerance == PlanTolerance.STRICT) strict else upToCap,
                    style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
                    maxLines = 1,
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ToggleRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 126.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dpx),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dpx)) {
                KioskText(
                    text = label,
                    style = KioskTheme.typography.body.copy(fontSize = 32.spx, fontWeight = FontWeight.W600),
                )
                KioskText(text = hint, style = KioskTheme.typography.meta, color = KioskTheme.colors.muted)
            }
            PlanSwitch(checked = checked, description = label, onChange = onChange, modifier = Modifier.testTag(tag))
        }
        RowDivider()
    }
}

@Composable
private fun SwapList(
    plan: PlanUi,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    Column {
        KioskText(
            text = stringResource(R.string.budget_plan_swap_items),
            style = KioskTheme.typography.section.copy(fontSize = 40.spx, letterSpacing = SWAP_TITLE_TRACKING_EM.em),
            modifier = Modifier.padding(top = 40.dpx, bottom = 4.dpx),
        )
        KioskText(
            text = stringResource(R.string.budget_plan_swap_hint),
            style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W400, lineHeight = HINT_LINE_EM.em),
            color = colors.muted,
            modifier = Modifier.padding(bottom = 8.dpx),
        )
        plan.groups.forEach { group ->
            Column(modifier = Modifier.padding(top = 20.dpx)) {
                GroupLabel(group.label, Modifier.padding(bottom = 8.dpx))
                val shape = RoundedCornerShape(32.dpx)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(colors.surface)
                            .border(2.dpx, colors.cardBorder, shape)
                            .padding(horizontal = 28.dpx),
                ) {
                    group.items.forEach { item ->
                        SwapRow(
                            item = item,
                            justSwapped = item.swap != null && item.swap == plan.lastSwap,
                            swapSerial = plan.swapSerial,
                            onSwap = { target -> onEvent(BudgetPlanEvent.Swap(target)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwapRow(
    item: PlanItemUi,
    justSwapped: Boolean,
    swapSerial: Int,
    onSwap: (SwapTarget) -> Unit,
) {
    val colors = KioskTheme.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 118.dpx).padding(vertical = 12.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dpx),
        ) {
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
                        text = stringResource(R.string.budget_plan_item_meta_price, itemMeta(item.meta), item.price),
                        style = KioskTheme.typography.meta.copy(fontSize = 24.spx),
                        color = colors.muted,
                    )
                }
            }
            item.swap?.let { target ->
                SwapButton(item.name, onClick = { onSwap(target) }, tag = budgetPlanSwapTag(target))
            }
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
                .height(80.dpx)
                .background(colors.surfaceAlt, KioskTheme.shapes.pill)
                .padding(start = 22.dpx, end = 28.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dpx),
    ) {
        KioskIcon(icon = KioskIcons.Swap, tint = colors.ink, modifier = Modifier.size(30.dpx))
        KioskText(
            text = stringResource(R.string.budget_plan_swap),
            style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W600),
            maxLines = 1,
        )
    }
}

@Composable
private fun SheetFooter(
    canReset: Boolean,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(start = 72.dpx, end = 72.dpx, top = 24.dpx, bottom = 48.dpx),
        horizontalArrangement = Arrangement.spacedBy(16.dpx),
    ) {
        SheetButton(
            onClick = { onEvent(BudgetPlanEvent.ResetPlan) },
            background = colors.bg,
            enabled = canReset,
            outline = colors.ink,
            modifier = Modifier.weight(1f).height(136.dpx).testTag(BUDGET_PLAN_RESET_TAG),
        ) {
            KioskText(
                text = stringResource(R.string.budget_plan_reset),
                style = KioskTheme.typography.body.copy(fontSize = 32.spx, fontWeight = FontWeight.W700),
                maxLines = 1,
            )
        }
        SheetButton(
            onClick = { onEvent(BudgetPlanEvent.CloseCustomize) },
            background = colors.accent,
            modifier = Modifier.weight(1f).height(136.dpx).testTag(BUDGET_PLAN_DONE_TAG),
        ) {
            KioskText(
                text = stringResource(R.string.budget_plan_done),
                style = KioskTheme.typography.button,
                color = colors.surface,
                maxLines = 1,
            )
        }
    }
}
