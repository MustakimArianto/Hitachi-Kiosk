package com.hitachi.kiosk.inactivity

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import com.hitachi.kiosk.core.domain.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where the kiosk is, as far as the inactivity timer cares (FR-023, architecture §11). */
enum class InactivityZone {
    /** Splash, Sign in, Merchants, Idle, Checkout, Complete and staff settings: no timer (TC-104). */
    OFF,

    /** SCR-05 Order type: with an empty cart the timeout goes straight to Idle (A-23). */
    ORDER_TYPE,

    /** Menu, Combo, Item detail, Your order and Budget Plan (SCR-06…SCR-10, SCR-19). */
    ORDERING,
}

/** The SCR-13 "Still there?" overlay (FR-023). */
@Immutable
sealed interface StillThereState {
    /** No overlay. */
    data object Hidden : StillThereState

    /** The overlay with [secondsLeft] of the 15 s countdown. */
    data class Showing(
        val secondsLeft: Int,
    ) : StillThereState {
        /** The last 5 s, when the ring pulses (`scenario=countdownLow`). */
        val countdownLow: Boolean get() = secondsLeft in 1..COUNTDOWN_LOW_SECONDS
    }

    companion object {
        /** The overlay's countdown (FR-023). */
        const val COUNTDOWN_SECONDS: Int = 15

        /** The ring pulses from here down (FR-023, `StillThere.dc.html`). */
        const val COUNTDOWN_LOW_SECONDS: Int = 5
    }
}

/** One-off effects of the [InactivityController]. */
sealed interface InactivityEffect {
    /** Back to Idle; Idle's ViewModel ends the order and clears the cart (FR-006 AC5). */
    data object GoToIdle : InactivityEffect
}

/**
 * The inactivity timer of FR-023 (architecture §5.1, technical-design §10.3). It runs while the kiosk is in an
 * ordering [InactivityZone] and the order can still be cancelled (it is suspended from Submitting on, so a pending
 * QR never shows the overlay, FR-023 AC4). Every touch moves the deadline ([onUserActivity]); when it passes the
 * overlay counts down 15 s and then times the order out (AC3). On Order type with an empty cart it goes straight to
 * Idle without the overlay (A-23).
 *
 * The deadline is kept on [Clock.elapsedRealtime] and the timer wakes up with coroutine `delay`s, so it is accurate to
 * well within the ±1 s of AC1 and tests drive it with a test clock and virtual time. Settings changes apply to the
 * next timer (FR-026 AC1). Not thread-safe: call it from the [scope]'s thread (the main thread in the app). It is a
 * plain class so the staff area (A-22, DEV-066) can run a second instance with its own timeout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InactivityController(
    private val clock: Clock,
    settingsRepository: SettingsRepository,
    private val sessions: OrderSessionHolder,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<StillThereState>(StillThereState.Hidden)

    /** The overlay to draw. */
    val state: StateFlow<StillThereState> = mutableState.asStateFlow()

    private val effects = Channel<InactivityEffect>(Channel.BUFFERED)

    /** One-off effects to act on (navigate to Idle). */
    val effect: Flow<InactivityEffect> = effects.receiveAsFlow()

    private val timeout: StateFlow<Duration> =
        settingsRepository.settings
            .map { it.idleTimeoutSeconds.coerceAtLeast(1).seconds }
            .stateIn(scope, SharingStarted.Eagerly, KioskSettings.DEFAULT_IDLE_TIMEOUT_SECONDS.seconds)

    private val zone = MutableStateFlow(InactivityZone.OFF)
    private var armed = false
    private var deadline: Duration = Duration.ZERO
    private var timer: Job? = null

    init {
        val cancellable =
            sessions.current.flatMapLatest { session ->
                session?.state?.map { it.phase.allowsTimer() } ?: flowOf(true)
            }
        scope.launch {
            combine(zone, cancellable) { zone, allowed -> zone != InactivityZone.OFF && allowed }
                .distinctUntilChanged()
                .collect { enabled -> if (enabled) arm() else disarm() }
        }
    }

    /** The current destination changed. */
    fun onDestinationChanged(zone: InactivityZone) {
        this.zone.value = zone
    }

    /**
     * A touch anywhere (the root pointer observer). It moves the deadline while the overlay is hidden; a touch on the
     * overlay is the overlay's own business ([onContinue] or [onStartOver]).
     */
    fun onUserActivity() {
        if (!armed || mutableState.value != StillThereState.Hidden) return
        deadline = clock.elapsedRealtime() + timeout.value
        if (timer?.isActive != true) startTimer()
    }

    /** A tap anywhere on the overlay or "Continue ordering": hide it and start a fresh timer (FR-023 AC2). */
    fun onContinue() {
        if (mutableState.value == StillThereState.Hidden) return
        mutableState.value = StillThereState.Hidden
        if (armed) restart()
    }

    /** "Start over": cancel the order and go back to Idle (FR-023, D-15). */
    fun onStartOver() {
        if (mutableState.value == StillThereState.Hidden) return
        stopTimer()
        mutableState.value = StillThereState.Hidden
        sessions.current.value?.cancel()
        effects.trySend(InactivityEffect.GoToIdle)
    }

    /** Hides the overlay and stops the timer, for example on Sign-out; the next touch or destination re-arms it. */
    fun reset() {
        stopTimer()
        mutableState.value = StillThereState.Hidden
    }

    private fun arm() {
        armed = true
        restart()
    }

    private fun disarm() {
        armed = false
        reset()
    }

    private fun restart(fromDeadline: Boolean = true) {
        if (fromDeadline) deadline = clock.elapsedRealtime() + timeout.value
        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = scope.launch { run() }
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private suspend fun run() {
        // Touches move the deadline without restarting this coroutine; it re-reads it on every wake-up.
        sleepUntil { deadline }
        if (zone.value == InactivityZone.ORDER_TYPE && cartIsEmpty()) {
            expire()
            return
        }
        val end = clock.elapsedRealtime() + StillThereState.COUNTDOWN_SECONDS.seconds
        while (true) {
            val left = end - clock.elapsedRealtime()
            if (left <= Duration.ZERO) break
            val secondsLeft = left.inWholeMilliseconds.ceilDiv(MILLIS_PER_SECOND)
            mutableState.value = StillThereState.Showing(secondsLeft.toInt())
            // Wake up when the shown number changes.
            delay(left - (secondsLeft - 1).seconds)
        }
        expire()
    }

    private suspend fun sleepUntil(target: () -> Duration) {
        while (true) {
            val left = target() - clock.elapsedRealtime()
            if (left <= Duration.ZERO) return
            delay(left)
        }
    }

    /** The countdown ran out, or the A-23 shortcut: time the order out and go to Idle with an empty cart (AC3). */
    private fun expire() {
        timer = null
        mutableState.value = StillThereState.Hidden
        sessions.current.value?.timeOut()
        effects.trySend(InactivityEffect.GoToIdle)
    }

    private fun cartIsEmpty(): Boolean =
        sessions.current.value
            ?.state
            ?.value
            ?.cart
            ?.isEmpty() ?: true

    /** Whether the timer may run in [this] phase: only while the order can still be cancelled (architecture §5.1). */
    private fun OrderPhase.allowsTimer(): Boolean = isCancellable

    private fun Long.ceilDiv(divisor: Long): Long = (this + divisor - 1) / divisor

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
