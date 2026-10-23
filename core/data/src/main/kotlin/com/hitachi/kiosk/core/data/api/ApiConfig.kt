package com.hitachi.kiosk.core.data.api

/**
 * Backend settings that `:app` provides through Hilt from its build config (architecture §7, technical-design §2.3,
 * §5.1).
 *
 * @property baseUrl the API base URL, for example `https://staging-api.hitachikiosk.example/`; a missing trailing
 *   slash is added.
 * @property environment `mock`, `dev`, `staging` or `prod`, for logs and diagnostics.
 * @property certificatePins SPKI pins (`sha256/…`) for the base URL host, primary and backup; empty disables pinning
 *   (TR-SEC-03).
 * @property appVersion sent as `X-App-Version`.
 * @property allowCleartext allows plain HTTP; only the debug build for the mock host sets it (api-spec §2.1).
 */
data class ApiConfig(
    val baseUrl: String,
    val environment: String,
    val certificatePins: List<String>,
    val appVersion: String,
    val allowCleartext: Boolean,
)
