package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** Test tag of the keypad sheet. */
const val BUDGET_PLAN_KEYPAD_TAG = "budget-plan-keypad"

/** Test tag of the typed amount. */
const val BUDGET_PLAN_KEYPAD_AMOUNT_TAG = "budget-plan-keypad-amount"

/** Test tag of the "Minimum Rp 1.000" message under the amount. */
const val BUDGET_PLAN_KEYPAD_ERROR_TAG = "budget-plan-keypad-error"

/** Test tags of "Use this budget" and "Cancel". */
const val BUDGET_PLAN_KEYPAD_USE_TAG = "budget-plan-keypad-use"
const val BUDGET_PLAN_KEYPAD_CANCEL_TAG = "budget-plan-keypad-cancel"

/** Test tag of a keypad key. */
fun budgetPlanKeyTag(key: KeypadKey): String = "budget-plan-key:${key.name}"

/** The keypad sheet rises like the Customize sheet (`sheetUp 620ms`). */
private const val KEYPAD_UP_MILLIS = 620
private const val KEYS_PER_ROW = 3
private const val AMOUNT_TRACKING_EM = -3f / 96
private const val AMOUNT_LINE_EM = 1.05f

/** The "Other" keypad (FR-032, TC-171): an exact amount, floored to the nearest Rp 100 when used. */
@Composable
internal fun KeypadSheet(
    keypad: KeypadUi,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val close = { onEvent(BudgetPlanEvent.CloseKeypad) }
    SheetOverlay(
        scrimDescription = stringResource(R.string.budget_plan_keypad_close),
        onDismiss = close,
        riseMillis = KEYPAD_UP_MILLIS,
        sheetModifier = Modifier.fillMaxWidth().testTag(BUDGET_PLAN_KEYPAD_TAG),
    ) {
        Column(modifier = Modifier.padding(start = 72.dpx, end = 72.dpx, top = 48.dpx, bottom = 48.dpx)) {
            KioskText(
                text = stringResource(R.string.budget_plan_keypad_title),
                style = KioskTheme.typography.meta,
                color = colors.muted,
            )
            KioskText(
                text = keypad.amount,
                style =
                    KioskTheme.typography.display.copy(
                        fontSize = 96.spx,
                        letterSpacing = AMOUNT_TRACKING_EM.em,
                        lineHeight = AMOUNT_LINE_EM.em,
                    ),
                color =
                    when {
                        keypad.tooLow -> colors.shortText
                        keypad.empty -> colors.placeholder
                        else -> colors.ink
                    },
                modifier =
                    Modifier
                        .padding(top = 8.dpx)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(BUDGET_PLAN_KEYPAD_AMOUNT_TAG),
            )
            if (keypad.tooLow) {
                KioskText(
                    text = stringResource(R.string.budget_plan_keypad_min),
                    style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
                    color = colors.shortText,
                    modifier =
                        Modifier
                            .padding(top = 4.dpx)
                            .semantics { liveRegion = LiveRegionMode.Assertive }
                            .testTag(BUDGET_PLAN_KEYPAD_ERROR_TAG),
                )
            }
            KioskText(
                text = stringResource(R.string.budget_plan_keypad_caption),
                style = KioskTheme.typography.meta,
                color = colors.muted,
                modifier = Modifier.padding(top = 8.dpx),
            )
            Column(
                modifier = Modifier.padding(top = 36.dpx),
                verticalArrangement = Arrangement.spacedBy(16.dpx),
            ) {
                KeypadKey.entries.chunked(KEYS_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dpx)) {
                        row.forEach { key ->
                            KeyButton(
                                key = key,
                                onClick = { onEvent(BudgetPlanEvent.PressKey(key)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 32.dpx),
                horizontalArrangement = Arrangement.spacedBy(16.dpx),
            ) {
                SheetButton(
                    onClick = close,
                    background = colors.surfaceAlt,
                    modifier = Modifier.weight(1f).height(136.dpx).testTag(BUDGET_PLAN_KEYPAD_CANCEL_TAG),
                ) {
                    KioskText(
                        text = stringResource(R.string.budget_plan_keypad_cancel),
                        style = KioskTheme.typography.button,
                        maxLines = 1,
                    )
                }
                SheetButton(
                    onClick = { onEvent(BudgetPlanEvent.UseKeypadBudget) },
                    background = colors.accent,
                    enabled = keypad.canUse,
                    modifier = Modifier.weight(1f).height(136.dpx).testTag(BUDGET_PLAN_KEYPAD_USE_TAG),
                ) {
                    KioskText(
                        text =
                            stringResource(
                                if (keypad.canUse) R.string.budget_plan_keypad_use else R.string.budget_plan_keypad_min,
                            ),
                        style = KioskTheme.typography.button,
                        color = colors.surface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    key: KeypadKey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = RoundedCornerShape(32.dpx)
    val description = if (key == KeypadKey.DELETE) stringResource(R.string.budget_plan_keypad_delete) else key.digits
    Box(
        modifier =
            modifier
                .pressable(onClick = onClick)
                .semantics { contentDescription = description }
                .testTag(budgetPlanKeyTag(key))
                .height(128.dpx)
                .clip(shape)
                .background(colors.surface)
                .border(2.dpx, colors.cardBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (key == KeypadKey.DELETE) {
            KioskIcon(icon = KioskIcons.Backspace, tint = colors.ink, modifier = Modifier.size(48.dpx))
        } else {
            KioskText(
                text = key.digits,
                style = KioskTheme.typography.section.copy(fontSize = 48.spx, fontWeight = FontWeight.W600),
                maxLines = 1,
            )
        }
    }
}
