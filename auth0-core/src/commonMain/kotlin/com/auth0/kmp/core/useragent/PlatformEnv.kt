package com.auth0.kmp.core.useragent

/**
 * Platform runtime descriptors reported in the `Auth0-Client` header's `env`,
 * e.g. the OS name and version.
 */
internal expect fun platformEnv(): Map<String, String>
