package com.hitachi.kiosk.core.data.api

/**
 * Called once for every HTTP 401 `TOKEN_INVALID` (architecture §7, TR-NET-06). The default binding in 0.1.0 is
 * [None]; DEV-014 (0.2.0) binds the `SessionInvalidator` that clears the token and binding and routes to Sign in.
 */
fun interface UnauthorizedHandler {
    fun onUnauthorized()

    companion object {
        /** Does nothing (0.1.0 default). */
        val None: UnauthorizedHandler = UnauthorizedHandler { }
    }
}
