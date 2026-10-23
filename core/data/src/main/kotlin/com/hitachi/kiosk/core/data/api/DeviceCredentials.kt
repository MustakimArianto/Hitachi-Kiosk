package com.hitachi.kiosk.core.data.api

import javax.inject.Inject
import javax.inject.Singleton

/** The device identity the backend issued with E1 (api-spec §2.2, FR-003). [toString] never prints the token. */
data class DeviceCredentials(
    val deviceId: String,
    val deviceToken: String,
) {
    override fun toString(): String = "DeviceCredentials(deviceId=$deviceId, deviceToken=•••)"
}

/**
 * The current device credentials for [com.hitachi.kiosk.core.data.api.interceptor.AuthInterceptor]: `null` until
 * the kiosk is activated. Read on OkHttp's thread for every call, so it must not suspend or block on I/O.
 */
fun interface DeviceCredentialsProvider {
    fun current(): DeviceCredentials?
}

/**
 * In-memory credentials, the default [DeviceCredentialsProvider] binding in 0.1.0. Nothing survives a restart.
 * DEV-014 (0.2.0) replaces it with the Tink-encrypted Secure DataStore (ADR-0001, TD-05).
 */
@Singleton
class InMemoryDeviceCredentialsStore
    @Inject
    constructor() : DeviceCredentialsProvider {
        @Volatile
        private var credentials: DeviceCredentials? = null

        override fun current(): DeviceCredentials? = credentials

        fun set(credentials: DeviceCredentials) {
            this.credentials = credentials
        }

        fun clear() {
            credentials = null
        }
    }
