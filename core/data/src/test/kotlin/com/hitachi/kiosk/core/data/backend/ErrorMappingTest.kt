package com.hitachi.kiosk.core.data.backend

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.BackendHarness
import com.hitachi.kiosk.core.data.fixtureResponse
import com.hitachi.kiosk.core.data.jsonResponse
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.ActivationRequest
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test

/** The error envelope (api-spec §2.4) mapped per the TD-04 table (technical-design §5.2). */
class ErrorMappingTest {
    @get:Rule
    val rule = MockWebServerRule()

    private val server get() = rule.server
    private val harness by lazy { BackendHarness(server) }
    private val key = IdempotencyKey("k-1")

    private fun envelope(code: String) = """{ "code": "$code", "message": "m", "details": { } }"""

    @Test
    fun `404 DEVICE_NOT_REGISTERED maps to Server with the code`() =
        runTest {
            server.enqueue(fixtureResponse("e1_error_not_registered", 404))

            val result = harness.api.activate(ActivationRequest(SampleData.SERIAL_NUMBER, null, null, "0.1.0"))

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.Server("DEVICE_NOT_REGISTERED", 404)))
        }

    @Test
    fun `401 maps to Unauthorized and calls the handler once`() =
        runTest {
            server.enqueue(jsonResponse(envelope("TOKEN_INVALID"), 401))

            val result = harness.api.merchants(SampleData.DEVICE_ID)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.Unauthorized))
            assertThat(harness.unauthorizedCalls.get()).isEqualTo(1)
        }

    @Test
    fun `409 PRICE_CHANGED maps the new versions and server totals`() =
        runTest {
            server.enqueue(fixtureResponse("e7_error_price_changed", 409))

            val result = harness.api.createOrder(SampleData.orderRequest, key)

            assertThat(result).isEqualTo(
                Outcome.Failure(DomainError.PriceChanged("menu-119", "cfg-42", SampleData.changedTotals)),
            )
        }

    @Test
    fun `409 PRICE_CHANGED with empty details maps to PriceChanged without details`() =
        runTest {
            server.enqueue(fixtureResponse("error", 409))

            val result = harness.api.createOrder(SampleData.orderRequest, key)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.PriceChanged(null, null, null)))
        }

    @Test
    fun `409 PRICE_CHANGED with malformed details still maps to PriceChanged`() =
        runTest {
            server.enqueue(jsonResponse("""{ "code": "PRICE_CHANGED", "details": { "serverTotals": "oops" } }""", 409))

            val result = harness.api.createOrder(SampleData.orderRequest, key)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.PriceChanged(null, null, null)))
        }

    @Test
    fun `409 ITEM_UNAVAILABLE maps the line and product ids`() =
        runTest {
            server.enqueue(fixtureResponse("e7_error_item_unavailable", 409))

            val result = harness.api.createOrder(SampleData.orderRequest, key)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.ItemUnavailable(listOf("l2"), listOf("c1"))))
        }

    @Test
    fun `409 ITEM_UNAVAILABLE without details maps to empty ids`() =
        runTest {
            server.enqueue(jsonResponse("""{ "code": "ITEM_UNAVAILABLE" }""", 409))

            val result = harness.api.createOrder(SampleData.orderRequest, key)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.ItemUnavailable(emptyList(), emptyList())))
        }

    @Test
    fun `other 409 codes map to Conflict`() =
        runTest {
            server.enqueue(fixtureResponse("e11_error_not_cancellable", 409))
            server.enqueue(jsonResponse(envelope("MERCHANT_CLOSED"), 409))

            val cancel =
                harness.api.cancelPayment(
                    SampleData.ORDER_ID,
                    SampleData.QRIS_PAYMENT_ID,
                    CancelPaymentReason.CUSTOMER_BACK,
                    key,
                )
            val order = harness.api.createOrder(SampleData.orderRequest, key)

            assertThat(cancel).isEqualTo(Outcome.Failure(DomainError.Conflict("PAYMENT_NOT_CANCELLABLE")))
            assertThat(order).isEqualTo(Outcome.Failure(DomainError.Conflict("MERCHANT_CLOSED")))
        }

    @Test
    fun `409 without an envelope maps to Conflict with the HTTP status`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(409)
                    .body("<html>conflict</html>")
                    .build(),
            )

            assertThat(harness.api.health()).isEqualTo(Outcome.Failure(DomainError.Conflict("HTTP_409")))
        }

    @Test
    fun `403, 400, 422 and 429 map to Server with the envelope code`() =
        runTest {
            server.enqueue(jsonResponse(envelope("DEVICE_DISABLED"), 403))
            server.enqueue(jsonResponse(envelope("VALIDATION_ERROR"), 400))
            server.enqueue(jsonResponse(envelope("PAYMENT_METHOD_UNAVAILABLE"), 422))
            server.enqueue(jsonResponse(envelope("RATE_LIMITED"), 429))

            val disabled = harness.api.activate(ActivationRequest(SampleData.SERIAL_NUMBER, null, null, "0.1.0"))
            val invalid = harness.api.createOrder(SampleData.orderRequest, key)
            val unavailable = harness.api.startPayment(SampleData.ORDER_ID, PaymentMethod.CARD, null, key)
            val limited = harness.api.paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID)

            assertThat(disabled).isEqualTo(Outcome.Failure(DomainError.Server("DEVICE_DISABLED", 403)))
            assertThat(invalid).isEqualTo(Outcome.Failure(DomainError.Server("VALIDATION_ERROR", 400)))
            assertThat(unavailable).isEqualTo(Outcome.Failure(DomainError.Server("PAYMENT_METHOD_UNAVAILABLE", 422)))
            assertThat(limited).isEqualTo(Outcome.Failure(DomainError.Server("RATE_LIMITED", 429)))
        }

    @Test
    fun `503 without a body maps to Server with the HTTP status`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(503).build())

            assertThat(harness.api.health()).isEqualTo(Outcome.Failure(DomainError.Server("HTTP_503", 503)))
        }

    @Test
    fun `502 GATEWAY_ERROR maps to Server with the code`() =
        runTest {
            server.enqueue(jsonResponse(envelope("GATEWAY_ERROR"), 502))

            val result = harness.api.startPayment(SampleData.ORDER_ID, PaymentMethod.QRIS, 300, key)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.Server("GATEWAY_ERROR", 502)))
        }
}
