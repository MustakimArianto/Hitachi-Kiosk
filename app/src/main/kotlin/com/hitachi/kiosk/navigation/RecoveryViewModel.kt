package com.hitachi.kiosk.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.recovery.PendingOrderRecoveryCoordinator
import com.hitachi.kiosk.recovery.RecoveryState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Tells the app root that the pending order of a process death turned out to be paid, so it goes to Complete once
 * the kiosk is on Idle (FR-016 AC4, DEV-052). The order itself stays in the coordinator for the Complete screen.
 */
@HiltViewModel
class RecoveryViewModel
    @Inject
    constructor(
        private val recovery: PendingOrderRecoveryCoordinator,
    ) : ViewModel() {
        /** `true` from the moment a recovered order is paid until [onCompleteShown]. */
        val completeRequested: StateFlow<Boolean> =
            recovery.state
                .map { it == RecoveryState.PaidWaiting }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        /** The root navigated to Complete, so it asks only once. */
        fun onCompleteShown() = recovery.completeShown()
    }
