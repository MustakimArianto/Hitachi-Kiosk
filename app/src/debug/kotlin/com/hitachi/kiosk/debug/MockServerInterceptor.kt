package com.hitachi.kiosk.debug

import com.hitachi.kiosk.core.testing.fake.FakeScenario
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug only: in mock-server mode, redirects every call to Prism and adds the `Prefer` header that selects the
 * scenario's example (api-spec §4). Scenarios Prism cannot express (`qris_expire_fast`, `slow_network`) get no header;
 * use the fakes for them.
 */
class MockServerInterceptor(
    private val mockBaseUrl: String,
    private val settings: () -> DeveloperSettings,
) : Interceptor {
    private val serverErrorSent = AtomicBoolean(false)
    private var lastScenario: FakeScenario? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val current = settings()
        val original = chain.request()
        if (current.backendMode != BackendMode.MOCK_SERVER) return chain.proceed(original)

        if (current.scenario != lastScenario) {
            lastScenario = current.scenario
            serverErrorSent.set(false)
        }
        val builder = original.newBuilder().url(original.url.rebasedOn(mockBaseUrl.toHttpUrl()))
        preferHeader(current.scenario, original)?.let { builder.header("Prefer", it) }
        return chain.proceed(builder.build())
    }

    private fun preferHeader(
        scenario: FakeScenario,
        request: Request,
    ): String? {
        val endpoint = Endpoint.of(request) ?: return null
        if (endpoint == Endpoint.CREATE_ORDER && scenario == FakeScenario.SERVER_ERROR_ONCE) {
            return if (serverErrorSent.compareAndSet(false, true)) "code=503" else null
        }
        return PREFER[scenario to endpoint]
            ?: "example=card".takeIf { endpoint == Endpoint.START_PAYMENT && request.bodyText().contains("\"CARD\"") }
    }

    internal enum class Endpoint(
        val method: String,
        val path: Regex,
    ) {
        ACTIVATE("POST", Regex("/v1/devices/activate")),
        MERCHANTS("GET", Regex("/v1/devices/[^/]+/merchants")),
        CREATE_ORDER("POST", Regex("/v1/orders")),
        START_PAYMENT("POST", Regex("/v1/orders/[^/]+/payments")),
        PAYMENT_STATUS("GET", Regex("/v1/orders/[^/]+/payments/[^/]+")),
        HEALTH("GET", Regex("/v1/health")),
        ;

        companion object {
            fun of(request: Request): Endpoint? =
                entries.firstOrNull { it.method == request.method && it.path.matches(request.url.encodedPath) }
        }
    }
}

/** The Prism example each scenario selects on its endpoint (api-spec §4, last column). */
private val PREFER: Map<Pair<FakeScenario, MockServerInterceptor.Endpoint>, String> =
    mapOf(
        (FakeScenario.ACTIVATE_UNREGISTERED to MockServerInterceptor.Endpoint.ACTIVATE) to "code=404",
        (FakeScenario.ACTIVATE_DISABLED to MockServerInterceptor.Endpoint.ACTIVATE) to "code=403",
        (FakeScenario.MERCHANTS_EMPTY to MockServerInterceptor.Endpoint.MERCHANTS) to "example=empty",
        (FakeScenario.MERCHANT_CLOSED to MockServerInterceptor.Endpoint.MERCHANTS) to "example=closed",
        (FakeScenario.ORDER_PRICE_CHANGED to MockServerInterceptor.Endpoint.CREATE_ORDER) to
            "code=409, example=priceChanged",
        (FakeScenario.ORDER_ITEM_UNAVAILABLE to MockServerInterceptor.Endpoint.CREATE_ORDER) to
            "code=409, example=itemUnavailable",
        (FakeScenario.QRIS_PAID_AFTER_5S to MockServerInterceptor.Endpoint.PAYMENT_STATUS) to "example=paid",
        (FakeScenario.QRIS_CANCELLED to MockServerInterceptor.Endpoint.PAYMENT_STATUS) to "example=cancelledByPayer",
        (FakeScenario.CARD_DECLINED to MockServerInterceptor.Endpoint.PAYMENT_STATUS) to "example=declined",
        (FakeScenario.CARD_TIMEOUT to MockServerInterceptor.Endpoint.PAYMENT_STATUS) to "example=terminalTimeout",
        (FakeScenario.GATEWAY_DOWN to MockServerInterceptor.Endpoint.HEALTH) to "example=gatewayDown",
    )

private fun HttpUrl.rebasedOn(base: HttpUrl): HttpUrl =
    newBuilder()
        .scheme(base.scheme)
        .host(base.host)
        .port(base.port)
        .build()

private fun Request.bodyText(): String {
    val body = body ?: return ""
    return Buffer().also(body::writeTo).readUtf8()
}
