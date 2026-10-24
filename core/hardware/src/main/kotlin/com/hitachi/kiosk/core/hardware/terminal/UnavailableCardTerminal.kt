package com.hitachi.kiosk.core.hardware.terminal

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.CardTerminal
import com.hitachi.kiosk.core.domain.device.TerminalEvent
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Rupiah
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * The [CardTerminal] of a build that has no EDC adapter yet. The transport and protocol wait on OQ-03 and ADR-0003
 * (technical-design §9.3), and the real adapter arrives in 0.9.0 (DEV-091). Until then every sale fails at once with a
 * terminal error, so the payment machine shows its generic failure and never charges anything, and the self-test
 * reports a hardware error (FR-025). Debug builds replace this with the scriptable fake (`BindingOverrides`).
 */
class UnavailableCardTerminal
    @Inject
    constructor() : CardTerminal {
        override fun startSale(
            amount: Rupiah,
            reference: String,
        ): Flow<TerminalEvent> {
            KioskLog.w("card_terminal_unavailable")
            return flowOf(TerminalEvent.Error(REASON))
        }

        override suspend fun cancel() = Unit

        override suspend fun selfTest(): Outcome<Unit> =
            Outcome.Failure(DomainError.Hardware(device = DEVICE, reason = REASON))

        companion object {
            const val DEVICE = "card_terminal"
            const val REASON = "adapter_unavailable"
        }
    }
