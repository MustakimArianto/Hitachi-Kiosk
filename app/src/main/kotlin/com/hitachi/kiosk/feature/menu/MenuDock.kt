package com.hitachi.kiosk.feature.menu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
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
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.ui.MenuPhoto
import kotlin.math.roundToInt

/** Test tag of "View order →". */
const val MENU_VIEW_ORDER_TAG = "menu-view-order"

/** Test tag of the dock's "N items" and total. */
const val MENU_DOCK_SUMMARY_TAG = "menu-dock-summary"

private const val DOCK_IN_MILLIS = 800
private const val DOCK_IN_DELAY_MILLIS = 300

/** The count bumps from its left edge (`transform-origin: left`). */
private val LeftCentre = TransformOrigin(pivotFractionX = 0f, pivotFractionY = 0.5f)

/** The dock slides up from 140 % of its height (`dockIn`). */
private const val DOCK_IN_FRACTION = 1.4f
private const val THUMB_IN_MILLIS = 480

/** Thumbnails overlap by 24 dpx (`margin-right: -24px`). */
private const val THUMB_OVERLAP_PX = 24

/** "View order" bumps 50 ms after the count (`700ms` vs `650ms`). */
private const val CTA_BUMP_DELAY_MILLIS = 700
private const val BUMP_SCALE = 1.06f
private const val BUMP_PEAK_FRACTION = 0.4f
private const val DISABLED_CTA_ALPHA = 0.4f
private const val TOTAL_TRACKING_EM = -0.8f / 44

/** The flying thumbnail: 180 × 140 dpx, starting at the card's (130, 90) and shrinking to 20 % (`flyA`). */
private const val FLY_WIDTH_PX = 180
private const val FLY_HEIGHT_PX = 140
private const val FLY_START_X_PX = 130
private const val FLY_START_Y_PX = 90
private const val FLY_TARGET_X_PX = -120
private const val FLY_TARGET_Y_PX = 68
private const val FLY_END_SCALE = 0.2f
private const val FLY_FADE_FROM = 0.8f

/**
 * The cart dock (FR-009): the last three line thumbnails, "N items", the rolling total and "View order →". After each
 * quick add ([addSerial] changes) the count and the button bump and the total rolls (DEV-028).
 */
@Composable
internal fun CartDock(
    dock: CartDockUi,
    addSerial: Int,
    viewOrderEnabled: Boolean,
    onViewOrder: () -> Unit,
    onViewOrderPlaced: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.dock
    val entrance = animateEntrance(DOCK_IN_MILLIS, DOCK_IN_DELAY_MILLIS)
    val countBump = rememberBump(addSerial, KioskMotion.Menu.DOCK_BUMP_DELAY_MILLIS)
    val ctaBump = rememberBump(addSerial, CTA_BUMP_DELAY_MILLIS)
    val description = stringResource(R.string.menu_dock_description)
    Row(
        modifier =
            modifier
                .graphicsLayer { translationY = size.height * DOCK_IN_FRACTION * (1f - entrance.value) }
                .fillMaxWidth()
                .height(168.dpx)
                .kioskShadow(shape, KioskTheme.elevation.floating)
                .background(colors.ink, shape)
                .semantics { contentDescription = description }
                .padding(start = 28.dpx, end = 16.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        DockThumbnails(dock.thumbnails)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dpx)
                    .semantics(mergeDescendants = true) {}
                    .testTag(MENU_DOCK_SUMMARY_TAG),
            verticalArrangement = Arrangement.spacedBy(2.dpx),
        ) {
            KioskText(
                text = pluralStringResource(R.plurals.menu_item_count, dock.itemCount, dock.itemCount),
                style = KioskTheme.typography.meta,
                color = colors.dockMuted,
                maxLines = 1,
                modifier =
                    Modifier.graphicsLayer {
                        transformOrigin = LeftCentre
                        scaleX = countBump.value
                        scaleY = countBump.value
                    },
            )
            RollingText(
                text = dock.total,
                style = KioskTheme.typography.section.copy(letterSpacing = TOTAL_TRACKING_EM.em),
                durationMillis = KioskMotion.Menu.TOTAL_ROLL_MILLIS,
                delayMillis = if (addSerial > 0) KioskMotion.Menu.TOTAL_ROLL_DELAY_MILLIS else 0,
                color = colors.bg,
                modifier = Modifier.height(56.dpx),
            )
        }
        ViewOrderButton(
            enabled = viewOrderEnabled,
            bump = ctaBump,
            onClick = onViewOrder,
            modifier = Modifier.onPlaced(onViewOrderPlaced),
        )
    }
}

/** Up to three 88 dpx circles with a 4 dpx ink ring, most recent first, each overlapping the next by 24 dpx. */
@Composable
private fun DockThumbnails(thumbnails: List<DockThumbnailUi>) {
    val overlap = THUMB_OVERLAP_PX.dpx
    Row(modifier = Modifier.padding(end = overlap)) {
        thumbnails.forEach { thumbnail ->
            key(thumbnail.lineId) {
                val pop = animateEntrance(THUMB_IN_MILLIS, easing = KioskEasing.Spring)
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
                            }.background(KioskTheme.colors.ink, CircleShape)
                            .padding(4.dpx),
                ) {
                    MenuPhoto(
                        imageUrl = thumbnail.imageUrl,
                        tone = thumbnail.tone,
                        contentDescription = null,
                        shape = CircleShape,
                        modifier = Modifier.size(88.dpx),
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewOrderButton(
    enabled: Boolean,
    bump: State<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val label = stringResource(R.string.menu_view_order)
    val interactionSource = remember { MutableInteractionSource() }
    val press = animatePressScale(interactionSource)
    Row(
        modifier =
            modifier
                .height(136.dpx)
                .alpha(if (enabled) 1f else DISABLED_CTA_ALPHA)
                .graphicsLayer {
                    scaleX = press.value * bump.value
                    scaleY = scaleX
                }.background(colors.accent, RoundedCornerShape(36.dpx))
                .kioskButton(label, MENU_VIEW_ORDER_TAG, interactionSource, enabled = enabled, onClick = onClick)
                .padding(start = 44.dpx, end = 40.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dpx),
    ) {
        KioskText(text = label, style = KioskTheme.typography.button, color = colors.surface, maxLines = 1)
        KioskIcon(icon = KioskIcons.ArrowRightBold, tint = colors.surface, modifier = Modifier.size(40.dpx))
    }
}

/**
 * The dock bump (`bumpA`): 1 → 1.06 → 1 over [KioskMotion.Menu.DOCK_BUMP_MILLIS] after [delayMillis], replayed each
 * time [serial] changes. Nothing moves with reduce motion.
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
                    durationMillis = KioskMotion.Menu.DOCK_BUMP_MILLIS
                    this.delayMillis = delayMillis
                    1f at 0 using KioskEasing.Spring
                    BUMP_SCALE at (KioskMotion.Menu.DOCK_BUMP_MILLIS * BUMP_PEAK_FRACTION).roundToInt() using
                        KioskEasing.Spring
                },
        )
    }
    return scale.asState()
}

/** Where the flight starts and ends: the cards and "View order", relative to the Menu root. */
@Stable
internal class FlyAnchors {
    var root: LayoutCoordinates? = null
    var viewOrder: LayoutCoordinates? = null
    val cards = mutableMapOf<String, LayoutCoordinates>()

    fun positionOf(coordinates: LayoutCoordinates?): Offset? {
        val root = root?.takeIf { it.isAttached } ?: return null
        val target = coordinates?.takeIf { it.isAttached } ?: return null
        return root.localPositionOf(target, Offset.Zero)
    }
}

/** One thumbnail on its way to the dock. [start] is its top-left, [delta] the move of its centre. */
private data class Flight(
    val product: MenuProductUi,
    val start: Offset,
    val delta: Offset,
)

/**
 * The fly-to-cart of DEV-028: on each quick add the product photo leaves its card and shrinks into "View order" over
 * 760 ms with [KioskEasing.Fly], fading out over the last 20 %. Not drawn with reduce motion.
 */
@Composable
internal fun FlyToCart(
    state: MenuUiState,
    anchors: FlyAnchors,
) {
    if (LocalReduceMotion.current) return
    val density = LocalDensity.current
    val startOffset = with(density) { Offset(FLY_START_X_PX.dpx.toPx(), FLY_START_Y_PX.dpx.toPx()) }
    val halfSize = with(density) { Offset(FLY_WIDTH_PX.dpx.toPx() / 2, FLY_HEIGHT_PX.dpx.toPx() / 2) }
    val targetOffset = with(density) { Offset(FLY_TARGET_X_PX.dpx.toPx(), FLY_TARGET_Y_PX.dpx.toPx()) }
    val progress = remember { Animatable(0f) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    val serial = state.addSerial
    val product = state.products.firstOrNull { it.id == state.lastAddedProductId }
    LaunchedEffect(serial) {
        if (serial == 0 || product == null) return@LaunchedEffect
        val card = anchors.positionOf(anchors.cards[product.id]) ?: return@LaunchedEffect
        val cta = anchors.positionOf(anchors.viewOrder) ?: return@LaunchedEffect
        val start = card + startOffset
        flight = Flight(product, start, cta + targetOffset - (start + halfSize))
        progress.snapTo(0f)
        progress.animateTo(1f, tween(KioskMotion.Menu.FLY_TO_CART_MILLIS, easing = LinearEasing))
        flight = null
    }
    val current = flight ?: return
    val shape = KioskTheme.shapes.large
    Box(
        modifier =
            Modifier
                .offset { IntOffset(current.start.x.roundToInt(), current.start.y.roundToInt()) }
                .size(FLY_WIDTH_PX.dpx, FLY_HEIGHT_PX.dpx)
                .graphicsLayer {
                    val t = progress.value
                    val move = KioskEasing.Fly.transform(t)
                    translationX = current.delta.x * move
                    translationY = current.delta.y * move
                    scaleX = 1f - (1f - FLY_END_SCALE) * move
                    scaleY = scaleX
                    alpha =
                        if (t < FLY_FADE_FROM) {
                            1f
                        } else {
                            1f - KioskEasing.Fly.transform((t - FLY_FADE_FROM) / (1f - FLY_FADE_FROM))
                        }
                }.kioskShadow(shape, KioskTheme.elevation.raised),
    ) {
        MenuPhoto(
            imageUrl = current.product.imageUrl,
            tone = current.product.tone,
            contentDescription = null,
            shape = shape,
            modifier = Modifier.size(FLY_WIDTH_PX.dpx, FLY_HEIGHT_PX.dpx),
        )
    }
}
