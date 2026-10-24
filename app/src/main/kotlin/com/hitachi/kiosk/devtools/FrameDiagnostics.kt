package com.hitachi.kiosk.devtools

import androidx.activity.ComponentActivity

/**
 * Frame-time diagnostics for the Activity window (JankStats, TR-PRF-05, TC-143). Only the debug build and the
 * `benchmarkRelease` build bind it; release builds get an empty `Optional`, so nothing is tracked there
 * (technical-design §4.3 TD-11, §13).
 */
fun interface FrameDiagnostics {
    /** Starts tracking [activity]'s frames while it is resumed. */
    fun track(activity: ComponentActivity)
}
