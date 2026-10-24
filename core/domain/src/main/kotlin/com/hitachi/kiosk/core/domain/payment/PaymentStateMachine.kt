package com.hitachi.kiosk.core.domain.payment

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.CardTerminal
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.device.TerminalEvent
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdGenerator
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.domain.model.TerminalResult
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.payment.PaymentUiState.Waiting
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.core.domain.time.Clock
import com.hitachi.kiosk.core.domain.time.ServerClockOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * The payment of one order, from E9 to `PAID` (architecture §5.2, technical-design §8.4, DEV-047, DEV-049, DEV-050).
 * It is a plain class that runs in the application scope, so leaving or recreating the Checkout screen never stops
 * it: the screen only observes [state] and sends the operations below. It implements the diagram of §5.2:
 * Creating → Waiting → Processing → Paid, with Checking (the architecture's `Unknown`) while the backend does not
 * answer, and Failed → Creating on "Try again" or "Pay by X instead" for the same order.
 *
 * **Paid means the backend said `PAID`** (A-15, TR-SEC-12): a terminal approval only moves the screen to Processing.
 * [OrderSession][com.hitachi.kiosk.core.domain.order.OrderSession] moves to `Paid` here, at the moment the state
 * becomes [PaymentUiState.Paid]; "Back to order" moves it back to `Reviewing`.
 *
 * **Not charged** is claimed only after a backend terminal non-paid status, or an E11 the backend accepted (FR-019,
 * TR-INT-06). A terminal result, a timeout or a lost connection never claims it.
 *
 * **Pending order**: E9 saves the payment on the pending order (TR-REL-02). The pending order stays until the order
 * is cancelled (E8 by [backToOrder] and [cancelOrder] clears it) or the Complete flow calls [acknowledgePaid].
 *
 * **Concurrency**: one coroutine per attempt polls and handles terminal events. An operation takes over by claiming
 * the state, then stopping that coroutine, so a late answer can never overwrite its result. Operations run in the
 * application scope too: a screen that goes away mid-call does not leave the order half-cancelled.
 */
@Suppress("TooManyFunctions") // One small function per transition of architecture §5.2.
class PaymentStateMachine(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val gateway: PaymentGateway,
    private val terminal: CardTerminal,
    private val orders: OrderRepository,
    private val connectivity: ConnectivityMonitor,
    private val sessions: OrderSessionHolder,
    private val policy: PaymentPolicy = PaymentPolicy(),
) {
    private val mutableState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)

    /** What Checkout renders. Starts [PaymentUiState.Idle]. */
    val state: StateFlow<PaymentUiState> = mutableState.asStateFlow()

    private val stateLock = Any()

    // Guarded by stateLock: true while an operation owns the state, so the polling coroutine cannot publish.
    private var claimed = false

    @Volatile
    private var current: PaymentAttempt? = null

    @Volatile
    private var job: Job? = null

    /**
     * Starts the payment of the created order [orderId] (FR-017, FR-018): E9 with a new `Idempotency-Key`, then the
     * QR or the terminal prompt. [qrExpirySeconds] is the Payment setting (3, 5 or 10 minutes, FR-025); the machine
     * keeps it for "Try again" and for a switch to QRIS. Returns `false` and changes nothing unless the machine is
     * [PaymentUiState.Idle], so a double tap on Pay cannot start two payments.
     */
    fun start(
        orderId: String,
        method: PaymentMethod,
        qrExpirySeconds: Int?,
    ): Boolean {
        val attempt = PaymentAttempt(orderId, method, qrExpirySeconds)
        val accepted =
            synchronized(stateLock) {
                val idle = !claimed && mutableState.value == PaymentUiState.Idle
                if (idle) mutableState.value = PaymentUiState.Creating(method)
                idle
            }
        if (accepted) {
            log("payment_state", PaymentUiState.Creating(method))
            job = scope.launch { runGuarded(attempt, previous = null) }
        }
        return accepted
    }

    /**
     * "Try again" on a failure (FR-019): a fresh payment for the same order and method, shown as "Creating your
     * payment…" first. A payment that may still be live (no backend answer yet) is cancelled first. Returns `false`
     * unless the state is [PaymentUiState.Failed] and the kiosk is not offline ("Reconnecting…", FR-019).
     */
    fun tryAgain(): Boolean = restart(to = null)

    /**
     * "Pay by X instead" (FR-019, TC-084) from a failure, or the method switcher while waiting (FR-017): the current
     * payment is cancelled (terminal sale first, E11 `SWITCH_METHOD`) and a payment with the other method is created
     * for the **same** order with a new key. Returns `false` when [to] is the current method, when the state does not
     * allow it, or while offline.
     */
    fun switchMethod(to: PaymentMethod): Boolean = restart(to)

    /**
     * "Back to order" while waiting (FR-016, FR-018 AC3, TC-086, TC-181): cancels the terminal sale, E11
     * `CUSTOMER_BACK` and E8, then moves the order session back to `Reviewing` and the machine to Idle. The next Pay
     * creates a new order with a new key (BR-08).
     */
    suspend fun backToOrder(): PaymentExit = inScope { leave(CancelOrderReason.CUSTOMER_BACK, backToOrder = true) }

    /**
     * "Cancel order" on a failure (FR-019): E11 if the payment may still be live, then E8 `PAYMENT_FAILED`, which
     * clears the pending order. On [PaymentExit.Left] the caller ends the order and returns to Idle.
     */
    suspend fun cancelOrder(): PaymentExit = inScope { leave(CancelOrderReason.PAYMENT_FAILED, backToOrder = false) }

    /**
     * The Complete screen took over a [PaymentUiState.Paid] payment: forgets the pending order and goes Idle. Does
     * nothing in any other state.
     */
    suspend fun acknowledgePaid() {
        inScope {
            if (claim { it is PaymentUiState.Paid }) {
                orders.clearPending()
                finish(PaymentUiState.Idle)
            }
        }
    }

    // Operations ----------------------------------------------------------------------------------------------------

    private fun restart(to: PaymentMethod?): Boolean {
        if (connectivity.connectivity.value == Connectivity.OFFLINE) return false
        if (!claim { it is PaymentUiState.Failed || (to != null && it is Waiting) }) return false
        val previous = current
        val method = to ?: previous?.method
        val sameMethod = to != null && to == previous?.method
        if (previous == null || method == null || sameMethod) {
            release()
            return false
        }
        val next = PaymentAttempt(previous.orderId, method, previous.qrExpirySeconds)
        val old = job
        job =
            scope.launch {
                old?.cancelAndJoin()
                finish(PaymentUiState.Creating(method))
                runGuarded(next, previous)
            }
        return true
    }

    private suspend fun leave(
        orderReason: CancelOrderReason,
        backToOrder: Boolean,
    ): PaymentExit {
        val attempt = current
        val allowed = claim { it is PaymentUiState.Failed || (backToOrder && it is Waiting) }
        if (!allowed || attempt == null) {
            if (allowed) release()
            return PaymentExit.NotAllowed
        }
        job?.cancelAndJoin()
        if (attempt.saleActive) cancelTerminal(attempt)
        val stopped = if (attempt.isLive) cancelLivePayment(attempt, CancelPaymentReason.CUSTOMER_BACK) else null
        return stopped ?: cancelOrderOnBackend(attempt, orderReason, backToOrder)
    }

    private suspend fun cancelLivePayment(
        attempt: PaymentAttempt,
        reason: CancelPaymentReason,
    ): PaymentExit? =
        when (val result = cancelPayment(attempt, reason)) {
            CancelResult.Cancelled -> null
            is CancelResult.Paid -> paidWhileLeaving(attempt, result.state)
            is CancelResult.NotCancelled -> stayFailed(attempt, result.error)
        }

    private suspend fun cancelOrderOnBackend(
        attempt: PaymentAttempt,
        reason: CancelOrderReason,
        backToOrder: Boolean,
    ): PaymentExit =
        when (val result = orders.cancel(attempt.orderId, reason)) {
            is Outcome.Success -> {
                finish(PaymentUiState.Idle)
                if (backToOrder) sessions.current.value?.backToOrder()
                PaymentExit.Left
            }

            is Outcome.Failure -> {
                val error = result.error
                if (error is DomainError.Conflict && error.code == ORDER_ALREADY_PAID) {
                    orderPaidMeanwhile(attempt) ?: stayFailed(attempt, error)
                } else {
                    stayFailed(attempt, error)
                }
            }
        }

    /** E8 said the order is already paid: E10 has the order number, and the machine moves to Paid. */
    private suspend fun orderPaidMeanwhile(attempt: PaymentAttempt): PaymentExit? {
        val paymentId = attempt.paymentId ?: return null
        val status = gateway.status(attempt.orderId, paymentId)
        val paid = (status as? Outcome.Success)?.value?.takeIf { it.status == PaymentStatus.PAID }
        return paid?.let { paidWhileLeaving(attempt, it) }
    }

    private suspend fun paidWhileLeaving(
        attempt: PaymentAttempt,
        paid: PaymentState,
    ): PaymentExit {
        completePayment(attempt, paid, force = true)
        release()
        return PaymentExit.AlreadyPaid
    }

    /** The backend could not cancel: the customer sees the failure and the order stays pending for recovery. */
    private fun stayFailed(
        attempt: PaymentAttempt,
        error: DomainError,
    ): PaymentExit {
        finish(PaymentUiState.Failed(attempt.method, failureReasonOf(error), notCharged = attempt.backendNonPaid))
        return PaymentExit.NotCancelled(error)
    }

    // One attempt ---------------------------------------------------------------------------------------------------

    /** An unexpected exception ends in a failure screen, never in a crash of the application scope. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runGuarded(
        attempt: PaymentAttempt,
        previous: PaymentAttempt?,
    ) {
        try {
            runAttempt(attempt, previous)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KioskLog.e("payment_unexpected_error", "type" to e.javaClass.simpleName, error = e)
            fail(attempt, FailureReason.GENERIC, notCharged = false)
        }
    }

    private suspend fun runAttempt(
        attempt: PaymentAttempt,
        previous: PaymentAttempt?,
    ) {
        if (previous != null && !supersede(previous)) return
        current = attempt
        val qrExpiry = attempt.qrExpirySeconds.takeIf { attempt.method == PaymentMethod.QRIS }
        val started = gateway.start(attempt.orderId, attempt.method, qrExpiry, ids.newIdempotencyKey())
        when (started) {
            is Outcome.Failure -> fail(attempt, failureReasonOf(started.error), notCharged = false)
            is Outcome.Success -> begin(attempt, started.value)
        }
    }

    /** Cancels what the previous attempt left, so the order never has two live payments. */
    private suspend fun supersede(previous: PaymentAttempt): Boolean {
        if (previous.saleActive) cancelTerminal(previous)
        if (!previous.isLive) return true
        return when (val result = cancelPayment(previous, CancelPaymentReason.SWITCH_METHOD)) {
            CancelResult.Cancelled -> {
                true
            }

            is CancelResult.Paid -> {
                completePayment(previous, result.state, force = false)
                false
            }

            is CancelResult.NotCancelled -> {
                fail(previous, failureReasonOf(result.error), notCharged = false)
                false
            }
        }
    }

    private suspend fun begin(
        attempt: PaymentAttempt,
        started: StartedPayment,
    ) {
        attempt.paymentId = started.paymentId
        attempt.amount = started.amount
        orders.recordPayment(attempt.orderId, started.paymentId, attempt.method)
        val waiting =
            when {
                started is StartedPayment.Qris && attempt.method == PaymentMethod.QRIS -> startQr(attempt, started)
                started is StartedPayment.Card && attempt.method == PaymentMethod.CARD -> startCard(attempt, started)
                else -> null
            }
        if (waiting == null) {
            // The backend answered with the other method: the screen cannot show it, and nothing was asked of the payer.
            fail(attempt, FailureReason.GENERIC, notCharged = false)
        } else if (publish(waiting)) {
            monitor(attempt)
        }
    }

    private fun startQr(
        attempt: PaymentAttempt,
        started: StartedPayment.Qris,
    ): PaymentUiState {
        val offset = ServerClockOffset.measure(started.serverTime ?: started.createdAt, clock.now())
        val remaining = (offset.toDeviceTime(started.expiresAt) - clock.now()).coerceAtLeast(Duration.ZERO)
        val deadline = clock.elapsedRealtime() + remaining
        attempt.qrDeadline = deadline
        val total = (started.expiresAt - started.createdAt).takeIf { it > Duration.ZERO } ?: remaining
        return PaymentUiState.WaitingForQr(started.amount, started.qrString, QrCountdown(deadline, total))
    }

    private fun startCard(
        attempt: PaymentAttempt,
        started: StartedPayment.Card,
    ): PaymentUiState {
        attempt.terminalRef = started.terminalRef
        attempt.terminalTid = started.terminal?.tid
        attempt.cardDeadline = clock.elapsedRealtime() + policy.terminalTimeout
        return PaymentUiState.WaitingForCard(started.amount)
    }

    /** Polls E10 every [PaymentPolicy.pollInterval] and reacts to the terminal until the payment ends. */
    private suspend fun monitor(attempt: PaymentAttempt) =
        coroutineScope {
            val events = Channel<TerminalEvent>(Channel.UNLIMITED)
            val sale = if (attempt.method == PaymentMethod.CARD) launchSale(attempt, events) else null
            try {
                pollLoop(attempt, events)
            } finally {
                sale?.cancel()
            }
        }

    private suspend fun pollLoop(
        attempt: PaymentAttempt,
        events: Channel<TerminalEvent>,
    ) {
        var nextPoll = clock.elapsedRealtime() + policy.pollInterval
        while (true) {
            val now = clock.elapsedRealtime()
            val expired = attempt.qrDeadline?.let { now >= it } == true
            val silent = attempt.cardDeadline?.let { now >= it } == true
            if (expired || silent) {
                if (expired) expire(attempt) else terminalTimedOut(attempt)
                return
            }
            if (now >= nextPoll) {
                if (poll(attempt)) return
                nextPoll = clock.elapsedRealtime() + policy.pollInterval
                continue
            }
            val wakeAt = listOfNotNull(nextPoll, attempt.qrDeadline, attempt.cardDeadline).min()
            val event = withTimeoutOrNull(wakeAt - now) { events.receive() }
            if (event != null && onTerminalEvent(attempt, event)) return
        }
    }

    /** E10. Returns `true` when the payment ended and polling must stop (FR-017 AC5, TR-NET-10). */
    private suspend fun poll(attempt: PaymentAttempt): Boolean {
        val paymentId = attempt.paymentId ?: return true
        return when (val result = gateway.status(attempt.orderId, paymentId)) {
            is Outcome.Failure -> {
                noAnswer(attempt)
            }

            is Outcome.Success -> {
                attempt.unknownSince = null
                onStatus(attempt, result.value)
            }
        }
    }

    private suspend fun onStatus(
        attempt: PaymentAttempt,
        status: PaymentState,
    ): Boolean =
        when (status.status) {
            PaymentStatus.PAID -> {
                completePayment(attempt, status, force = false)
                true
            }

            PaymentStatus.PENDING -> {
                restore(attempt)
            }

            PaymentStatus.PROCESSING -> {
                !enterProcessing(attempt, force = false)
            }

            PaymentStatus.FAILED, PaymentStatus.EXPIRED, PaymentStatus.CANCELLED -> {
                attempt.backendNonPaid = true
                fail(attempt, failureReasonOf(status, attempt.method), notCharged = true)
            }
        }

    /** The status is known again after "Checking your payment…". Returns `true` when the machine was taken over. */
    private fun restore(attempt: PaymentAttempt): Boolean {
        if (mutableState.value !is PaymentUiState.Checking) return false
        val before = attempt.resumeState ?: return false
        attempt.resumeState = null
        return !publish(before)
    }

    /**
     * No backend answer (network, 5xx, an answer the kiosk cannot read): "Checking your payment…" and keep polling for
     * [PaymentPolicy.unknownLimit], then fail without "not charged" (A-17, TR-INT-06). Returns `true` when it ended.
     */
    private fun noAnswer(attempt: PaymentAttempt): Boolean {
        val now = clock.elapsedRealtime()
        val since = attempt.unknownSince
        if (since == null) {
            attempt.unknownSince = now
            attempt.resumeState = mutableState.value.takeUnless { it is PaymentUiState.Checking }
            return !publish(PaymentUiState.Checking(attempt.method))
        }
        return now - since >= policy.unknownLimit && fail(attempt, FailureReason.NETWORK, notCharged = false)
    }

    // Terminal ------------------------------------------------------------------------------------------------------

    private fun CoroutineScope.launchSale(
        attempt: PaymentAttempt,
        events: Channel<TerminalEvent>,
    ): Job {
        attempt.saleStarted = true
        attempt.saleActive = true
        val reference = attempt.terminalRef.orEmpty()
        return launch { collectSale(attempt, reference, events) }
    }

    @Suppress("TooGenericExceptionCaught") // An adapter must not take the payment down with it.
    private suspend fun collectSale(
        attempt: PaymentAttempt,
        reference: String,
        events: Channel<TerminalEvent>,
    ) {
        try {
            terminal.startSale(attempt.amount, reference).collect { events.trySend(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KioskLog.w("payment_terminal_failed", "type" to e.javaClass.simpleName)
            events.trySend(TerminalEvent.Error("terminal_failed"))
        }
    }

    /**
     * Returns `true` when the sale ended and the monitor must stop. Anything after a final event is ignored, so an
     * adapter that repeats a result cannot relay it twice.
     */
    private suspend fun onTerminalEvent(
        attempt: PaymentAttempt,
        event: TerminalEvent,
    ): Boolean = if (attempt.saleActive) handleTerminalEvent(attempt, event) else false

    private suspend fun handleTerminalEvent(
        attempt: PaymentAttempt,
        event: TerminalEvent,
    ): Boolean =
        when (event) {
            TerminalEvent.Prompted -> {
                markPrompted(attempt)
                false
            }

            is TerminalEvent.Approved -> {
                onApproved(attempt, event)
            }

            is TerminalEvent.Declined -> {
                terminalEnded(attempt, FailureReason.DECLINED, declineCode = event.code)
            }

            TerminalEvent.Cancelled -> {
                terminalEnded(attempt, FailureReason.CANCELLED, declineCode = null)
            }

            is TerminalEvent.Error -> {
                KioskLog.w("payment_terminal_error", "reason" to event.reason)
                terminalEnded(attempt, FailureReason.GENERIC, declineCode = null)
            }
        }

    private fun markPrompted(attempt: PaymentAttempt) {
        val shown = mutableState.value
        if (shown is PaymentUiState.WaitingForCard) {
            publish(shown.copy(terminalPrompted = true))
        } else {
            (attempt.resumeState as? PaymentUiState.WaitingForCard)?.let {
                attempt.resumeState = it.copy(terminalPrompted = true)
            }
        }
    }

    /**
     * The terminal approved (FR-018): Processing, and E12 when the terminal reports to the kiosk. The payment is only
     * paid when E10 says `PAID` (A-15), so this never ends the payment itself.
     */
    private suspend fun onApproved(
        attempt: PaymentAttempt,
        event: TerminalEvent.Approved,
    ): Boolean {
        attempt.saleActive = false
        attempt.cardDeadline = null
        var done = !enterProcessing(attempt, force = false, keepChecking = true)
        val paymentId = attempt.paymentId
        if (!done && policy.relayTerminalResults && paymentId != null) {
            val result =
                TerminalResult(
                    approved = true,
                    terminalRef = attempt.terminalRef.orEmpty(),
                    tid = event.tid,
                    approvalCode = event.approvalCode,
                    cardBrand = event.cardBrand,
                    maskedPan = event.maskedPan,
                    rawResponseCode = event.rawResponseCode,
                )
            done =
                when (val relayed = gateway.relayTerminalResult(attempt.orderId, paymentId, result)) {
                    // No automatic retry of E12 (TD §5.3): the polling answers the question.
                    is Outcome.Failure -> noAnswer(attempt)

                    is Outcome.Success -> afterRelay(attempt, relayed.value.status)
                }
        }
        return done
    }

    private suspend fun afterRelay(
        attempt: PaymentAttempt,
        status: PaymentStatus,
    ): Boolean =
        when {
            status == PaymentStatus.PAID -> {
                poll(attempt)
            }

            status.isNonPaidFinal -> {
                attempt.backendNonPaid = true
                fail(attempt, failureReasonOf(status, attempt.method), notCharged = true)
            }

            else -> {
                false
            }
        }

    /**
     * The terminal declined, was cancelled at the terminal, or failed. The terminal result is relayed (declines only),
     * then E10 is read once: `PAID` still wins, and only a backend terminal non-paid status claims "not charged".
     */
    private suspend fun terminalEnded(
        attempt: PaymentAttempt,
        reason: FailureReason,
        declineCode: String?,
    ): Boolean {
        attempt.saleActive = false
        attempt.cardDeadline = null
        val paymentId = attempt.paymentId ?: return fail(attempt, reason, notCharged = false)
        var nonPaid = false
        if (declineCode != null && policy.relayTerminalResults) {
            val declined = declinedResult(attempt, declineCode)
            val relayed = gateway.relayTerminalResult(attempt.orderId, paymentId, declined)
            nonPaid = (relayed as? Outcome.Success)?.value?.status?.isNonPaidFinal == true
        }
        val status = (gateway.status(attempt.orderId, paymentId) as? Outcome.Success)?.value
        if (status?.status == PaymentStatus.PAID) {
            completePayment(attempt, status, force = false)
            return true
        }
        nonPaid = nonPaid || status?.status?.isNonPaidFinal == true
        attempt.backendNonPaid = nonPaid
        return fail(attempt, reason, notCharged = nonPaid)
    }

    private fun declinedResult(
        attempt: PaymentAttempt,
        code: String,
    ) = TerminalResult(
        approved = false,
        terminalRef = attempt.terminalRef.orEmpty(),
        tid = attempt.terminalTid.orEmpty(),
        approvalCode = null,
        cardBrand = null,
        maskedPan = null,
        rawResponseCode = code,
    )

    /** The terminal stayed silent for [PaymentPolicy.terminalTimeout] (A-16, TR-INT-05): cancel it and E11 `TIMEOUT`. */
    private suspend fun terminalTimedOut(attempt: PaymentAttempt) {
        cancelAndFail(attempt, FailureReason.TERMINAL_TIMEOUT, CancelPaymentReason.TIMEOUT)
    }

    /** The QR passed its device-side expiry (FR-017, NFR-017): E11 `EXPIRED_ON_KIOSK`, E10 first when it answers 409. */
    private suspend fun expire(attempt: PaymentAttempt) {
        cancelAndFail(attempt, FailureReason.EXPIRED, CancelPaymentReason.EXPIRED_ON_KIOSK)
    }

    private suspend fun cancelAndFail(
        attempt: PaymentAttempt,
        failure: FailureReason,
        reason: CancelPaymentReason,
    ) {
        if (attempt.saleActive) cancelTerminal(attempt)
        when (val result = cancelPayment(attempt, reason)) {
            CancelResult.Cancelled -> fail(attempt, failure, notCharged = true)
            is CancelResult.Paid -> completePayment(attempt, result.state, force = false)
            is CancelResult.NotCancelled -> fail(attempt, failure, notCharged = false)
        }
    }

    @Suppress("TooGenericExceptionCaught") // A failing terminal must not stop the cancel of the payment.
    private suspend fun cancelTerminal(attempt: PaymentAttempt) {
        attempt.saleActive = false
        try {
            terminal.cancel()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KioskLog.w("payment_terminal_cancel_failed", "type" to e.javaClass.simpleName)
        }
    }

    // Backend cancel ------------------------------------------------------------------------------------------------

    private sealed interface CancelResult {
        /** The payment ended without being paid, and the backend said so. */
        data object Cancelled : CancelResult

        /** The backend refused the cancel because the payment is paid. */
        data class Paid(
            val state: PaymentState,
        ) : CancelResult

        data class NotCancelled(
            val error: DomainError,
        ) : CancelResult
    }

    /** E11. A 409 means the payment may be paid, so E10 decides (api-spec E11). */
    private suspend fun cancelPayment(
        attempt: PaymentAttempt,
        reason: CancelPaymentReason,
    ): CancelResult {
        val paymentId = attempt.paymentId ?: return CancelResult.Cancelled
        val key = attempt.cancelKey ?: ids.newIdempotencyKey().also { attempt.cancelKey = it }
        return when (val result = gateway.cancel(attempt.orderId, paymentId, reason, key)) {
            is Outcome.Failure -> {
                val error = result.error
                if (error is DomainError.Conflict) readAfterConflict(attempt, error) else notCancelled(error)
            }

            is Outcome.Success -> {
                val status = result.value.status
                when {
                    status == PaymentStatus.PAID -> readAfterConflict(attempt, DomainError.Unknown)
                    status.isNonPaidFinal -> cancelled(attempt)
                    else -> notCancelled(DomainError.Unknown)
                }
            }
        }
    }

    private suspend fun readAfterConflict(
        attempt: PaymentAttempt,
        error: DomainError,
    ): CancelResult {
        val paymentId = attempt.paymentId ?: return notCancelled(error)
        return when (val result = gateway.status(attempt.orderId, paymentId)) {
            is Outcome.Failure -> {
                notCancelled(result.error)
            }

            is Outcome.Success -> {
                val state = result.value
                when {
                    state.status == PaymentStatus.PAID -> CancelResult.Paid(state)
                    state.status.isNonPaidFinal -> cancelled(attempt)
                    else -> notCancelled(error)
                }
            }
        }
    }

    private fun cancelled(attempt: PaymentAttempt): CancelResult {
        attempt.backendNonPaid = true
        return CancelResult.Cancelled
    }

    private fun notCancelled(error: DomainError): CancelResult = CancelResult.NotCancelled(error)

    // Processing, Paid, Failed --------------------------------------------------------------------------------------

    /**
     * Shows "Confirming payment" and remembers when it began, for the 800 ms minimum. `false` if taken over. With
     * [keepChecking] a terminal approval that arrives while "Checking your payment…" shows does not hide it: the
     * backend still has not answered, so Processing is only what the screen returns to (A-17).
     */
    private fun enterProcessing(
        attempt: PaymentAttempt,
        force: Boolean,
        keepChecking: Boolean = false,
    ): Boolean {
        val shown = mutableState.value
        if (shown is PaymentUiState.Processing) return true
        if (attempt.processingSince == null) attempt.processingSince = clock.elapsedRealtime()
        if (keepChecking && shown is PaymentUiState.Checking) {
            attempt.resumeState = PaymentUiState.Processing(attempt.method)
            return true
        }
        return publish(PaymentUiState.Processing(attempt.method), force)
    }

    /**
     * The backend said `PAID` (A-15): Processing for at least [PaymentPolicy.minProcessing], then
     * [PaymentUiState.Paid] and `OrderSession.paid()`. Polling has already stopped (FR-017 AC5).
     */
    private suspend fun completePayment(
        attempt: PaymentAttempt,
        paid: PaymentState,
        force: Boolean,
    ) {
        if (!enterProcessing(attempt, force)) return
        val since = attempt.processingSince ?: clock.elapsedRealtime()
        val wait = since + policy.minProcessing - clock.elapsedRealtime()
        if (wait > Duration.ZERO) delay(wait)
        val orderNumber =
            paid.paidOrder?.orderNumber ?: orders.pending()?.takeIf { it.orderId == attempt.orderId }?.orderNumber
        val done =
            PaymentUiState.Paid(
                method = attempt.method,
                amount = paid.amount,
                orderNumber = orderNumber,
                paidAt = paid.paidAt,
                issuer = paid.issuer,
                reference = paid.reference,
            )
        if (publish(done, force)) sessions.current.value?.paid()
    }

    /** Publishes [PaymentUiState.Failed]. Always `true`: the payment ended, so the monitor stops. */
    private fun fail(
        attempt: PaymentAttempt,
        reason: FailureReason,
        notCharged: Boolean,
    ): Boolean {
        publish(PaymentUiState.Failed(attempt.method, reason, notCharged))
        return true
    }

    // State ---------------------------------------------------------------------------------------------------------

    /** Publishes [next] unless an operation owns the state. [force] is for the operation itself. */
    private fun publish(
        next: PaymentUiState,
        force: Boolean = false,
    ): Boolean {
        val accepted =
            synchronized(stateLock) {
                val allowed = force || !claimed
                if (allowed) mutableState.value = next
                allowed
            }
        if (accepted) log("payment_state", next)
        return accepted
    }

    /** Takes the state for an operation when [allowed] accepts it. The polling coroutine can no longer publish. */
    private fun claim(allowed: (PaymentUiState) -> Boolean): Boolean =
        synchronized(stateLock) {
            val ok = !claimed && allowed(mutableState.value)
            if (ok) claimed = true
            ok
        }

    private fun release() {
        synchronized(stateLock) { claimed = false }
    }

    /** The operation ends in [next] and gives the state back. */
    private fun finish(next: PaymentUiState) {
        synchronized(stateLock) {
            mutableState.value = next
            claimed = false
        }
        log("payment_state", next)
    }

    /** Runs [block] in the application scope, so a caller that goes away does not cancel it half-way. */
    private suspend fun <T> inScope(block: suspend () -> T): T = scope.async { block() }.await()

    private fun log(
        event: String,
        state: PaymentUiState,
    ) {
        val name = state::class.simpleName
        if (state is PaymentUiState.Failed) {
            KioskLog.i(event, "state" to name, "reason" to state.reason, "notCharged" to state.notCharged)
        } else {
            KioskLog.i(event, "state" to name)
        }
    }

    private companion object {
        const val ORDER_ALREADY_PAID = "ORDER_ALREADY_PAID"
    }
}
