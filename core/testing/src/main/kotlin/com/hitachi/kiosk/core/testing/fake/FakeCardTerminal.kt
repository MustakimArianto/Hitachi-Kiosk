package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.CardTerminal
import com.hitachi.kiosk.core.domain.device.TerminalEvent
import com.hitachi.kiosk.core.domain.model.Rupiah
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.selects.select
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A [CardTerminal] that approves, declines, errors or never answers (FR-018, architecture §9). A sale emits
 * [TerminalEvent.Prompted], waits [resultDelay] and then emits the result for [mode]. [Mode.NO_ANSWER] never answers,
 * so the 90 s terminal timeout can be tested. [cancel] ends a running sale with [TerminalEvent.Cancelled].
 */
class FakeCardTerminal(
    var mode: Mode = Mode.APPROVE,
    var resultDelay: Duration = 2.seconds,
) : CardTerminal {
    enum class Mode { APPROVE, DECLINE, CUSTOMER_CANCEL, ERROR, NO_ANSWER }

    data class Sale(
        val amount: Rupiah,
        val reference: String,
    )

    private val recordedSales = mutableListOf<Sale>()

    @Volatile
    private var cancelSignal = CompletableDeferred<Unit>()

    /** Every sale started, in order. */
    val sales: List<Sale> get() = recordedSales.toList()

    var cancelCount: Int = 0
        private set

    val selfTestScript = Script<Unit>(Outcome.Success(Unit))

    override fun startSale(
        amount: Rupiah,
        reference: String,
    ): Flow<TerminalEvent> =
        channelFlow {
            recordedSales += Sale(amount, reference)
            val cancelled = CompletableDeferred<Unit>().also { cancelSignal = it }
            send(TerminalEvent.Prompted)
            val answer = async { result(reference) }
            val event =
                select {
                    cancelled.onAwait { TerminalEvent.Cancelled }
                    answer.onAwait { it }
                }
            answer.cancel()
            send(event)
        }

    override suspend fun cancel() {
        cancelCount++
        cancelSignal.complete(Unit)
    }

    override suspend fun selfTest(): Outcome<Unit> = selfTestScript.next()

    private suspend fun result(reference: String): TerminalEvent {
        delay(resultDelay)
        return when (mode) {
            Mode.APPROVE -> TerminalEvent.Approved(reference = reference, tid = TID, approvalCode = "123456")
            Mode.DECLINE -> TerminalEvent.Declined(code = "05")
            Mode.CUSTOMER_CANCEL -> TerminalEvent.Cancelled
            Mode.ERROR -> TerminalEvent.Error(reason = "terminal_error")
            Mode.NO_ANSWER -> awaitCancellation()
        }
    }

    private companion object {
        const val TID = "TID 0003 1182"
    }
}
