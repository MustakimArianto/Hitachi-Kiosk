package com.hitachi.kiosk.core.domain.device

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.model.Rupiah
import kotlinx.coroutines.flow.Flow

/**
 * The EDC card terminal (FR-018, technical-design §9.3). The transport is decided in ADR-0003 (OQ-03); adapters only
 * turn vendor events into [TerminalEvent]s.
 */
interface CardTerminal {
    /** Prompts the customer for [amount] with the backend's [reference]. The flow ends after a final event. */
    fun startSale(
        amount: Rupiah,
        reference: String,
    ): Flow<TerminalEvent>

    /** Cancels the sale in progress, if any. */
    suspend fun cancel()

    /** Checks that the terminal answers (Settings › Payment, FR-025). */
    suspend fun selfTest(): Outcome<Unit>
}

/** What the terminal reports during a sale. Everything except [Prompted] is final. */
sealed interface TerminalEvent {
    /** The terminal shows the amount and waits for the card. */
    data object Prompted : TerminalEvent

    data class Approved(
        val reference: String,
        val tid: String,
        val approvalCode: String? = null,
        val cardBrand: String? = null,
        val maskedPan: String? = null,
        val rawResponseCode: String? = null,
    ) : TerminalEvent

    data class Declined(
        val code: String,
    ) : TerminalEvent

    data object Cancelled : TerminalEvent

    data class Error(
        val reason: String,
    ) : TerminalEvent
}
