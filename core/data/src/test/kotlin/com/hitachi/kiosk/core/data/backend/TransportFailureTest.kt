package com.hitachi.kiosk.core.data.backend

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.BackendHarness
import com.hitachi.kiosk.core.data.LogCapture
import com.hitachi.kiosk.core.data.api.NetworkTimeouts
import com.hitachi.kiosk.core.data.fixtureResponse
import com.hitachi.kiosk.core.data.jsonResponse
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import mockwebserver3.junit4.MockWebServerRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/** Timeouts, disconnects and bodies that do not parse (NFR-010, technical-design §5.2). */
class TransportFailureTest {
    @get:Rule
    val rule = MockWebServerRule()

    private val server get() = rule.server
    private val shortTimeouts =
        NetworkTimeouts(
            connect = 500.milliseconds,
            read = 300.milliseconds,
            write = 300.milliseconds,
            call = 2_000.milliseconds,
        )
    private val harness by lazy { BackendHarness(server, shortTimeouts) }
    private val logs = LogCapture()

    @Before
    fun installLogs() = logs.install()

    @After
    fun uninstallLogs() = logs.uninstall()

    @Test
    fun `slow headers map to a Network timeout`() =
        runTest {
            server.enqueue(fixtureResponse("e14_response").newBuilder().headersDelay(2, TimeUnit.SECONDS).build())

            assertThat(harness.api.health()).isEqualTo(Outcome.Failure(DomainError.Network(isTimeout = true)))
        }

    @Test
    fun `a stalled body maps to a Network timeout`() =
        runTest {
            server.enqueue(fixtureResponse("e10_response_pending").newBuilder().bodyDelay(2, TimeUnit.SECONDS).build())

            val result = harness.api.paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.Network(isTimeout = true)))
        }

    @Test
    fun `a dropped connection maps to Network`() =
        runTest {
            repeat(3) { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()) }

            val result = harness.api.paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.Network(isTimeout = false)))
        }

    @Test
    fun `an unreachable server maps to Network`() =
        runTest {
            val closed = MockWebServer().apply { start() }
            val unreachable = BackendHarness(closed, shortTimeouts)
            closed.close()

            assertThat(unreachable.api.health()).isEqualTo(Outcome.Failure(DomainError.Network(isTimeout = false)))
        }

    @Test
    fun `malformed JSON maps to Unknown and the body is never logged`() =
        runTest {
            server.enqueue(jsonResponse("""{ "paymentId": "pay_SECRET_REF", "status": """))

            val result = harness.api.paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.Unknown))
            val record = logs.records.single { it.event == "api_unparsable_response" }
            assertThat(record.fields["endpoint"]).isEqualTo("E10")
            assertThat(logs.records.flatMap { it.fields.values }.none { "SECRET" in it }).isTrue()
        }

    @Test
    fun `a missing required field maps to Unknown`() =
        runTest {
            server.enqueue(jsonResponse("""{ "orderId": "ord_1" }"""))

            assertThat(
                harness.api.createOrder(SampleData.orderRequest, IdempotencyKey("k")),
            ).isEqualTo(Outcome.Failure(DomainError.Unknown))
        }

    @Test
    fun `an unknown payment status maps to Unknown, never to a guess`() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{ "paymentId": "pay_Q1x8", "method": "QRIS", "status": "REFUNDED", "amount": 172700 }""",
                ),
            )

            val result = harness.api.paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.Unknown))
        }

    @Test
    fun `a QRIS start without a QR string maps to Unknown`() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    { "paymentId": "p", "method": "QRIS", "status": "PENDING", "amount": 1,
                      "createdAt": "2026-10-22T07:15:31Z" }
                    """.trimIndent(),
                    201,
                ),
            )

            val result = harness.api.startPayment(SampleData.ORDER_ID, PaymentMethod.QRIS, 300, IdempotencyKey("k"))

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.Unknown))
        }

    @Test
    fun `an invalid timestamp maps to Unknown`() =
        runTest {
            server.enqueue(jsonResponse("""{ "deviceId": "d", "merchantId": "m", "boundAt": "yesterday" }"""))

            assertThat(harness.api.bind(SampleData.DEVICE_ID, SampleData.MERCHANT_ID, IdempotencyKey("k")))
                .isEqualTo(Outcome.Failure(DomainError.Unknown))
        }

    @Test
    fun `an empty 200 body maps to Unknown`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(200).build())

            assertThat(harness.api.health()).isEqualTo(Outcome.Failure(DomainError.Unknown))
        }
}
