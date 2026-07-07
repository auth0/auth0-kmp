package com.auth0.kmp.webauth.internal

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.primitives.encodeBase64Url
import com.auth0.kmp.core.primitives.generateSecureRandomBytes

@OptIn(InternalAuth0Api::class)
internal fun ByteArray.base64UrlNoPad(): String = encodeBase64Url()

internal fun randomUrlSafeString(byteLength: Int = 32): String =
    generateSecureRandomBytes(byteLength).base64UrlNoPad()
