package com.auth0.kmp.webauth.pkce

import com.auth0.kmp.core.primitives.generateSecureRandomBytes
import com.auth0.kmp.core.primitives.sha256
import com.auth0.kmp.webauth.internal.base64UrlNoPad

internal class Pkce private constructor(
    val codeVerifier: String,
    val codeChallenge: String
) {
    val codeChallengeMethod = "S256"

    companion object {
        private const val VERIFIER_BYTE_LENGTH = 32
        fun generate(): Pkce {
            val verifier = generateSecureRandomBytes(VERIFIER_BYTE_LENGTH).base64UrlNoPad()
            val challenge = verifier.encodeToByteArray().sha256().base64UrlNoPad()
            return Pkce(verifier, challenge)
        }
    }
}
