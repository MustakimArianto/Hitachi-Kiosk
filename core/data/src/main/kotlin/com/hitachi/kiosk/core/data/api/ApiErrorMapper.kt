package com.hitachi.kiosk.core.data.api

import com.hitachi.kiosk.core.data.dto.ErrorEnvelopeDto
import com.hitachi.kiosk.core.data.dto.ItemUnavailableDetailsDto
import com.hitachi.kiosk.core.data.dto.PriceChangedDetailsDto
import com.hitachi.kiosk.core.data.mapper.toDomain
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.log.KioskLog
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs one backend call and turns its transport outcome into an [Outcome] (technical-design §5.2, TD-04,
 * TR-NET-07). No exception leaves `:core:data`; coroutine cancellation is rethrown. Logs carry the endpoint ID
 * (`E1`–`E14`), the HTTP status and the error `code`, never a body (NFR-006).
 *
 * A [DomainError.Network] from any endpoint except E14 is also reported to [NetworkErrorSignal], which makes the
 * connectivity monitor re-probe E14 immediately (FR-028, technical-design §7.3).
 */
class ApiErrorMapper
    @Inject
    constructor(
        private val unauthorizedHandler: UnauthorizedHandler,
        private val networkErrors: NetworkErrorSignal = NetworkErrorSignal(),
    ) {
        /**
         * Sends [request] and maps a successful response (2xx, or any status for which [isSuccess] is true) with
         * [onSuccess]. A [onSuccess] that throws [IllegalArgumentException] (for example an unknown payment status)
         * is reported as [DomainError.Unknown].
         */
        @Suppress("TooGenericExceptionCaught") // The module boundary: nothing may escape (conventions §4).
        internal suspend fun <T, R> execute(
            endpoint: String,
            isSuccess: (Response<T>) -> Boolean = { it.isSuccessful },
            request: suspend () -> Response<T>,
            onSuccess: (Response<T>) -> R,
        ): Outcome<R> =
            try {
                val response = request()
                if (isSuccess(
                        response,
                    )
                ) {
                    Outcome.Success(onSuccess(response))
                } else {
                    Outcome.Failure(httpError(endpoint, response))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InterruptedIOException) {
                // SocketTimeoutException and OkHttp's call timeout.
                KioskLog.w(
                    "api_network_error",
                    "endpoint" to endpoint,
                    "timeout" to true,
                    "type" to e.javaClass.simpleName,
                )
                reportNetworkError(endpoint)
                Outcome.Failure(DomainError.Network(isTimeout = true))
            } catch (e: IOException) {
                KioskLog.w(
                    "api_network_error",
                    "endpoint" to endpoint,
                    "timeout" to false,
                    "type" to e.javaClass.simpleName,
                )
                reportNetworkError(endpoint)
                Outcome.Failure(DomainError.Network(isTimeout = false))
            } catch (e: Exception) {
                // SerializationException, IllegalArgumentException from a mapper, or anything unexpected. The exception
                // message can quote the body, so only its type is logged.
                KioskLog.w("api_unparsable_response", "endpoint" to endpoint, "type" to e.javaClass.simpleName)
                Outcome.Failure(DomainError.Unknown)
            }

        /** E14 is the probe itself, so its failure must not trigger another probe. */
        private fun reportNetworkError(endpoint: String) {
            if (endpoint != HEALTH_ENDPOINT) networkErrors.report()
        }

        private suspend fun httpError(
            endpoint: String,
            response: Response<*>,
        ): DomainError {
            val status = response.code()
            val envelope = parseEnvelope(response)
            val code = envelope?.code
            KioskLog.w("api_http_error", "endpoint" to endpoint, "status" to status, "code" to code)
            return when {
                status == HTTP_UNAUTHORIZED -> {
                    unauthorizedHandler.onUnauthorized()
                    DomainError.Unauthorized
                }

                status == HTTP_CONFLICT && code == CODE_PRICE_CHANGED -> {
                    priceChanged(endpoint, envelope?.details)
                }

                status == HTTP_CONFLICT && code == CODE_ITEM_UNAVAILABLE -> {
                    itemUnavailable(endpoint, envelope?.details)
                }

                status == HTTP_CONFLICT -> {
                    DomainError.Conflict(code ?: "HTTP_$status")
                }

                else -> {
                    DomainError.Server(code = code ?: "HTTP_$status", httpStatus = status)
                }
            }
        }

        /** The error envelope (api-spec §2.4), or `null` when the body is empty or not an envelope. */
        private fun parseEnvelope(response: Response<*>): ErrorEnvelopeDto? =
            try {
                response.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let {
                    KioskJson.decodeFromString<ErrorEnvelopeDto>(it)
                }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IOException) {
                null
            }

        private fun priceChanged(
            endpoint: String,
            details: JsonObject?,
        ): DomainError.PriceChanged {
            val parsed = decodeDetails<PriceChangedDetailsDto>(endpoint, details) ?: PriceChangedDetailsDto()
            return DomainError.PriceChanged(
                menuVersion = parsed.menuVersion,
                configVersion = parsed.configVersion,
                serverTotals = parsed.serverTotals?.toDomain(),
            )
        }

        private fun itemUnavailable(
            endpoint: String,
            details: JsonObject?,
        ): DomainError.ItemUnavailable {
            val parsed = decodeDetails<ItemUnavailableDetailsDto>(endpoint, details) ?: ItemUnavailableDetailsDto()
            return DomainError.ItemUnavailable(lineIds = parsed.lineIds, productIds = parsed.productIds)
        }

        private inline fun <reified D> decodeDetails(
            endpoint: String,
            details: JsonObject?,
        ): D? =
            try {
                details?.let { KioskJson.decodeFromJsonElement<D>(it) }
            } catch (e: SerializationException) {
                KioskLog.w("api_unparsable_error_details", "endpoint" to endpoint, "type" to e.javaClass.simpleName)
                null
            } catch (e: IllegalArgumentException) {
                KioskLog.w("api_unparsable_error_details", "endpoint" to endpoint, "type" to e.javaClass.simpleName)
                null
            }

        private companion object {
            const val HEALTH_ENDPOINT = "E14"
            const val HTTP_UNAUTHORIZED = 401
            const val HTTP_CONFLICT = 409
            const val CODE_PRICE_CHANGED = "PRICE_CHANGED"
            const val CODE_ITEM_UNAVAILABLE = "ITEM_UNAVAILABLE"
        }
    }
