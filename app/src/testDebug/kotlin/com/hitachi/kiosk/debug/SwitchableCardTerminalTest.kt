package com.hitachi.kiosk.debug

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.TerminalEvent
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeCardTerminal
import com.hitachi.kiosk.core.testing.fake.TestClock
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** The debug card terminal switch and the Checkout simulate bar (FR-018, FR-019; DEV-049, DEV-050). */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableCardTerminalTest {
    private val real = FakeCardTerminal(mode = FakeCardTerminal.Mode.ERROR, resultDelay = 0.seconds)
    private var settings = DeveloperSettings()
    private val terminal = SwitchableCardTerminal(real = real, settings = { settings })

    @Test
    fun `the manual terminal prompts and waits for the simulate bar`() =
        runTest {
            val events = mutableListOf<TerminalEvent>()
            backgroundScope.launch { terminal.startSale(Rupiah(172_700), "ref-1").toList(events) }
            runCurrent()
            assertThat(events).containsExactly(TerminalEvent.Prompted)
            assertThat(terminal.manual.isWaiting).isTrue()

            assertThat(terminal.manual.approve()).isTrue()
            runCurrent()

            assertThat(events.last()).isInstanceOf(TerminalEvent.Approved::class.java)
            assertThat((events.last() as TerminalEvent.Approved).reference).isEqualTo("ref-1")
            assertThat(terminal.manual.isWaiting).isFalse()
        }

    @Test
    fun `decline and cancel end the manual sale, and nothing waits afterwards`() =
        runTest {
            val declined = mutableListOf<TerminalEvent>()
            backgroundScope.launch { terminal.startSale(Rupiah(1), "r").toList(declined) }
            runCurrent()
            assertThat(terminal.manual.decline()).isTrue()
            runCurrent()
            assertThat(declined.last()).isEqualTo(TerminalEvent.Declined("05"))
            assertThat(terminal.manual.decline()).isFalse()

            val cancelled = mutableListOf<TerminalEvent>()
            backgroundScope.launch { terminal.startSale(Rupiah(1), "r").toList(cancelled) }
            runCurrent()
            terminal.cancel()
            runCurrent()
            assertThat(cancelled.last()).isEqualTo(TerminalEvent.Cancelled)
        }

    @Test
    fun `the Developer options pick the terminal at the time of the sale`() =
        runTest {
            settings = DeveloperSettings(terminalMode = TerminalMode.DECLINE)
            val declined = mutableListOf<TerminalEvent>()
            backgroundScope.launch { terminal.startSale(Rupiah(1), "r").toList(declined) }
            advanceTimeBy(2.seconds)
            runCurrent()
            assertThat(declined.last()).isInstanceOf(TerminalEvent.Declined::class.java)

            settings = DeveloperSettings(terminalMode = TerminalMode.REAL)
            val failed = mutableListOf<TerminalEvent>()
            backgroundScope.launch { terminal.startSale(Rupiah(1), "r").toList(failed) }
            runCurrent()
            assertThat(failed.last()).isInstanceOf(TerminalEvent.Error::class.java)
        }

    @Test
    fun `the simulate bar queues PAID or a failure once on the fake backend`() =
        runTest {
            val backend = SwitchableBackendApi(real = FakeBackendApi(), settings = { settings }, clock = TestClock())
            val bindings = DebugBindings().apply { this.backend = backend }
            val simulator = CheckoutSimulator(MutableStateFlow(settings), bindings)

            simulator.approve(PaymentMethod.QRIS)
            val paid = backend.paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID)
            val after = backend.paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID)
            assertThat((paid as Outcome.Success).value.status).isEqualTo(PaymentStatus.PAID)
            assertThat((after as Outcome.Success).value.status).isEqualTo(PaymentStatus.PENDING)

            simulator.decline(PaymentMethod.QRIS)
            val failed = backend.paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID)
            assertThat((failed as Outcome.Success).value.status).isEqualTo(PaymentStatus.FAILED)
        }

    @Test
    fun `the simulate bar drives a waiting terminal and ignores the real backend`() =
        runTest {
            val bindings = DebugBindings()
            val backend = SwitchableBackendApi(real = FakeBackendApi(), settings = { settings }, clock = TestClock())
            bindings.backend = backend
            bindings.terminal = terminal
            val simulator = CheckoutSimulator(MutableStateFlow(settings), bindings)
            val events = mutableListOf<TerminalEvent>()
            backgroundScope.launch { terminal.startSale(Rupiah(1), "r").toList(events) }
            runCurrent()

            simulator.approve(PaymentMethod.CARD)
            runCurrent()
            assertThat(events.last()).isInstanceOf(TerminalEvent.Approved::class.java)

            settings = DeveloperSettings(backendMode = BackendMode.REAL)
            val real = FakeBackendApi().apply { paymentStatus.fail(DomainError.Network()) }
            val untouched = SwitchableBackendApi(real = real, settings = { settings }, clock = TestClock())
            bindings.backend = untouched
            simulator.approve(PaymentMethod.QRIS)
            assertThat(untouched.fakeOrNull()).isNull()
            assertThat(real.callsTo(FakeBackendApi.Endpoint.E10)).isEmpty()
        }
}
