package com.hitachi.kiosk.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Watches the device state for the whole app: when an activated kiosk loses its activation (HTTP 401 or Log out),
 * [signedOut] fires so the root can return to Sign in from any screen (FR-003, TR-NET-06). The state can change on
 * any thread (an OkHttp call, DataStore); the effect is sent from [viewModelScope], so the root navigates on the main
 * thread.
 */
@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        activation: ActivationRepository,
    ) : ViewModel() {
        private val effects = Channel<Unit>(Channel.CONFLATED)
        val signedOut: Flow<Unit> = effects.receiveAsFlow()

        init {
            viewModelScope.launch {
                activation.state
                    .map { it == DeviceState.NotActivated }
                    .distinctUntilChanged()
                    // The first value is the state when the app subscribes, not a change.
                    .drop(1)
                    .filter { it }
                    .collect { effects.send(Unit) }
            }
        }
    }
