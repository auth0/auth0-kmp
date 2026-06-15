package com.auth0.kmp.core.result

import com.auth0.kmp.core.error.Auth0Error

/**
 * The outcome of an SDK operation: either a [Success] carrying data, or a
 * [Failure] carrying a typed error.
 *
 * @param D the success data type.
 * @param E the error type, constrained to [Auth0Error].
 */
sealed interface Result<out D, out E : Auth0Error> {

    /** A successful outcome holding [data]. */
    data class Success<out D>(val data: D) : Result<D, Nothing>

    /** A failed outcome holding [error]. */
    data class Failure<out E : Auth0Error>(val error: E) : Result<Nothing, E>
}

/**
 * Runs [onSuccess] or [onFailure] depending on which variant this is.
 */
inline fun <D, E : Auth0Error> Result<D, E>.fold(
    onSuccess: (D) -> Unit,
    onFailure: (E) -> Unit
) {
    when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Failure -> onFailure(error)
    }
}

/** Returns the success data, or `null` if this is a [Result.Failure]. */
fun <D, E : Auth0Error> Result<D, E>.getOrNull(): D? =
    when (this) {
        is Result.Success -> data
        is Result.Failure -> null
    }

/**
 * Transforms the success data with [transform], leaving a [Result.Failure]
 * untouched. The error type is preserved.
 */
inline fun <D, R, E : Auth0Error> Result<D, E>.map(transform: (D) -> R): Result<R, E> {
    return when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Failure -> this
    }
}

/**
 * Chains another [Result]-returning operation onto a success, short-circuiting
 * on the first [Result.Failure]. The error type is preserved across the chain.
 */
inline fun <D, R, E : Auth0Error> Result<D, E>.flatMap(transform: (D) -> Result<R, E>): Result<R, E> {
    return when (this) {
        is Result.Success -> transform(data)
        is Result.Failure -> this
    }
}
