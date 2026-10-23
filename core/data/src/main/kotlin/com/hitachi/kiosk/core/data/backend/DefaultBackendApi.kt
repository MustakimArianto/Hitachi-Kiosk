package com.hitachi.kiosk.core.data.backend

import com.hitachi.kiosk.core.data.api.ApiErrorMapper
import com.hitachi.kiosk.core.data.api.KioskApi
import com.hitachi.kiosk.core.data.api.KioskBackend
import com.hitachi.kiosk.core.data.api.toRequestKey
import com.hitachi.kiosk.core.data.dto.BindRequestDto
import com.hitachi.kiosk.core.data.dto.CancelOrderRequestDto
import com.hitachi.kiosk.core.data.dto.CancelPaymentRequestDto
import com.hitachi.kiosk.core.data.dto.ChangeStaffPinRequestDto
import com.hitachi.kiosk.core.data.dto.StartPaymentRequestDto
import com.hitachi.kiosk.core.data.mapper.toDomain
import com.hitachi.kiosk.core.data.mapper.toDto
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.Activation
import com.hitachi.kiosk.core.domain.model.ActivationRequest
import com.hitachi.kiosk.core.domain.model.Binding
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.domain.model.CreatedOrder
import com.hitachi.kiosk.core.domain.model.Health
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Merchant
import com.hitachi.kiosk.core.domain.model.MerchantConfig
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.StaffPinHash
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.domain.model.TerminalResult
import retrofit2.Response
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real [BackendApi] over HTTP (DEV-007, architecture §7). It does not retry; repositories do (technical-design
 * §5.3). Not bound to [BackendApi] here: `:app` binds it, so the debug build can wrap it (DEV-009).
 */
@Singleton
@Suppress("TooManyFunctions") // One function per endpoint.
class DefaultBackendApi internal constructor(
    private val api: KioskApi,
    private val errors: ApiErrorMapper,
) : BackendApi {
    @Inject
    constructor(
        @KioskBackend retrofit: Retrofit,
        errors: ApiErrorMapper,
    ) : this(retrofit.create(KioskApi::class.java), errors)

    override suspend fun activate(request: ActivationRequest): Outcome<Activation> =
        errors.execute("E1", request = { api.activate(request.toDto()) }) { it.requireBody().toDomain() }

    override suspend fun merchants(deviceId: String): Outcome<List<Merchant>> =
        errors.execute("E2", request = { api.merchants(deviceId) }) { response ->
            response.requireBody().merchants.map { it.toDomain() }
        }

    override suspend fun bind(
        deviceId: String,
        merchantId: String,
        key: IdempotencyKey,
    ): Outcome<Binding> =
        errors.execute("E3", request = { api.bind(deviceId, BindRequestDto(merchantId), key.toRequestKey()) }) {
            it.requireBody().toDomain()
        }

    override suspend fun unbind(deviceId: String): Outcome<Unit> =
        errors.execute("E4", request = { api.unbind(deviceId) }) { }

    override suspend fun config(
        merchantId: String,
        etag: String?,
    ): Outcome<Conditional<MerchantConfig>> =
        errors.execute("E5", isSuccess = ::isSuccessOrNotModified, request = { api.config(merchantId, etag) }) {
            it.toConditional { dto -> dto.toDomain() }
        }

    override suspend fun menu(
        merchantId: String,
        etag: String?,
    ): Outcome<Conditional<Menu>> =
        errors.execute("E6", isSuccess = ::isSuccessOrNotModified, request = { api.menu(merchantId, etag) }) {
            it.toConditional { dto -> dto.toDomain() }
        }

    override suspend fun createOrder(
        request: OrderRequest,
        key: IdempotencyKey,
    ): Outcome<CreatedOrder> =
        errors.execute("E7", request = { api.createOrder(request.toDto(), key.toRequestKey()) }) {
            it.requireBody().toDomain()
        }

    override suspend fun cancelOrder(
        orderId: String,
        reason: CancelOrderReason,
        key: IdempotencyKey,
    ): Outcome<CancelledOrder> =
        errors.execute(
            "E8",
            request = { api.cancelOrder(orderId, CancelOrderRequestDto(reason.name), key.toRequestKey()) },
        ) { it.requireBody().toDomain() }

    override suspend fun startPayment(
        orderId: String,
        method: PaymentMethod,
        qrExpirySeconds: Int?,
        key: IdempotencyKey,
    ): Outcome<StartedPayment> {
        val body =
            StartPaymentRequestDto(
                method = method.name,
                qrExpirySeconds = qrExpirySeconds.takeIf { method == PaymentMethod.QRIS },
            )
        return errors.execute("E9", request = { api.startPayment(orderId, body, key.toRequestKey()) }) {
            it.requireBody().toDomain()
        }
    }

    override suspend fun paymentStatus(
        orderId: String,
        paymentId: String,
    ): Outcome<PaymentState> =
        errors.execute("E10", request = { api.paymentStatus(orderId, paymentId) }) { it.requireBody().toDomain() }

    override suspend fun cancelPayment(
        orderId: String,
        paymentId: String,
        reason: CancelPaymentReason,
        key: IdempotencyKey,
    ): Outcome<PaymentUpdate> =
        errors.execute(
            "E11",
            request = {
                api.cancelPayment(orderId, paymentId, CancelPaymentRequestDto(reason.name), key.toRequestKey())
            },
        ) { it.requireBody().toDomain() }

    override suspend fun relayTerminalResult(
        orderId: String,
        paymentId: String,
        result: TerminalResult,
    ): Outcome<PaymentUpdate> =
        errors.execute("E12", request = { api.relayTerminalResult(orderId, paymentId, result.toDto()) }) {
            it.requireBody().toDomain()
        }

    override suspend fun changeStaffPin(
        deviceId: String,
        currentPin: String,
        newPin: String,
        key: IdempotencyKey,
    ): Outcome<StaffPinHash> =
        errors.execute(
            "E13",
            request = {
                api.changeStaffPin(
                    deviceId,
                    ChangeStaffPinRequestDto(currentPin, newPin),
                    key.toRequestKey(),
                )
            },
        ) { it.requireBody().staffPin.toDomain() }

    override suspend fun health(): Outcome<Health> =
        errors.execute("E14", request = { api.health() }) { it.requireBody().toDomain() }

    private fun <T> isSuccessOrNotModified(response: Response<T>): Boolean =
        response.isSuccessful || response.code() == HTTP_NOT_MODIFIED

    private inline fun <T, R> Response<T>.toConditional(map: (T) -> R): Conditional<R> =
        if (code() == HTTP_NOT_MODIFIED) {
            Conditional.NotModified
        } else {
            Conditional.Modified(value = map(requireBody()), etag = headers()[ETAG])
        }

    private fun <T> Response<T>.requireBody(): T = requireNotNull(body()) { "Empty body" }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val ETAG = "ETag"
    }
}
