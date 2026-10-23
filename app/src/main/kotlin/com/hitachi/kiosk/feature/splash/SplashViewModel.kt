package com.hitachi.kiosk.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.navigation.StartupRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** SCR-01: runs startup routing once and reports where to go (FR-001, DEV-011). */
@HiltViewModel
class SplashViewModel
    @Inject
    constructor(
        router: StartupRouter,
    ) : ViewModel() {
        private val effects = Channel<SplashEffect>(Channel.BUFFERED)
        val effect: Flow<SplashEffect> = effects.receiveAsFlow()

        init {
            viewModelScope.launch { effects.send(SplashEffect.Navigate(router.route())) }
        }
    }
