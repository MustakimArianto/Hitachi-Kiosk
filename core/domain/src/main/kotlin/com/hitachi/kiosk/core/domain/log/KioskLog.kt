package com.hitachi.kiosk.core.domain.log

/** Log severity. Release builds keep [INFO] and above (technical-design §12). */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** Where log records go. `:app` installs a Timber-backed sink at startup (architecture §13.2). */
fun interface LogSink {
    fun log(
        level: LogLevel,
        event: String,
        fields: Map<String, String>,
        error: Throwable?,
    )
}

/**
 * The only logging entry point for every module (conventions §8). A record is an event name plus key-value fields,
 * never free text with user input. [Redactor] runs before any sink sees a field (NFR-006, TR-PRV-03).
 */
object KioskLog {
    @Volatile
    private var sink: LogSink = LogSink { _, _, _, _ -> }

    /** Installs the sink. Until then records are dropped. */
    fun install(sink: LogSink) {
        this.sink = sink
    }

    fun d(
        event: String,
        vararg fields: Pair<String, Any?>,
    ) = log(LogLevel.DEBUG, event, fields, null)

    fun i(
        event: String,
        vararg fields: Pair<String, Any?>,
    ) = log(LogLevel.INFO, event, fields, null)

    fun w(
        event: String,
        vararg fields: Pair<String, Any?>,
        error: Throwable? = null,
    ) = log(LogLevel.WARN, event, fields, error)

    fun e(
        event: String,
        vararg fields: Pair<String, Any?>,
        error: Throwable? = null,
    ) = log(LogLevel.ERROR, event, fields, error)

    private fun log(
        level: LogLevel,
        event: String,
        fields: Array<out Pair<String, Any?>>,
        error: Throwable?,
    ) {
        sink.log(level, event, Redactor.redact(fields.associate { (key, value) -> key to value.toString() }), error)
    }
}

/**
 * Privacy rules for log fields (NFR-006, technical-design §12): secrets are dropped, payment references keep only
 * their last 4 characters, customer data is dropped.
 */
object Redactor {
    private val dropped = setOf("pin", "currentpin", "newpin", "currentpinproof", "devicetoken", "authorization")
    private val truncated = setOf("paymentid", "reference", "terminalref", "approvalcode", "qrstring")
    private const val KEEP_LAST = 4
    private const val MASK = "•••"

    fun redact(fields: Map<String, String>): Map<String, String> =
        buildMap {
            for ((key, value) in fields) {
                val name = key.lowercase()
                when {
                    name in dropped || name.startsWith("customer") -> Unit
                    name in truncated -> put(key, truncate(value))
                    else -> put(key, value)
                }
            }
        }

    /** Keeps the last 4 characters of a payment reference. */
    fun truncate(value: String): String = if (value.length <= KEEP_LAST) MASK else MASK + value.takeLast(KEEP_LAST)
}
