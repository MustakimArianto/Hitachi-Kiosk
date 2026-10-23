package com.hitachi.kiosk.core.data.api

import com.hitachi.kiosk.core.data.dto.ActivationDto
import com.hitachi.kiosk.core.data.dto.ActivationRequestDto
import com.hitachi.kiosk.core.data.dto.BindRequestDto
import com.hitachi.kiosk.core.data.dto.BindingDto
import com.hitachi.kiosk.core.data.dto.CancelOrderRequestDto
import com.hitachi.kiosk.core.data.dto.CancelPaymentRequestDto
import com.hitachi.kiosk.core.data.dto.CancelledOrderDto
import com.hitachi.kiosk.core.data.dto.ChangeStaffPinRequestDto
import com.hitachi.kiosk.core.data.dto.CreatedOrderDto
import com.hitachi.kiosk.core.data.dto.HealthDto
import com.hitachi.kiosk.core.data.dto.MenuDto
import com.hitachi.kiosk.core.data.dto.MerchantConfigDto
import com.hitachi.kiosk.core.data.dto.MerchantsDto
import com.hitachi.kiosk.core.data.dto.OrderRequestDto
import com.hitachi.kiosk.core.data.dto.PaymentStateDto
import com.hitachi.kiosk.core.data.dto.PaymentUpdateDto
import com.hitachi.kiosk.core.data.dto.StaffPinResponseDto
import com.hitachi.kiosk.core.data.dto.StartPaymentRequestDto
import com.hitachi.kiosk.core.data.dto.StartedPaymentDto
import com.hitachi.kiosk.core.data.dto.TerminalResultDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Tag

/**
 * The backend contract E1–E14 of `docs/api/api-spec.md` (architecture §7, DEV-007). Paths are relative, so the base
 * URL may carry a path prefix. Mutating calls take their `Idempotency-Key` as a request tag, copied to the header
 * by [com.hitachi.kiosk.core.data.api.interceptor.IdempotencyKeyInterceptor].
 */
@Suppress("TooManyFunctions") // One function per endpoint.
internal interface KioskApi {
    /** E1. */
    @Unauthenticated
    @POST("v1/devices/activate")
    suspend fun activate(
        @Body body: ActivationRequestDto,
    ): Response<ActivationDto>

    /** E2. */
    @GET("v1/devices/{deviceId}/merchants")
    suspend fun merchants(
        @Path("deviceId") deviceId: String,
    ): Response<MerchantsDto>

    /** E3. */
    @PUT("v1/devices/{deviceId}/binding")
    suspend fun bind(
        @Path("deviceId") deviceId: String,
        @Body body: BindRequestDto,
        @Tag key: RequestIdempotencyKey?,
    ): Response<BindingDto>

    /** E4: 204, no body. */
    @DELETE("v1/devices/{deviceId}/binding")
    suspend fun unbind(
        @Path("deviceId") deviceId: String,
    ): Response<Unit>

    /** E5: 304 when [etag] still matches. */
    @GET("v1/merchants/{merchantId}/config")
    suspend fun config(
        @Path("merchantId") merchantId: String,
        @Header("If-None-Match") etag: String?,
    ): Response<MerchantConfigDto>

    /** E6: 304 when [etag] still matches. */
    @GET("v1/merchants/{merchantId}/menu")
    suspend fun menu(
        @Path("merchantId") merchantId: String,
        @Header("If-None-Match") etag: String?,
    ): Response<MenuDto>

    /** E7: the key is required. */
    @POST("v1/orders")
    suspend fun createOrder(
        @Body body: OrderRequestDto,
        @Tag key: RequestIdempotencyKey?,
    ): Response<CreatedOrderDto>

    /** E8. */
    @POST("v1/orders/{orderId}/cancel")
    suspend fun cancelOrder(
        @Path("orderId") orderId: String,
        @Body body: CancelOrderRequestDto,
        @Tag key: RequestIdempotencyKey?,
    ): Response<CancelledOrderDto>

    /** E9: the key is required. */
    @POST("v1/orders/{orderId}/payments")
    suspend fun startPayment(
        @Path("orderId") orderId: String,
        @Body body: StartPaymentRequestDto,
        @Tag key: RequestIdempotencyKey?,
    ): Response<StartedPaymentDto>

    /** E10. */
    @GET("v1/orders/{orderId}/payments/{paymentId}")
    suspend fun paymentStatus(
        @Path("orderId") orderId: String,
        @Path("paymentId") paymentId: String,
    ): Response<PaymentStateDto>

    /** E11. */
    @POST("v1/orders/{orderId}/payments/{paymentId}/cancel")
    suspend fun cancelPayment(
        @Path("orderId") orderId: String,
        @Path("paymentId") paymentId: String,
        @Body body: CancelPaymentRequestDto,
        @Tag key: RequestIdempotencyKey?,
    ): Response<PaymentUpdateDto>

    /** E12: no key (technical-design §5.3). */
    @POST("v1/orders/{orderId}/payments/{paymentId}/terminal-result")
    suspend fun relayTerminalResult(
        @Path("orderId") orderId: String,
        @Path("paymentId") paymentId: String,
        @Body body: TerminalResultDto,
    ): Response<PaymentUpdateDto>

    /** E13. */
    @PUT("v1/devices/{deviceId}/staff-pin")
    suspend fun changeStaffPin(
        @Path("deviceId") deviceId: String,
        @Body body: ChangeStaffPinRequestDto,
        @Tag key: RequestIdempotencyKey?,
    ): Response<StaffPinResponseDto>

    /** E14. */
    @Unauthenticated
    @GET("v1/health")
    suspend fun health(): Response<HealthDto>
}
