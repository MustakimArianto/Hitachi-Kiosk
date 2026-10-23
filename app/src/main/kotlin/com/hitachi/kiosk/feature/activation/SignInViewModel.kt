package com.hitachi.kiosk.feature.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.setup.ActivateKiosk
import com.hitachi.kiosk.di.VersionName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SCR-02 Sign in (FR-002, FR-003, DEV-013). Reads the serial number when the screen opens; Log in is enabled only
 * once it is known. Log in runs at most once at a time, so a double tap sends one E1 (FR-003 AC3).
 */
@HiltViewModel
class SignInViewModel
    @Inject
    constructor(
        private val identityProvider: DeviceIdentityProvider,
        private val activateKiosk: ActivateKiosk,
        @VersionName versionName: String,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SignInUiState(versionName = versionName))
        val state: StateFlow<SignInUiState> = mutableState.asStateFlow()

        private val effects = Channel<SignInEffect>(Channel.BUFFERED)
        val effect: Flow<SignInEffect> = effects.receiveAsFlow()

        private var identity: DeviceIdentity? = null
        private var signingIn = false

        init {
            viewModelScope.launch { readIdentity() }
        }

        fun onEvent(event: SignInEvent) {
            when (event) {
                SignInEvent.LogIn -> logIn()
            }
        }

        private suspend fun readIdentity() {
            when (val outcome = identityProvider.identify()) {
                is Outcome.Success -> {
                    identity = outcome.value
                    mutableState.update { it.copy(busy = false, buttonEnabled = true) }
                }

                // FR-002: no serial number, no Log in. Staff restart the kiosk.
                is Outcome.Failure -> {
                    mutableState.update {
                        it.copy(
                            busy = false,
                            buttonEnabled = false,
                            error = SignInError.SerialUnreadable,
                        )
                    }
                }
            }
        }

        private fun logIn() {
            val identity = identity ?: return
            if (signingIn) return
            signingIn = true
            mutableState.update {
                it.copy(button = SignInButton.SIGNING_IN, busy = true, buttonEnabled = false, error = null)
            }
            viewModelScope.launch {
                val outcome = activateKiosk(identity)
                signingIn = false
                when (outcome) {
                    is Outcome.Success -> onActivated(outcome.value)
                    is Outcome.Failure -> showError(outcome.error.toSignInError(identity.serialNumber))
                }
            }
        }

        private suspend fun onActivated(state: DeviceState) {
            when (state) {
                is DeviceState.Activated -> effects.send(SignInEffect.NavigateToMerchants)

                is DeviceState.Bound -> effects.send(SignInEffect.NavigateToIdle)

                // Cannot follow a successful activation; shown as a generic failure rather than ignored.
                DeviceState.NotActivated -> showError(SignInError.Generic)
            }
        }

        private fun showError(error: SignInError) {
            // FR-003 AC2: the button is tappable again as soon as the error shows.
            mutableState.update {
                it.copy(button = SignInButton.TRY_AGAIN, busy = false, buttonEnabled = true, error = error)
            }
        }

        private fun DomainError.toSignInError(serialNumber: String): SignInError =
            when {
                this is DomainError.Network -> SignInError.Network
                this is DomainError.Server && code == CODE_NOT_REGISTERED -> SignInError.NotRegistered(serialNumber)
                this is DomainError.Server && code == CODE_DISABLED -> SignInError.Disabled
                else -> SignInError.Generic
            }

        private companion object {
            const val CODE_NOT_REGISTERED = "DEVICE_NOT_REGISTERED"
            const val CODE_DISABLED = "DEVICE_DISABLED"
        }
    }
