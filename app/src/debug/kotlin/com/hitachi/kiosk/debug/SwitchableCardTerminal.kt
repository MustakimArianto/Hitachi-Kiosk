package com.hitachi.kiosk.debug

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.CardTerminal
import com.hitachi.kiosk.core.domain.device.TerminalEvent
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.testing.fake.FakeCardTerminal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * Debug only: sends each sale to the release terminal adapter, to a [ManualCardTerminal] that waits for the Checkout
 * simulate bar, or to a [FakeCardTerminal] that answers by itself, following the Developer options at the time of the
 * sale (FR-018, DEV-049, technical-design §4.3).
 */
class SwitchableCardTerminal(
    private val real: CardTerminal,
    private val settings: () -> DeveloperSettings,
) : CardTerminal {
    private val fake = FakeCardTerminal()

    /** The terminal the simulate bar drives in [TerminalMode.MANUAL]. */
    val manual = ManualCardTerminal()

    @Volatile
    private var active: CardTerminal = manual

    /** The implementation for the current settings. */
    @Synchronized
    fun target(): CardTerminal {
        val mode = settings().terminalMode
        return when (mode) {
            TerminalMode.REAL -> real
            TerminalMode.MANUAL -> manual
            TerminalMode.APPROVE -> fake.also { it.mode = FakeCardTerminal.Mode.APPROVE }
            TerminalMode.DECLINE -> fake.also { it.mode = FakeCardTerminal.Mode.DECLINE }
            TerminalMode.CUSTOMER_CANCEL -> fake.also { it.mode = FakeCardTerminal.Mode.CUSTOMER_CANCEL }
            TerminalMode.ERROR -> fake.also { it.mode = FakeCardTerminal.Mode.ERROR }
            TerminalMode.NO_ANSWER -> fake.also { it.mode = FakeCardTerminal.Mode.NO_ANSWER }
        }
    }

    override fun startSale(
        amount: Rupiah,
        reference: String,
    ): Flow<TerminalEvent> {
        val chosen = target()
        active = chosen
        return chosen.startSale(amount, reference)
    }

    /** Cancels the sale on the terminal that took it, whatever the settings say now. */
    override suspend fun cancel() = active.cancel()

    override suspend fun selfTest(): Outcome<Unit> = target().selfTest()
}

/**
 * Debug only: a terminal that prompts and then waits for [approve] or [decline], which the Checkout simulate bar calls
 * (FR-019, "Prototype only · simulate the result"). [cancel] ends the sale with [TerminalEvent.Cancelled].
 */
class ManualCardTerminal : CardTerminal {
    private val lock = Any()
    private var answer: CompletableDeferred<TerminalEvent>? = null
    private var reference: String = ""

    /** Whether a sale is waiting for an answer. */
    val isWaiting: Boolean get() = synchronized(lock) { answer?.isCompleted == false }

    override fun startSale(
        amount: Rupiah,
        reference: String,
    ): Flow<TerminalEvent> =
        channelFlow {
            val waiting = CompletableDeferred<TerminalEvent>()
            synchronized(lock) {
                answer = waiting
                this@ManualCardTerminal.reference = reference
            }
            send(TerminalEvent.Prompted)
            send(waiting.await())
        }

    override suspend fun cancel() {
        synchronized(lock) { answer }?.complete(TerminalEvent.Cancelled)
    }

    override suspend fun selfTest(): Outcome<Unit> = Outcome.Success(Unit)

    /** The customer's card is approved. `false` when no sale is waiting. */
    fun approve(): Boolean =
        synchronized(lock) { answer }
            ?.complete(TerminalEvent.Approved(reference = reference, tid = TID, approvalCode = APPROVAL_CODE)) == true

    /** The bank declines the card. `false` when no sale is waiting. */
    fun decline(): Boolean =
        synchronized(lock) { answer }?.complete(TerminalEvent.Declined(code = DECLINE_CODE)) == true

    private companion object {
        const val TID = "TID 0003 1182"
        const val APPROVAL_CODE = "123456"
        const val DECLINE_CODE = "05"
    }
}
