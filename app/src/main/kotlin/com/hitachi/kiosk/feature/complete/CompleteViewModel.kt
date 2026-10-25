package com.hitachi.kiosk.feature.complete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.payment.PaymentStateMachine
import com.hitachi.kiosk.core.domain.payment.PaymentUiState
import com.hitachi.kiosk.core.domain.time.Clock
import com.hitachi.kiosk.recovery.RecoveredPaidSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val RETURN_AFTER: Duration = CompleteUiState.RETURN_SECONDS.seconds
private val COUNTDOWN_TICK: Duration = 1.seconds
private const val MILLIS_PER_SECOND = 1_000L

/**
 * SCR-12 Complete (FR-020, DEV-060). On entry it takes the paid details from the [PaymentStateMachine] (the order of
 * this session) or, when the app was killed during the payment and a recovery found the order paid, from
 * [RecoveredPaidSource]. Then it runs the 20 s return timer on the injected [Clock] (AC2).
 *
 * **Hand-off**: Checkout leaves the machine in [PaymentUiState.Paid]. This ViewModel reads it at once, and
 * acknowledges it when the screen is shown ([CompleteEvent.Shown], or on leaving at the latest), which forgets the
 * pending order. A kill between Checkout and this screen therefore still finds the paid order on the next start
 * (technical-design §8.6, FR-016 AC4).
 *
 * **Leaving** (Done, the timer, or nothing to show) ends the order session, so the cart is gone before Idle appears
 * (AC3, BR-09), and sends [CompleteEffect.ToIdle] once. Printing is not part of 0.6.0 (PO decision 2026-10-25), so
 * nothing here waits for or reports a printer.
 */
@HiltViewModel
class CompleteViewModel
    @Inject
    constructor(
        private val machine: PaymentStateMachine,
        recovered: RecoveredPaidSource,
        private val sessions: OrderSessionHolder,
        private val clock: Clock,
    ) : ViewModel() {
        private val orderType: OrderType? =
            sessions.current.value
                ?.state
                ?.value
                ?.orderType

        private val paid = machine.state.value as? PaymentUiState.Paid
        private val recoveredOrder = if (paid == null) recovered.consumeRecoveredPaid() else null

        private val mutableState = MutableStateFlow(initialState())
        val state: StateFlow<CompleteUiState> = mutableState.asStateFlow()

        private val effects = Channel<CompleteEffect>(Channel.BUFFERED)
        val effect: Flow<CompleteEffect> = effects.receiveAsFlow()

        private val leaving = AtomicBoolean(false)
        private val acknowledged = AtomicBoolean(false)

        init {
            if (paid == null && recoveredOrder == null) {
                // A restored back stack, or an entry without a paid order: there is nothing to show. Idle takes a
                // recovered order to Complete by itself if one turns up (FR-016 AC4).
                KioskLog.w("complete_without_paid_order")
                leave()
            } else {
                viewModelScope.launch { countDown() }
            }
        }

        fun onEvent(event: CompleteEvent) {
            when (event) {
                CompleteEvent.Shown -> acknowledge()
                CompleteEvent.Done -> leave()
            }
        }

        private fun initialState(): CompleteUiState =
            when {
                paid != null -> {
                    CompleteUiState(
                        orderNumber = paid.orderNumber.orEmpty(),
                        amount = IdrFormatter.format(paid.amount),
                        method = paid.method,
                        orderType = orderType,
                    )
                }

                recoveredOrder != null -> {
                    CompleteUiState(
                        orderNumber = recoveredOrder.orderNumber,
                        amount = IdrFormatter.format(recoveredOrder.totals.total),
                        method = recoveredOrder.method,
                        orderType = null,
                    )
                }

                else -> {
                    CompleteUiState()
                }
            }

        /** The deadline is on the monotonic clock, so a changed device time cannot shorten or stretch it (NFR-017). */
        private suspend fun countDown() {
            val deadline = clock.elapsedRealtime() + RETURN_AFTER
            while (true) {
                val left = deadline - clock.elapsedRealtime()
                if (left <= Duration.ZERO) break
                mutableState.update { it.copy(secondsLeft = secondsCeil(left)) }
                delay(COUNTDOWN_TICK)
            }
            mutableState.update { it.copy(secondsLeft = 0) }
            leave()
        }

        private fun secondsCeil(left: Duration): Int =
            ((left.inWholeMilliseconds + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

        /** Takes the paid payment over from the machine once: it forgets the pending order and goes Idle. */
        private fun acknowledge() {
            if (paid == null || !acknowledged.compareAndSet(false, true)) return
            // The machine runs this in the application scope, so leaving the screen does not cancel it.
            viewModelScope.launch { machine.acknowledgePaid() }
        }

        private fun leave() {
            if (!leaving.compareAndSet(false, true)) return
            acknowledge()
            sessions.endOrder()
            effects.trySend(CompleteEffect.ToIdle)
        }
    }
