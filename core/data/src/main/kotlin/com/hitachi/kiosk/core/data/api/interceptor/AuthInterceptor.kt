package com.hitachi.kiosk.core.data.api.interceptor

import com.hitachi.kiosk.core.data.api.DeviceCredentialsProvider
import com.hitachi.kiosk.core.data.api.Unauthenticated
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

/**
 * Adds `Authorization: Bearer <token>` to every call except the [Unauthenticated] ones (E1, E14), and
 * `X-Device-Id` to every call once the kiosk is activated, E14 included (api-spec §2.2, technical-design §5.1).
 */
internal class AuthInterceptor(
    private val credentials: DeviceCredentialsProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val current = credentials.current() ?: return chain.proceed(request)
        val unauthenticated =
            request
                .tag(Invocation::class.java)
                ?.method()
                ?.isAnnotationPresent(Unauthenticated::class.java) == true
        val builder = request.newBuilder().header(DEVICE_ID, current.deviceId)
        if (!unauthenticated) builder.header(AUTHORIZATION, "Bearer ${current.deviceToken}")
        return chain.proceed(builder.build())
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
        const val DEVICE_ID = "X-Device-Id"
    }
}
