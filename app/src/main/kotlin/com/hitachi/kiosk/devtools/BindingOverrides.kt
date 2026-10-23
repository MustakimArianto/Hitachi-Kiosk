package com.hitachi.kiosk.devtools

import com.hitachi.kiosk.core.domain.backend.BackendApi

/**
 * Lets the debug build wrap the real backend client, to switch between fakes, the mock server and the real backend
 * at runtime (technical-design §4.3). Only the `debug` source set binds it; in release the real client is used as is.
 * The payment gateway delegates to [BackendApi], so it follows the same switch.
 */
fun interface BindingOverrides {
    fun backendApi(real: BackendApi): BackendApi
}
