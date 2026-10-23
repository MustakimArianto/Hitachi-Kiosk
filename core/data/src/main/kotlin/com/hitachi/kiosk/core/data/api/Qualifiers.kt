package com.hitachi.kiosk.core.data.api

import javax.inject.Qualifier

/**
 * The extra OkHttp interceptors added after the kiosk's own (architecture §7), as a Hilt multibound
 * `Set<Interceptor>`. Empty by default; the debug app contributes the redacting logging interceptor with
 * `@Provides @IntoSet @NetworkInterceptors`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NetworkInterceptors

/**
 * The backend's `OkHttpClient` and `Retrofit`. Qualified so that nothing else (for example an image loader) picks
 * up a client that sends the device token.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KioskBackend
