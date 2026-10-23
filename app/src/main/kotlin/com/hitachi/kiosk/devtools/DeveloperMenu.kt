package com.hitachi.kiosk.devtools

import android.content.Context

/**
 * Opens the debug Developer options (DEV-009). Only the `debug` source set binds it; release builds get an empty
 * `Optional`, so the 5-tap gesture on the splash mark does nothing there.
 */
fun interface DeveloperMenu {
    fun open(context: Context)
}
