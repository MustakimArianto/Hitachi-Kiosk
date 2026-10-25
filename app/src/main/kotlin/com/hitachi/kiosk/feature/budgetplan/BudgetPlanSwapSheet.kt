package com.hitachi.kiosk.feature.budgetplan

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.ui.MenuPhoto

/** Test tag of the swap picker. */
const val BUDGET_PLAN_SWAP_SHEET_TAG = "budget-plan-swap-sheet"

/** Test tags of the picker's close button and "Done". */
const val BUDGET_PLAN_SWAP_CLOSE_TAG = "budget-plan-swap-close"
const val BUDGET_PLAN_SWAP_DONE_TAG = "budget-plan-swap-done"

/** Test tag of a picker row. */
fun budgetPlanSwapRowTag(index: Int): String = "budget-plan-swap-row:$index"

/** The sheet's top edge, in design px from the top of the screen (`top: 240px`). */
private const val SHEET_TOP_PX = 240

private const val TITLE_TRACKING_EM = -1.2f / 48
private const val TITLE_LINE_EM = 1.05f
private const val NAME_TRACKING_EM = -0.3f / 30
private const val NAME_LINE_EM = 1.15f

/**
 * The swap picker (`S_BudgetPas_swapOpen`, FR-032, DEV-109, TC-200): the current choice and its same-role
 * alternatives, each with the price change and the plan total after it. A tap on a row swaps at once and the sheet
 * stays open; "Done" closes it.
 */
@Composable
internal fun SwapSheet(
    swap: SwapSheetUi,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val close = { onEvent(BudgetPlanEvent.CloseSwap) }
    val title = swapTitleText(swap.title)
    SheetOverlay(
        scrimDescription = stringResource(R.string.budget_plan_close),
        onDismiss = close,
        riseMillis = KioskMotion.BudgetPlan.CUSTOMIZE_SHEET_UP_MILLIS,
        sheetModifier =
            Modifier
                .fillMaxSize()
                .padding(top = SHEET_TOP_PX.dpx)
                .semantics { contentDescription = title }
                .testTag(BUDGET_PLAN_SWAP_SHEET_TAG),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SwapHeader(title, swap.planTotal, close)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 72.dpx, end = 72.dpx, top = 8.dpx, bottom = 24.dpx),
            ) {
                val shape = KioskTheme.shapes.card
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(KioskTheme.colors.surface)
                            .border(2.dpx, KioskTheme.colors.cardBorder, shape)
                            .padding(horizontal = 28.dpx),
                ) {
                    swap.rows.forEachIndexed { index, row ->
                        SwapRow(row, onClick = { onEvent(BudgetPlanEvent.PickAlternative(index)) }, index = index)
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        KioskTheme.colors.bg,
                    ).padding(start = 72.dpx, end = 72.dpx, top = 24.dpx, bottom = 48.dpx),
            ) {
                SheetButton(
                    onClick = close,
                    background = KioskTheme.colors.accent,
                    modifier = Modifier.fillMaxWidth().height(136.dpx).testTag(BUDGET_PLAN_SWAP_DONE_TAG),
                ) {
                    KioskText(
                        text = stringResource(R.string.budget_plan_done),
                        style = KioskTheme.typography.button,
                        color = KioskTheme.colors.surface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun swapTitleText(title: SwapTitle): String =
    when (title) {
        is SwapTitle.Adult -> stringResource(R.string.budget_plan_swap_title_adult, title.number)
        is SwapTitle.Child -> stringResource(R.string.budget_plan_swap_title_child, title.number)
        SwapTitle.SharedMeal -> stringResource(R.string.budget_plan_swap_title_shared)
        SwapTitle.SharedSide -> stringResource(R.string.budget_plan_swap_title_side)
        SwapTitle.SharedDessert -> stringResource(R.string.budget_plan_swap_title_dessert)
    }

@Composable
private fun SwapHeader(
    title: String,
    planTotal: String,
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
                text = title,
                style =
                    KioskTheme.typography.section.copy(
                        fontSize = 48.spx,
                        fontWeight = FontWeight.W800,
                        letterSpacing = TITLE_TRACKING_EM.em,
                        lineHeight = TITLE_LINE_EM.em,
                    ),
            )
            KioskText(
                text = stringResource(R.string.budget_plan_swap_plan_total, planTotal),
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
                    .testTag(BUDGET_PLAN_SWAP_CLOSE_TAG),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.Close, tint = colors.ink, modifier = Modifier.size(40.dpx))
        }
    }
}

@Composable
private fun SwapRow(
    row: SwapRowUi,
    onClick: () -> Unit,
    index: Int,
) {
    val colors = KioskTheme.colors
    val name = row.name ?: stringResource(R.string.budget_plan_swap_separate)
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .pressable(onClick = onClick)
                    .semantics(mergeDescendants = true) { contentDescription = name }
                    .testTag(budgetPlanSwapRowTag(index))
                    .heightIn(min = 136.dpx)
                    .padding(vertical = 16.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dpx),
        ) {
            MenuPhoto(
                imageUrl = row.imageUrl,
                tone = row.tone,
                contentDescription = null,
                shape = KioskTheme.shapes.small,
                modifier = Modifier.size(88.dpx),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dpx)) {
                KioskText(
                    text = name,
                    style =
                        KioskTheme.typography.body.copy(
                            fontWeight = FontWeight.W600,
                            letterSpacing = NAME_TRACKING_EM.em,
                            lineHeight = NAME_LINE_EM.em,
                        ),
                )
                KioskText(
                    text = itemMeta(row.meta),
                    style = KioskTheme.typography.meta.copy(fontSize = 24.spx),
                    color = colors.muted,
                )
                if (row.current) {
                    KioskText(
                        text = stringResource(R.string.budget_plan_swap_current),
                        style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W700),
                        color = colors.success,
                        modifier =
                            Modifier
                                .background(
                                    colors.successBg,
                                    KioskTheme.shapes.pill,
                                ).padding(horizontal = 16.dpx, vertical = 4.dpx),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dpx)) {
                KioskText(
                    text =
                        when (val change = row.change) {
                            PriceChange.Same -> stringResource(R.string.budget_plan_swap_same_price)
                            is PriceChange.Up -> stringResource(R.string.budget_plan_swap_up, change.amount)
                            is PriceChange.Down -> stringResource(R.string.budget_plan_swap_down, change.amount)
                        },
                    style = KioskTheme.typography.meta.copy(fontSize = 24.spx),
                    color = colors.muted,
                    maxLines = 1,
                )
                KioskText(
                    text = stringResource(R.string.budget_plan_swap_row_total, row.total),
                    style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
                    maxLines = 1,
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(
                            48.dpx,
                        ).background(if (row.current) colors.success else colors.surfaceAlt, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (row.current) {
                    KioskIcon(
                        icon = KioskIcons.Check,
                        tint = colors.surface,
                        modifier = Modifier.size(30.dpx),
                    )
                }
            }
        }
        RowDivider()
    }
}
