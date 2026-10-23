package com.hitachi.kiosk.core.domain

import com.hitachi.kiosk.core.domain.error.DomainError

/**
 * Result of every repository and port call (architecture §13.3, conventions §4): a value or a [DomainError].
 * Exceptions never cross a module boundary.
 */
sealed interface Outcome<out T> {
    /** The call succeeded with [value]. */
    data class Success<out T>(
        val value: T,
    ) : Outcome<T>

    /** The call failed with [error]. */
    data class Failure(
        val error: DomainError,
    ) : Outcome<Nothing>
}

/** The value, or `null` on failure. */
fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

/** The error, or `null` on success. */
fun Outcome<*>.errorOrNull(): DomainError? = (this as? Outcome.Failure)?.error

/** Transforms a successful value and keeps a failure as it is. */
inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

/** Chains a call that itself returns an [Outcome]. */
inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> =
    when (this) {
        is Outcome.Success -> transform(value)
        is Outcome.Failure -> this
    }
