package com.hitachi.kiosk.inactivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import com.hitachi.kiosk.core.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** What the root sends to the [InactivityViewModel] (conventions §3). */
sealed interface InactivityEvent {
    /** A touch anywhere on the screen (technical-design §10.3). */
    data object UserActivity : InactivityEvent

    /** The back stack moved to a destination in [zone]. */
    data class DestinationChanged(
        val zone: InactivityZone,
    ) : InactivityEvent

    /** A tap anywhere on the overlay or "Continue ordering" (FR-023 AC2). */
    data object Continue : InactivityEvent

    /** "Start over" (FR-023, D-15). */
    data object StartOver : InactivityEvent

    /** The kiosk signed out: hide the overlay (FR-003). */
    data object SignedOut : InactivityEvent

    /** The placeholder "Simulate inactivity" action (removed when every placeholder screen is gone). */
    data object ShowNow : InactivityEvent
}

/**
 * Holds the customer [InactivityController] for `KioskRoot` (FR-023). The controller lives in [viewModelScope], so it
 * runs on the main thread with the touches that feed it and stops with the activity's ViewModel store.
 */
@HiltViewModel
class InactivityViewModel
    @Inject
    constructor(
        clock: Clock,
        settingsRepository: SettingsRepository,
        orderSessionHolder: OrderSessionHolder,
    ) : ViewModel() {
        private val controller = InactivityController(clock, settingsRepository, orderSessionHolder, viewModelScope)

        /** The SCR-13 overlay. */
        val state: StateFlow<StillThereState> = controller.state

        /** Navigate to Idle. */
        val effect: Flow<InactivityEffect> = controller.effect

        fun onEvent(event: InactivityEvent) {
            when (event) {
                InactivityEvent.UserActivity -> controller.onUserActivity()
                is InactivityEvent.DestinationChanged -> controller.onDestinationChanged(event.zone)
                InactivityEvent.Continue -> controller.onContinue()
                InactivityEvent.StartOver -> controller.onStartOver()
                InactivityEvent.SignedOut -> controller.reset()
                InactivityEvent.ShowNow -> controller.showNow()
            }
        }
    }
