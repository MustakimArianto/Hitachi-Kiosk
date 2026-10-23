package com.hitachi.kiosk.devtools

/** Counts taps on the splash mark; the fifth consecutive tap opens Developer options (DEV-009). */
class DeveloperMenuTapCounter(
    private val required: Int = REQUIRED_TAPS,
) {
    private var taps = 0

    /** Returns true when this tap completes the gesture, and starts counting again. */
    fun onTap(): Boolean {
        taps++
        if (taps < required) return false
        taps = 0
        return true
    }

    private companion object {
        const val REQUIRED_TAPS = 5
    }
}
