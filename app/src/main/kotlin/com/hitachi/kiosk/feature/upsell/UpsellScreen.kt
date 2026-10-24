package com.hitachi.kiosk.feature.upsell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.BackPill
import com.hitachi.kiosk.core.designsystem.component.HeaderPillStyle
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.RollingText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Common
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.motion.riseIn
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.navigation.screenTag
import com.hitachi.kiosk.ui.MenuPhoto
import kotlin.math.roundToInt

/** Test tag of the "‹ Menu" pill. */
const val UPSELL_BACK_TAG = "upsell-back"

/** Test tag of "No thanks". */
const val UPSELL_NO_THANKS_TAG = "upsell-no-thanks"

/** Test tag of "Continue · Rp X" (or "Review order"). */
const val UPSELL_CONTINUE_TAG = "upsell-continue"

/** Test tag of the "Your order" strip. */
const val UPSELL_STRIP_TAG = "upsell-strip"

/** Test tag of the "Nothing more to suggest" panel. */
const val UPSELL_EMPTY_TAG = "upsell-empty"

/** The heading starts at 208 dpx and the strip at 452 dpx; a larger heading pushes the strip down. */
private const val CONTENT_TOP_PX = 208
private const val HEADING_MIN_HEIGHT_PX = 452 - CONTENT_TOP_PX

/** The content clears the dock: 152 dpx plus its 48 dpx margin and the 48 dpx gap above it. */
private const val CONTENT_BOTTOM_PX = 248

/** Design timings of `Upsell.dc.html`. */
private const val HEADER_FADE_MILLIS = 500
private const val HEADING_UP_MILLIS = 700
private const val TITLE_RISE_MILLIS = 900
private const val TITLE_DELAY_MILLIS = 80
private const val SUBTITLE_DELAY_MILLIS = 160
private const val STRIP_IN_MILLIS = 700
private const val STRIP_IN_DELAY_MILLIS = 220
private const val STRIP_IN_DISTANCE_PX = 16
private const val THUMB_IN_MILLIS = 480
private const val DOCK_IN_MILLIS = 800
private const val DOCK_IN_DELAY_MILLIS = 360

/** The dock slides up from 140 % of its height (`dockIn`). */
private const val DOCK_IN_FRACTION = 1.4f

/** After a quick add the count and "Continue" (120 ms later) bump to 105 % and the amounts roll (`bumpA`, `rollA`). */
private const val BUMP_MILLIS = 460
private const val CTA_BUMP_DELAY_MILLIS = 120
private const val BUMP_SCALE = 1.05f
private const val BUMP_PEAK_FRACTION = 0.4f
private const val ROLL_MILLIS = 480

/** Strip thumbnails overlap by 20 dpx (`margin-right: -20px`). */
private const val THUMB_OVERLAP_PX = 20

/** Letter spacings of the design in px, as em of their font size. */
private const val STRIP_TOTAL_TRACKING_EM = -0.4f / 32
private const val EMPTY_TITLE_TRACKING_EM = -0.02f

/**
 * SCR-09 Upsell (`Upsell.dc.html`, `S_Upsell_noSuggestions`; FR-014, DEV-039). Draws [state] exactly: the "‹ Menu"
 * and order type pills, the heading, the "Your order" strip, the 2 × 2 suggestions (or the "Nothing more to suggest"
 * panel) and the dock with "No thanks" and "Continue · Rp X" (only "Review order" when there is nothing to suggest).
 */
@Composable
fun UpsellScreen(
    state: UpsellUiState,
    onEvent: (UpsellEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(screenTag("SCR-09")),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 72.dpx, end = 72.dpx, top = CONTENT_TOP_PX.dpx, bottom = CONTENT_BOTTOM_PX.dpx),
        ) {
            UpsellHeading(
                noSuggestions = state.noSuggestions,
                modifier = Modifier.heightIn(min = HEADING_MIN_HEIGHT_PX.dpx),
            )
            OrderStrip(state)
            Spacer(Modifier.height(48.dpx))
            if (state.noSuggestions) {
                NothingToSuggest()
            } else if (state.loaded) {
                SuggestionGrid(state.suggestions, onEvent)
            }
        }
        UpsellHeader(orderType = state.orderType, onBack = { onEvent(UpsellEvent.BackToMenu) })
        UpsellDock(
            state = state,
            onNoThanks = { onEvent(UpsellEvent.NoThanks) },
            onContinue = { onEvent(UpsellEvent.Continue) },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 72.dpx, end = 72.dpx, bottom = 48.dpx),
        )
    }
}

@Composable
private fun UpsellHeader(
    orderType: OrderType,
    onBack: () -> Unit,
) {
    val colors = KioskTheme.colors
    val fade = animateEntrance(HEADER_FADE_MILLIS)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 72.dpx, end = 72.dpx, top = 48.dpx)
                .height(96.dpx)
                .fadeIn(fade),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BackPill(
            text = stringResource(R.string.upsell_back_to_menu),
            onClick = onBack,
            style = HeaderPillStyle.Flat,
            modifier = Modifier.testTag(UPSELL_BACK_TAG),
        )
        val takeaway = orderType == OrderType.TAKEAWAY
        Row(
            modifier =
                Modifier
                    .height(96.dpx)
                    .border(2.dpx, colors.line, KioskTheme.shapes.pill)
                    .padding(horizontal = 34.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dpx),
        ) {
            KioskIcon(
                icon = if (takeaway) KioskIcons.Bag else KioskIcons.DineIn,
                tint = colors.ink,
                modifier = Modifier.size(32.dpx),
            )
            KioskText(
                text =
                    stringResource(
                        if (takeaway) R.string.upsell_order_type_takeaway else R.string.upsell_order_type_dine_in,
                    ),
                style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
                maxLines = 1,
            )
        }
    }
}

/** "Before you pay", the title and the subtitle; "You're all set" when there is nothing to suggest. */
@Composable
private fun UpsellHeading(
    noSuggestions: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val eyebrow = animateEntrance(HEADING_UP_MILLIS, TITLE_DELAY_MILLIS)
    val title = animateEntrance(TITLE_RISE_MILLIS, TITLE_DELAY_MILLIS)
    val subtitle = animateEntrance(HEADING_UP_MILLIS, SUBTITLE_DELAY_MILLIS)
    Column(modifier = modifier) {
        KioskText(
            text = stringResource(R.string.upsell_eyebrow),
            style = KioskTheme.typography.meta,
            color = colors.muted,
            modifier =
                Modifier
                    .padding(bottom = 12.dpx)
                    .fadeUp(eyebrow, Common.UP_DISTANCE_PX.dpx),
        )
        Box(
            modifier =
                Modifier
                    .clipToBounds()
                    .padding(bottom = 6.dpx),
        ) {
            KioskText(
                text = stringResource(if (noSuggestions) R.string.upsell_title_none else R.string.upsell_title),
                style = KioskTheme.typography.title.copy(lineHeight = 1.em),
                modifier = Modifier.riseIn(title),
                cssLineBox = true,
            )
        }
        KioskText(
            text = stringResource(if (noSuggestions) R.string.upsell_subtitle_none else R.string.upsell_subtitle),
            style = KioskTheme.typography.body,
            color = colors.muted,
            modifier =
                Modifier
                    .padding(top = 16.dpx)
                    .fadeUp(subtitle, Common.UP_DISTANCE_PX.dpx),
        )
    }
}

/** "Your order so far": the last three line thumbnails, "Your order", "N items" · subtotal. */
@Composable
private fun OrderStrip(state: UpsellUiState) {
    val colors = KioskTheme.colors
    val entrance = animateEntrance(STRIP_IN_MILLIS, STRIP_IN_DELAY_MILLIS)
    val countBump = rememberBump(state.addSerial, delayMillis = 0)
    val description = stringResource(R.string.upsell_strip_description)
    Row(
        modifier =
            Modifier
                .fadeUp(entrance, STRIP_IN_DISTANCE_PX.dpx)
                .fillMaxWidth()
                .height(112.dpx)
                .background(colors.surfaceAlt, KioskTheme.shapes.large)
                .semantics { contentDescription = description }
                .testTag(UPSELL_STRIP_TAG)
                .padding(start = 20.dpx, end = 32.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        StripThumbnails(state.thumbnails)
        KioskText(
            text = stringResource(R.string.upsell_your_order),
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
            maxLines = 1,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dpx),
        )
        KioskText(
            text = pluralStringResource(R.plurals.upsell_item_count, state.itemCount, state.itemCount),
            style = KioskTheme.typography.meta.copy(fontSize = 28.spx),
            color = colors.muted,
            maxLines = 1,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = countBump.value
                    scaleY = countBump.value
                },
        )
        Box(
            modifier =
                Modifier
                    .size(6.dpx)
                    .background(colors.closedDot, CircleShape),
        )
        RollingText(
            text = state.subtotal,
            style =
                KioskTheme.typography.button.copy(
                    fontSize = 32.spx,
                    letterSpacing = STRIP_TOTAL_TRACKING_EM.em,
                    lineHeight = 40.spx,
                ),
            durationMillis = ROLL_MILLIS,
            modifier = Modifier.heightIn(min = 40.dpx),
        )
    }
}

/** Up to three 72 dpx circles with a 4 dpx strip-coloured ring, most recent first, each overlapping the next. */
@Composable
private fun StripThumbnails(thumbnails: List<UpsellThumbnailUi>) {
    val overlap = THUMB_OVERLAP_PX.dpx
    Row(modifier = Modifier.padding(end = overlap)) {
        thumbnails.forEach { thumbnail ->
            key(thumbnail.lineId) {
                val pop = animateEntrance(THUMB_IN_MILLIS, easing = KioskEasing.Pop)
                Box(
                    modifier =
                        Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width - overlap.roundToPx(), placeable.height) {
                                    placeable.place(0, 0)
                                }
                            }.graphicsLayer {
                                alpha = pop.value.coerceIn(0f, 1f)
                                scaleX = pop.value
                                scaleY = pop.value
                            }.background(KioskTheme.colors.surfaceAlt, CircleShape)
                            .padding(4.dpx),
                ) {
                    MenuPhoto(
                        imageUrl = thumbnail.imageUrl,
                        tone = thumbnail.tone,
                        contentDescription = null,
                        shape = CircleShape,
                        modifier = Modifier.size(72.dpx),
                    )
                }
            }
        }
    }
}

/** Two columns; the cards of a row share its height, like the design's CSS grid. */
@Composable
private fun SuggestionGrid(
    suggestions: List<UpsellSuggestionUi>,
    onEvent: (UpsellEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dpx)) {
        suggestions.chunked(2).forEachIndexed { row, pair ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(24.dpx),
            ) {
                pair.forEachIndexed { column, suggestion ->
                    key(suggestion.id) {
                        SuggestionCard(
                            suggestion = suggestion,
                            index = row * 2 + column,
                            onAdd = { onEvent(UpsellEvent.QuickAdd(suggestion.id)) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag(upsellCardTag(suggestion.id)),
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** "Nothing more to suggest" (`S_Upsell_noSuggestions`, TC-172). */
@Composable
private fun NothingToSuggest() {
    val colors = KioskTheme.colors
    val entrance = animateEntrance(CARD_IN_MILLIS, CARD_IN_DELAY_MILLIS)
    Column(
        modifier =
            Modifier
                .cardIn(entrance, CARD_IN_DISTANCE_PX.dpx)
                .fillMaxWidth()
                .heightIn(min = 560.dpx)
                .background(colors.surfaceAlt, KioskTheme.shapes.card)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(UPSELL_EMPTY_TAG)
                .padding(horizontal = 48.dpx, vertical = 48.dpx),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dpx, Alignment.CenterVertically),
    ) {
        Box(
            modifier =
                Modifier
                    .size(120.dpx)
                    .background(colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.Check, tint = colors.success, modifier = Modifier.size(60.dpx))
        }
        KioskText(
            text = stringResource(R.string.upsell_empty_title),
            style =
                KioskTheme.typography.title.copy(
                    fontSize = 44.spx,
                    letterSpacing = EMPTY_TITLE_TRACKING_EM.em,
                    lineHeight = 1.15.em,
                ),
            textAlign = TextAlign.Center,
        )
        KioskText(
            text = stringResource(R.string.upsell_empty_body),
            style = KioskTheme.typography.body,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 700.dpx),
        )
    }
}

/**
 * The dock: "No thanks" and "Continue · Rp X", or only "Review order · Rp X" when there is nothing to suggest. After
 * each quick add "Continue" bumps 120 ms after the strip's count and its total rolls.
 */
@Composable
private fun UpsellDock(
    state: UpsellUiState,
    onNoThanks: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entrance = animateEntrance(DOCK_IN_MILLIS, DOCK_IN_DELAY_MILLIS)
    Row(
        modifier =
            modifier
                .graphicsLayer { translationY = size.height * DOCK_IN_FRACTION * (1f - entrance.value) }
                .fillMaxWidth()
                .height(152.dpx),
        horizontalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        if (!state.noSuggestions) NoThanksButton(onClick = onNoThanks)
        ContinueButton(
            label = stringResource(if (state.noSuggestions) R.string.upsell_review_order else R.string.upsell_continue),
            total = state.subtotal,
            addSerial = state.addSerial,
            onClick = onContinue,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NoThanksButton(onClick: () -> Unit) {
    val label = stringResource(R.string.upsell_no_thanks)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            Modifier
                .width(300.dpx)
                .fillMaxHeight()
                .pressScale(scale)
                .background(KioskTheme.colors.surfaceAlt, KioskTheme.shapes.card)
                .upsellButton(label, UPSELL_NO_THANKS_TAG, interactionSource, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(text = label, style = KioskTheme.typography.button, maxLines = 1)
    }
}

@Composable
private fun ContinueButton(
    label: String,
    total: String,
    addSerial: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val interactionSource = remember { MutableInteractionSource() }
    val press = animatePressScale(interactionSource)
    val bump = rememberBump(addSerial, CTA_BUMP_DELAY_MILLIS)
    Row(
        modifier =
            modifier
                .fillMaxHeight()
                .graphicsLayer {
                    scaleX = press.value * bump.value
                    scaleY = scaleX
                }.kioskShadow(shape, KioskTheme.elevation.floating)
                .background(colors.accent, shape)
                .upsellButton(
                    description = stringResource(R.string.upsell_cta_description, label, total),
                    tag = UPSELL_CONTINUE_TAG,
                    interactionSource = interactionSource,
                    onClick = onClick,
                ).padding(start = 48.dpx, end = 44.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        KioskText(text = label, style = KioskTheme.typography.button, color = colors.surface, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dpx)) {
            RollingText(
                text = total,
                style = KioskTheme.typography.button.copy(lineHeight = 44.spx),
                durationMillis = ROLL_MILLIS,
                color = colors.surface,
                modifier = Modifier.heightIn(min = 44.dpx),
            )
            KioskIcon(icon = KioskIcons.ArrowRightBold, tint = colors.surface, modifier = Modifier.size(40.dpx))
        }
    }
}

/**
 * The cart bump (`bumpA`): 1 → 1.05 → 1 over 460 ms with the pop easing after [delayMillis], replayed each time
 * [serial] changes. Nothing moves with reduce motion.
 */
@Composable
private fun rememberBump(
    serial: Int,
    delayMillis: Int,
): State<Float> {
    val scale = remember { Animatable(1f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(serial) {
        if (serial == 0 || reduce) return@LaunchedEffect
        scale.snapTo(1f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec =
                keyframes {
                    durationMillis = BUMP_MILLIS
                    this.delayMillis = delayMillis
                    1f at 0 using KioskEasing.Pop
                    BUMP_SCALE at (BUMP_MILLIS * BUMP_PEAK_FRACTION).roundToInt() using KioskEasing.Pop
                },
        )
    }
    return scale.asState()
}

@Composable
private fun UpsellPreview(state: UpsellUiState) {
    KioskTheme(reduceMotion = true) {
        DesignFrame { UpsellScreen(state = state, onEvent = {}) }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun UpsellDefaultPreview() = UpsellPreview(UpsellSamples.Default)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun UpsellJustAddedPreview() = UpsellPreview(UpsellSamples.JustAdded)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun UpsellNoSuggestionsPreview() = UpsellPreview(UpsellSamples.NoSuggestions)
