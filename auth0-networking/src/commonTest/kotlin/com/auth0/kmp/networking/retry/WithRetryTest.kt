package com.auth0.kmp.networking.retry

import com.auth0.kmp.core.error.NetworkError
import com.auth0.kmp.core.result.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class WithRetryTest {

    private val recordedDelays = mutableListOf<Duration>()
    private val recordingDelay: suspend (Duration) -> Unit = { recordedDelays.add(it) }

    private fun policy(
        maxAttempts: Int,
        delay: Duration = 10.milliseconds,
        retryOn: (NetworkError) -> Boolean = { true },
    ) = RetryPolicy(
        maxAttempts = maxAttempts,
        backoff = Backoff.Fixed(delay),
        retryOn = retryOn,
    )

    /** Pops a canned [Result] per call and counts invocations. */
    private class ScriptedBlock(results: List<Result<String, NetworkError>>) {
        private val queue = ArrayDeque(results)
        var calls = 0
            private set

        suspend fun invoke(): Result<String, NetworkError> {
            calls++
            return queue.removeFirst()
        }
    }

    @Test
    fun returnsSuccess_onFirstTry_withoutDelaying() = runTest {
        val block = ScriptedBlock(listOf(Result.Success("ok")))

        val result = withRetry(policy(maxAttempts = 3), recordingDelay) { block.invoke() }

        assertEquals(Result.Success("ok"), result)
        assertEquals(1, block.calls)
        assertTrue(recordedDelays.isEmpty())
    }

    @Test
    fun retriesUntilSuccess_thenStops() = runTest {
        val block = ScriptedBlock(
            listOf(
                Result.Failure(NetworkError.Timeout),
                Result.Failure(NetworkError.Timeout),
                Result.Success("ok"),
            )
        )

        val result = withRetry(policy(maxAttempts = 5), recordingDelay) { block.invoke() }

        assertEquals(Result.Success("ok"), result)
        assertEquals(3, block.calls)
        assertEquals(2, recordedDelays.size)
    }

    @Test
    fun exhaustsBudget_thenReturnsLastFailure() = runTest {
        val block = ScriptedBlock(
            listOf(
                Result.Failure(NetworkError.Timeout),
                Result.Failure(NetworkError.Timeout),
                Result.Failure(NetworkError.Unauthorized),
            )
        )

        val result = withRetry(policy(maxAttempts = 3), recordingDelay) { block.invoke() }

        assertEquals(Result.Failure(NetworkError.Unauthorized), result)
        assertEquals(3, block.calls)
        assertEquals(2, recordedDelays.size)
    }

    @Test
    fun stopsImmediately_whenRetryOnReturnsFalse() = runTest {
        val block = ScriptedBlock(
            listOf(
                Result.Failure(NetworkError.Forbidden),
                Result.Success("unreached"),
            )
        )

        val result = withRetry(
            policy(maxAttempts = 5, retryOn = { it != NetworkError.Forbidden }),
            recordingDelay,
        ) { block.invoke() }

        assertEquals(Result.Failure(NetworkError.Forbidden), result)
        assertEquals(1, block.calls)
        assertTrue(recordedDelays.isEmpty())
    }

    @Test
    fun delaysFollowBackoffSequence_inOrder() = runTest {
        val block = ScriptedBlock(
            listOf(
                Result.Failure(NetworkError.Timeout),
                Result.Failure(NetworkError.Timeout),
                Result.Failure(NetworkError.Timeout),
                Result.Success("ok"),
            )
        )
        val backoff = Backoff.Exponential(
            base = 100.milliseconds,
            multiplier = 2.0,
            maxDelay = 10_000.milliseconds,
            jitter = false,
        )
        val expPolicy = RetryPolicy(maxAttempts = 5, backoff = backoff, retryOn = { true })

        withRetry(expPolicy, recordingDelay) { block.invoke() }

        assertEquals(
            listOf(100.milliseconds, 200.milliseconds, 400.milliseconds),
            recordedDelays,
        )
    }

    @Test
    fun none_runsExactlyOnce_evenOnFailure() = runTest {
        val block = ScriptedBlock(
            listOf(
                Result.Failure(NetworkError.Timeout),
                Result.Success("unreached"),
            )
        )

        val result = withRetry(RetryPolicy.None, recordingDelay) { block.invoke() }

        assertEquals(Result.Failure(NetworkError.Timeout), result)
        assertEquals(1, block.calls)
        assertTrue(recordedDelays.isEmpty())
    }
}
