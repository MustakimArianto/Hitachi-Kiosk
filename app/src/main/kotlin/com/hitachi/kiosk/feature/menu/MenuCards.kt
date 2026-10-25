package com.hitachi.kiosk.feature.menu

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Common
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.BadgeType
import com.hitachi.kiosk.ui.MenuPhoto

/** Test tag of a product card's tappable photo and name. */
fun menuProductTag(productId: String): String = "menu-product:$productId"

/** Test tag of a card's round "+" button. */
fun menuAddTag(productId: String): String = "menu-add:$productId"

/** Test tag of a card's "Choose ›" pill. */
fun menuChooseTag(productId: String): String = "menu-choose:$productId"

/** Card entrance delay of the first card (`220 + i * 60` ms). */
private const val CARD_DELAY_MILLIS = 220
private const val BUDGET_ROW_DELAY_MILLIS = 120
private const val ADD_BUTTON_COLOR_MILLIS = 320
private const val RING_MILLIS = 700
private const val RING_START_ALPHA = 0.8f
private const val RING_END_SCALE = 1.9f
private const val CHECK_IN_MILLIS = 420
private const val CHECK_START_SCALE = 0.4f
private const val CHECK_START_ROTATION = -45f

/** Opacity of the add controls offline and at 20 (`Menu.dc.html`). */
private const val OFFLINE_ALPHA = 0.35f
private const val AT_MAX_ALPHA = 0.45f

/** Letter spacings of the design in px, as em of their font size. */
private const val NAME_TRACKING_EM = -0.4f / 34
private const val PRICE_TRACKING_EM = -0.3f / 34
private const val BUDGET_TITLE_TRACKING_EM = -0.5f / 34
private const val NAME_LINE_HEIGHT_EM = 1.15f

/**
 * One grid card (FR-009): photo with an optional badge and the name open Item detail; below them the price and the
 * round "+" quick add, or "Choose ›" when the product needs a choice (A-08). It fades up [index] × 60 ms after the
 * first card. The 2 dpx border is inside the card, so the inner padding is 16 + 2 dpx as in the design.
 */
@Composable
internal fun ProductCard(
    product: MenuProductUi,
    index: Int,
    addEnabled: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val entrance =
        animateEntrance(
            KioskMotion.Menu.CARDS_IN_MILLIS,
            CARD_DELAY_MILLIS + index * KioskMotion.Menu.CARDS_STAGGER_MILLIS,
        )
    val interactionSource = remember { MutableInteractionSource() }
    val lift = animatePressScale(interactionSource, KioskMotion.Press.CARD_SCALE, KioskMotion.Press.CARD_MILLIS)
    Column(
        modifier =
            modifier
                .fadeUp(entrance, Common.UP_DISTANCE_PX.dpx)
                .pressScale(lift)
                .background(colors.surface, shape)
                .border(2.dpx, colors.cardBorder, shape)
                .padding(start = 18.dpx, end = 18.dpx, top = 18.dpx, bottom = 26.dpx),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onOpen,
                    ).testTag(menuProductTag(product.id)),
        ) {
            Box {
                MenuPhoto(
                    imageUrl = product.imageUrl,
                    tone = product.tone,
                    contentDescription = null,
                    shape = KioskTheme.shapes.large,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(300.dpx),
                )
                product.badge?.let { ProductBadge(it, Modifier.padding(start = 20.dpx, top = 20.dpx)) }
            }
            Box(
                modifier =
                    Modifier
                        .heightIn(min = 80.dpx)
                        .padding(start = 12.dpx, end = 12.dpx, top = 24.dpx),
            ) {
                KioskText(
                    text = product.name,
                    style =
                        KioskTheme.typography.item.copy(
                            fontSize = 34.spx,
                            letterSpacing = NAME_TRACKING_EM.em,
                            lineHeight = NAME_LINE_HEIGHT_EM.em,
                        ),
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dpx, top = 20.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            KioskText(
                text = product.price,
                style = KioskTheme.typography.button.copy(letterSpacing = PRICE_TRACKING_EM.em),
                maxLines = 1,
            )
            if (product.needsChoice) {
                ChoosePill(product = product, enabled = addEnabled, onClick = onOpen)
            } else {
                AddButton(product = product, enabled = addEnabled, onClick = onAdd)
            }
        }
    }
}

@Composable
private fun ProductBadge(
    badge: MenuBadgeUi,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val fallback =
        stringResource(
            when (badge.type) {
                BadgeType.NEW -> R.string.menu_badge_new
                BadgeType.BEST_SELLER -> R.string.menu_badge_best_seller
                BadgeType.SPICY -> R.string.menu_badge_spicy
                BadgeType.SHARE -> R.string.menu_badge_share
                BadgeType.LOCAL -> R.string.menu_badge_local
            },
        )
    Box(
        modifier =
            modifier
                .background(colors.surface, KioskTheme.shapes.pill)
                .padding(horizontal = 18.dpx, vertical = 8.dpx),
    ) {
        KioskText(
            text = badge.label ?: fallback,
            style = KioskTheme.typography.meta.copy(fontSize = 24.spx, fontWeight = FontWeight.W700),
            color = if (badge.isAccent) colors.accent else colors.ink,
            maxLines = 1,
        )
    }
}

/**
 * The round "+" (DEV-028). After a quick add it turns accent with a check and a ring burst for 1.4 s. Dimmed and
 * disabled offline; dimmed but tappable at 20, so the tap shows "Maximum 20 per item".
 */
@Composable
private fun AddButton(
    product: MenuProductUi,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = KioskTheme.colors
    val background by animateColorAsState(
        targetValue = if (product.justAdded) colors.accent else colors.surfaceAlt,
        animationSpec = motionSpec(tween<Color>(ADD_BUTTON_COLOR_MILLIS, easing = KioskEasing.Emphasized)),
        label = "add-bg",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            Modifier
                .size(96.dpx)
                .alpha(addAlpha(enabled, product.atMax))
                .pressScale(scale)
                .background(background, CircleShape)
                .kioskButton(
                    description = stringResource(R.string.menu_add_description, product.name),
                    tag = menuAddTag(product.id),
                    interactionSource = interactionSource,
                    enabled = enabled,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (product.justAdded) {
            AddedCheck()
        } else {
            KioskIcon(icon = KioskIcons.Plus, tint = colors.ink, modifier = Modifier.size(42.dpx))
        }
    }
}

/** The accent check that pops in with a ring bursting out of the button (`checkIn`, `ringOut`). */
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

/** "Choose ›": the product needs a choice, so it opens Item detail instead of adding (A-08, TC-039). */
@Composable
private fun ChoosePill(
    product: MenuProductUi,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = KioskTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            Modifier
                .height(96.dpx)
                .alpha(addAlpha(enabled, atMax = false))
                .pressScale(scale)
                .background(colors.surfaceAlt, KioskTheme.shapes.pill)
                .kioskButton(
                    description = stringResource(R.string.menu_choose_description, product.name),
                    tag = menuChooseTag(product.id),
                    interactionSource = interactionSource,
                    enabled = enabled,
                    onClick = onClick,
                ).padding(start = 32.dpx, end = 28.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dpx),
    ) {
        KioskText(
            text = stringResource(R.string.menu_choose),
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
            maxLines = 1,
        )
        KioskIcon(icon = KioskIcons.ChevronRight, tint = colors.ink, modifier = Modifier.size(30.dpx))
    }
}

private fun addAlpha(
    enabled: Boolean,
    atMax: Boolean,
): Float =
    when {
        !enabled -> OFFLINE_ALPHA
        atMax -> AT_MAX_ALPHA
        else -> 1f
    }

/** The slim dark "Plan by budget" row on Popular (FR-009, FR-032). */
@Composable
internal fun BudgetPlanRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val entrance = animateEntrance(KioskMotion.Menu.CARDS_IN_MILLIS, BUDGET_ROW_DELAY_MILLIS)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val title = stringResource(R.string.menu_budget_title)
    val description = stringResource(R.string.menu_budget_description)
    Row(
        modifier =
            modifier
                .fadeUp(entrance, Common.UP_DISTANCE_PX.dpx)
                .fillMaxWidth()
                .height(128.dpx)
                .pressScale(scale)
                .background(colors.ink, KioskTheme.shapes.card)
                .kioskButton(description, MENU_BUDGET_PLAN_TAG, interactionSource, onClick = onClick)
                .padding(horizontal = 24.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        Box(
            modifier =
                Modifier
                    .size(80.dpx)
                    .background(colors.accent, KioskTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.People, tint = colors.surface, modifier = Modifier.size(44.dpx))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dpx)) {
            KioskText(
                text = title,
                style = KioskTheme.typography.button.copy(letterSpacing = BUDGET_TITLE_TRACKING_EM.em),
                color = colors.bg,
                maxLines = 1,
            )
            KioskText(
                text = stringResource(R.string.menu_budget_body),
                style = KioskTheme.typography.meta.copy(fontWeight = FontWeight.W400),
                color = colors.dockSubtle,
                maxLines = 1,
            )
        }
        Box(
            modifier =
                Modifier
                    .size(80.dpx)
                    .background(colors.bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.ArrowRightBold, tint = colors.ink, modifier = Modifier.size(36.dpx))
        }
    }
}

/** The compact dark "Plan by budget" pill that heads the category bar on every tab (`Menu.dc.html` `showPill`). */
@Composable
internal fun BudgetPlanPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val label = stringResource(R.string.menu_budget_pill)
    Row(
        modifier =
            modifier
                .height(96.dpx)
                .pressScale(scale)
                .background(colors.ink, KioskTheme.shapes.pill)
                .kioskButton(label, MENU_BUDGET_PILL_TAG, interactionSource, onClick = onClick)
                .padding(start = 28.dpx, end = 36.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dpx),
    ) {
        KioskIcon(icon = KioskIcons.People, tint = colors.bg, modifier = Modifier.size(40.dpx))
        KioskText(
            text = label,
            style =
                KioskTheme.typography.button.copy(
                    fontSize = 30.spx,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.em,
                ),
            color = colors.bg,
            maxLines = 1,
        )
    }
}

/**
 * A tappable control without ripple (the design uses the `.press` scale instead), announced as one button with
 * [description] (the design's `aria-label`) and [tag]. When not [enabled] it ignores taps and is announced as
 * disabled (NFR-007, TC-175 b).
 */
internal fun Modifier.kioskButton(
    description: String,
    tag: String,
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    role: Role = Role.Button,
    selected: Boolean? = null,
    onClick: () -> Unit,
): Modifier =
    clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    ).clearAndSetSemantics {
        testTag = tag
        contentDescription = description
        this.role = role
        if (selected != null) this.selected = selected
        if (enabled) {
            onClick {
                onClick()
                true
            }
        } else {
            disabled()
        }
    }
