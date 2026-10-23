package com.hitachi.kiosk.core.data.api.interceptor

import com.hitachi.kiosk.core.data.api.RequestIdempotencyKey
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Copies the caller's [RequestIdempotencyKey] tag into the `Idempotency-Key` header (api-spec §2.2). It never
 * generates a key, so every retry of a request reuses the caller's key (technical-design §5.1, §5.3, BR-08).
 */
internal class IdempotencyKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = request.tag(RequestIdempotencyKey::class.java) ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER, key.value).build())
    }

    companion object {
        const val HEADER = "Idempotency-Key"
    }
}
