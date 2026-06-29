package com.auth0.kmp.core

/**
 * Configuration for the networking layer.
 *
 * @param enableLogging whether the HTTP request line, URL, and response status
 *   are logged. Bodies and headers are not logged, so credentials and tokens are
 *   never written to the log. Intended for debugging; defaults to `false`.
 * @param connectTimeoutMillis time allowed to establish a connection, in
 *   milliseconds.
 * @param requestTimeoutMillis time allowed for the full request to complete, in
 *   milliseconds.
 * @param defaultHeaders headers sent on every request. Headers set on an
 *   individual request are added on top of these; when the same key appears in
 *   both, the request's value overrides the default.
 */
public data class NetworkingConfiguration(
    val enableLogging: Boolean = false,
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 10_000,
    val defaultHeaders: Map<String, String> = emptyMap()
)
