package com.hitachi.kiosk.core.data.net

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.BackendHarness
import com.hitachi.kiosk.core.data.api.NetworkErrorSignal
import com.hitachi.kiosk.core.data.api.NetworkTimeouts
import com.hitachi.kiosk.core.data.fixtureResponse
import com.hitachi.kiosk.core.data.jsonResponse
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * The E14 probe (FR-028, api-spec E14): only a backend and a payment gateway that are both `OK` count as healthy, and
 * a failing E14 never feeds the network-error hook (TC-154).
 */
class ConnectivityProbeTest {
    @get:Rule
    val rule = MockWebServerRule()

    private val server get() = rule.server
    private val shortTimeouts =
        NetworkTimeouts(
            connect = 500.milliseconds,
            read = 300.milliseconds,
            write = 300.milliseconds,
            call = 1_000.milliseconds,
        )

    // FakeBackendApi ----------------------------------------------------------------------------------------------

    @Test
    fun `an all OK answer is healthy`() =
        runTest {
            assertThat(FakeBackendApi().canTakePayments()).isTrue()
        }

    @Test
    fun `a gateway that is DEGRADED makes checkout unavailable`() =
        runTest {
            val api = FakeBackendApi().apply { health.succeed(SampleData.healthGatewayDegraded) }

            assertThat(api.canTakePayments()).isFalse()
        }

    @Test
    fun `a gateway that is DOWN makes checkout unavailable`() =
        runTest {
            val api = FakeBackendApi().apply { health.succeed(SampleData.healthGatewayDown) }

            assertThat(api.canTakePayments()).isFalse()
        }

    @Test
    fun `a degraded backend makes checkout unavailable`() =
        runTest {
            val api = FakeBackendApi().apply { health.succeed(SampleData.healthBackendDegraded) }

            assertThat(api.canTakePayments()).isFalse()
        }

    @Test
    fun `a failed E14 call is unhealthy`() =
        runTest {
            val api = FakeBackendApi().apply { health.fail(DomainError.Network()) }

            assertThat(api.canTakePayments()).isFalse()
        }

    // MockWebServer: the real transport ---------------------------------------------------------------------------

    @Test
    fun `the real E14 with every component OK is healthy`() =
        runTest {
            server.enqueue(fixtureResponse("e14_response"))

            assertThat(BackendHarness(server, shortTimeouts).api.canTakePayments()).isTrue()
        }

    @Test
    fun `the real E14 with the gateway DOWN is unhealthy`() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{"status":"OK","components":{"api":"OK","database":"OK","paymentGateway":"DOWN"}}""",
                ),
            )

            assertThat(BackendHarness(server, shortTimeouts).api.canTakePayments()).isFalse()
        }

    @Test
    fun `the real E14 with an unknown gateway status is unhealthy`() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{"status":"OK","components":{"api":"OK","database":"OK","paymentGateway":"MAYBE"}}""",
                ),
            )

            assertThat(BackendHarness(server, shortTimeouts).api.canTakePayments()).isFalse()
        }

    @Test
    fun `a 5xx, malformed JSON, a timeout and a dropped connection are all unhealthy without a crash`() =
        runTest {
            val harness = BackendHarness(server, shortTimeouts)
            server.enqueue(jsonResponse("""{"code":"SERVER_ERROR"}""", code = 503))
            server.enqueue(jsonResponse("not json"))
            server.enqueue(fixtureResponse("e14_response").newBuilder().headersDelay(2, TimeUnit.SECONDS).build())
            repeat(3) { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()) }

            repeat(4) { assertThat(harness.api.canTakePayments()).isFalse() }
        }

    // The network-error hook --------------------------------------------------------------------------------------

    @Test
    fun `a failed E14 does not report a network error, a failed real call does`() =
        runBlocking {
            val signal = NetworkErrorSignal()
            val reports = AtomicInteger()
            val listener =
                launch(start = CoroutineStart.UNDISPATCHED) { signal.reports.collect { reports.incrementAndGet() } }
            val api = BackendHarness(server, shortTimeouts, signal).api
            server.enqueue(fixtureResponse("e14_response").newBuilder().headersDelay(2, TimeUnit.SECONDS).build())
            repeat(3) { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()) }

            assertThat(api.canTakePayments()).isFalse()
            yield()
            assertThat(reports.get()).isEqualTo(0)

            val result = api.merchants(SampleData.DEVICE_ID)
            yield()
            assertThat(result).isEqualTo(Outcome.Failure(DomainError.Network(isTimeout = false)))
            assertThat(reports.get()).isEqualTo(1)
            listener.cancel()
        }

    @Test
    fun `a report with no listener is dropped`() =
        runTest(UnconfinedTestDispatcher()) {
            val signal = NetworkErrorSignal()
            signal.report()
            var reports = 0
            backgroundScope.launch { signal.reports.collect { reports++ } }

            assertThat(reports).isEqualTo(0)
            signal.report()
            assertThat(reports).isEqualTo(1)
        }
}
