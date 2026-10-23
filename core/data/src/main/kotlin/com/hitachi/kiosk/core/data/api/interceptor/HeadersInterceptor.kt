package com.hitachi.kiosk.core.data.api.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** Adds `X-App-Version` and `Accept-Language: en` to every call (api-spec §2.2; English-only UI, D-29). */
internal class HeadersInterceptor(
    private val appVersion: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain
                .request()
                .newBuilder()
                .header(APP_VERSION, appVersion)
                .header(ACCEPT_LANGUAGE, LANGUAGE)
                .build(),
        )

    companion object {
        const val APP_VERSION = "X-App-Version"
        const val ACCEPT_LANGUAGE = "Accept-Language"
        const val LANGUAGE = "en"
    }
}
