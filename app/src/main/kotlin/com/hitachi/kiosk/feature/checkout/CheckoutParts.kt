package com.hitachi.kiosk.feature.checkout

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.SchibstedGrotesk
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

// Loops that the motion inventory does not name; the periods are those of `Checkout.dc.html`.
private const val PULSE_DOT_MILLIS = 1_600
private const val WAVE_MILLIS = 1_600
private const val WAVE_STAGGER_MILLIS = 200
private const val ARROW_BOB_MILLIS = 1_400
private const val BREATHE_MILLIS = 1_600

private const val PULSE_ALPHA = 0.45f
private const val PULSE_SPREAD_PX = 18

// The terminal illustration is drawn in the design's 600 × 600 box.
private const val TERMINAL_BOX_PX = 600
private const val CARD_TAP_X_PX = -70f
private const val CARD_TAP_Y_PX = 96f
private const val CARD_TAP_REST_DEGREES = -12f
private const val CARD_TAP_END_DEGREES = -3f
private const val CARD_TAP_IN_PERCENT = 45
private const val CARD_TAP_OUT_PERCENT = 60
private const val PERCENT = 100
private const val CARD_SHADOW_ALPHA = 0.2f
private const val CARD_LINE_ALPHA = 0.35f
private const val ARROW_BOB_PX = 18f
private const val WAVE_REST_ALPHA = 0.15f
private const val WAVE_PEAK_FRACTION = 0.4f

private const val FULL_TURN = 360f
private const val BREATHE_LOW = 0.94f
private const val BREATHE_HIGH = 1.03f
private const val MARK_VIEWBOX = 100f
private const val OUTER_RADIUS = 45f
private const val OUTER_STROKE = 3f
private const val INNER_RADIUS = 36f
private const val INNER_STROKE = 2f

// `stroke-dasharray` of the two arcs over their circumferences: 60 of 2π·45 and 36 of 2π·36.
private const val OUTER_SWEEP = 76.4f
private const val INNER_SWEEP = 57.3f

/** The tap area of every Checkout button: no ripple, the design's `.press` scale instead (NFR-007). */
@Suppress("LongParameterList")
internal fun Modifier.checkoutButton(
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

/** An outline button of the dock: "Back to order" with its chevron, or "Pay by X instead" (112 dpx, 2 dpx ink border). */
@Composable
internal fun OutlineButton(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    chevron: Boolean = false,
    textSizePx: Int = 30,
) {
    val colors = KioskTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val ink = if (enabled) colors.ink else colors.disabledText
    Row(
        modifier =
            modifier
                .height(112.dpx)
                .pressScale(scale)
                .clip(KioskTheme.shapes.large)
                .border(2.dpx, ink, KioskTheme.shapes.large)
                .checkoutButton(text, tag, interactionSource, enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dpx, Alignment.CenterHorizontally),
    ) {
        if (chevron) KioskIcon(KioskIcons.ChevronLeft, tint = ink, modifier = Modifier.size(40.dpx))
        KioskText(
            text = text,
            style = KioskTheme.typography.button.copy(fontSize = textSizePx.spx, letterSpacing = 0.em),
            color = ink,
            maxLines = 1,
        )
    }
}

/** The soft "Cancel order" button (112 dpx, `surfaceAlt`). */
@Composable
internal fun SoftButton(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Row(
        modifier =
            modifier
                .height(112.dpx)
                .pressScale(scale)
                .clip(KioskTheme.shapes.large)
                .background(KioskTheme.colors.surfaceAlt)
                .checkoutButton(text, tag, interactionSource, enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        KioskText(
            text = text,
            style = KioskTheme.typography.button.copy(fontSize = 30.spx, letterSpacing = 0.em),
            maxLines = 1,
        )
    }
}

/** The accent "Try again" button (152 dpx), or its disabled "Reconnecting…" look while offline (`S_Checkout_offline`). */
@Composable
internal fun RetryButton(
    text: String,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    val foreground = if (enabled) colors.surface else colors.disabledText
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(152.dpx)
                .pressScale(scale)
                .clip(KioskTheme.shapes.card)
                .background(if (enabled) colors.accent else colors.line)
                .checkoutButton(text, tag, interactionSource, enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dpx, Alignment.CenterHorizontally),
    ) {
        KioskIcon(KioskIcons.Retry, tint = foreground, modifier = Modifier.size(46.dpx))
        KioskText(
            text = text,
            style = KioskTheme.typography.button.copy(fontSize = 36.spx, letterSpacing = 0.em),
            color = foreground,
            maxLines = 1,
        )
    }
}

/** The dot of "Waiting for your payment": 16 dpx, with a ring that grows 18 dpx and fades (`pulseDot`). */
@Composable
internal fun PulseDot(modifier: Modifier = Modifier) {
    val accent = KioskTheme.colors.accent
    val ringSpread = PULSE_SPREAD_PX.dpx
    val progress: State<Float> =
        if (LocalReduceMotion.current) {
            remember { mutableFloatStateOf(1f) }
        } else {
            rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(PULSE_DOT_MILLIS, easing = KioskEasing.Press)),
                label = "pulse",
            )
        }
    Box(
        modifier =
            modifier
                .size(16.dpx)
                .drawBehind {
                    val p = progress.value
                    val radius = size.minDimension / 2f
                    drawCircle(
                        color = accent.copy(alpha = PULSE_ALPHA * (1f - p)),
                        radius = radius + ringSpread.toPx() * p,
                    )
                    drawCircle(color = accent, radius = radius)
                },
    )
}

/**
 * The card terminal of the card view (`Checkout.dc.html`): the terminal with its [amount], three signal waves, and a
 * card that taps the reader in a 2.6 s loop. At rest (reduce motion) the card hovers top right and the waves are
 * faint.
 */
@Composable
internal fun TerminalIllustration(
    amount: String,
    amountLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val measurer = rememberTextMeasurer()
    val reduce = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "terminal")
    val loop = KioskMotion.Checkout.CARD_TAP_LOOP_MILLIS
    val tap by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                keyframes {
                    durationMillis = loop
                    0f at 0 using KioskEasing.EaseInOut
                    1f at loop * CARD_TAP_IN_PERCENT / PERCENT
                    1f at loop * CARD_TAP_OUT_PERCENT / PERCENT using KioskEasing.EaseInOut
                    0f at loop
                },
            ),
        label = "tap",
    )
    val wave by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(WAVE_MILLIS, easing = KioskEasing.Linear)),
        "wave",
    )
    val shape = RoundedCornerShape(20.dpx)
    Box(modifier = modifier.size(620.dpx, TERMINAL_BOX_PX.dpx)) {
        Canvas(Modifier.size(TERMINAL_BOX_PX.dpx)) {
            drawTerminal(colors.ink, colors.surface, colors.surfaceAlt, colors.radioOff, colors.success)
            drawTerminalText(measurer, amountLabel, amount, colors.muted, colors.ink)
            drawKeyAndWaves(colors.accent, if (reduce) null else wave)
        }
        Box(
            modifier =
                Modifier
                    .offset(410.dpx, 10.dpx)
                    .size(210.dpx, 132.dpx)
                    .graphicsLayer {
                        val progress = if (reduce) 0f else tap
                        translationX = CARD_TAP_X_PX * progress * density
                        translationY = CARD_TAP_Y_PX * progress * density
                        rotationZ = CARD_TAP_REST_DEGREES + (CARD_TAP_END_DEGREES - CARD_TAP_REST_DEGREES) * progress
                    }.kioskShadow(shape, KioskTheme.elevation.floating.copy(color = colors.ink.copy(CARD_SHADOW_ALPHA)))
                    .background(colors.ink, shape),
        ) {
            Box(
                Modifier
                    .offset(22.dpx, 36.dpx)
                    .size(40.dpx, 30.dpx)
                    .background(colors.accent, RoundedCornerShape(7.dpx)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 22.dpx, bottom = 22.dpx)
                    .size(120.dpx, 8.dpx)
                    .background(colors.bg.copy(alpha = CARD_LINE_ALPHA), RoundedCornerShape(4.dpx)),
            )
        }
    }
}

/** A rectangle in the terminal's 600 × 600 design box. */
private data class DesignRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
)

/** One signal wave: `M288 46 q14 -14 0 -28` is start (288, 46), bend 14 and height 28. */
private data class WaveSpec(
    val x: Float,
    val y: Float,
    val bend: Float,
    val height: Float,
)

// `Checkout.dc.html` `<svg viewBox="0 0 600 600">`.
private val TerminalBody = DesignRect(x = 160f, y = 70f, width = 280f, height = 500f, radius = 48f)
private val TerminalScreen = DesignRect(x = 188f, y = 100f, width = 224f, height = 156f, radius = 20f)
private val TerminalSpeaker = DesignRect(x = 215f, y = 516f, width = 170f, height = 10f, radius = 5f)
private val KeyColumns = listOf(196f, 271f, 346f)
private val KeyRows = listOf(282f, 334f, 386f)
private val AccentKey = DesignRect(x = 196f, y = 438f, width = 58f, height = 40f, radius = 12f)
private val ZeroKey = AccentKey.copy(x = 271f)
private val SuccessKey = AccentKey.copy(x = 346f)
private val Waves = listOf(WaveSpec(288f, 46f, 14f, 28f), WaveSpec(304f, 58f, 26f, 52f), WaveSpec(320f, 70f, 38f, 76f))
private const val BODY_STROKE = 3f
private const val KEY_STROKE = 2f
private const val WAVE_STROKE = 5f
private const val SCREEN_CENTER_X = 300f
private const val LABEL_BASELINE_Y = 156f
private const val AMOUNT_BASELINE_Y = 208f
private const val LABEL_SIZE = 20f
private const val AMOUNT_SIZE = 38f

private fun DrawScope.fill(
    rect: DesignRect,
    color: Color,
) {
    val u = size.width / TERMINAL_BOX_PX
    drawRoundRect(
        color,
        Offset(rect.x * u, rect.y * u),
        Size(rect.width * u, rect.height * u),
        CornerRadius(rect.radius * u),
    )
}

private fun DrawScope.outline(
    rect: DesignRect,
    color: Color,
    strokeWidth: Float,
) {
    val u = size.width / TERMINAL_BOX_PX
    drawRoundRect(
        color,
        Offset(rect.x * u, rect.y * u),
        Size(rect.width * u, rect.height * u),
        CornerRadius(rect.radius * u),
        style = Stroke(strokeWidth * u),
    )
}

/** The terminal's body, screen, keypad and speaker slot. */
private fun DrawScope.drawTerminal(
    ink: Color,
    surface: Color,
    surfaceAlt: Color,
    keyLine: Color,
    success: Color,
) {
    fill(TerminalBody, surface)
    outline(TerminalBody, ink, BODY_STROKE)
    fill(TerminalScreen, surfaceAlt)
    KeyRows.forEach { y ->
        KeyColumns.forEach { x -> outline(AccentKey.copy(x = x, y = y), keyLine, KEY_STROKE) }
    }
    outline(ZeroKey, keyLine, KEY_STROKE)
    outline(SuccessKey, success, KEY_STROKE)
    fill(TerminalSpeaker, ink)
}

private fun DrawScope.drawTerminalText(
    measurer: TextMeasurer,
    label: String,
    amount: String,
    muted: Color,
    ink: Color,
) {
    val u = size.width / TERMINAL_BOX_PX
    val labelStyle = TextStyle(fontFamily = SchibstedGrotesk, fontSize = (LABEL_SIZE * u).toSp())
    val amountStyle =
        TextStyle(fontFamily = SchibstedGrotesk, fontWeight = FontWeight.W800, fontSize = (AMOUNT_SIZE * u).toSp())
    drawCentered(measurer, label, SCREEN_CENTER_X * u, LABEL_BASELINE_Y * u, labelStyle, muted)
    drawCentered(measurer, amount, SCREEN_CENTER_X * u, AMOUNT_BASELINE_Y * u, amountStyle, ink)
}

/** Draws [text] centred on [x] with its baseline on [baseline], like SVG `text-anchor="middle"`. */
private fun DrawScope.drawCentered(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    baseline: Float,
    style: TextStyle,
    color: Color,
) {
    val layout = measurer.measure(text, style)
    drawText(layout, color = color, topLeft = Offset(x - layout.size.width / 2f, baseline - layout.firstBaseline))
}

/**
 * The accent key and the three signal waves above the terminal. The waves fade in turn, 200 ms apart (`wave`); with a
 * `null` [wave] (reduce motion) they rest at their faint opacity.
 */
private fun DrawScope.drawKeyAndWaves(
    accent: Color,
    wave: Float?,
) {
    val u = size.width / TERMINAL_BOX_PX
    outline(AccentKey, accent, KEY_STROKE)
    Waves.forEachIndexed { index, spec ->
        val alpha = wave?.let { waveAlpha((it - index * WAVE_STAGGER_MILLIS / WAVE_MILLIS.toFloat() + 1f) % 1f) }
        val path =
            Path().apply {
                moveTo(spec.x * u, spec.y * u)
                relativeQuadraticTo(spec.bend * u, -spec.bend * u, 0f, -spec.height * u)
            }
        drawPath(
            path,
            accent.copy(alpha = alpha ?: WAVE_REST_ALPHA),
            style = Stroke(WAVE_STROKE * u, cap = StrokeCap.Round),
        )
    }
}

/** `@keyframes wave`: `.15` at the ends, `1` at 40 %. */
private fun waveAlpha(phase: Float): Float =
    if (phase < WAVE_PEAK_FRACTION) {
        WAVE_REST_ALPHA + (1f - WAVE_REST_ALPHA) * (phase / WAVE_PEAK_FRACTION)
    } else {
        1f - (1f - WAVE_REST_ALPHA) * ((phase - WAVE_PEAK_FRACTION) / (1f - WAVE_PEAK_FRACTION))
    }

/** The bobbing arrow under the terminal (`arrowBob`). */
@Composable
internal fun BobbingArrow(modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val bob by rememberInfiniteTransition(label = "arrow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                tween(ARROW_BOB_MILLIS / 2, easing = KioskEasing.EaseInOut),
                RepeatMode.Reverse,
            ),
        label = "bob",
    )
    KioskIcon(
        icon = KioskIcons.ArrowDown,
        tint = KioskTheme.colors.accent,
        modifier =
            modifier
                .size(80.dpx)
                .graphicsLayer { translationY = if (reduce) 0f else ARROW_BOB_PX * bob * density },
    )
}

/**
 * The spinner of "Creating your payment…", "Checking your payment…" and "Confirming payment" (`Checkout.dc.html`
 * `isProcessing`): a track, an accent arc turning once per 1.1 s, an ink arc turning back once per 1.9 s, and the
 * brand mark breathing in the middle. With reduce motion it rests.
 */
@Composable
internal fun ProcessingMark(modifier: Modifier = Modifier) {
    val colors = KioskTheme.colors
    val reduce = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "processing")
    val spin by transition.animateFloat(
        0f,
        FULL_TURN,
        infiniteRepeatable(tween(KioskMotion.Checkout.PROCESSING_SPINNER_LOOP_MILLIS, easing = KioskEasing.Linear)),
        "spin",
    )
    val spinBack by transition.animateFloat(
        0f,
        -FULL_TURN,
        infiniteRepeatable(
            tween(KioskMotion.Checkout.PROCESSING_SPINNER_SLOW_LOOP_MILLIS, easing = KioskEasing.Linear),
        ),
        "spinBack",
    )
    val breathe by transition.animateFloat(
        BREATHE_LOW,
        BREATHE_HIGH,
        infiniteRepeatable(tween(BREATHE_MILLIS / 2, easing = KioskEasing.EaseInOut), RepeatMode.Reverse),
        "breathe",
    )
    Box(modifier = modifier.size(340.dpx), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(340.dpx)) {
            val u = size.width / MARK_VIEWBOX
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(colors.surfaceAlt, radius = OUTER_RADIUS * u, center = center, style = Stroke(OUTER_STROKE * u))
            drawRing(center, OUTER_RADIUS * u, colors.accent, if (reduce) 0f else spin, OUTER_SWEEP, OUTER_STROKE * u)
            drawRing(center, INNER_RADIUS * u, colors.ink, if (reduce) 0f else spinBack, INNER_SWEEP, INNER_STROKE * u)
        }
        Box(
            modifier =
                Modifier
                    .size(100.dpx)
                    .graphicsLayer {
                        val scale = if (reduce) BREATHE_LOW else breathe
                        scaleX = scale
                        scaleY = scale
                    }.background(colors.ink, RoundedCornerShape(30.dpx)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dpx).background(colors.accent, CircleShape))
        }
    }
}

/** An arc of [sweep] degrees on the circle of [radius] around [center], with round ends. */
private fun DrawScope.drawRing(
    center: Offset,
    radius: Float,
    color: Color,
    start: Float,
    sweep: Float,
    strokeWidth: Float,
) {
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(strokeWidth, cap = StrokeCap.Round),
    )
}
