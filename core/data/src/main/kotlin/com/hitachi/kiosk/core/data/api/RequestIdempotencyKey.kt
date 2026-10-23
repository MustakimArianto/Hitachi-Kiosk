package com.hitachi.kiosk.core.data.api

import com.hitachi.kiosk.core.domain.id.IdempotencyKey

/**
 * The `Idempotency-Key` of one request, carried as an OkHttp request tag (technical-design §5.1). A plain class,
 * because Retrofit keys tags by the parameter's class and a value class would be erased to `String`.
 */
internal data class RequestIdempotencyKey(
    val value: String,
)

internal fun IdempotencyKey.toRequestKey(): RequestIdempotencyKey = RequestIdempotencyKey(value)
