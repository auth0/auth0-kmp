package com.auth0.kmp.credentials

/**
 * Builds the stable map key an [com.auth0.kmp.core.model.ApiCredentials] entry is
 * stored under, from its [audience] and optional [scope]. Deterministic and
 * collision-free: `:` and `\` in the inputs are escaped so no audience/scope value
 * can forge another entry's key.
 */
internal fun apiCredentialsKey(audience: String, scope: String?): String {
    val normalizedScopes = scope
        ?.split(" ")
        ?.filter { it.isNotEmpty() }
        ?.map { escape(it) }
        ?.distinct()
        ?.sorted()
        .orEmpty()

    val escapedAudience = escape(audience)
    return if (normalizedScopes.isEmpty()) escapedAudience
    else escapedAudience + ":" + normalizedScopes.joinToString(":")
}

private fun escape(value: String): String =
    value.replace("\\", "\\\\").replace(":", "\\:")
