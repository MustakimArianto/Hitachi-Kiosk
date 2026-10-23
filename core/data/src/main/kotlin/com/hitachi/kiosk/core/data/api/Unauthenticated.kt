package com.hitachi.kiosk.core.data.api

/**
 * Marks a [KioskApi] call sent without `Authorization` (E1, E14; api-spec §2.2). Read at runtime by
 * [com.hitachi.kiosk.core.data.api.interceptor.AuthInterceptor] through the request's `retrofit2.Invocation` tag.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Unauthenticated
