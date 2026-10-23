package com.hitachi.kiosk.log

import android.util.Log
import com.hitachi.kiosk.core.domain.log.LogLevel
import com.hitachi.kiosk.core.domain.log.LogSink
import timber.log.Timber

/**
 * Sends [com.hitachi.kiosk.core.domain.log.KioskLog] records to Timber (architecture §13.2). Fields arrive already
 * redacted. Release builds keep INFO and above; the rotating file tree is added by DEV-073.
 */
class TimberLogSink(
    private val minLevel: LogLevel,
) : LogSink {
    override fun log(
        level: LogLevel,
        event: String,
        fields: Map<String, String>,
        error: Throwable?,
    ) {
        if (level < minLevel) return
        val message =
            if (fields.isEmpty()) {
                event
            } else {
                "$event ${fields.entries.joinToString(
                    " ",
                ) { "${it.key}=${it.value}" }}"
            }
        Timber.log(level.priority, error, message)
    }

    private val LogLevel.priority: Int
        get() =
            when (this) {
                LogLevel.DEBUG -> Log.DEBUG
                LogLevel.INFO -> Log.INFO
                LogLevel.WARN -> Log.WARN
                LogLevel.ERROR -> Log.ERROR
            }
}
