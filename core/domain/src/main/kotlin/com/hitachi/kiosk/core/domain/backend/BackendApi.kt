package com.hitachi.kiosk.core.domain.backend

import com.hitachi.kiosk.core.domain.Outcome
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

/**
 * The kiosk backend, endpoints E1–E14 of `docs/api/api-spec.md`, in domain types (NFR-012, DEV-005).
 *
 * Calls do not retry: repositories add [com.hitachi.kiosk.core.domain.retryWithBackoff] where the endpoint allows
 * it (technical-design §5.3). Calls that take an [IdempotencyKey] must reuse the same key for every retry of the
 * same request.
 */
@Suppress("TooManyFunctions") // One function per endpoint keeps the contract readable.
interface BackendApi {
    /** E1: activate the kiosk by serial number (FR-003). */
    suspend fun activate(request: ActivationRequest): Outcome<Activation>

    /** E2: merchants this kiosk may serve (FR-004). */
    suspend fun merchants(deviceId: String): Outcome<List<Merchant>>

    /** E3: bind the kiosk to a live merchant (FR-004). */
    suspend fun bind(
        deviceId: String,
        merchantId: String,
        key: IdempotencyKey,
    ): Outcome<Binding>

    /** E4: unbind (Log out) (FR-004). */
    suspend fun unbind(deviceId: String): Outcome<Unit>

    /** E5: merchant config, conditional on [etag] (FR-028). */
    suspend fun config(
        merchantId: String,
        etag: String?,
    ): Outcome<Conditional<MerchantConfig>>

    /** E6: full menu, conditional on [etag] (FR-028). */
    suspend fun menu(
        merchantId: String,
        etag: String?,
    ): Outcome<Conditional<Menu>>

    /** E7: create an order. [key] is required and identifies one request body (BR-08, FR-016). */
    suspend fun createOrder(
        request: OrderRequest,
        key: IdempotencyKey,
    ): Outcome<CreatedOrder>

    /** E8: abandon an unpaid order (FR-019). */
    suspend fun cancelOrder(
        orderId: String,
        reason: CancelOrderReason,
        key: IdempotencyKey,
    ): Outcome<CancelledOrder>

    /** E9: start a QRIS or card payment. [key] is required, one per attempt (FR-017, FR-018). */
    suspend fun startPayment(
        orderId: String,
        method: PaymentMethod,
        qrExpirySeconds: Int?,
        key: IdempotencyKey,
    ): Outcome<StartedPayment>

    /** E10: payment status, polled every 2 s (FR-017). */
    suspend fun paymentStatus(
        orderId: String,
        paymentId: String,
    ): Outcome<PaymentState>

    /** E11: cancel a pending payment (FR-019). */
    suspend fun cancelPayment(
        orderId: String,
        paymentId: String,
        reason: CancelPaymentReason,
        key: IdempotencyKey,
    ): Outcome<PaymentUpdate>

    /** E12: relay the EDC terminal result, only if the terminal reports to the kiosk (OQ-03). */
    suspend fun relayTerminalResult(
        orderId: String,
        paymentId: String,
        result: TerminalResult,
    ): Outcome<PaymentUpdate>

    /** E13: change the staff PIN; returns the new hash (FR-027). Never log the PINs (NFR-006). */
    suspend fun changeStaffPin(
        deviceId: String,
        currentPin: String,
        newPin: String,
        key: IdempotencyKey,
    ): Outcome<StaffPinHash>

    /** E14: backend and gateway reachability (FR-027, FR-028). */
    suspend fun health(): Outcome<Health>
}
