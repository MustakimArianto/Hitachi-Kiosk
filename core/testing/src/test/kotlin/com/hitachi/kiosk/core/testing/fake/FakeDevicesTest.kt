package com.hitachi.kiosk.core.testing.fake

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.PrinterState
import com.hitachi.kiosk.core.domain.device.TerminalEvent
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class FakeDevicesTest {
    private val amount = Rupiah(172_700)
    private val reference = "TRX-20261022-000247"

    @Test
    fun `the card terminal prompts and then approves`() =
        runTest {
            val terminal = FakeCardTerminal(mode = FakeCardTerminal.Mode.APPROVE)

            terminal.startSale(amount, reference).test {
                assertThat(awaitItem()).isEqualTo(TerminalEvent.Prompted)
                val approved = awaitItem() as TerminalEvent.Approved
                assertThat(approved.reference).isEqualTo(reference)
                awaitComplete()
            }
            assertThat(currentTime).isEqualTo(2_000)
            assertThat(terminal.sales).containsExactly(FakeCardTerminal.Sale(amount, reference))
        }

    @Test
    fun `the card terminal declines, errors or is cancelled by the customer`() =
        runTest {
            suspend fun resultOf(mode: FakeCardTerminal.Mode): TerminalEvent {
                var last: TerminalEvent? = null
                FakeCardTerminal(
                    mode = mode,
                    resultDelay = 1.seconds,
                ).startSale(amount, reference).collect { last = it }
                return last!!
            }

            assertThat(resultOf(FakeCardTerminal.Mode.DECLINE)).isEqualTo(TerminalEvent.Declined("05"))
            assertThat(resultOf(FakeCardTerminal.Mode.ERROR)).isEqualTo(TerminalEvent.Error("terminal_error"))
            assertThat(resultOf(FakeCardTerminal.Mode.CUSTOMER_CANCEL)).isEqualTo(TerminalEvent.Cancelled)
        }

    @Test
    fun `a terminal that never answers can be cancelled by the kiosk`() =
        runTest {
            val terminal = FakeCardTerminal(mode = FakeCardTerminal.Mode.NO_ANSWER)

            terminal.startSale(amount, reference).test {
                assertThat(awaitItem()).isEqualTo(TerminalEvent.Prompted)
                launch {
                    advanceTimeBy(90.seconds)
                    terminal.cancel()
                }
                assertThat(awaitItem()).isEqualTo(TerminalEvent.Cancelled)
                awaitComplete()
            }
            assertThat(terminal.cancelCount).isEqualTo(1)
            assertThat(terminal.selfTest()).isEqualTo(Outcome.Success(Unit))
        }

    @Test
    fun `the printer records jobs and fails when not ready or mid-job`() =
        runTest {
            val printer = FakePrinterDriver()
            val job = byteArrayOf(0x1B, 0x40, 0x0A, 0x0A)

            assertThat(printer.write(job)).isEqualTo(Outcome.Success(Unit))
            printer.failMidJob = true
            assertThat(printer.write(job)).isEqualTo(Outcome.Failure(DomainError.Hardware("printer", "failed_mid_job")))
            assertThat(printer.writtenJobs.map { it.size }).containsExactly(4, 2).inOrder()

            printer.state = PrinterState.OUT_OF_PAPER
            assertThat(printer.status()).isEqualTo(PrinterState.OUT_OF_PAPER)
            assertThat(printer.write(job)).isEqualTo(Outcome.Failure(DomainError.Hardware("printer", "out_of_paper")))
            printer.state = PrinterState.OFFLINE
            assertThat(printer.write(job)).isEqualTo(Outcome.Failure(DomainError.Hardware("printer", "offline")))
        }

    @Test
    fun `device identity succeeds, is unavailable or is slow`() =
        runTest {
            val identity = FakeDeviceIdentityProvider()

            assertThat(identity.identify()).isEqualTo(Outcome.Success(SampleData.deviceIdentity))
            identity.unavailable()
            assertThat(
                identity.identify(),
            ).isEqualTo(Outcome.Failure(DomainError.Hardware("identity", "serial_unavailable")))
            identity.slow(5.seconds)
            assertThat(identity.identify()).isEqualTo(Outcome.Success(SampleData.deviceIdentity))
            assertThat(currentTime).isEqualTo(5_000)
        }

    @Test
    fun `connectivity switches between states`() =
        runTest {
            val monitor = FakeConnectivityMonitor()

            monitor.connectivity.test {
                assertThat(awaitItem()).isEqualTo(Connectivity.ONLINE)
                monitor.set(Connectivity.OFFLINE)
                assertThat(awaitItem()).isEqualTo(Connectivity.OFFLINE)
                monitor.set(Connectivity.CHECKING)
                assertThat(awaitItem()).isEqualTo(Connectivity.CHECKING)
            }
        }

    @Test
    fun `the clock moves only when told`() {
        val clock = TestClock()
        val start = clock.now()

        clock.advanceBy(10.seconds)
        assertThat(clock.now()).isEqualTo(start + 10.seconds)
        assertThat(clock.elapsedRealtime()).isEqualTo(10.seconds)

        clock.setWallClock(start + 600.seconds)
        assertThat(clock.now()).isEqualTo(start + 600.seconds)
        assertThat(clock.elapsedRealtime()).isEqualTo(10.seconds)
    }

    @Test
    fun `ids are sequential`() {
        val ids = FakeIdGenerator()

        assertThat(ids.newIdempotencyKey()).isEqualTo(IdempotencyKey("key-1"))
        assertThat(ids.newIdempotencyKey()).isEqualTo(IdempotencyKey("key-2"))
    }

    @Test
    fun `the payment gateway scripts each call and records the start keys`() =
        runTest {
            val gateway = FakePaymentGateway()
            val first = IdempotencyKey("a")
            val second = IdempotencyKey("b")

            assertThat(
                gateway.start(SampleData.ORDER_ID, PaymentMethod.QRIS, 300, first),
            ).isEqualTo(Outcome.Success(SampleData.qrisPayment))
            gateway.startCard.fail(DomainError.Server("PAYMENT_METHOD_UNAVAILABLE", 422))
            assertThat(gateway.start(SampleData.ORDER_ID, PaymentMethod.CARD, null, second))
                .isEqualTo(Outcome.Failure(DomainError.Server("PAYMENT_METHOD_UNAVAILABLE", 422)))
            gateway.status.succeed(SampleData.paymentPaid)
            assertThat(
                gateway.status(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID),
            ).isEqualTo(Outcome.Success(SampleData.paymentPaid))
            assertThat(
                gateway.cancel(
                    SampleData.ORDER_ID,
                    SampleData.QRIS_PAYMENT_ID,
                    CancelPaymentReason.EXPIRED_ON_KIOSK,
                    first,
                ),
            ).isEqualTo(Outcome.Success(SampleData.paymentCancelled))
            assertThat(
                gateway.relayTerminalResult(SampleData.ORDER_ID, SampleData.CARD_PAYMENT_ID, SampleData.terminalResult),
            ).isEqualTo(Outcome.Success(SampleData.terminalRelayed))
            assertThat(gateway.startKeys).containsExactly(first, second).inOrder()
        }
}
