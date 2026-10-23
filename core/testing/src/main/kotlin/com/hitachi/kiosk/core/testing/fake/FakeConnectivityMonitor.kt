package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A [ConnectivityMonitor] a test switches between online, offline and checking (FR-028). */
class FakeConnectivityMonitor(
    initial: Connectivity = Connectivity.ONLINE,
) : ConnectivityMonitor {
    private val state = MutableStateFlow(initial)

    override val connectivity: StateFlow<Connectivity> = state.asStateFlow()

    fun set(connectivity: Connectivity) {
        state.value = connectivity
    }
}
