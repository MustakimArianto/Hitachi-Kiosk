package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.error.DomainError
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
import com.hitachi.kiosk.core.domain.time.Clock
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A scriptable [BackendApi] (NFR-012, DEV-005). Every endpoint has its own [Script], preset to the api-spec happy
 * path ([SampleData]). Tests script outcomes and delays per endpoint, or apply a [FakeScenario]. Every call is recorded
 * in [calls].
 *
 * When a [clock] is given, QRIS payments expire relative to it instead of at the fixed sample time, so countdowns are
 * realistic on a device (api-spec §4).
 */
@Suppress("TooManyFunctions")
class FakeBackendApi(
    private val clock: Clock? = null,
) : BackendApi {
    /** Endpoint IDs of api-spec §1. */
    enum class Endpoint { E1, E2, E3, E4, E5, E6, E7, E8, E9, E10, E11, E12, E13, E14 }

    /** One recorded call: the endpoint, its arguments and the idempotency key, if any. */
    data class Call(
        val endpoint: Endpoint,
        val args: List<Any?>,
        val key: IdempotencyKey? = null,
    )

    val activate = Script<Activation>(Outcome.Success(SampleData.activation))
    val merchants = Script<List<Merchant>>(Outcome.Success(SampleData.merchants))
    val bind = Script<Binding>(Outcome.Success(SampleData.binding))
    val unbind = Script<Unit>(Outcome.Success(Unit))
    val config =
        Script<Conditional<MerchantConfig>>(Outcome.Success(Conditional.Modified(SampleData.config, "\"cfg-42\"")))
    val menu = Script<Conditional<Menu>>(Outcome.Success(Conditional.Modified(SampleData.menu, "\"menu-118\"")))
    val createOrder = Script<CreatedOrder>(Outcome.Success(SampleData.createdOrder))
    val cancelOrder = Script<CancelledOrder>(Outcome.Success(SampleData.cancelledOrder))
    val startQris = Script<StartedPayment>(Outcome.Success(SampleData.qrisPayment))
    val startCard = Script<StartedPayment>(Outcome.Success(SampleData.cardPayment))
    val paymentStatus = Script<PaymentState>(Outcome.Success(SampleData.paymentPending))
    val cancelPayment = Script<PaymentUpdate>(Outcome.Success(SampleData.paymentCancelled))
    val relayTerminalResult = Script<PaymentUpdate>(Outcome.Success(SampleData.terminalRelayed))
    val changeStaffPin = Script<StaffPinHash>(Outcome.Success(SampleData.newStaffPin))
    val health = Script<Health>(Outcome.Success(SampleData.health))

    /** Added to every answer, for the `slow_network` scenario. */
    var extraDelay: Duration = Duration.ZERO

    private val recorded = mutableListOf<Call>()

    val calls: List<Call> get() = synchronized(recorded) { recorded.toList() }

    fun callsTo(endpoint: Endpoint): List<Call> = calls.filter { it.endpoint == endpoint }

    /** Applies one of the api-spec §4 mock scenarios on top of the current scripts. */
    fun apply(scenario: FakeScenario) {
        scenario.applyTo(this)
    }

    override suspend fun activate(request: ActivationRequest): Outcome<Activation> =
        answer(Endpoint.E1, listOf(request), null, activate)

    override suspend fun merchants(deviceId: String): Outcome<List<Merchant>> =
        answer(Endpoint.E2, listOf(deviceId), null, merchants)

    override suspend fun bind(
        deviceId: String,
        merchantId: String,
        key: IdempotencyKey,
    ): Outcome<Binding> = answer(Endpoint.E3, listOf(deviceId, merchantId), key, bind)

    override suspend fun unbind(deviceId: String): Outcome<Unit> = answer(Endpoint.E4, listOf(deviceId), null, unbind)

    override suspend fun config(
        merchantId: String,
        etag: String?,
    ): Outcome<Conditional<MerchantConfig>> = answer(Endpoint.E5, listOf(merchantId, etag), null, config)

    override suspend fun menu(
        merchantId: String,
        etag: String?,
    ): Outcome<Conditional<Menu>> = answer(Endpoint.E6, listOf(merchantId, etag), null, menu)

    override suspend fun createOrder(
        request: OrderRequest,
        key: IdempotencyKey,
    ): Outcome<CreatedOrder> = answer(Endpoint.E7, listOf(request), key, createOrder)

    override suspend fun cancelOrder(
        orderId: String,
        reason: CancelOrderReason,
        key: IdempotencyKey,
    ): Outcome<CancelledOrder> = answer(Endpoint.E8, listOf(orderId, reason), key, cancelOrder)

    override suspend fun startPayment(
        orderId: String,
        method: PaymentMethod,
        qrExpirySeconds: Int?,
        key: IdempotencyKey,
    ): Outcome<StartedPayment> {
        val script =
            when (method) {
                PaymentMethod.QRIS -> startQris
                PaymentMethod.CARD -> startCard
            }
        val outcome = answer(Endpoint.E9, listOf(orderId, method, qrExpirySeconds), key, script)
        return outcome.withExpiryFromClock(qrExpirySeconds)
    }

    override suspend fun paymentStatus(
        orderId: String,
        paymentId: String,
    ): Outcome<PaymentState> = answer(Endpoint.E10, listOf(orderId, paymentId), null, paymentStatus)

    override suspend fun cancelPayment(
        orderId: String,
        paymentId: String,
        reason: CancelPaymentReason,
        key: IdempotencyKey,
    ): Outcome<PaymentUpdate> = answer(Endpoint.E11, listOf(orderId, paymentId, reason), key, cancelPayment)

    override suspend fun relayTerminalResult(
        orderId: String,
        paymentId: String,
        result: TerminalResult,
    ): Outcome<PaymentUpdate> = answer(Endpoint.E12, listOf(orderId, paymentId, result), null, relayTerminalResult)

    override suspend fun changeStaffPin(
        deviceId: String,
        currentPin: String,
        newPin: String,
        key: IdempotencyKey,
    ): Outcome<StaffPinHash> {
        // The PINs are not recorded: they must never appear in test output either (NFR-006).
        val outcome = answer(Endpoint.E13, listOf(deviceId), key, changeStaffPin)
        return if (outcome is Outcome.Success && currentPin != SampleData.STAFF_PIN) {
            Outcome.Failure(DomainError.Conflict(code = "PIN_INCORRECT"))
        } else {
            outcome
        }
    }

    override suspend fun health(): Outcome<Health> = answer(Endpoint.E14, emptyList(), null, health)

    private suspend fun <T> answer(
        endpoint: Endpoint,
        args: List<Any?>,
        key: IdempotencyKey?,
        script: Script<T>,
    ): Outcome<T> {
        synchronized(recorded) { recorded += Call(endpoint, args, key) }
        return script.next(extraDelay)
    }

    private fun Outcome<StartedPayment>.withExpiryFromClock(qrExpirySeconds: Int?): Outcome<StartedPayment> {
        val now = clock?.now() ?: return this
        val payment = (this as? Outcome.Success)?.value as? StartedPayment.Qris ?: return this
        val seconds = qrExpirySeconds ?: DEFAULT_QR_EXPIRY_SECONDS
        return Outcome.Success(payment.copy(createdAt = now, expiresAt = now + seconds.seconds))
    }

    private companion object {
        const val DEFAULT_QR_EXPIRY_SECONDS = 300
    }
}
