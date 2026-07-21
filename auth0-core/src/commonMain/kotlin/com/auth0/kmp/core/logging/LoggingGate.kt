package com.auth0.kmp.core.logging

/**
 * Whether the consuming application is running in a debuggable/development
 * context. When `false`, the SDK installs no HTTP request/response logging
 * regardless of the configured [com.auth0.kmp.core.NetworkLogLevel].
 */
internal expect fun isDebugBuild(): Boolean
