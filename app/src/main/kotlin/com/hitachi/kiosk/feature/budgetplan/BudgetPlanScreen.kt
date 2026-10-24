package com.hitachi.kiosk.feature.budgetplan

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.BackPill
import com.hitachi.kiosk.core.designsystem.component.CartPill
import com.hitachi.kiosk.core.designsystem.component.HeaderPillStyle
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.RollingText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animateOnEnter
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.motionSpec
import com.hitachi.kiosk.core.designsystem.motion.riseIn
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.theme.kioskShadow
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.planner.PlanKind
import com.hitachi.kiosk.navigation.screenTag

/** Test tag of the back pill ("Menu" on step 1, "Your table" on step 2). */
const val BUDGET_PLAN_BACK_TAG = "budget-plan-back"

/** Test tag of the "My order" pill. */
const val BUDGET_PLAN_CART_TAG = "budget-plan-cart"

/** Test tag of the dock button: "See plans", "Reduce group", "Add plan to order" or "Added to order". */
const val BUDGET_PLAN_CTA_TAG = "budget-plan-cta"

/** Test tag of the dock's summary and total. */
const val BUDGET_PLAN_DOCK_SUMMARY_TAG = "budget-plan-dock-summary"

/** The fade-up entrances of `BudgetPas.dc.html`: 700 ms, 24 dpx, at the delays of each block. */
internal const val FADE_UP_MILLIS = 700
private const val HEADER_FADE_MILLIS = 500
private const val EYEBROW_DELAY_MILLIS = 80
private const val TITLE_RISE_MILLIS = 900
private const val TITLE_RISE_DELAY_MILLIS = 80
private const val SUBTITLE_DELAY_MILLIS = 160

/** Rolling amounts: the budget, the plan totals and the dock total (`rollA 480ms`). */
internal const val AMOUNT_ROLL_MILLIS = 480

/** The dock rises from 140 % of its height, 800 ms @300 (`dockIn`). */
private const val DOCK_IN_MILLIS = 800
private const val DOCK_IN_DELAY_MILLIS = 300
private const val DOCK_IN_FRACTION = 1.4f

/** The CTA changes color over 360 ms (`transition: background-color 360ms`). */
private const val CTA_COLOR_MILLIS = 360

/** The Added ring grows to 1.25 × 1.6 and fades over 700 ms; the check pops in over 420 ms. */
private const val RING_OUT_MILLIS = 700
private const val RING_OUT_SCALE_X = 0.25f
private const val RING_OUT_SCALE_Y = 0.6f
private const val RING_START_ALPHA = 0.8f
private const val CHECK_IN_MILLIS = 420
private const val CHECK_START_SCALE = 0.4f
private const val CHECK_START_ROTATION = -45f

/** The background fade above the dock: 320 dpx, transparent to `bg` at 55 %. */
private const val FADE_HEIGHT_PX = 320
private const val FADE_SOLID_AT = 0.55f

/** Letter spacing of the 80 px title (−2.4 px) and of the 44 px dock total (−0.8 px), in em. */
private const val TITLE_TRACKING_EM = -2.4f / 80
private const val DOCK_TOTAL_TRACKING_EM = -0.8f / 44

/**
 * SCR-19 Budget Plan (`BudgetPas.dc.html`, `S_BudgetPas_*`, FR-032, DEV-043, DEV-043a). Draws [state] exactly: the
 * header pills, step 1 "Your table" or step 2 "Your plan", the dock, and the Customize sheet or the budget keypad.
 */
@Composable
fun BudgetPlanScreen(
    state: BudgetPlanUiState,
    onEvent: (BudgetPlanEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(screenTag("SCR-19")),
    ) {
        if (!state.loaded) return@Box
        // Each step mounts its own content, so its entrances replay as in the design.
        key(state.step) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = 160.dpx)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 72.dpx, end = 72.dpx, top = 40.dpx, bottom = 320.dpx),
            ) {
                TitleBlock(state)
                when (state.step) {
                    BudgetPlanStep.TABLE -> StepOne(state, onEvent)
                    BudgetPlanStep.PLAN -> state.plan?.let { StepTwo(state, it, onEvent) }
                }
            }
        }
        Header(state, onEvent)
        BottomFade(Modifier.align(Alignment.BottomCenter))
        Dock(state, onEvent, Modifier.align(Alignment.BottomCenter))
        state.customize?.let { customize ->
            state.plan?.let { plan -> CustomizeSheet(customize, plan, onEvent) }
        }
        state.keypad?.let { KeypadSheet(it, onEvent) }
    }
}

@Composable
private fun Header(
    state: BudgetPlanUiState,
    onEvent: (BudgetPlanEvent) -> Unit,
) {
    val fade = animateEntrance(HEADER_FADE_MILLIS)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 72.dpx, end = 72.dpx, top = 48.dpx)
                .fadeIn(fade),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackPill(
            text =
                stringResource(
                    if (state.step ==
                        BudgetPlanStep.TABLE
                    ) {
                        R.string.customize_menu_pill
                    } else {
                        R.string.budget_plan_your_table
                    },
                ),
            onClick = { onEvent(BudgetPlanEvent.Back) },
            style = HeaderPillStyle.Flat,
            modifier = Modifier.testTag(BUDGET_PLAN_BACK_TAG),
        )
        CartPill(
            text = stringResource(R.string.customize_my_order_pill),
            count = state.cartCount,
            contentDescription =
                pluralStringResource(R.plurals.customize_my_order_description, state.cartCount, state.cartCount),
            onClick = { onEvent(BudgetPlanEvent.ViewOrder) },
            style = HeaderPillStyle.Flat,
            modifier = Modifier.testTag(BUDGET_PLAN_CART_TAG),
        )
    }
}

@Composable
private fun TitleBlock(state: BudgetPlanUiState) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val eyebrow = animateEntrance(FADE_UP_MILLIS, EYEBROW_DELAY_MILLIS)
    val title = animateEntrance(TITLE_RISE_MILLIS, TITLE_RISE_DELAY_MILLIS)
    val subtitle = animateEntrance(FADE_UP_MILLIS, SUBTITLE_DELAY_MILLIS)
    val table = state.step == BudgetPlanStep.TABLE
    val titleText =
        when {
            table -> R.string.budget_plan_title_table
            state.plan?.title == PlanTitle.TWO_PLANS -> R.string.budget_plan_title_two_plans
            state.plan?.title == PlanTitle.PERFECT_FIT -> R.string.budget_plan_title_perfect_fit
            else -> R.string.budget_plan_title_your_plan
        }
    Column {
        KioskText(
            text = stringResource(if (table) R.string.budget_plan_eyebrow_table else R.string.budget_plan_eyebrow_plan),
            style = typography.meta,
            color = colors.muted,
            modifier = Modifier.padding(bottom = 12.dpx).fadeUp(eyebrow, UP_DISTANCE.dpx),
        )
        Box(modifier = Modifier.clipToBounds().padding(bottom = 6.dpx)) {
            KioskText(
                text = stringResource(titleText),
                style = typography.title.copy(letterSpacing = TITLE_TRACKING_EM.em, lineHeight = 1.em),
                modifier = Modifier.riseIn(title),
            )
        }
        KioskText(
            text =
                stringResource(if (table) R.string.budget_plan_subtitle_table else R.string.budget_plan_subtitle_plan),
            style = typography.body,
            color = colors.muted,
            modifier = Modifier.padding(top = 16.dpx).fadeUp(subtitle, UP_DISTANCE.dpx),
        )
    }
}

/** Distance of every fade-up entrance, in design px. */
internal const val UP_DISTANCE = 24

@Composable
private fun BottomFade(modifier: Modifier = Modifier) {
    val bg = KioskTheme.colors.bg
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(FADE_HEIGHT_PX.dpx)
                .background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), FADE_SOLID_AT to bg, 1f to bg)),
    )
}

/** What the dock shows: the summary line, the amount and the button. */
private enum class DockCta { SEE_PLANS, REDUCE_GROUP, ADD, ADDED }

@Composable
private fun Dock(
    state: BudgetPlanUiState,
    onEvent: (BudgetPlanEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.dock
    val enter = animateEntrance(DOCK_IN_MILLIS, DOCK_IN_DELAY_MILLIS)
    val cta = dockCta(state)
    val summary = dockSummary(state, cta)
    val total =
        when (cta) {
            DockCta.REDUCE_GROUP -> {
                stringResource(R.string.budget_plan_dock_no_total)
            }

            DockCta.SEE_PLANS -> {
                state.budget
            }

            DockCta.ADD, DockCta.ADDED -> {
                state.plan
                    ?.hero
                    ?.total
                    .orEmpty()
            }
        }
    val description = stringResource(R.string.budget_plan_dock_description)
    Row(
        modifier =
            modifier
                .padding(start = 72.dpx, end = 72.dpx, bottom = 48.dpx)
                .graphicsLayer { translationY = size.height * DOCK_IN_FRACTION * (1f - enter.value) }
                .fillMaxWidth()
                .height(168.dpx)
                .kioskShadow(shape, KioskTheme.elevation.floating)
                .background(colors.ink, shape)
                .semantics { contentDescription = description }
                .padding(start = 40.dpx, end = 16.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {}
                    .testTag(BUDGET_PLAN_DOCK_SUMMARY_TAG),
            verticalArrangement = Arrangement.spacedBy(2.dpx),
        ) {
            KioskText(text = summary, style = KioskTheme.typography.meta, color = colors.dockMuted, maxLines = 1)
            RollingText(
                text = total,
                style =
                    KioskTheme.typography.section.copy(
                        fontSize = 44.spx,
                        letterSpacing = DOCK_TOTAL_TRACKING_EM.em,
                        lineHeight = (56f / 44f).em,
                    ),
                durationMillis = AMOUNT_ROLL_MILLIS,
                color = colors.bg,
            )
        }
        DockButton(
            cta = cta,
            enabled =
                when (cta) {
                    DockCta.SEE_PLANS -> state.canSeePlans
                    DockCta.REDUCE_GROUP -> false
                    DockCta.ADD, DockCta.ADDED -> true
                },
            onClick = {
                onEvent(if (cta == DockCta.SEE_PLANS) BudgetPlanEvent.SeePlans else BudgetPlanEvent.AddPlan)
            },
        )
    }
}

private fun dockCta(state: BudgetPlanUiState): DockCta =
    when {
        state.groupTooLarge -> DockCta.REDUCE_GROUP
        state.step == BudgetPlanStep.TABLE || state.plan == null -> DockCta.SEE_PLANS
        state.added -> DockCta.ADDED
        else -> DockCta.ADD
    }

/** "3 people · budget", "12 people · max 10", or "3 people · 6 items[ · exact match]" (`BudgetPas.dc.html`). */
@Composable
private fun dockSummary(
    state: BudgetPlanUiState,
    cta: DockCta,
): String {
    val people = pluralStringResource(R.plurals.budget_plan_people, state.people, state.people)
    val plan = state.plan
    return when {
        cta == DockCta.REDUCE_GROUP -> {
            stringResource(R.string.budget_plan_dock_group, people, state.maxGroupSize)
        }

        cta == DockCta.SEE_PLANS || plan == null -> {
            stringResource(R.string.budget_plan_dock_budget, people)
        }

        else -> {
            val items = pluralStringResource(R.plurals.budget_plan_items, plan.itemCount, plan.itemCount)
            val exact = plan.hero.kind == PlanKind.EXACT
            stringResource(
                if (exact) R.string.budget_plan_dock_plan_exact else R.string.budget_plan_dock_plan,
                people,
                items,
            )
        }
    }
}

@Composable
private fun DockButton(
    cta: DockCta,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = KioskTheme.colors
    val added = cta == DockCta.ADDED
    val background by animateColorAsState(
        targetValue = if (added) colors.bg else colors.accent,
        animationSpec = motionSpec(tween(CTA_COLOR_MILLIS, easing = KioskEasing.Emphasized)),
        label = "cta-bg",
    )
    val foreground = if (added) colors.ink else colors.surface
    val shape = RoundedCornerShape(36.dpx)
    Box(
        modifier =
            Modifier
                .height(136.dpx)
                .disabledFade(enabled)
                .pressable(onClick = onClick, enabled = enabled)
                .clip(shape)
                .background(background)
                .testTag(BUDGET_PLAN_CTA_TAG),
        contentAlignment = Alignment.Center,
    ) {
        if (added) AddedRing(shape)
        Row(
            modifier = Modifier.padding(start = 44.dpx, end = 40.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dpx),
        ) {
            if (added) AddedCheck()
            KioskText(
                text =
                    stringResource(
                        when (cta) {
                            DockCta.SEE_PLANS -> R.string.budget_plan_see_plans
                            DockCta.REDUCE_GROUP -> R.string.budget_plan_reduce_group
                            DockCta.ADD -> R.string.budget_plan_add
                            DockCta.ADDED -> R.string.budget_plan_added
                        },
                    ),
                style = KioskTheme.typography.button,
                color = foreground,
                maxLines = 1,
            )
            if (!added) {
                KioskIcon(
                    icon = KioskIcons.ArrowRightBold,
                    tint = colors.surface,
                    modifier = Modifier.size(40.dpx),
                )
            }
        }
    }
}

/** The accent ring that grows and fades around "Added to order" (`ringOut`); none with reduce motion. */
@Composable
private fun AddedRing(shape: RoundedCornerShape) {
    if (LocalReduceMotion.current) return
    val ring = animateOnEnter(tween(RING_OUT_MILLIS, easing = KioskEasing.Press))
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1f + RING_OUT_SCALE_X * ring.value
                    scaleY = 1f + RING_OUT_SCALE_Y * ring.value
                    alpha = RING_START_ALPHA * (1f - ring.value)
                }.border(3.dpx, KioskTheme.colors.accent, shape),
    )
}

@Composable
private fun AddedCheck() {
    val check = animateOnEnter(tween(CHECK_IN_MILLIS, easing = KioskEasing.Pop))
    KioskIcon(
        icon = KioskIcons.Check,
        tint = KioskTheme.colors.accent,
        modifier =
            Modifier.size(40.dpx).graphicsLayer {
                val p = check.value
                alpha = p.coerceIn(0f, 1f)
                scaleX = CHECK_START_SCALE + (1f - CHECK_START_SCALE) * p
                scaleY = scaleX
                rotationZ = CHECK_START_ROTATION * (1f - p)
            },
    )
}

@Composable
private fun BudgetPlanPreview(state: BudgetPlanUiState) {
    KioskTheme(reduceMotion = true) {
        DesignFrame { BudgetPlanScreen(state = state, onEvent = {}) }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun BudgetPlanStepOnePreview() = BudgetPlanPreview(BudgetPlanSamples.StepOne)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun BudgetPlanStepTwoPreview() = BudgetPlanPreview(BudgetPlanSamples.StepTwo)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun BudgetPlanExactPreview() = BudgetPlanPreview(BudgetPlanSamples.Exact)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun BudgetPlanTooLowPreview() = BudgetPlanPreview(BudgetPlanSamples.TooLow)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun BudgetPlanGroupPreview() = BudgetPlanPreview(BudgetPlanSamples.Group)
