package com.hitachi.kiosk.debug

import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.map
import com.hitachi.kiosk.core.domain.model.ActivationRequest
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.TerminalResult
import com.hitachi.kiosk.core.domain.time.Clock
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeScenario

/**
 * Debug only: sends each call to the real backend client or to a [FakeBackendApi], following the Developer options
 * at the time of the call (DEV-009, technical-design §4.3). In mock-server mode the real client is used and
 * [MockServerInterceptor] redirects it to Prism.
 */
@Suppress("TooManyFunctions")
class SwitchableBackendApi(
    private val real: BackendApi,
    private val settings: () -> DeveloperSettings,
    private val clock: Clock,
) : BackendApi {
    private var fake: Pair<FakeScenario, FakeBackendApi>? = null

    /** The implementation for the current settings. */
    @Synchronized
    fun target(): BackendApi {
        val current = settings()
        if (current.backendMode != BackendMode.FAKES) return real
        fake?.let { (scenario, api) -> if (scenario == current.scenario) return api }
        // A new scenario starts from a fresh fake, so one-shot answers (server_error_once) run again.
        return FakeBackendApi(clock).apply { apply(current.scenario) }.also { fake = current.scenario to it }
    }

    /** The in-app fake that answers now, or `null` for the real client and the mock server (the simulate bar needs it). */
    fun fakeOrNull(): FakeBackendApi? =
        if (settings().backendMode == BackendMode.FAKES) target() as? FakeBackendApi else null

    override suspend fun activate(request: ActivationRequest) = target().activate(request)

    override suspend fun merchants(deviceId: String) = target().merchants(deviceId)

    override suspend fun bind(
        deviceId: String,
        merchantId: String,
        key: IdempotencyKey,
    ) = target().bind(deviceId, merchantId, key)

    override suspend fun unbind(deviceId: String) = target().unbind(deviceId)

    override suspend fun config(
        merchantId: String,
        etag: String?,
    ) = target().config(merchantId, etag).map { it.mapModified(DemoImages::apply) }

    override suspend fun menu(
        merchantId: String,
        etag: String?,
    ) = target().menu(merchantId, etag).map { it.mapModified(DemoImages::apply) }

    override suspend fun createOrder(
        request: OrderRequest,
        key: IdempotencyKey,
    ) = target().createOrder(request, key)

    override suspend fun cancelOrder(
        orderId: String,
        reason: CancelOrderReason,
        key: IdempotencyKey,
    ) = target().cancelOrder(orderId, reason, key)

    override suspend fun startPayment(
        orderId: String,
        method: PaymentMethod,
        qrExpirySeconds: Int?,
        key: IdempotencyKey,
    ) = target().startPayment(orderId, method, qrExpirySeconds, key)

    override suspend fun paymentStatus(
        orderId: String,
        paymentId: String,
    ) = target().paymentStatus(orderId, paymentId)

    override suspend fun cancelPayment(
        orderId: String,
        paymentId: String,
        reason: CancelPaymentReason,
        key: IdempotencyKey,
    ) = target().cancelPayment(orderId, paymentId, reason, key)

    override suspend fun relayTerminalResult(
        orderId: String,
        paymentId: String,
        result: TerminalResult,
    ) = target().relayTerminalResult(orderId, paymentId, result)

    override suspend fun changeStaffPin(
        deviceId: String,
        currentPin: String,
        newPin: String,
        key: IdempotencyKey,
    ) = target().changeStaffPin(deviceId, currentPin, newPin, key)

    override suspend fun health() = target().health()
}

/** The same answer, with the new value of a `Modified` one passed through [transform]; a 304 stays a 304. */
private inline fun <T> Conditional<T>.mapModified(transform: (T) -> T): Conditional<T> =
    when (this) {
        is Conditional.Modified -> copy(value = transform(value))
        Conditional.NotModified -> this
    }
