package com.auth0.kmp.networking.retry

import com.auth0.kmp.core.error.NetworkError
import com.auth0.kmp.core.result.Result
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * Runs [block], retrying it according to [policy] until it succeeds, the attempt
 * budget is exhausted, or [policy] declines to retry the failure.
 *
 * @param policy the attempt count, backoff, and retry predicate to apply.
 * @param delayFn how to wait between attempts; defaults to real coroutine [delay].
 * @param block the operation to run; re-invoked from scratch on each attempt.
 * @return the first successful [Result], or the last failure once retrying stops.
 */
internal suspend fun <T> withRetry(
    policy: RetryPolicy,
    delayFn: suspend (Duration) -> Unit = { delay(it) },
    block: suspend () -> Result<T, NetworkError>
): Result<T, NetworkError> {
    // attempt counts tries (not retries): 1 on the first call, so maxAttempts == 1 never retries.
    var attempt = 1
    while (true) {
        val result = block()
        if (result is Result.Success) return result
        val error = (result as Result.Failure).error

        if (attempt >= policy.maxAttempts || !policy.retryOn(error)) {
            return result
        }
        // delay for the attempt that just failed, before incrementing — so the first retry waits backoff(1).
        delayFn(policy.backoff.delayFor(attempt))
        attempt++
    }
}