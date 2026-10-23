package com.hitachi.kiosk.devtools

import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider

/**
 * Lets the debug build wrap real implementations, to switch them to fakes at runtime (technical-design §4.3 TD-11).
 * Only the `debug` source set binds it; in release the real implementations are used as they are.
 */
interface BindingOverrides {
    /**
     * Switches between fakes, the mock server and the real backend. The payment gateway delegates to [BackendApi], so
     * it follows the same switch.
     */
    fun backendApi(real: BackendApi): BackendApi

    /** Switches between the real serial-number reader and the FR-002 fakes (success, unavailable, slow). */
    fun deviceIdentityProvider(real: DeviceIdentityProvider): DeviceIdentityProvider

    /** The connectivity the offline banner reads (FR-028). Instrumented tests replace it with a scriptable fake. */
    fun connectivityMonitor(real: ConnectivityMonitor): ConnectivityMonitor
}
