package com.hitachi.kiosk.core.data.api

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the connectivity monitor that a real backend call failed with `DomainError.Network`, so it can re-probe E14
 * at once instead of waiting for the next network callback (technical-design §7.3, FR-028). [ApiErrorMapper] reports
 * it for every endpoint except E14, whose own failure is the probe's result.
 *
 * It is a data-internal hook: it carries no payload, so no Android, DTO or domain type crosses it. A report with no
 * listener is dropped, and a burst of reports is conflated into one.
 */
@Singleton
class NetworkErrorSignal
    @Inject
    constructor() {
        private val events =
            MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /** One element per reported network error that arrived while someone was listening. */
        val reports: Flow<Unit> = events.asSharedFlow()

        /** Called by [ApiErrorMapper]; never suspends. */
        fun report() {
            events.tryEmit(Unit)
        }
    }
