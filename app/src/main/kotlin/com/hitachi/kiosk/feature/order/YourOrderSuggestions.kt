package com.hitachi.kiosk.feature.order

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.ui.MenuPhoto

/** Test tag of the "Goes well with your order" strip. */
const val YOUR_ORDER_SUGGESTIONS_TAG = "your-order-suggestions"

/** Test tag of a suggestion card; [key] is [SuggestionCardUi.key]. */
fun suggestionCardTag(key: String): String = "suggestion-card:$key"

/** Test tag of a card's "Add" button. */
fun suggestionAddTag(key: String): String = "suggestion-add:$key"

/** Test tag of a card's stepper "+". */
fun suggestionIncrementTag(key: String): String = "suggestion-inc:$key"

/** Test tag of a card's stepper "−". */
fun suggestionDecrementTag(key: String): String = "suggestion-dec:$key"

/** `Payment.dc.html` strip: the section rises in at 450 ms (`up` 600 ms), each card at 560 ms + 80 ms per card. */
private const val SECTION_UP_MILLIS = 600
private const val SECTION_UP_DELAY_MILLIS = 450
private const val CARD_IN_DELAY_MILLIS = 560
private const val CARD_IN_STAGGER_MILLIS = 80

/** The stepper pops in over 420 ms (`popIn`). */
private const val STEPPER_POP_MILLIS = 420

/** The stepper's disabled "+" is drawn at 30 % (`opacity: .3`). */
private const val DISABLED_ALPHA = 0.3f

private const val TITLE_TRACKING_EM = -0.01f
private const val TITLE_LINE_HEIGHT_EM = 1.15f
private const val NAME_LINE_HEIGHT_EM = 1.2f
private const val NOTE_LINE_HEIGHT_EM = 1.25f

/** The name block is at least 108 dpx tall when a card has a note, otherwise 72 dpx (`blockMin`). */
private const val NAME_BLOCK_WITH_NOTE_PX = 108
private const val NAME_BLOCK_PX = 72

/**
 * The "Goes well with your order" strip (`Payment.dc.html`, `S_Payment_upsell*`; FR-014, D-33, DEV-106): a heading,
 * "Fits your budget" for Budget Plan orders and up to three 296 dpx cards. It is the last element of the order list,
 * so it scrolls with the lines.
 */
@Composable
internal fun SuggestionStrip(
    suggestions: SuggestionsUi,
    onEvent: (YourOrderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val entrance = animateEntrance(SECTION_UP_MILLIS, SECTION_UP_DELAY_MILLIS)
    val nameBlock = if (suggestions.cards.any { it.note != null }) NAME_BLOCK_WITH_NOTE_PX else NAME_BLOCK_PX
    Column(
        modifier =
            modifier
                .fadeUp(entrance, KioskMotion.Common.UP_DISTANCE_PX.dpx)
                .fillMaxWidth()
                .testTag(YOUR_ORDER_SUGGESTIONS_TAG),
    ) {
        KioskText(
            text = stringResource(R.string.your_order_suggestions_title),
            style =
                KioskTheme.typography.item.copy(
                    fontSize = 36.spx,
                    letterSpacing = TITLE_TRACKING_EM.em,
                    lineHeight = TITLE_LINE_HEIGHT_EM.em,
                ),
            modifier =
                Modifier
                    .padding(top = 16.dpx, bottom = if (suggestions.budgetLimited) 8.dpx else 24.dpx)
                    .semantics { heading() },
        )
        if (suggestions.budgetLimited) {
            KioskText(
                text = stringResource(R.string.your_order_suggestions_budget),
                style = KioskTheme.typography.meta,
                color = colors.muted,
                modifier = Modifier.padding(bottom = 24.dpx),
            )
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(24.dpx),
        ) {
            suggestions.cards.forEachIndexed { index, card ->
                SuggestionCard(
                    card = card,
                    index = index,
                    nameBlockMin = nameBlock,
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    card: SuggestionCardUi,
    index: Int,
    nameBlockMin: Int,
    onEvent: (YourOrderEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.large
    val entrance =
        animateEntrance(
            KioskMotion.YourOrder.ROWS_IN_MILLIS,
            CARD_IN_DELAY_MILLIS + index * CARD_IN_STAGGER_MILLIS,
        )
    Column(
        modifier =
            Modifier
                .fadeUp(entrance, KioskMotion.Common.UP_DISTANCE_PX.dpx)
                .width(296.dpx)
                .fillMaxHeight()
                .clip(shape)
                .background(colors.surface, shape)
                .border(1.dpx, colors.line, shape)
                .testTag(suggestionCardTag(card.key)),
    ) {
        Box(modifier = Modifier.size(width = 296.dpx, height = 168.dpx)) {
            MenuPhoto(
                imageUrl = card.imageUrl,
                tone = card.tone,
                contentDescription = null,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().height(168.dpx),
            )
            KioskText(
                text = stringResource(card.kind.label()),
                style = KioskTheme.typography.meta.copy(lineHeight = 1.3.em),
                maxLines = 1,
                modifier =
                    Modifier
                        .padding(start = 16.dpx, top = 16.dpx)
                        .background(colors.surface, KioskTheme.shapes.pill)
                        .padding(horizontal = 16.dpx, vertical = 4.dpx),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 24.dpx, end = 24.dpx, top = 20.dpx, bottom = 24.dpx),
        ) {
            Column(modifier = Modifier.heightIn(min = nameBlockMin.dpx)) {
                KioskText(
                    text = card.name,
                    style =
                        KioskTheme.typography.item.copy(
                            fontSize = 30.spx,
                            letterSpacing = TITLE_TRACKING_EM.em,
                            lineHeight = NAME_LINE_HEIGHT_EM.em,
                        ),
                )
                card.note?.let {
                    KioskText(
                        text = it,
                        style =
                            KioskTheme.typography.meta.copy(
                                fontSize = 24.spx,
                                lineHeight = NOTE_LINE_HEIGHT_EM.em,
                            ),
                        color = colors.muted,
                        modifier = Modifier.padding(top = 4.dpx),
                    )
                }
            }
            KioskText(
                text =
                    if (card.pricePlus) {
                        stringResource(R.string.your_order_suggestion_price_plus, card.price)
                    } else {
                        card.price
                    },
                style = KioskTheme.typography.item.copy(fontSize = 30.spx),
                maxLines = 1,
                modifier = Modifier.padding(top = 8.dpx),
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 112.dpx),
                contentAlignment = Alignment.BottomStart,
            ) {
                if (card.quantity > 0) {
                    CardStepper(card, onEvent)
                } else {
                    AddButton(card, onClick = { onEvent(YourOrderEvent.SuggestionAdd(card.key)) })
                }
            }
        }
    }
}

private fun SuggestionKind.label(): Int =
    when (this) {
        SuggestionKind.MAIN -> R.string.your_order_suggestion_kind_main
        SuggestionKind.SIDE -> R.string.your_order_suggestion_kind_side
        SuggestionKind.DRINK -> R.string.your_order_suggestion_kind_drink
        SuggestionKind.DESSERT -> R.string.your_order_suggestion_kind_dessert
        SuggestionKind.UPGRADE -> R.string.your_order_suggestion_kind_upgrade
    }

/** The 248 × 96 dpx ink pill "+ Add" (`notAdded`). */
@Composable
private fun AddButton(
    card: SuggestionCardUi,
    onClick: () -> Unit,
) {
    val colors = KioskTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            Modifier
                .size(width = 248.dpx, height = 96.dpx)
                .pressScale(scale)
                .background(colors.ink, KioskTheme.shapes.pill)
                .orderButton(
                    description = stringResource(R.string.your_order_suggestion_add_description, card.name),
                    tag = suggestionAddTag(card.key),
                    interactionSource = interactionSource,
                    onClick = onClick,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dpx, Alignment.CenterHorizontally),
    ) {
        KioskIcon(icon = KioskIcons.Plus, tint = colors.bg, modifier = Modifier.size(32.dpx))
        KioskText(
            text = stringResource(R.string.your_order_suggestion_add),
            style = KioskTheme.typography.button.copy(letterSpacing = 0.em),
            color = colors.bg,
            maxLines = 1,
        )
    }
}

/** The "− n +" stepper (`added`): a white pill with an accent ring; the meal card's "+" is dimmed at its limit. */
@Composable
private fun CardStepper(
    card: SuggestionCardUi,
    onEvent: (YourOrderEvent) -> Unit,
) {
    val colors = KioskTheme.colors
    val pop = animateEntrance(STEPPER_POP_MILLIS, easing = KioskEasing.Pop)
    val group = stringResource(R.string.your_order_suggestion_quantity_description, card.name)
    Row(
        modifier =
            Modifier
                .graphicsLayer {
                    val p = pop.value
                    scaleX = POP_FROM_SCALE + (1f - POP_FROM_SCALE) * p
                    scaleY = scaleX
                    alpha = p.coerceIn(0f, 1f)
                }.size(width = 248.dpx, height = 96.dpx)
                .background(colors.surface, KioskTheme.shapes.pill)
                .border(3.dpx, colors.accent, KioskTheme.shapes.pill)
                .semantics { contentDescription = group },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperSide(
            description = stringResource(R.string.your_order_suggestion_decrement_description, card.name),
            tag = suggestionDecrementTag(card.key),
            enabled = true,
            icon = KioskIcons.Minus,
            onClick = { onEvent(YourOrderEvent.SuggestionRemove(card.key)) },
        )
        Box(modifier = Modifier.width(56.dpx), contentAlignment = Alignment.Center) {
            KioskText(
                text = card.quantity.toString(),
                style = KioskTheme.typography.button.copy(letterSpacing = 0.em),
                color = colors.accent,
                maxLines = 1,
            )
        }
        StepperSide(
            description = stringResource(R.string.your_order_suggestion_increment_description, card.name),
            tag = suggestionIncrementTag(card.key),
            enabled = card.canAddMore,
            icon = KioskIcons.Plus,
            onClick = { onEvent(YourOrderEvent.SuggestionAdd(card.key)) },
        )
    }
}

private const val POP_FROM_SCALE = 0.6f

@Composable
private fun StepperSide(
    description: String,
    tag: String,
    enabled: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            Modifier
                .size(96.dpx)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .pressScale(scale)
                .background(KioskTheme.colors.surface, CircleShape)
                .orderButton(description, tag, interactionSource, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KioskIcon(icon = icon, tint = KioskTheme.colors.accent, modifier = Modifier.size(36.dpx))
    }
}
