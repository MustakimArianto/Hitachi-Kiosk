package com.hitachi.kiosk.core.domain.device

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the kiosk can reach the backend and the gateway (FR-028, technical-design §7.3). The checkout gate and the
 * offline banner read only this flow.
 */
interface ConnectivityMonitor {
    val connectivity: StateFlow<Connectivity>
}

enum class Connectivity { ONLINE, OFFLINE, CHECKING }
