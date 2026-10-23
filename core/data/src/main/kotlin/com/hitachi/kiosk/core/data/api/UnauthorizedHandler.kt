package com.hitachi.kiosk.core.data.api

/**
 * Called once for every HTTP 401 `TOKEN_INVALID`, before the call returns `DomainError.Unauthorized`
 * (architecture §7, TR-NET-06). The binding is [com.hitachi.kiosk.core.data.repository.SessionInvalidator], which
 * wipes the token and binding so the app routes to Sign in.
 */
fun interface UnauthorizedHandler {
    suspend fun onUnauthorized()

    companion object {
        /** Does nothing; for tests of the transport alone. */
        val None: UnauthorizedHandler = UnauthorizedHandler { }
    }
}
