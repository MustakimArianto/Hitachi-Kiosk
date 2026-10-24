package com.hitachi.kiosk.feature.upsell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Common
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.ui.MenuPhoto

/** Test tag of a suggestion card's round "+" button. */
fun upsellAddTag(productId: String): String = "upsell-add:$productId"

/** Test tag of a suggestion card. */
fun upsellCardTag(productId: String): String = "upsell-card:$productId"

/** Cards rise in from 40 dpx below and 98 % scale (`cardIn`), 760 ms, the first at 300 ms, then every 70 ms. */
internal const val CARD_IN_MILLIS = 760
internal const val CARD_IN_DELAY_MILLIS = 300
private const val CARD_IN_STAGGER_MILLIS = 70
internal const val CARD_IN_DISTANCE_PX = 40
private const val CARD_IN_SCALE = 0.98f

/** `.lift:active`: the card scales to 98.5 % over 220 ms while its "+" is pressed. */
private const val LIFT_SCALE = 0.985f
private const val LIFT_MILLIS = 220

/** The button background and the card border change over 320 ms (`transition`). */
private const val COLOR_MILLIS = 320
private const val ADDED_LABEL_MILLIS = 400
private const val RING_MILLIS = 700
private const val RING_START_ALPHA = 0.8f
private const val RING_END_SCALE = 1.9f
private const val CHECK_IN_MILLIS = 420
private const val CHECK_START_SCALE = 0.4f
private const val CHECK_START_ROTATION = -45f

/** Letter spacings of the design in px, as em of their font size. */
private const val NAME_TRACKING_EM = -0.4f / 34
private const val PRICE_TRACKING_EM = -0.3f / 34
private const val NAME_LINE_HEIGHT_EM = 1.15f

/**
 * The `cardIn` entrance of the suggestion cards and the empty panel: fades in while rising 40 dpx and growing from
 * 98 %. Drawn in a graphics layer, no relayout.
 */
internal fun Modifier.cardIn(
    progress: State<Float>,
    distance: Dp,
): Modifier =
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = distance.toPx() * (1f - p)
        scaleX = CARD_IN_SCALE + (1f - CARD_IN_SCALE) * p
        scaleY = scaleX
    }

/**
 * One suggestion (FR-014): photo with the kind chip, name, note, price and the round "+". Once the cart holds it, the
 * border turns accent, "N added" appears under the price and the "+" turns ink with a white plus; right after a tap
 * it is accent with a check and a ring burst for 1.4 s (`Upsell.dc.html`). The card rises in [index] × 70 ms after
 * the first. The 2 dpx border is inside the card, so the inner padding is the design's plus 2 dpx.
 */
@Composable
internal fun SuggestionCard(
    suggestion: UpsellSuggestionUi,
    index: Int,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val entrance = animateEntrance(CARD_IN_MILLIS, CARD_IN_DELAY_MILLIS + index * CARD_IN_STAGGER_MILLIS)
    val interactionSource = remember { MutableInteractionSource() }
    val lift = animatePressScale(interactionSource, LIFT_SCALE, LIFT_MILLIS)
    val border by animateColorAsState(
        targetValue = if (suggestion.inCart) colors.accent else colors.cardBorder,
        animationSpec = motionSpec(tween<Color>(COLOR_MILLIS)),
        label = "upsell-card-border",
    )
    Column(
        modifier =
            modifier
                .cardIn(entrance, CARD_IN_DISTANCE_PX.dpx)
                .pressScale(lift)
                .background(colors.surface, shape)
                .border(2.dpx, border, shape)
                .padding(start = 18.dpx, end = 18.dpx, top = 18.dpx, bottom = 22.dpx),
    ) {
        Box {
            MenuPhoto(
                imageUrl = suggestion.imageUrl,
                tone = suggestion.tone,
                contentDescription = null,
                shape = KioskTheme.shapes.large,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(220.dpx),
            )
            KindChip(suggestion.role, Modifier.padding(start = 20.dpx, top = 20.dpx))
        }
        Column(
            modifier =
                Modifier
                    .heightIn(min = 108.dpx)
                    .padding(start = 12.dpx, end = 12.dpx, top = 24.dpx),
            verticalArrangement = Arrangement.spacedBy(6.dpx),
        ) {
            KioskText(
                text = suggestion.name,
                style =
                    KioskTheme.typography.item.copy(
                        fontSize = 34.spx,
                        letterSpacing = NAME_TRACKING_EM.em,
                        lineHeight = NAME_LINE_HEIGHT_EM.em,
                    ),
            )
            note(suggestion)?.let { note ->
                KioskText(text = note, style = KioskTheme.typography.meta, color = colors.muted)
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dpx, top = 12.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dpx)) {
                KioskText(
                    text = suggestion.price,
                    style = KioskTheme.typography.button.copy(letterSpacing = PRICE_TRACKING_EM.em),
                    maxLines = 1,
                )
                if (suggestion.inCart) AddedLabel(suggestion.addedQuantity)
            }
            AddButton(suggestion = suggestion, interactionSource = interactionSource, onClick = onAdd)
        }
    }
}

/** "Sea salt · 320 kcal": the description and the calories, whichever exist. */
@Composable
private fun note(suggestion: UpsellSuggestionUi): String? {
    val kcal = suggestion.kcal?.let { pluralStringResource(R.plurals.upsell_kcal, it, it) }
    val description = suggestion.description
    return when {
        description != null && kcal != null -> stringResource(R.string.upsell_note, description, kcal)
        else -> description ?: kcal
    }
}

/** The white chip over the photo: what kind of add-on it is ("Side", "Dessert"). */
@Composable
private fun KindChip(
    role: ProductRole,
    modifier: Modifier = Modifier,
) {
    val label =
        stringResource(
            when (role) {
                ProductRole.MAIN -> R.string.upsell_kind_main
                ProductRole.SIDE -> R.string.upsell_kind_side
                ProductRole.DRINK -> R.string.upsell_kind_drink
                ProductRole.DESSERT -> R.string.upsell_kind_dessert
            },
        )
    Box(
        modifier =
            modifier
                .background(KioskTheme.colors.surface, KioskTheme.shapes.pill)
                .padding(horizontal = 18.dpx, vertical = 8.dpx),
    ) {
        KioskText(
            text = label,
            style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W700),
            maxLines = 1,
        )
    }
}

/** "N added" in accent under the price; it fades up when it first appears. */
@Composable
private fun AddedLabel(quantity: Int) {
    val entrance = animateEntrance(ADDED_LABEL_MILLIS)
    KioskText(
        text = pluralStringResource(R.plurals.upsell_added_count, quantity, quantity),
        style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W600),
        color = KioskTheme.colors.accent,
        maxLines = 1,
        modifier = Modifier.fadeUp(entrance, Common.UP_DISTANCE_PX.dpx),
    )
}

/**
 * The round "+" (FR-014 AC3). `surfaceAlt` with an ink plus; ink with a white plus once the cart holds the
 * suggestion; accent with the check and ring for 1.4 s right after a tap.
 */
@Composable
private fun AddButton(
    suggestion: UpsellSuggestionUi,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val colors = KioskTheme.colors
    val background by animateColorAsState(
        targetValue =
            when {
                suggestion.justAdded -> colors.accent
                suggestion.inCart -> colors.ink
                else -> colors.surfaceAlt
            },
        animationSpec = motionSpec(tween<Color>(COLOR_MILLIS, easing = KioskEasing.Emphasized)),
        label = "upsell-add-bg",
    )
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            Modifier
                .size(96.dpx)
                .pressScale(scale)
                .background(background, CircleShape)
                .upsellButton(
                    description = stringResource(R.string.upsell_add_description, suggestion.name),
                    tag = upsellAddTag(suggestion.id),
                    interactionSource = interactionSource,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (suggestion.justAdded) {
            AddedCheck()
        } else {
            KioskIcon(
                icon = KioskIcons.Plus,
                tint = if (suggestion.inCart) colors.surface else colors.ink,
                modifier = Modifier.size(42.dpx),
            )
        }
    }
}

/** The white check that pops in with an accent ring bursting out of the button (`checkIn`, `ringOut`). */
@Composable
private fun AddedCheck() {
    val colors = KioskTheme.colors
    val ring = animateEntrance(RING_MILLIS, easing = KioskEasing.Press)
    val check = animateEntrance(CHECK_IN_MILLIS, easing = KioskEasing.Pop)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = ring.value
                    alpha = RING_START_ALPHA * (1f - p)
                    scaleX = 1f + (RING_END_SCALE - 1f) * p
                    scaleY = scaleX
                }.border(3.dpx, colors.accent, CircleShape),
    )
    KioskIcon(
        icon = KioskIcons.Check,
        tint = colors.surface,
        modifier =
            Modifier
                .size(44.dpx)
                .graphicsLayer {
                    val p = check.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = CHECK_START_SCALE + (1f - CHECK_START_SCALE) * p
                    scaleY = scaleX
                    rotationZ = CHECK_START_ROTATION * (1f - p)
                },
    )
}

/**
 * A tappable control without ripple (the design uses the `.press` scale instead), announced as one button with
 * [description] (the design's `aria-label`) and [tag] (NFR-007).
 */
internal fun Modifier.upsellButton(
    description: String,
    tag: String,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier =
    clickable(
        interactionSource = interactionSource,
        indication = null,
        role = Role.Button,
        onClick = onClick,
    ).clearAndSetSemantics {
        testTag = tag
        contentDescription = description
        role = Role.Button
        onClick {
            onClick()
            true
        }
    }
