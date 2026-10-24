package com.hitachi.kiosk.feature.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.domain.order.SubmitOrder
import com.hitachi.kiosk.core.domain.payment.PaymentStateMachine
import com.hitachi.kiosk.core.domain.payment.PaymentUiState
import com.hitachi.kiosk.core.domain.pricing.PricingEngine
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import com.hitachi.kiosk.core.testing.fake.FakeActivationRepository
import com.hitachi.kiosk.core.testing.fake.FakeCardTerminal
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fake.FakeCatalogSyncRepository
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fake.FakeOrderRepository
import com.hitachi.kiosk.core.testing.fake.FakePaymentGateway
import com.hitachi.kiosk.core.testing.fake.FakeSettingsRepository
import com.hitachi.kiosk.core.testing.fake.VirtualTimeClock
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Optional
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * SCR-11 behaviour (FR-016–FR-019; DEV-048–DEV-050; TC-068, TC-069, TC-071, TC-072, TC-074–TC-082, TC-084–TC-086,
 * TC-138, TC-178, TC-181) against the real [PaymentStateMachine] and [SubmitOrder] with fakes and virtual time.
 * Robolectric only because `toRoute` reads the route through an `android.os.Bundle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CheckoutViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    /** Every port faked, the clock on the virtual time of the test, the machine in its background scope. */
    private inner class Harness(
        private val scope: TestScope,
    ) {
        val clock = VirtualTimeClock(scope.testScheduler)
        val holder = DefaultOrderSessionHolder()
        val catalogs = FakeCatalogRepository()
        val sync = FakeCatalogSyncRepository()
        val settings = FakeSettingsRepository()
        val orders = FakeOrderRepository()
        val ids = FakeIdGenerator()
        val gateway = FakePaymentGateway(orderTotal = { orders.pendingTotal })
        val terminal = FakeCardTerminal(resultDelay = 2.seconds)
        val connectivity = FakeConnectivityMonitor()
        val activation = FakeActivationRepository(SampleData.boundState)
        val machine =
            PaymentStateMachine(
                scope = scope.backgroundScope,
                clock = clock,
                ids = ids,
                gateway = gateway,
                terminal = terminal,
                orders = orders,
                connectivity = connectivity,
                sessions = holder,
            )
        val submit = SubmitOrder(holder, catalogs, sync, settings, orders, ids)

        /** Crispy Chicken on Your order, as after "Pay" was offered. */
        val session: OrderSession =
            holder.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42").also {
                it.startBuilding()
                val custom = ProductCustomization.defaults(checkNotNull(SampleData.menu.product("c1")), SampleData.menu)
                it.add(custom.toCartItem(), 1, custom.unitPrice, custom.choices())
                it.startReviewing()
            }

        val effects = mutableListOf<CheckoutEffect>()

        suspend fun viewModel(method: PaymentMethod = PaymentMethod.QRIS): CheckoutViewModel {
            activation.load()
            val vm =
                CheckoutViewModel(
                    savedStateHandle = SavedStateHandle(mapOf("method" to method.name)),
                    machine = machine,
                    submitOrder = submit,
                    sessions = holder,
                    catalogs = catalogs,
                    settings = settings,
                    activation = activation,
                    connectivity = connectivity,
                    clock = clock,
                    debugTools = Optional.empty(),
                )
            created += vm
            scope.backgroundScope.launch { vm.effect.toList(effects) }
            scope.runCurrent()
            return vm
        }

        fun pass(duration: Duration) {
            scope.advanceTimeBy(duration)
            scope.runCurrent()
        }

        fun send(
            vm: CheckoutViewModel,
            vararg events: CheckoutEvent,
        ) {
            events.forEach(vm::onEvent)
            scope.runCurrent()
        }

        /** A QRIS E9 answer created now by the backend, valid for [life]. */
        fun qris(life: Duration = 300.seconds): StartedPayment.Qris {
            val now = clock.now()
            return SampleData.qrisPayment.copy(
                amount = cartTotal,
                createdAt = now,
                expiresAt = now + life,
                serverTime = now,
            )
        }

        /** What Pay sends as the total of the cart on Your order, which the backend order must repeat (TR-INT-04). */
        val cartTotal: Rupiah
            get() = PricingEngine.price(session.state.value.cart.lines, PricingRules.of(SampleData.config)).total

        fun status(
            status: PaymentStatus,
            reason: PaymentFailureReason? = null,
        ) = SampleData.paymentPending.copy(status = status, failureReason = reason)
    }

    private val created = mutableListOf<CheckoutViewModel>()

    /**
     * The ViewModel's ticker and state mapping live as long as its scope. Cancelling them ends the test, as clearing
     * the ViewModel does in the app; otherwise the virtual-time ticker would never let `runTest` finish.
     */
    private fun checkoutTest(block: suspend TestScope.() -> Unit) =
        runTest(main.dispatcher) {
            try {
                block()
            } finally {
                created.forEach { it.viewModelScope.cancel() }
            }
        }

    private val CheckoutViewModel.ui get() = state.value

    private val CheckoutViewModel.stage get() = state.value.stage

    private fun harness(
        scope: TestScope,
        setUp: Harness.() -> Unit = {},
    ) = Harness(scope).apply(setUp)

    // Entry ---------------------------------------------------------------------------------------------------------

    @Test
    fun `entry shows Creating, submits one order and shows the QR with the backend total (TC-068, TC-074)`() =
        checkoutTest {
            val h = harness(this) { orders.create.succeed(SampleData.createdOrder, delay = 1.seconds) }
            h.gateway.startQris.succeed(h.qris())

            val vm = h.viewModel()

            assertThat(vm.stage).isEqualTo(CheckoutStage.Creating(PaymentMethod.QRIS))
            h.pass(1.seconds)
            assertThat(h.orders.requests).hasSize(1)
            val stage = vm.stage as CheckoutStage.WaitingForQr
            assertThat(stage.remainingSeconds).isEqualTo(300)
            assertThat(stage.totalSeconds).isEqualTo(300)
            assertThat(vm.ui.amount).isEqualTo("Rp 41.800")
            assertThat(vm.ui.brandName).isEqualTo("Urban Grill")
            assertThat(vm.ui.kioskName).isEqualTo("Kiosk 3")
            assertThat(vm.ui.methods).containsExactly(PaymentMethod.QRIS, PaymentMethod.CARD).inOrder()
            val start = h.gateway.starts.single()
            assertThat(start.method).isEqualTo(PaymentMethod.QRIS)
            assertThat(start.orderId).isEqualTo(SampleData.ORDER_ID)
            // The QRIS expiry comes from merchant config for now (FR-025 arrives with the staff settings).
            assertThat(start.qrExpirySeconds).isEqualTo(300)
            assertThat(h.session.state.value.phase).isEqualTo(OrderPhase.PAYING)
        }

    @Test
    fun `a card payment starts the terminal view with the same order (FR-018)`() =
        checkoutTest {
            val h = harness(this)

            val vm = h.viewModel(PaymentMethod.CARD)
            h.pass(1.milliseconds)

            assertThat(vm.stage).isEqualTo(CheckoutStage.WaitingForCard(terminalPrompted = true))
            assertThat(
                h.gateway.starts
                    .single()
                    .method,
            ).isEqualTo(PaymentMethod.CARD)
            assertThat(vm.ui.amount).isEqualTo("Rp 41.800")
        }

    @Test
    fun `the QR countdown follows the clock once a second (TC-160)`() =
        checkoutTest {
            val h = harness(this)
            h.gateway.startQris.succeed(h.qris())
            val vm = h.viewModel()

            h.pass(1.seconds)
            assertThat((vm.stage as CheckoutStage.WaitingForQr).remainingSeconds).isEqualTo(299)
            h.pass(59.seconds)
            assertThat((vm.stage as CheckoutStage.WaitingForQr).remainingSeconds).isEqualTo(240)
        }

    @Test
    fun `a second collector or a new instance does not submit again (TC-069, TC-138)`() =
        checkoutTest {
            val h = harness(this)
            val vm = h.viewModel()
            backgroundScope.launch { vm.state.collect {} }
            h.pass(10.seconds)

            // A configuration change keeps the instance; a re-created one finds the machine past Idle.
            val again = h.viewModel()
            h.pass(10.seconds)

            assertThat(h.orders.requests).hasSize(1)
            assertThat(h.gateway.starts).hasSize(1)
            assertThat(again.stage).isInstanceOf(CheckoutStage.WaitingForQr::class.java)
        }

    @Test
    fun `without an order it returns to Idle and not on Your order it goes back (FR-016)`() =
        checkoutTest {
            val h = harness(this)
            h.session.startBuilding()

            h.viewModel()

            assertThat(h.effects).containsExactly(CheckoutEffect.ToYourOrder)
            assertThat(h.orders.requests).isEmpty()

            val none = harness(this)
            none.holder.endOrder()
            none.viewModel()
            assertThat(none.effects).containsExactly(CheckoutEffect.ToIdle)
        }

    // E7 answers ----------------------------------------------------------------------------------------------------

    @Test
    fun `PRICE_CHANGED goes back to Your order and starts no payment (TC-071)`() =
        checkoutTest {
            val h = harness(this)
            h.orders.create.fail(DomainError.PriceChanged("menu-119", "cfg-42", SampleData.changedTotals))

            h.viewModel()

            assertThat(h.effects).containsExactly(CheckoutEffect.ToYourOrder)
            assertThat(h.gateway.starts).isEmpty()
            assertThat(h.machine.state.value).isEqualTo(PaymentUiState.Idle)
            assertThat(h.session.state.value.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(h.session.state.value.issues.pricesChanged).isTrue()
        }

    @Test
    fun `ITEM_UNAVAILABLE goes back to Your order with the line flagged and starts no payment (TC-072)`() =
        checkoutTest {
            val h = harness(this)
            h.orders.create.fail(DomainError.ItemUnavailable(lineIds = listOf("l1"), productIds = emptyList()))

            h.viewModel()

            assertThat(h.effects).containsExactly(CheckoutEffect.ToYourOrder)
            assertThat(h.gateway.starts).isEmpty()
            assertThat(h.session.state.value.issues.unavailableLineIds).containsExactly("l1")
        }

    @Test
    fun `a failed E7 shows the failure without a not-charged note, and Try again resends the same body (FR-016)`() =
        checkoutTest {
            val h = harness(this)
            h.orders.create.enqueue(Outcome.Failure(DomainError.Network()))
            val vm = h.viewModel()

            val failed = vm.stage as CheckoutStage.Failed
            assertThat(failed.failure.kind).isEqualTo(FailureKind.UNREACHABLE)
            assertThat(failed.failure.notCharged).isFalse()
            assertThat(failed.failure.paused).isFalse()
            assertThat(failed.failure.switchTo).isEqualTo(PaymentMethod.CARD)
            assertThat(h.gateway.starts).isEmpty()

            h.send(vm, CheckoutEvent.TryAgain)

            assertThat(h.orders.createKeys).hasSize(2)
            assertThat(h.orders.createKeys[0]).isEqualTo(h.orders.createKeys[1])
            assertThat(h.orders.requests[0]).isEqualTo(h.orders.requests[1])
            assertThat(vm.stage).isInstanceOf(CheckoutStage.WaitingForQr::class.java)
        }

    @Test
    fun `Pay by card instead after a failed E7 submits again with the other method`() =
        checkoutTest {
            val h = harness(this)
            h.orders.create.enqueue(Outcome.Failure(DomainError.Network()))
            val vm = h.viewModel()

            h.send(vm, CheckoutEvent.PayWith(PaymentMethod.CARD))

            assertThat(h.orders.requests).hasSize(2)
            assertThat(
                h.gateway.starts
                    .single()
                    .method,
            ).isEqualTo(PaymentMethod.CARD)
            assertThat(vm.stage).isEqualTo(CheckoutStage.WaitingForCard(terminalPrompted = true))
        }

    @Test
    fun `rapid Try again taps send one E7 (TC-069)`() =
        checkoutTest {
            val h = harness(this)
            h.orders.create.enqueue(Outcome.Failure(DomainError.Network()))
            val vm = h.viewModel()
            h.orders.create.always(Outcome.Success(SampleData.createdOrder), delay = 1.seconds)

            h.send(vm, *Array(10) { CheckoutEvent.TryAgain })
            h.pass(1.seconds)

            assertThat(h.orders.requests).hasSize(2)
            assertThat(h.gateway.starts).hasSize(1)
        }

    @Test
    fun `Cancel order after a failed E7 ends the order without calling the backend (FR-019)`() =
        checkoutTest {
            val h = harness(this)
            h.orders.create.enqueue(Outcome.Failure(DomainError.Network()))
            val vm = h.viewModel()

            h.send(vm, CheckoutEvent.CancelOrder)

            assertThat(h.effects).containsExactly(CheckoutEffect.ToIdle)
            assertThat(h.holder.current.value).isNull()
            assertThat(h.orders.cancels).isEmpty()
        }

    @Test
    fun `Back to order after a failed E7 returns with the cart intact`() =
        checkoutTest {
            val h = harness(this)
            h.orders.create.enqueue(Outcome.Failure(DomainError.Network()))
            val vm = h.viewModel()

            h.send(vm, CheckoutEvent.BackToOrder)

            assertThat(h.effects).containsExactly(CheckoutEffect.ToYourOrder)
            assertThat(h.session.state.value.cart.lines).hasSize(1)
        }

    // Waiting and failures ------------------------------------------------------------------------------------------

    @Test
    fun `Back to order cancels the payment and the order and keeps the cart (TC-086, TC-181)`() =
        checkoutTest {
            val h = harness(this)
            val vm = h.viewModel(PaymentMethod.CARD)
            h.pass(100.milliseconds)

            h.send(vm, CheckoutEvent.BackToOrder)

            assertThat(h.effects).containsExactly(CheckoutEffect.ToYourOrder)
            assertThat(h.terminal.cancelCount).isEqualTo(1)
            assertThat(h.gateway.cancels).hasSize(1)
            assertThat(h.orders.cancels).containsExactly(SampleData.ORDER_ID to CancelOrderReason.CUSTOMER_BACK)
            assertThat(h.session.state.value.phase).isEqualTo(OrderPhase.REVIEWING)
            assertThat(h.session.state.value.cart.lines).hasSize(1)
            assertThat(h.session.state.value.submission).isNull()
        }

    @Test
    fun `a stale failure of an order that ended elsewhere does not block the next order (FR-016)`() =
        checkoutTest {
            val h = harness(this)
            h.gateway.startQris.succeed(h.qris())
            h.gateway.status.enqueue(Outcome.Success(h.status(PaymentStatus.EXPIRED)))
            h.viewModel()
            h.pass(3.seconds)
            assertThat(h.machine.state.value).isInstanceOf(PaymentUiState.Failed::class.java)

            // The order ends (a 401 wipe) and a new one reaches Your order before anything reset the machine.
            h.holder.endOrder()
            h.orders.clearPending()
            h.holder.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42").also {
                it.startBuilding()
                val custom = ProductCustomization.defaults(checkNotNull(SampleData.menu.product("c1")), SampleData.menu)
                it.add(custom.toCartItem(), 1, custom.unitPrice, custom.choices())
                it.startReviewing()
            }
            val next = h.viewModel()
            h.pass(1.seconds)

            assertThat(h.orders.requests).hasSize(2)
            assertThat(next.stage).isInstanceOf(CheckoutStage.WaitingForQr::class.java)
        }

    @Test
    fun `the method switcher pays the same order with the other method (TC-084)`() =
        checkoutTest {
            val h = harness(this)
            val vm = h.viewModel()
            h.pass(100.milliseconds)

            h.send(vm, CheckoutEvent.PayWith(PaymentMethod.CARD))
            h.pass(100.milliseconds)

            assertThat(vm.stage).isInstanceOf(CheckoutStage.WaitingForCard::class.java)
            assertThat(h.gateway.starts.map { it.method })
                .containsExactly(PaymentMethod.QRIS, PaymentMethod.CARD)
                .inOrder()
            assertThat(
                h.gateway.starts
                    .map { it.orderId }
                    .toSet(),
            ).containsExactly(SampleData.ORDER_ID)
            assertThat(h.orders.requests).hasSize(1)
        }

    @Test
    fun `a cancelled QR payment fails with the note and Try again creates a fresh payment (TC-076, TC-178)`() =
        checkoutTest {
            val h = harness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.FAILED, PaymentFailureReason.CANCELLED_BY_PAYER))
            val vm = h.viewModel()
            h.pass(2.seconds)

            val failure = (vm.stage as CheckoutStage.Failed).failure
            assertThat(failure.kind).isEqualTo(FailureKind.CANCELLED)
            assertThat(failure.method).isEqualTo(PaymentMethod.QRIS)
            assertThat(failure.notCharged).isTrue()
            assertThat(failure.switchTo).isEqualTo(PaymentMethod.CARD)

            h.gateway.status.succeed(SampleData.paymentPending)
            h.send(vm, CheckoutEvent.TryAgain)
            h.pass(100.milliseconds)

            assertThat(h.gateway.starts).hasSize(2)
            assertThat(h.gateway.startKeys.distinct()).hasSize(2)
            assertThat(vm.stage).isInstanceOf(CheckoutStage.WaitingForQr::class.java)
        }

    @Test
    fun `Cancel order after a failure cancels the order, ends it and returns to Idle (TC-178)`() =
        checkoutTest {
            val h = harness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            val vm = h.viewModel()
            h.pass(2.seconds)
            assertThat((vm.stage as CheckoutStage.Failed).failure.kind).isEqualTo(FailureKind.EXPIRED)

            h.send(vm, CheckoutEvent.CancelOrder)

            assertThat(h.effects).containsExactly(CheckoutEffect.ToIdle)
            assertThat(h.orders.cancels).containsExactly(SampleData.ORDER_ID to CancelOrderReason.PAYMENT_FAILED)
            assertThat(h.holder.current.value).isNull()
        }

    @Test
    fun `a backend that cannot cancel keeps the failure on screen and does not leave (TC-086)`() =
        checkoutTest {
            val h = harness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            val vm = h.viewModel()
            h.pass(2.seconds)
            h.orders.cancel.fail(DomainError.Network())

            h.send(vm, CheckoutEvent.CancelOrder)

            assertThat(h.effects).isEmpty()
            assertThat(vm.stage).isInstanceOf(CheckoutStage.Failed::class.java)
            assertThat(vm.ui.busy).isFalse()
        }

    @Test
    fun `Try again is refused while offline and works again once the connection is back (TC-178)`() =
        checkoutTest {
            val h = harness(this)
            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            val vm = h.viewModel()
            h.pass(2.seconds)
            h.gateway.status.succeed(SampleData.paymentPending)

            h.connectivity.set(Connectivity.OFFLINE)
            h.pass(10.milliseconds)
            assertThat((vm.stage as CheckoutStage.Failed).failure.offline).isTrue()
            h.send(vm, CheckoutEvent.TryAgain, CheckoutEvent.PayWith(PaymentMethod.CARD))
            assertThat(h.gateway.starts).hasSize(1)

            h.connectivity.set(Connectivity.ONLINE)
            h.pass(10.milliseconds)
            assertThat((vm.stage as CheckoutStage.Failed).failure.offline).isFalse()
            h.send(vm, CheckoutEvent.TryAgain)
            assertThat(h.gateway.starts).hasSize(2)
        }

    @Test
    fun `a lost connection after a failed E7 shows Ordering is paused with no switch button`() =
        checkoutTest {
            val h = harness(this)
            h.orders.create.enqueue(Outcome.Failure(DomainError.Network()))
            h.connectivity.set(Connectivity.OFFLINE)
            val vm = h.viewModel()

            val failure = (vm.stage as CheckoutStage.Failed).failure
            assertThat(failure.paused).isTrue()
            assertThat(failure.notCharged).isFalse()
            h.send(vm, CheckoutEvent.TryAgain)
            assertThat(h.orders.requests).hasSize(1)

            h.connectivity.set(Connectivity.ONLINE)
            h.pass(10.milliseconds)
            assertThat((vm.stage as CheckoutStage.Failed).failure.paused).isFalse()
        }

    @Test
    fun `a switch is only offered when the other method is enabled in settings (FR-025)`() =
        checkoutTest {
            val h = harness(this)
            h.settings.value = h.settings.value.copy(paymentMethods = setOf(PaymentMethod.QRIS))
            h.gateway.status.succeed(h.status(PaymentStatus.EXPIRED))
            val vm = h.viewModel()
            h.pass(2.seconds)

            assertThat(vm.ui.methods).containsExactly(PaymentMethod.QRIS)
            assertThat((vm.stage as CheckoutStage.Failed).failure.switchTo).isNull()
        }

    @Test
    fun `an unknown status shows Checking and never the not-charged note (FR-019, TC-085)`() =
        checkoutTest {
            val h = harness(this)
            h.gateway.status.fail(DomainError.Network())
            val vm = h.viewModel()

            h.pass(2.seconds)

            assertThat(vm.stage).isEqualTo(CheckoutStage.Checking(PaymentMethod.QRIS))
            h.pass(60.seconds)
            val failure = (vm.stage as CheckoutStage.Failed).failure
            assertThat(failure.kind).isEqualTo(FailureKind.UNREACHABLE)
            assertThat(failure.notCharged).isFalse()
        }

    @Test
    fun `a terminal that never answers ends in Terminal not responding after 90 s (TC-082)`() =
        checkoutTest {
            val h = harness(this)
            h.terminal.mode = FakeCardTerminal.Mode.NO_ANSWER
            val vm = h.viewModel(PaymentMethod.CARD)

            h.pass(90.seconds)

            val failure = (vm.stage as CheckoutStage.Failed).failure
            assertThat(failure.kind).isEqualTo(FailureKind.TERMINAL_TIMEOUT)
            assertThat(failure.method).isEqualTo(PaymentMethod.CARD)
            assertThat(h.terminal.cancelCount).isEqualTo(1)
        }

    // QR that cannot be drawn -----------------------------------------------------------------------------------------

    @Test
    fun `a QR that cannot be drawn shows the generic failure, and Try again makes a new order and QR`() =
        checkoutTest {
            val h = harness(this)
            val vm = h.viewModel()
            h.pass(100.milliseconds)

            h.send(vm, CheckoutEvent.QrEncodeFailed)

            val failure = (vm.stage as CheckoutStage.Failed).failure
            assertThat(failure.kind).isEqualTo(FailureKind.GENERIC)
            assertThat(failure.notCharged).isFalse()

            h.send(vm, CheckoutEvent.TryAgain)
            h.pass(100.milliseconds)

            assertThat(h.orders.cancels).containsExactly(SampleData.ORDER_ID to CancelOrderReason.CUSTOMER_BACK)
            assertThat(h.orders.requests).hasSize(2)
            assertThat(vm.stage).isInstanceOf(CheckoutStage.WaitingForQr::class.java)
        }

    @Test
    fun `a QR that cannot be drawn can still be paid by card`() =
        checkoutTest {
            val h = harness(this)
            val vm = h.viewModel()
            h.pass(100.milliseconds)
            h.send(vm, CheckoutEvent.QrEncodeFailed)

            h.send(vm, CheckoutEvent.PayWith(PaymentMethod.CARD))
            h.pass(100.milliseconds)

            assertThat(vm.stage).isInstanceOf(CheckoutStage.WaitingForCard::class.java)
            assertThat(h.orders.requests).hasSize(1)
        }

    // Paid ----------------------------------------------------------------------------------------------------------

    @Test
    fun `Paid goes to Complete after at least 800 ms of Confirming, and the machine is handed back (TC-075)`() =
        checkoutTest {
            val h = harness(this)
            val vm = h.viewModel()
            h.pass(100.milliseconds)
            h.gateway.status.succeed(SampleData.paymentPaid)

            h.pass(2.seconds - 100.milliseconds)
            assertThat(vm.stage).isEqualTo(CheckoutStage.Processing(PaymentMethod.QRIS))
            assertThat(h.effects).isEmpty()

            h.pass(800.milliseconds)

            assertThat(h.effects).containsExactly(CheckoutEffect.ToComplete)
            assertThat(h.machine.state.value).isEqualTo(PaymentUiState.Idle)
            assertThat(h.session.state.value.phase).isEqualTo(OrderPhase.PAID)
        }

    @Test
    fun `a terminal approval alone is not Paid (A-15)`() =
        checkoutTest {
            val h = harness(this)
            val vm = h.viewModel(PaymentMethod.CARD)

            h.pass(5.seconds)

            assertThat(vm.stage).isEqualTo(CheckoutStage.Processing(PaymentMethod.CARD))
            assertThat(h.effects).isEmpty()
            assertThat(h.session.state.value.phase).isEqualTo(OrderPhase.PAYING)
        }

    @Test
    fun `Back to order when the order was paid meanwhile goes to Complete, not back`() =
        checkoutTest {
            val h = harness(this)
            val vm = h.viewModel()
            h.pass(100.milliseconds)
            h.orders.cancel.fail(DomainError.Conflict(code = "ORDER_ALREADY_PAID"))
            h.gateway.cancel.fail(DomainError.Conflict(code = "PAYMENT_ALREADY_PAID"))
            h.gateway.status.succeed(SampleData.paymentPaid)

            h.send(vm, CheckoutEvent.BackToOrder)
            h.pass(2.seconds)

            assertThat(h.effects).containsExactly(CheckoutEffect.ToComplete)
        }
}
