package com.hitachi.kiosk.core.designsystem.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** The named easing curves of the design (PRD §11.4, NFR-009, DEV-004). */
object KioskEasing {
    /** `(.16, 1, .3, 1)`: entrances, title rises, sheets. */
    val Emphasized: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** `(.2, 0, 0, 1)`: press feedback. */
    val Press: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** `(.3, 1.15, .4, 1)`: springy pops with a slight overshoot (dots, indicators). */
    val Spring: Easing = CubicBezierEasing(0.3f, 1.15f, 0.4f, 1f)

    /** `(.65, 0, .35, 1)`: symmetric in-out (progress bars). */
    val InOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** `(.55, 0, .75, .2)`: accelerating fly-to-cart. */
    val Fly: Easing = CubicBezierEasing(0.55f, 0f, 0.75f, 0.2f)
}

/**
 * Durations and delays of the motion inventory, PRD §11.4, in milliseconds (NFR-009, DEV-004). `…Millis` is a
 * duration, `…DelayMillis` the delay from the screen's start, `…StaggerMillis` the gap between consecutive
 * items. Where the PRD gives two delays (`@900/1000`), they are the first and second line or card. Loops
 * name one period. Every spec built from these must go through [motionSpec].
 */
object KioskMotion {
    /** Splash. */
    object Splash {
        const val MARK_IN_MILLIS: Int = 900
        const val MARK_IN_DELAY_MILLIS: Int = 150
        const val DOT_IN_MILLIS: Int = 600
        const val DOT_IN_DELAY_MILLIS: Int = 700
        const val RING_OUT_MILLIS: Int = 1400
        const val RING_OUT_DELAY_MILLIS: Int = 900
        const val TITLE_RISE_MILLIS: Int = 900
        const val TITLE_RISE_DELAY_MILLIS: Int = 900
        const val TITLE_RISE_SECOND_DELAY_MILLIS: Int = 1000
        const val TAGLINE_UP_MILLIS: Int = 800
        const val TAGLINE_UP_DELAY_MILLIS: Int = 1350
        const val BAR_FILL_MILLIS: Int = 2200
        const val BAR_FILL_DELAY_MILLIS: Int = 1600
        const val AUTO_ADVANCE_MILLIS: Int = 3900
    }

    /** Sign in. */
    object SignIn {
        const val MARK_IN_MILLIS: Int = 800
        const val MARK_IN_DELAY_MILLIS: Int = 80
        const val BREATHE_LOOP_MILLIS: Int = 3200
        const val TITLE_RISE_MILLIS: Int = 900
        const val TITLE_RISE_DELAY_MILLIS: Int = 160
        const val TITLE_RISE_SECOND_DELAY_MILLIS: Int = 240
        const val DOCK_MILLIS: Int = 800
        const val DOCK_DELAY_MILLIS: Int = 300

        /** Linear spinner rotation period. */
        const val SPINNER_LOOP_MILLIS: Int = 800
    }

    /** Merchants. */
    object Merchants {
        const val TITLE_RISE_MILLIS: Int = 900
        const val CARD_UP_MILLIS: Int = 700
        const val CARD_UP_DELAY_MILLIS: Int = 220
        const val SOON_ROWS_STAGGER_MILLIS: Int = 60
        const val SOON_ROWS_DELAY_MILLIS: Int = 360
        const val OPEN_NOW_PULSE_LOOP_MILLIS: Int = 1800
        const val ARROW_NUDGE_LOOP_MILLIS: Int = 1800
    }

    /** Idle (attract screen). */
    object Idle {
        const val SLIDE_CROSSFADE_MILLIS: Int = 1400
        const val KEN_BURNS_MILLIS: Int = 8000

        /** Linear progress per slide. */
        const val PROGRESS_MILLIS: Int = 7000
        const val SHEET_UP_MILLIS: Int = 900
        const val SHEET_UP_DELAY_MILLIS: Int = 100
        const val TITLE_RISE_MILLIS: Int = 1000
        const val TITLE_RISE_DELAY_MILLIS: Int = 250
        const val CTA_RING_LOOP_MILLIS: Int = 2800
        const val TAP_ICON_LOOP_MILLIS: Int = 1800
    }

    /** Order type. */
    object OrderType {
        const val LOGO_TILE_MILLIS: Int = 1100
        const val LOGO_TILE_DELAY_MILLIS: Int = 100
        const val CARDS_IN_MILLIS: Int = 900
        const val CARDS_IN_DELAY_MILLIS: Int = 360
        const val CARDS_IN_SECOND_DELAY_MILLIS: Int = 440
        const val SELECTION_FILL_MILLIS: Int = 600
        const val CHECK_POP_MILLIS: Int = 500
        const val CHECK_POP_DELAY_MILLIS: Int = 150
        const val RING_MILLIS: Int = 800
        const val RING_DELAY_MILLIS: Int = 200
        const val ICON_LIFT_MILLIS: Int = 600
        const val ICON_LIFT_DELAY_MILLIS: Int = 120

        /** Time from the tap to navigating to Menu. */
        const val ADVANCE_MILLIS: Int = 950
    }

    /** Menu. */
    object Menu {
        const val PILLS_IN_MILLIS: Int = 600
        const val PILLS_STAGGER_MILLIS: Int = 50
        const val CARDS_IN_MILLIS: Int = 700
        const val CARDS_STAGGER_MILLIS: Int = 60

        /** Fly-to-cart, with [KioskEasing.Fly]. */
        const val FLY_TO_CART_MILLIS: Int = 760
        const val DOCK_BUMP_MILLIS: Int = 460
        const val DOCK_BUMP_DELAY_MILLIS: Int = 650
        const val TOTAL_ROLL_MILLIS: Int = 480
        const val TOTAL_ROLL_DELAY_MILLIS: Int = 650
    }

    /** Item detail. */
    object Item {
        const val HERO_SCALE_MILLIS: Int = 1200
        const val SHEET_UP_MILLIS: Int = 800
        const val SHEET_UP_DELAY_MILLIS: Int = 120
        const val SECTIONS_FADE_UP_MILLIS: Int = 650
        const val SECTIONS_FADE_UP_DELAY_MILLIS: Int = 560
        const val SECTIONS_FADE_UP_SECOND_DELAY_MILLIS: Int = 640
        const val SECTIONS_FADE_UP_THIRD_DELAY_MILLIS: Int = 720
        const val QTY_ROLL_MILLIS: Int = 340
        const val PRICE_ROLL_MILLIS: Int = 420
        const val FLY_MILLIS: Int = 820
        const val SWAP_IN_MILLIS: Int = 480
    }

    /** Combo. */
    object Combo {
        const val LIST_FADE_UP_STAGGER_MILLIS: Int = 60
        const val LIST_FADE_UP_DELAY_MILLIS: Int = 200
        const val RADIO_DOT_MILLIS: Int = 420
        const val CHECK_MILLIS: Int = 380

        /** Size indicator, with [KioskEasing.Spring]. */
        const val SIZE_INDICATOR_MILLIS: Int = 550
        const val TOTAL_ROLL_MILLIS: Int = 460
        const val ADDED_STATE_MILLIS: Int = 1800
    }

    /** Your order. */
    object YourOrder {
        const val ROWS_IN_MILLIS: Int = 700
        const val ROWS_STAGGER_MILLIS: Int = 70
        const val REMOVE_MILLIS: Int = 460
        const val PANEL_UP_MILLIS: Int = 900
        const val PANEL_UP_DELAY_MILLIS: Int = 300
        const val TOTALS_ROLL_MILLIS: Int = 420
        const val METHOD_TILES_MILLIS: Int = 700
        const val METHOD_TILES_STAGGER_MILLIS: Int = 70
        const val METHOD_TILES_DELAY_MILLIS: Int = 500
    }

    /** Checkout. */
    object Checkout {
        const val QR_IN_MILLIS: Int = 900
        const val QR_IN_DELAY_MILLIS: Int = 220

        /** The countdown ring moves in 1 s steps. */
        const val COUNTDOWN_STEP_MILLIS: Int = 1000
        const val CARD_TAP_LOOP_MILLIS: Int = 2600
        const val PROCESSING_SPINNER_LOOP_MILLIS: Int = 1100
        const val PROCESSING_SPINNER_SLOW_LOOP_MILLIS: Int = 1900
        const val FAIL_POP_MILLIS: Int = 560
        const val SHAKE_MILLIS: Int = 520
        const val SHAKE_DELAY_MILLIS: Int = 500
    }

    /** Complete. */
    object Complete {
        const val FLOOD_MILLIS: Int = 1500
        const val BADGE_MILLIS: Int = 700
        const val BADGE_DELAY_MILLIS: Int = 900
        const val CHECK_DRAW_MILLIS: Int = 560
        const val CHECK_DRAW_DELAY_MILLIS: Int = 1250
        const val RING_WAVES_MILLIS: Int = 1600
        const val RING_WAVES_DELAY_MILLIS: Int = 1050
        const val RING_WAVES_SECOND_DELAY_MILLIS: Int = 1300
        const val CONFETTI_MILLIS: Int = 1500

        /** "~@1150" in the PRD. */
        const val CONFETTI_DELAY_MILLIS: Int = 1150
        const val NUMBER_DIGITS_MILLIS: Int = 600
        const val NUMBER_DIGITS_STAGGER_MILLIS: Int = 90
        const val NUMBER_DIGITS_DELAY_MILLIS: Int = 1700
        const val RECEIPT_PRINT_MILLIS: Int = 1800
        const val RECEIPT_PRINT_DELAY_MILLIS: Int = 2100

        /** Return-to-Idle ring. */
        const val RETURN_RING_MILLIS: Int = 20_000
    }

    /** Budget Plan. */
    object BudgetPlan {
        /** Step swap: crossfade plus a 24 dpx slide. */
        const val STEP_SLIDE_PX: Int = 24
        const val HERO_CARD_RISE_MILLIS: Int = 700
        const val COLLAPSIBLE_ROW_MILLIS: Int = 400

        /** Customize sheet up, with [KioskEasing.Emphasized]. */
        const val CUSTOMIZE_SHEET_UP_MILLIS: Int = 620
    }

    /** "Still there?" inactivity overlay. */
    object StillThere {
        const val SCRIM_IN_MILLIS: Int = 600

        /** The countdown ring moves in 1 s steps. */
        const val COUNTDOWN_STEP_MILLIS: Int = 1000

        /** The ring pulses during the last 5 s. */
        const val PULSE_LAST_MILLIS: Int = 5000
    }

    /** Staff settings. */
    object Settings {
        const val KEYPAD_STAGGER_MILLIS: Int = 30
        const val KEYPAD_DELAY_MILLIS: Int = 260
        const val DOT_POP_MILLIS: Int = 360
        const val ACTION_BUSY_MILLIS: Int = 1500

        /** Time an action stays "done" before returning to idle. */
        const val ACTION_DONE_MILLIS: Int = 3800
    }
}

/**
 * Whether animations are reduced (FR-026, NFR-009, TR-ACC-05). When true, every spec passed through
 * [motionSpec] collapses to [snap], so nothing animates and nothing waits. Set by `KioskTheme(reduceMotion = …)`,
 * which the app feeds from the setting OR the system "Remove animations" setting.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** [spec], or [snap] when [LocalReduceMotion] is on (NFR-009, TR-ACC-05, DEV-004). */
@Composable
@ReadOnlyComposable
fun <T> motionSpec(spec: AnimationSpec<T>): AnimationSpec<T> = spec.reducedIf(LocalReduceMotion.current)

/** [spec], or [snap] when [LocalReduceMotion] is on; for APIs that need a finite spec (NFR-009, DEV-004). */
@Composable
@ReadOnlyComposable
fun <T> motionSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = spec.reducedIf(LocalReduceMotion.current)

/** This spec, or a zero-delay [snap] when [reduce] is true (NFR-009, TR-ACC-05, DEV-004). */
fun <T> AnimationSpec<T>.reducedIf(reduce: Boolean): AnimationSpec<T> = if (reduce) snap() else this

/** This finite spec, or a zero-delay [snap] when [reduce] is true (NFR-009, TR-ACC-05, DEV-004). */
fun <T> FiniteAnimationSpec<T>.reducedIf(reduce: Boolean): FiniteAnimationSpec<T> = if (reduce) snap() else this
