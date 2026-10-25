package com.hitachi.kiosk.feature.complete

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.RecoveredPaidOrder
import com.hitachi.kiosk.core.domain.payment.PaymentStateMachine
import com.hitachi.kiosk.core.domain.payment.PaymentUiState
import com.hitachi.kiosk.core.testing.fake.FakeCardTerminal
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fake.FakeOrderRepository
import com.hitachi.kiosk.core.testing.fake.FakePaymentGateway
import com.hitachi.kiosk.core.testing.fake.VirtualTimeClock
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import com.hitachi.kiosk.recovery.RecoveredPaidSource
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * SCR-12 behaviour (FR-020, DEV-060; TC-088, TC-089, TC-090) with the real [PaymentStateMachine] driven to `Paid` by
 * fakes, the test clock, and a recovered paid order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompleteViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Source(
        var order: RecoveredPaidOrder? = null,
    ) : RecoveredPaidSource {
        var consumed = 0

        override fun consumeRecoveredPaid(): RecoveredPaidOrder? {
            consumed++
            return order.also { order = null }
        }
    }

    private inner class Harness(
        val scope: TestScope,
    ) {
        val clock = VirtualTimeClock(scope.testScheduler)
        val holder = DefaultOrderSessionHolder()
        val orders = FakeOrderRepository()
        val gateway = FakePaymentGateway(orderTotal = { orders.pendingTotal })
        val source = Source()
        val machine =
            PaymentStateMachine(
                scope = scope.backgroundScope,
                clock = clock,
                ids = FakeIdGenerator(),
                gateway = gateway,
                terminal = FakeCardTerminal(),
                orders = orders,
                connectivity = FakeConnectivityMonitor(),
                sessions = holder,
            )
        val effects = mutableListOf<CompleteEffect>()

        /** A QRIS payment that the backend reports `PAID`, as when Checkout hands over (A-15). */
        fun pay(orderType: OrderType) {
            holder.chooseOrderType(orderType, "menu-118", "cfg-42")
            val now = clock.now()
            gateway.startQris.succeed(
                SampleData.qrisPayment.copy(createdAt = now, expiresAt = now + 300.seconds, serverTime = now),
            )
            gateway.status.succeed(SampleData.paymentPaid)
            machine.start(SampleData.ORDER_ID, PaymentMethod.QRIS, 300)
            pass(3.seconds)
            assertThat(machine.state.value).isInstanceOf(PaymentUiState.Paid::class.java)
        }

        fun viewModel(): CompleteViewModel {
            val vm = CompleteViewModel(machine, source, holder, clock)
            created += vm
            scope.backgroundScope.launch { vm.effect.toList(effects) }
            scope.runCurrent()
            return vm
        }

        fun pass(duration: Duration) {
            scope.advanceTimeBy(duration)
            scope.runCurrent()
        }
    }

    private val created = mutableListOf<CompleteViewModel>()

    private fun completeTest(block: suspend TestScope.() -> Unit) =
        runTest(main.dispatcher) {
            try {
                block()
            } finally {
                created.forEach { it.viewModelScope.cancel() }
            }
        }

    @Test
    fun `it shows the backend order number, the amount and the method of the paid payment (TC-088)`() =
        completeTest {
            val h = Harness(this)
            h.pay(OrderType.DINE_IN)

            val state = h.viewModel().state.value

            assertThat(state.orderNumber).isEqualTo("A247")
            assertThat(state.amount).isEqualTo("Rp 172.700")
            assertThat(state.method).isEqualTo(PaymentMethod.QRIS)
            assertThat(state.orderType).isEqualTo(OrderType.DINE_IN)
            assertThat(state.secondsLeft).isEqualTo(20)
        }

    @Test
    fun `a takeaway order keeps its order type for the takeaway copy (TC-090)`() =
        completeTest {
            val h = Harness(this)
            h.pay(OrderType.TAKEAWAY)

            assertThat(
                h
                    .viewModel()
                    .state.value.orderType,
            ).isEqualTo(OrderType.TAKEAWAY)
        }

    @Test
    fun `the machine stays Paid until Complete is shown, then it is acknowledged once`() =
        completeTest {
            val h = Harness(this)
            h.pay(OrderType.DINE_IN)
            val vm = h.viewModel()
            assertThat(h.machine.state.value).isInstanceOf(PaymentUiState.Paid::class.java)

            vm.onEvent(CompleteEvent.Shown)
            h.pass(1.milliseconds)

            assertThat(h.machine.state.value).isEqualTo(PaymentUiState.Idle)
            assertThat(vm.state.value.orderNumber).isEqualTo("A247")
        }

    @Test
    fun `it returns to Idle after 20 s plus or minus 1 s and clears the session (TC-089)`() =
        completeTest {
            val h = Harness(this)
            h.pay(OrderType.DINE_IN)
            val vm = h.viewModel()

            h.pass(19.seconds - 1.milliseconds)
            assertThat(h.effects).isEmpty()
            assertThat(vm.state.value.secondsLeft).isEqualTo(2)
            assertThat(h.holder.current.value).isNotNull()

            h.pass(1.seconds + 1.milliseconds)

            assertThat(h.effects).containsExactly(CompleteEffect.ToIdle)
            assertThat(vm.state.value.secondsLeft).isEqualTo(0)
            assertThat(h.holder.current.value).isNull()
            assertThat(h.machine.state.value).isEqualTo(PaymentUiState.Idle)
        }

    @Test
    fun `the countdown shows 20 down to 1 once a second`() =
        completeTest {
            val h = Harness(this)
            h.pay(OrderType.DINE_IN)
            val vm = h.viewModel()

            h.pass(5.seconds)

            assertThat(vm.state.value.secondsLeft).isEqualTo(15)
        }

    @Test
    fun `Done returns at once, clears the session and sends one effect (TC-089)`() =
        completeTest {
            val h = Harness(this)
            h.pay(OrderType.DINE_IN)
            val vm = h.viewModel()
            h.pass(3.seconds)

            vm.onEvent(CompleteEvent.Done)
            vm.onEvent(CompleteEvent.Done)
            h.pass(1.milliseconds)

            assertThat(h.effects).containsExactly(CompleteEffect.ToIdle)
            assertThat(h.holder.current.value).isNull()
            assertThat(h.machine.state.value).isEqualTo(PaymentUiState.Idle)

            // The timer does not send a second effect later.
            h.pass(30.seconds)
            assertThat(h.effects).hasSize(1)
        }

    @Test
    fun `an order that a recovery found paid shows its totals and a neutral order type (FR-016 AC4)`() =
        completeTest {
            val h = Harness(this)
            h.source.order =
                RecoveredPaidOrder(
                    orderId = SampleData.ORDER_ID,
                    orderNumber = "A310",
                    paymentId = "pay-1",
                    method = PaymentMethod.CARD,
                    totals = SampleData.createdOrder.totals,
                    paidAt = null,
                    reference = null,
                    issuer = null,
                )

            val vm = h.viewModel()

            assertThat(vm.state.value.orderNumber).isEqualTo("A310")
            assertThat(vm.state.value.amount).isEqualTo("Rp 172.700")
            assertThat(vm.state.value.method).isEqualTo(PaymentMethod.CARD)
            assertThat(vm.state.value.orderType).isNull()
            h.pass(20.seconds)
            assertThat(h.effects).containsExactly(CompleteEffect.ToIdle)
        }

    @Test
    fun `with nothing paid it goes straight back to Idle instead of showing an empty ticket`() =
        completeTest {
            val h = Harness(this)

            h.viewModel()

            assertThat(h.effects).containsExactly(CompleteEffect.ToIdle)
        }
}
