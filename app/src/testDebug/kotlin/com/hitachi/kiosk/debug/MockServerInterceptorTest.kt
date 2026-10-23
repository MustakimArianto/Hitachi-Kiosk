package com.hitachi.kiosk.debug

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.testing.fake.FakeScenario
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Rule
import org.junit.Test

/** The debug mock-server mode: redirect to Prism and pick the scenario's example (api-spec §4). */
class MockServerInterceptorTest {
    @get:Rule
    val serverRule = MockWebServerRule()

    private val server: MockWebServer get() = serverRule.server
    private var settings = DeveloperSettings(BackendMode.MOCK_SERVER)

    private val client by lazy {
        OkHttpClient
            .Builder()
            .addInterceptor(MockServerInterceptor(mockBaseUrl = server.url("/").toString(), settings = { settings }))
            .build()
    }

    @Test
    fun `mock server mode redirects calls to Prism`() {
        val sent = call(get("/v1/health"))

        assertThat(sent.url.encodedPath).isEqualTo("/v1/health")
        assertThat(sent.headers["Prefer"]).isNull()
    }

    @Test
    fun `scenarios select the Prism example of their endpoint`() {
        settings = DeveloperSettings(BackendMode.MOCK_SERVER, FakeScenario.GATEWAY_DOWN)
        assertThat(call(get("/v1/health")).headers["Prefer"]).isEqualTo("example=gatewayDown")

        settings = DeveloperSettings(BackendMode.MOCK_SERVER, FakeScenario.ACTIVATE_UNREGISTERED)
        assertThat(call(post("/v1/devices/activate", "{}")).headers["Prefer"]).isEqualTo("code=404")
        assertThat(call(get("/v1/health")).headers["Prefer"]).isNull()

        settings = DeveloperSettings(BackendMode.MOCK_SERVER, FakeScenario.ORDER_PRICE_CHANGED)
        assertThat(call(post("/v1/orders", "{}")).headers["Prefer"]).isEqualTo("code=409, example=priceChanged")

        settings = DeveloperSettings(BackendMode.MOCK_SERVER, FakeScenario.CARD_DECLINED)
        assertThat(call(get("/v1/orders/o/payments/p")).headers["Prefer"]).isEqualTo("example=declined")

        settings = DeveloperSettings(BackendMode.MOCK_SERVER, FakeScenario.MERCHANTS_EMPTY)
        assertThat(call(get("/v1/devices/dev_0003/merchants")).headers["Prefer"]).isEqualTo("example=empty")

        settings = DeveloperSettings(BackendMode.MOCK_SERVER, FakeScenario.MERCHANT_CLOSED)
        assertThat(call(get("/v1/devices/dev_0003/merchants")).headers["Prefer"]).isEqualTo("example=closed")
    }

    @Test
    fun `a card payment start asks Prism for the card example`() {
        val sent = call(post("/v1/orders/ord_1/payments", """{"method":"CARD"}"""))

        assertThat(sent.headers["Prefer"]).isEqualTo("example=card")
        assertThat(sent.body?.utf8()).isEqualTo("""{"method":"CARD"}""")
    }

    @Test
    fun `server_error_once fails only the first order`() {
        settings = DeveloperSettings(BackendMode.MOCK_SERVER, FakeScenario.SERVER_ERROR_ONCE)

        assertThat(call(post("/v1/orders", "{}")).headers["Prefer"]).isEqualTo("code=503")
        assertThat(call(post("/v1/orders", "{}")).headers["Prefer"]).isNull()
    }

    @Test
    fun `other modes leave the request alone`() {
        settings = DeveloperSettings(BackendMode.REAL, FakeScenario.GATEWAY_DOWN)
        val direct = server.url("/v1/health").toString()

        server.enqueue(MockResponse(code = 200))
        client.newCall(Request.Builder().url(direct).build()).execute().close()

        assertThat(server.takeRequest().headers["Prefer"]).isNull()
    }

    private fun get(path: String) = Request.Builder().url("$UNREACHABLE$path").build()

    private fun post(
        path: String,
        json: String,
    ) = Request
        .Builder()
        .url("$UNREACHABLE$path")
        .post(json.toRequestBody("application/json".toMediaType()))
        .build()

    private fun call(request: Request): RecordedRequest {
        server.enqueue(MockResponse(code = 200))
        client.newCall(request).execute().close()
        return server.takeRequest()
    }

    private companion object {
        // Never contacted: the interceptor rewrites it to the mock server.
        const val UNREACHABLE = "http://dev-api.hitachikiosk.invalid"
    }
}
