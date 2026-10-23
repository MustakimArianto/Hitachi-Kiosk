package com.hitachi.kiosk.core.data.backend

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.BackendHarness
import com.hitachi.kiosk.core.data.api.KioskJson
import com.hitachi.kiosk.core.data.fixtureJson
import com.hitachi.kiosk.core.data.fixtureResponse
import com.hitachi.kiosk.core.data.jsonBody
import com.hitachi.kiosk.core.data.mapper.toDto
import com.hitachi.kiosk.core.data.takeRequestNow
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.ActivationRequest
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test

/**
 * Every E1–E14 example of api-spec.md, served by MockWebServer, maps to the same domain objects as [SampleData];
 * request bodies built from [SampleData] equal the spec's request examples (DEV-006, DEV-007, TR-NET-02).
 */
class BackendContractTest {
    @get:Rule
    val rule = MockWebServerRule()

    private val server get() = rule.server
    private val harness by lazy { BackendHarness(server) }
    private val api get() = harness.api
    private val gateway by lazy { BackendPaymentGateway(api) }
    private val key = IdempotencyKey("5b1c0c7e-2f1a-4c55-9d0e-1c2a7f0e9b11")

    @Test
    fun `E1 activate sends the spec request and maps the activation`() =
        runTest {
            server.enqueue(fixtureResponse("e1_response"))

            val result = api.activate(ActivationRequest(SampleData.SERIAL_NUMBER, "iMin Swan 2", "14", "0.2.0"))

            assertThat(result).isEqualTo(Outcome.Success(SampleData.activation))
            val request = server.takeRequestNow()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/v1/devices/activate")
            assertThat(request.jsonBody()).isEqualTo(fixtureJson("e1_request"))
        }

    @Test
    fun `E2 merchants maps live and coming soon merchants`() =
        runTest {
            server.enqueue(fixtureResponse("e2_response"))

            assertThat(api.merchants(SampleData.DEVICE_ID)).isEqualTo(Outcome.Success(SampleData.merchants))
            val request = server.takeRequestNow()
            assertThat(request.method).isEqualTo("GET")
            assertThat(request.url.encodedPath).isEqualTo("/v1/devices/dev_0003/merchants")
        }

    @Test
    fun `E3 bind sends the merchant and maps the binding`() =
        runTest {
            server.enqueue(fixtureResponse("e3_response"))

            assertThat(
                api.bind(SampleData.DEVICE_ID, SampleData.MERCHANT_ID, key),
            ).isEqualTo(Outcome.Success(SampleData.binding))
            val request = server.takeRequestNow()
            assertThat(request.method).isEqualTo("PUT")
            assertThat(request.url.encodedPath).isEqualTo("/v1/devices/dev_0003/binding")
            assertThat(request.jsonBody()).isEqualTo(fixtureJson("e3_request"))
        }

    @Test
    fun `E4 unbind maps 204 to Unit`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(204).build())

            assertThat(api.unbind(SampleData.DEVICE_ID)).isEqualTo(Outcome.Success(Unit))
            val request = server.takeRequestNow()
            assertThat(request.method).isEqualTo("DELETE")
            assertThat(request.url.encodedPath).isEqualTo("/v1/devices/dev_0003/binding")
        }

    @Test
    fun `E5 config maps the whole config and keeps the ETag`() =
        runTest {
            server.enqueue(fixtureResponse("e5_response", 200, "ETag" to "\"cfg-42\""))

            val result = api.config(SampleData.MERCHANT_ID, etag = null)

            assertThat(result).isEqualTo(Outcome.Success(Conditional.Modified(SampleData.config, "\"cfg-42\"")))
            val request = server.takeRequestNow()
            assertThat(request.url.encodedPath).isEqualTo("/v1/merchants/mer_urbangrill/config")
            assertThat(request.headers["If-None-Match"]).isNull()
        }

    @Test
    fun `E6 menu maps the whole menu, ignores promo banners and keeps the ETag`() =
        runTest {
            server.enqueue(fixtureResponse("e6_response", 200, "ETag" to "\"menu-118\""))

            val result = api.menu(SampleData.MERCHANT_ID, etag = null)

            assertThat(result).isEqualTo(Outcome.Success(Conditional.Modified(SampleData.menu, "\"menu-118\"")))
            assertThat(server.takeRequestNow().url.encodedPath).isEqualTo("/v1/merchants/mer_urbangrill/menu")
        }

    @Test
    fun `E7 create order sends the spec request in English and maps the created order`() =
        runTest {
            server.enqueue(fixtureResponse("e7_response", 201))

            assertThat(
                api.createOrder(SampleData.orderRequest, key),
            ).isEqualTo(Outcome.Success(SampleData.createdOrder))
            val request = server.takeRequestNow()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/v1/orders")
            assertThat(request.jsonBody()).isEqualTo(fixtureJson("e7_request"))
        }

    @Test
    fun `E7 combo line encodes like the spec's combo example`() {
        val encoded =
            KioskJson.encodeToJsonElement(
                com.hitachi.kiosk.core.data.dto.OrderLineDto
                    .serializer(),
                SampleData.comboLine.toDto(),
            )

        assertThat(encoded).isEqualTo(fixtureJson("e7_request_combo_line"))
    }

    @Test
    fun `E8 cancel order sends the reason and maps the cancelled order`() =
        runTest {
            server.enqueue(fixtureResponse("e8_response"))

            val result = api.cancelOrder(SampleData.ORDER_ID, CancelOrderReason.CUSTOMER_BACK, key)

            assertThat(result).isEqualTo(Outcome.Success(SampleData.cancelledOrder))
            val request = server.takeRequestNow()
            assertThat(request.url.encodedPath).isEqualTo("/v1/orders/ord_7Hq2k9/cancel")
            assertThat(request.jsonBody()).isEqualTo(fixtureJson("e8_request"))
        }

    @Test
    fun `E9 QRIS start sends the expiry and maps the QR payment`() =
        runTest {
            server.enqueue(fixtureResponse("e9_response_qris", 201))

            val result = gateway.start(SampleData.ORDER_ID, PaymentMethod.QRIS, qrExpirySeconds = 300, key = key)

            assertThat(result).isEqualTo(Outcome.Success(SampleData.qrisPayment))
            val request = server.takeRequestNow()
            assertThat(request.url.encodedPath).isEqualTo("/v1/orders/ord_7Hq2k9/payments")
            assertThat(request.jsonBody()).isEqualTo(fixtureJson("e9_request_qris"))
        }

    @Test
    fun `E9 card start sends no QR expiry and maps the card payment`() =
        runTest {
            server.enqueue(fixtureResponse("e9_response_card", 201))

            val result = gateway.start(SampleData.ORDER_ID, PaymentMethod.CARD, qrExpirySeconds = 300, key = key)

            assertThat(result).isEqualTo(Outcome.Success(SampleData.cardPayment))
            assertThat(server.takeRequestNow().jsonBody()).isEqualTo(fixtureJson("e9_request_card"))
        }

    @Test
    fun `E10 maps pending, paid and failed payments`() =
        runTest {
            server.enqueue(fixtureResponse("e10_response_pending"))
            server.enqueue(fixtureResponse("e10_response_paid"))
            server.enqueue(fixtureResponse("e10_response_failed"))

            assertThat(gateway.status(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID))
                .isEqualTo(Outcome.Success(SampleData.paymentPending))
            assertThat(gateway.status(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID))
                .isEqualTo(Outcome.Success(SampleData.paymentPaid))
            assertThat(gateway.status(SampleData.ORDER_ID, SampleData.CARD_PAYMENT_ID))
                .isEqualTo(Outcome.Success(SampleData.paymentDeclined))
            val request = server.takeRequestNow()
            assertThat(request.method).isEqualTo("GET")
            assertThat(request.url.encodedPath).isEqualTo("/v1/orders/ord_7Hq2k9/payments/pay_Q1x8")
        }

    @Test
    fun `E11 cancel payment sends the reason and maps the update`() =
        runTest {
            server.enqueue(fixtureResponse("e11_response"))

            val result =
                gateway.cancel(
                    SampleData.ORDER_ID,
                    SampleData.QRIS_PAYMENT_ID,
                    CancelPaymentReason.EXPIRED_ON_KIOSK,
                    key,
                )

            assertThat(result).isEqualTo(Outcome.Success(SampleData.paymentCancelled))
            val request = server.takeRequestNow()
            assertThat(request.url.encodedPath).isEqualTo("/v1/orders/ord_7Hq2k9/payments/pay_Q1x8/cancel")
            assertThat(request.jsonBody()).isEqualTo(fixtureJson("e11_request"))
        }

    @Test
    fun `E12 relays the terminal result`() =
        runTest {
            server.enqueue(fixtureResponse("e12_response"))

            val result =
                gateway.relayTerminalResult(
                    SampleData.ORDER_ID,
                    SampleData.CARD_PAYMENT_ID,
                    SampleData.terminalResult,
                )

            assertThat(result).isEqualTo(Outcome.Success(SampleData.terminalRelayed))
            val request = server.takeRequestNow()
            assertThat(request.url.encodedPath).isEqualTo("/v1/orders/ord_7Hq2k9/payments/pay_C7m2/terminal-result")
            assertThat(request.jsonBody()).isEqualTo(fixtureJson("e12_request"))
        }

    @Test
    fun `E13 change PIN sends both PINs and maps the new hash`() =
        runTest {
            server.enqueue(fixtureResponse("e13_response"))

            val result = api.changeStaffPin(SampleData.DEVICE_ID, currentPin = "1234", newPin = "5678", key = key)

            assertThat(result).isEqualTo(Outcome.Success(SampleData.newStaffPin))
            val request = server.takeRequestNow()
            assertThat(request.method).isEqualTo("PUT")
            assertThat(request.url.encodedPath).isEqualTo("/v1/devices/dev_0003/staff-pin")
            assertThat(request.jsonBody()).isEqualTo(fixtureJson("e13_request"))
        }

    @Test
    fun `E14 health maps every component`() =
        runTest {
            server.enqueue(fixtureResponse("e14_response"))

            assertThat(api.health()).isEqualTo(Outcome.Success(SampleData.health))
            assertThat(server.takeRequestNow().url.encodedPath).isEqualTo("/v1/health")
        }
}
