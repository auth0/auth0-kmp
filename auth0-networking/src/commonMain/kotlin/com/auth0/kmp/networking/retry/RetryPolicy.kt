package com.auth0.kmp.networking.retry

import com.auth0.kmp.core.error.NetworkError
import kotlin.time.Duration


/**
 * Controls how a request is retried after a failure.
 *
 * @param maxAttempts total number of tries, including the first. `1` disables
 *   retrying; `3` allows the original call plus two retries.
 * @param backoff how long to wait between attempts.
 * @param retryOn decides, per [NetworkError], whether another attempt should be
 *   made. Returning `false` stops retrying immediately.
 */
data class RetryPolicy(
    val maxAttempts: Int,
    val backoff: Backoff,
    val retryOn: (NetworkError) -> Boolean
) {
    companion object {
        /** Executes the request once, with no retries. */
        val None: RetryPolicy = RetryPolicy(
            maxAttempts = 1,
            backoff = Backoff.Fixed(Duration.ZERO),
            retryOn = { false }
        )
    }
}
