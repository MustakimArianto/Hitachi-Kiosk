package com.hitachi.kiosk.core.domain.model

/** Result of a conditional GET with `If-None-Match` (E5, E6; TR-NET-09). */
sealed interface Conditional<out T> {
    /** The resource changed. Store [etag] and send it on the next request. */
    data class Modified<out T>(
        val value: T,
        val etag: String?,
    ) : Conditional<T>

    /** HTTP 304: keep the cached copy. */
    data object NotModified : Conditional<Nothing>
}
