package com.auth0.kmp.core.useragent

/**
 * Platform runtime descriptors reported in the `Auth0-Client` header's `env`.
 * The value is platform-specific: the Android API level, or the iOS OS version.
 */
internal expect fun platformEnv(): Map<String, String>
