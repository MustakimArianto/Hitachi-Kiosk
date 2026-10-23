package com.hitachi.kiosk.core.hardware.identity

/**
 * One place the kiosk's serial number may be read from (FR-002, technical-design §9.1, ADR-0002).
 * [IminDeviceIdentityProvider] tries the sources in order; the debug Developer options show each raw [SerialReading].
 */
interface SerialSource {
    /** Short, stable name for logs and diagnostics, such as `prop:persist.sys.imin.sn`. */
    val name: String

    /**
     * Reads the source once. Never throws. Each read is a system property lookup or one binder call, so it is cheap
     * enough to run on the calling thread.
     */
    fun read(): SerialReading
}

/** What one [SerialSource] answered (FR-002). The value is raw: [isPlausibleSerial] decides whether it is usable. */
sealed interface SerialReading {
    /** The source answered a non-blank [value], exactly as read. */
    data class Value(
        val value: String,
    ) : SerialReading

    /** The source answered `null` or an empty string. */
    data object Blank : SerialReading

    /** The source cannot be used on this device or build, for example because a permission is missing. */
    data class Unavailable(
        val reason: String,
    ) : SerialReading

    /** The source threw. Only the exception type and message are kept. */
    data class Failed(
        val type: String,
        val message: String?,
    ) : SerialReading {
        companion object {
            fun of(error: Throwable): Failed = Failed(type = error.javaClass.simpleName, message = error.message)
        }
    }
}

/** The ordered serial-number sources (ADR-0002), shared by the provider and the debug diagnostics. */
class SerialSources(
    val all: List<SerialSource>,
)

private val placeholderSerials = setOf("unknown", "null")

/**
 * Whether [value] can be a real serial number (FR-002): not blank, not a placeholder such as `unknown`
 * (`Build.UNKNOWN`), and not all zeros.
 */
fun isPlausibleSerial(value: String): Boolean {
    val serial = value.trim()
    return serial.isNotEmpty() && serial.lowercase() !in placeholderSerials && serial.any { it != '0' }
}

internal fun String?.toSerialReading(): SerialReading =
    if (isNullOrBlank()) SerialReading.Blank else SerialReading.Value(this)
