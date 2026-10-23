package com.hitachi.kiosk.core.data.api

import kotlinx.serialization.json.Json

/** The JSON settings of every backend call (architecture §7, TR-NET-08). */
internal val KioskJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
