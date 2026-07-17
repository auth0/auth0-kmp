package com.auth0.kmp.core

/**
 * Configuration for the networking layer.
 *
 * @param logLevel how much of each HTTP exchange is logged; defaults to
 *   [NetworkLogLevel.NONE]. Logs are emitted verbatim with no redaction, so
 *   [NetworkLogLevel.HEADERS] and [NetworkLogLevel.BODY] reveal request body and headers.
 *   Intended for local debugging only; never enable in production.
 * @param connectTimeoutMillis time allowed to establish a connection, in
 *   milliseconds.
 * @param requestTimeoutMillis time allowed for the full request to complete, in
 *   milliseconds.
 * @param defaultHeaders headers sent on every request. Headers set on an
 *   individual request are added on top of these; when the same key appears in
 *   both, the request's value overrides the default.
 */
public data class NetworkingConfiguration(
    val logLevel: NetworkLogLevel = NetworkLogLevel.NONE,
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 10_000,
    val defaultHeaders: Map<String, String> = emptyMap()
)
