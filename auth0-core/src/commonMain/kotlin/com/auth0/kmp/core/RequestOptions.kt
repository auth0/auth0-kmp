package com.auth0.kmp.core

import com.auth0.kmp.networking.retry.RetryPolicy

/**
 * Per-call transport options shared across authentication operations.
 *
 * Carries the transport-mechanic knobs that are identical regardless of which
 * operation is called: extra request [parameters], extra [headers], and a
 * [retryPolicy]. OAuth semantics such as `scope` and `audience` are not here —
 * they are typed per-operation parameters.
 *
 * @param parameters extra request parameters, sent in the endpoint's natural
 *   channel (form body for `POST /oauth/token`, query string for `GET`
 *   endpoints). Reserved parameters populated by the SDK for the operation take
 *   precedence over entries here with the same name.
 * @param headers extra request headers. Headers set by the SDK for the operation
 *   take precedence over entries here with the same name.
 * @param retryPolicy how the request is retried on failure. Prefer a [retryPolicy]
 *   whose `retryOn` predicate accepts only transient transport errors; retrying a
 *   request that failed for a non-transient reason wastes attempts.
 */
public data class RequestOptions(
    val parameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val retryPolicy: RetryPolicy = RetryPolicy.None,
)
