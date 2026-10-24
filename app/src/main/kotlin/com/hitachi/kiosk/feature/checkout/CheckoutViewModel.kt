package com.hitachi.kiosk.feature.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.visiblePaymentMethods
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderState
import com.hitachi.kiosk.core.domain.order.SubmitOrder
import com.hitachi.kiosk.core.domain.order.SubmitResult
import com.hitachi.kiosk.core.domain.payment.FailureReason
import com.hitachi.kiosk.core.domain.payment.PaymentExit
import com.hitachi.kiosk.core.domain.payment.PaymentStateMachine
import com.hitachi.kiosk.core.domain.payment.PaymentUiState
import com.hitachi.kiosk.core.domain.pricing.PricingEngine
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import com.hitachi.kiosk.core.domain.time.Clock
import com.hitachi.kiosk.devtools.CheckoutDebugTools
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import com.hitachi.kiosk.navigation.CheckoutRoute as CheckoutDestination

/** The QR life when neither merchant config nor Payment settings say otherwise: 5 minutes (FR-017, FR-025). */
internal const val DEFAULT_QR_EXPIRY_SECONDS = 300

/** The countdown on screen moves once a second (FR-017, TC-160). */
private val COUNTDOWN_TICK = 1.seconds

private const val MILLIS_PER_SECOND = 1_000.0

/**
 * SCR-11 Checkout (FR-016–FR-019, DEV-048, DEV-049, DEV-050). It observes the application-scoped
 * [PaymentStateMachine], which keeps running when this screen is recreated or left (architecture §5.2), and sends it
 * the customer's choices. What this ViewModel adds is the order half: on entry it submits the order ([SubmitOrder],
 * E7) and starts the payment with the method chosen on Your order, once. A configuration change keeps this instance,
 * so nothing is submitted twice (TC-138); a second Pay cannot start a second order (TC-069).
 *
 * [SubmitOrder] failures that leave no payment behind (`Failed`) are shown as a local failure, because the machine
 * only knows payments. `PRICE_CHANGED` and `ITEM_UNAVAILABLE` go back to Your order, where the session holds the
 * issues to review (FR-016). The method switcher and the failure buttons call the machine for the same order.
 *
 * Paid means the backend said `PAID` (A-15): only the machine's [PaymentUiState.Paid] leads to Complete.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("LongParameterList") // Wiring: one parameter per port this screen combines.
class CheckoutViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val machine: PaymentStateMachine,
        private val submitOrder: SubmitOrder,
        private val sessions: OrderSessionHolder,
        private val catalogs: CatalogRepository,
        settings: SettingsRepository,
        activation: ActivationRepository,
        private val connectivity: ConnectivityMonitor,
        private val clock: Clock,
        debugTools: Optional<CheckoutDebugTools>,
    ) : ViewModel() {
        private val method: PaymentMethod = savedStateHandle.toRoute<CheckoutDestination>().paymentMethod

        private val local = MutableStateFlow(CheckoutLocalState())
        private val mutableState = MutableStateFlow(CheckoutUiState(stage = CheckoutStage.Creating(method)))

        val state: StateFlow<CheckoutUiState> = mutableState.asStateFlow()

        private val effects = Channel<CheckoutEffect>(Channel.BUFFERED)
        val effect: Flow<CheckoutEffect> = effects.receiveAsFlow()

        /** The debug-only simulate bar, `null` in release builds (FR-019, TC-087). */
        val simulateTools: CheckoutDebugTools? = debugTools.orElse(null)

        private val submitting = AtomicBoolean(false)
        private val leaving = AtomicBoolean(false)
        private val paidHandled = AtomicBoolean(false)

        @Volatile
        private var finished = false

        init {
            viewModelScope.launch { follow(settings, activation) }
            viewModelScope.launch { watchMachine() }
            begin()
        }

        fun onEvent(event: CheckoutEvent) {
            when (event) {
                CheckoutEvent.BackToOrder -> backToOrder()
                CheckoutEvent.TryAgain -> tryAgain()
                is CheckoutEvent.PayWith -> payWith(event.method)
                CheckoutEvent.CancelOrder -> cancelOrder()
                CheckoutEvent.QrEncodeFailed -> qrEncodeFailed()
            }
        }

        // State ---------------------------------------------------------------------------------------------------

        /** Maps the machine, the local state, connectivity and the catalog to the screen, until the screen is left. */
        private suspend fun follow(
            settings: SettingsRepository,
            activation: ActivationRepository,
        ) {
            val context =
                combine(
                    catalogs.catalog,
                    settings.settings,
                    activation.state,
                    sessions.current.flatMapLatest { session -> session?.state ?: flowOf(null) },
                ) { catalog, kioskSettings, device, order -> checkoutContext(catalog, kioskSettings, device, order) }
            // The ticker only runs while a QR is on screen.
            val ticks =
                machine.state.flatMapLatest {
                    if (it is PaymentUiState.WaitingForQr) {
                        ticker()
                    } else {
                        flowOf(
                            Unit,
                        )
                    }
                }
            combine(machine.state, local, connectivity.connectivity, context, ticks) { payment, local, link, ctx, _ ->
                checkoutUiState(payment, local, link, ctx, clock, method)
            }.collect { next -> if (!finished) mutableState.value = next }
        }

        private fun ticker(): Flow<Unit> =
            flow {
                while (true) {
                    emit(Unit)
                    delay(COUNTDOWN_TICK)
                }
            }

        /** Remembers the order total and hands a paid payment over to Complete (A-15). */
        private suspend fun watchMachine() {
            machine.state.collect { payment ->
                when (payment) {
                    is PaymentUiState.Waiting -> local.update { it.copy(amount = payment.amount) }
                    is PaymentUiState.Paid -> onPaid()
                    else -> Unit
                }
            }
        }

        private fun onPaid() {
            if (!paidHandled.compareAndSet(false, true)) return
            finished = true
            effects.trySend(CheckoutEffect.ToComplete)
            // The Complete placeholder of 0.5.0 cannot hand the payment over; the real Complete screen (0.6.0) takes
            // the paid details first and acknowledges itself. The call runs in the application scope.
            viewModelScope.launch { machine.acknowledgePaid() }
        }

        // Entry ---------------------------------------------------------------------------------------------------

        /**
         * Submits the order and starts the payment once, unless the machine is already past Idle (TC-138). A machine
         * that still shows the Failed or Paid state of an earlier order, while the session is a new order on Your
         * order, is stale (the earlier order ended without going through the machine, for example a 401 wipe): it is
         * reset first, so the new order is not stuck behind it.
         */
        private fun begin() {
            val shown = machine.state.value
            if (shown == PaymentUiState.Idle) {
                enter()
            } else if (shown.isStaleFor(
                    sessions.current.value
                        ?.state
                        ?.value,
                )
            ) {
                viewModelScope.launch {
                    KioskLog.w("checkout_stale_payment_state", "state" to shown::class.simpleName)
                    if (shown is PaymentUiState.Paid) machine.acknowledgePaid() else machine.reset()
                    enter()
                }
            }
        }

        private fun PaymentUiState.isStaleFor(order: OrderState?): Boolean =
            order?.phase == OrderPhase.REVIEWING && (this is PaymentUiState.Failed || this is PaymentUiState.Paid)

        private fun enter() {
            val session = sessions.current.value
            when {
                session == null -> {
                    leave(CheckoutEffect.ToIdle)
                }

                session.state.value.phase != OrderPhase.REVIEWING -> {
                    KioskLog.w("checkout_entered_without_reviewing", "phase" to session.state.value.phase)
                    leave(CheckoutEffect.ToYourOrder)
                }

                else -> {
                    submit(method)
                }
            }
        }

        @Suppress("TooGenericExceptionCaught") // Anything unexpected ends in the failure panel, never in a crash.
        private fun submit(with: PaymentMethod) {
            if (!submitting.compareAndSet(false, true)) return
            local.update { it.copy(creating = with, failure = null) }
            viewModelScope.launch {
                try {
                    afterSubmit(submitOrder(), with)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    KioskLog.e("checkout_submit_unexpected_error", "type" to e.javaClass.simpleName, error = e)
                    local.update { it.copy(creating = null, failure = LocalFailure(with, LocalFailure.Source.SUBMIT)) }
                } finally {
                    submitting.set(false)
                }
            }
        }

        private suspend fun afterSubmit(
            result: SubmitResult,
            with: PaymentMethod,
        ) {
            when (result) {
                is SubmitResult.Created -> {
                    val order = result.order
                    val started = machine.start(order.orderId, with, qrExpirySeconds())
                    if (!started) KioskLog.w("checkout_payment_not_started")
                    local.update { it.copy(creating = null, amount = order.totals.total) }
                }

                SubmitResult.PriceChanged, is SubmitResult.ItemUnavailable -> {
                    // The session holds the issues to review; no payment was started (FR-016 AC3).
                    local.update { it.copy(creating = null) }
                    leave(CheckoutEffect.ToYourOrder)
                }

                is SubmitResult.Failed -> {
                    local.update { it.copy(creating = null, failure = LocalFailure(with, LocalFailure.Source.SUBMIT)) }
                }

                SubmitResult.Ignored -> {
                    KioskLog.w("checkout_submit_ignored")
                    local.update { it.copy(creating = null) }
                    leave(CheckoutEffect.ToYourOrder)
                }
            }
        }

        /** Payment settings 3 / 5 / 10 min arrive with 0.7.0 (FR-025); until then config's QRIS default, else 5 min. */
        private suspend fun qrExpirySeconds(): Int {
            val qris =
                catalogs.catalog
                    .first()
                    ?.config
                    ?.paymentMethods
                    ?.firstOrNull { it.method == PaymentMethod.QRIS }
            val configured = qris?.qrExpirySecondsDefault
            val allowed = qris?.qrExpirySecondsAllowed.orEmpty()
            return configured?.takeIf { allowed.isEmpty() || it in allowed } ?: DEFAULT_QR_EXPIRY_SECONDS
        }

        // Customer actions ----------------------------------------------------------------------------------------

        private fun tryAgain() {
            if (connectivity.connectivity.value == Connectivity.OFFLINE) return
            val failure = local.value.failure
            when (failure?.source) {
                null -> machine.tryAgain()
                LocalFailure.Source.SUBMIT -> submit(failure.method)
                LocalFailure.Source.QR_ENCODE -> restartAfterBack(failure.method)
            }
        }

        private fun payWith(other: PaymentMethod) {
            val failure = local.value.failure
            when (failure?.source) {
                null -> {
                    machine.switchMethod(other)
                }

                LocalFailure.Source.SUBMIT -> {
                    submit(other)
                }

                LocalFailure.Source.QR_ENCODE -> {
                    if (machine.switchMethod(other)) local.update { it.copy(failure = null) }
                }
            }
        }

        private fun backToOrder() {
            if (local.value.failure?.source == LocalFailure.Source.SUBMIT) {
                // No payment and no order on the backend; the session is already back on Your order.
                leave(CheckoutEffect.ToYourOrder)
                return
            }
            exclusively {
                when (val exit = machine.backToOrder()) {
                    PaymentExit.Left -> leave(CheckoutEffect.ToYourOrder)

                    PaymentExit.AlreadyPaid -> Unit

                    // The machine is Paid now; watchMachine goes to Complete.
                    PaymentExit.NotAllowed -> KioskLog.w("checkout_back_not_allowed")

                    is PaymentExit.NotCancelled -> KioskLog.w("checkout_back_not_cancelled", "error" to exit.error)
                }
            }
        }

        private fun cancelOrder() {
            val failure = local.value.failure
            if (failure?.source == LocalFailure.Source.SUBMIT) {
                endOrder()
                return
            }
            exclusively {
                // A QR that cannot be drawn is still a live payment: leave it like "Back to order", then end the order.
                val exit = if (failure != null) machine.backToOrder() else machine.cancelOrder()
                when (exit) {
                    PaymentExit.Left -> endOrder()
                    PaymentExit.AlreadyPaid -> Unit
                    PaymentExit.NotAllowed -> KioskLog.w("checkout_cancel_not_allowed")
                    is PaymentExit.NotCancelled -> KioskLog.w("checkout_cancel_not_cancelled", "error" to exit.error)
                }
            }
        }

        /** The QR cannot be drawn: the payment cannot be made with it (FR-017). No domain behaviour is invented. */
        private fun qrEncodeFailed() {
            KioskLog.w("checkout_qr_encode_failed")
            val failure = LocalFailure(PaymentMethod.QRIS, LocalFailure.Source.QR_ENCODE)
            local.update { if (it.failure == null) it.copy(failure = failure) else it }
        }

        /** "Try again" after a QR that cannot be drawn: leave the payment and order, then submit a new order. */
        private fun restartAfterBack(with: PaymentMethod) {
            exclusively {
                if (machine.backToOrder() == PaymentExit.Left) {
                    local.update { it.copy(failure = null) }
                    submit(with)
                }
            }
        }

        private fun endOrder() {
            // Paying orders cannot be cancelled in the session (only before Submitting); ending it drops the cart.
            sessions.current.value?.cancel()
            sessions.endOrder()
            leave(CheckoutEffect.ToIdle)
        }

        private fun leave(effect: CheckoutEffect) {
            finished = true
            effects.trySend(effect)
        }

        /** Runs [block] unless another leave action is running, and keeps the buttons disabled meanwhile. */
        private fun exclusively(block: suspend () -> Unit) {
            if (!leaving.compareAndSet(false, true)) return
            local.update { it.copy(busy = true) }
            viewModelScope.launch {
                try {
                    block()
                } finally {
                    leaving.set(false)
                    local.update { it.copy(busy = false) }
                }
            }
        }
    }

/** What only Checkout holds, besides the machine. */
internal data class CheckoutLocalState(
    /** The method being submitted: "Creating your payment…" while E7 runs, before the machine knows the payment. */
    val creating: PaymentMethod? = null,
    /** A failure with no payment to show it, so the machine cannot report it. */
    val failure: LocalFailure? = null,
    /** The order total known from the backend, so the failure panels keep their amount. */
    val amount: Rupiah? = null,
    val busy: Boolean = false,
)

/** A failure Checkout shows itself. */
internal data class LocalFailure(
    val method: PaymentMethod,
    val source: Source,
) {
    enum class Source {
        /** E7 failed, or something unexpected happened while submitting: no payment exists. */
        SUBMIT,

        /** The payment exists and is waiting, but its QR cannot be drawn. */
        QR_ENCODE,
    }
}

/** What the mapping needs besides the machine: names, methods and the kiosk-side total (FR-017). */
internal data class CheckoutContext(
    val brandName: String = "",
    val kioskName: String = "",
    val methods: List<PaymentMethod> = emptyList(),
    val kioskTotal: Rupiah? = null,
)

internal fun checkoutContext(
    catalog: Catalog?,
    settings: KioskSettings,
    device: DeviceState,
    order: OrderState?,
): CheckoutContext {
    val kiosk = (device as? DeviceState.Bound)?.device ?: (device as? DeviceState.Activated)?.device
    val merchant = (device as? DeviceState.Bound)?.merchant
    val total =
        if (catalog != null && order != null) {
            PricingEngine.price(order.cart.lines, PricingRules.of(catalog.config, settings.roundingStep)).total
        } else {
            null
        }
    return CheckoutContext(
        brandName = catalog?.config?.brandName ?: merchant?.name.orEmpty(),
        kioskName = kiosk?.kioskName.orEmpty(),
        methods = catalog?.config?.visiblePaymentMethods(settings).orEmpty(),
        kioskTotal = total,
    )
}

/**
 * Maps the payment machine to what Checkout draws (FR-017–FR-019). Pure, so the screenshot tests build the boards
 * from the same mapping. [fallback] is the method of the route, shown while nothing else is known.
 */
internal fun checkoutUiState(
    payment: PaymentUiState,
    local: CheckoutLocalState,
    link: Connectivity,
    context: CheckoutContext,
    clock: Clock,
    fallback: PaymentMethod,
): CheckoutUiState {
    val offline = link == Connectivity.OFFLINE
    val amount = (payment as? PaymentUiState.Waiting)?.amount ?: local.amount ?: context.kioskTotal
    val stage =
        when {
            local.failure != null -> {
                val failure = local.failure
                CheckoutStage.Failed(
                    CheckoutFailure(
                        method = failure.method,
                        kind = failure.source.kind(),
                        offline = offline,
                        switchTo = context.other(failure.method),
                    ),
                )
            }

            local.creating != null -> {
                CheckoutStage.Creating(local.creating)
            }

            else -> {
                payment.stage(offline, context, clock, fallback)
            }
        }
    return CheckoutUiState(
        stage = stage,
        brandName = context.brandName,
        kioskName = context.kioskName,
        amount = amount?.let(IdrFormatter::format).orEmpty(),
        methods = context.methods,
        busy = local.busy,
    )
}

private fun PaymentUiState.stage(
    offline: Boolean,
    context: CheckoutContext,
    clock: Clock,
    fallback: PaymentMethod,
): CheckoutStage =
    when (this) {
        PaymentUiState.Idle -> {
            CheckoutStage.Creating(fallback)
        }

        is PaymentUiState.Creating -> {
            CheckoutStage.Creating(method)
        }

        is PaymentUiState.WaitingForQr -> {
            CheckoutStage.WaitingForQr(
                qrString = qrString,
                remainingSeconds = countdown.remaining(clock).wholeSecondsCeil(),
                totalSeconds = countdown.total.wholeSecondsCeil(),
            )
        }

        is PaymentUiState.WaitingForCard -> {
            CheckoutStage.WaitingForCard(terminalPrompted)
        }

        is PaymentUiState.Checking -> {
            CheckoutStage.Checking(method)
        }

        is PaymentUiState.Processing -> {
            CheckoutStage.Processing(method)
        }

        // Between the backend's PAID and the move to Complete the screen keeps showing "Confirming payment".
        is PaymentUiState.Paid -> {
            CheckoutStage.Processing(method)
        }

        is PaymentUiState.Failed -> {
            CheckoutStage.Failed(
                CheckoutFailure(
                    method = method,
                    kind = reason.kind(),
                    notCharged = notCharged,
                    offline = offline,
                    switchTo = context.other(method),
                ),
            )
        }
    }

private fun LocalFailure.Source.kind(): FailureKind =
    when (this) {
        LocalFailure.Source.SUBMIT -> FailureKind.UNREACHABLE
        LocalFailure.Source.QR_ENCODE -> FailureKind.GENERIC
    }

private fun FailureReason.kind(): FailureKind =
    when (this) {
        FailureReason.EXPIRED -> FailureKind.EXPIRED
        FailureReason.CANCELLED -> FailureKind.CANCELLED
        FailureReason.DECLINED -> FailureKind.DECLINED
        FailureReason.TERMINAL_TIMEOUT -> FailureKind.TERMINAL_TIMEOUT
        FailureReason.NETWORK -> FailureKind.UNREACHABLE
        FailureReason.GENERIC -> FailureKind.GENERIC
    }

/** The other method, when merchant config and Payment settings both allow it (FR-019, FR-025). */
private fun CheckoutContext.other(method: PaymentMethod): PaymentMethod? =
    PaymentMethod.entries.firstOrNull { it != method && it in methods }

/** The seconds a countdown still has, rounded up so the code never reads 0:00 while it is still alive. */
private fun Duration.wholeSecondsCeil(): Int = ceil(inWholeMilliseconds / MILLIS_PER_SECOND).toInt()
