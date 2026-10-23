package com.hitachi.kiosk.core.data.repository

import com.hitachi.kiosk.core.data.api.UnauthorizedHandler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Handles HTTP 401 `TOKEN_INVALID` (architecture §7, TR-NET-06, FR-003): wipes the token and binding. The device
 * state then reads [com.hitachi.kiosk.core.domain.model.DeviceState.NotActivated], and the app routes to Sign in.
 * The wipe finishes even if the caller is cancelled meanwhile.
 */
internal class SessionInvalidator
    @Inject
    constructor(
        private val session: DeviceSession,
    ) : UnauthorizedHandler {
        override suspend fun onUnauthorized() {
            withContext(NonCancellable) { session.wipe(reason = "unauthorized") }
        }
    }
