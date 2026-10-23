package com.hitachi.kiosk.core.data.backend

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.BackendHarness
import com.hitachi.kiosk.core.data.TEST_APP_VERSION
import com.hitachi.kiosk.core.data.TEST_CREDENTIALS
import com.hitachi.kiosk.core.data.fixtureResponse
import com.hitachi.kiosk.core.data.takeRequestNow
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.ActivationRequest
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test

/** Request headers of api-spec §2.2, added by the interceptors in architecture §7 order. */
class RequestHeadersTest {
    @get:Rule
    val rule = MockWebServerRule()

    private val server get() = rule.server
    private val harness by lazy { BackendHarness(server) }

    private val RecordedRequest.authorization get() = headers["Authorization"]

    @Test
    fun `authenticated calls send the bearer token, device id, app version and English`() =
        runTest {
            server.enqueue(fixtureResponse("e2_response"))

            harness.api.merchants(SampleData.DEVICE_ID)

            val request = server.takeRequestNow()
            assertThat(request.authorization).isEqualTo("Bearer ${TEST_CREDENTIALS.deviceToken}")
            assertThat(request.headers["X-Device-Id"]).isEqualTo(SampleData.DEVICE_ID)
            assertThat(request.headers["X-App-Version"]).isEqualTo(TEST_APP_VERSION)
            assertThat(request.headers["Accept-Language"]).isEqualTo("en")
            assertThat(request.headers["Idempotency-Key"]).isNull()
        }

    @Test
    fun `E1 is sent without Authorization`() =
        runTest {
            server.enqueue(fixtureResponse("e1_response"))

            harness.api.activate(ActivationRequest(SampleData.SERIAL_NUMBER, null, null, TEST_APP_VERSION))

            val request = server.takeRequestNow()
            assertThat(request.authorization).isNull()
            assertThat(request.headers["Content-Type"]).isEqualTo("application/json; charset=utf-8")
            assertThat(request.headers["X-App-Version"]).isEqualTo(TEST_APP_VERSION)
            assertThat(request.headers["Accept-Language"]).isEqualTo("en")
        }

    @Test
    fun `E14 is sent without Authorization but with the device id`() =
        runTest {
            server.enqueue(fixtureResponse("e14_response"))

            harness.api.health()

            val request = server.takeRequestNow()
            assertThat(request.authorization).isNull()
            assertThat(request.headers["X-Device-Id"]).isEqualTo(SampleData.DEVICE_ID)
        }

    @Test
    fun `before activation no token or device id is sent`() =
        runTest {
            harness.credentials.clear()
            server.enqueue(fixtureResponse("e2_response"))

            harness.api.merchants(SampleData.DEVICE_ID)

            val request = server.takeRequestNow()
            assertThat(request.authorization).isNull()
            assertThat(request.headers["X-Device-Id"]).isNull()
            assertThat(request.headers["X-App-Version"]).isEqualTo(TEST_APP_VERSION)
        }

    @Test
    fun `the caller's Idempotency-Key is forwarded unchanged on every retry`() =
        runTest {
            val key = IdempotencyKey("5b1c0c7e-2f1a-4c55-9d0e-1c2a7f0e9b11")
            server.enqueue(fixtureResponse("e7_response", 201))
            server.enqueue(fixtureResponse("e7_response", 201))

            harness.api.createOrder(SampleData.orderRequest, key)
            harness.api.createOrder(SampleData.orderRequest, key)

            assertThat(server.takeRequestNow().headers["Idempotency-Key"]).isEqualTo(key.value)
            assertThat(server.takeRequestNow().headers["Idempotency-Key"]).isEqualTo(key.value)
        }

    @Test
    fun `E9 forwards the key and E10 sends none`() =
        runTest {
            server.enqueue(fixtureResponse("e9_response_qris", 201))
            server.enqueue(fixtureResponse("e10_response_pending"))

            harness.api.startPayment(SampleData.ORDER_ID, PaymentMethod.QRIS, 300, IdempotencyKey("k-9"))
            harness.api.paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID)

            assertThat(server.takeRequestNow().headers["Idempotency-Key"]).isEqualTo("k-9")
            assertThat(server.takeRequestNow().headers["Idempotency-Key"]).isNull()
        }
}
