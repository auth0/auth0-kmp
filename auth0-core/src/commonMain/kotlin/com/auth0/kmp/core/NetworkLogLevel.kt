package com.auth0.kmp.core

/**
 * How much of each HTTP exchange the SDK writes to the log. Levels are
 * cumulative: each includes everything the previous one logs.
 *
 * - [NONE] logging off.
 * - [BASIC] request method, URL, and response status.
 * - [HEADERS] the above plus request and response headers.
 * - [BODY] the above plus request and response bodies.
 */
public enum class NetworkLogLevel { NONE, BASIC, HEADERS, BODY }
