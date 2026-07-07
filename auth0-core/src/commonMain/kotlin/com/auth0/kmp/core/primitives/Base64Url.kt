package com.auth0.kmp.core.primitives

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlin.io.encoding.Base64

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
private val base64UrlNoPad = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/**
 * Decodes a base64url-encoded string to its raw bytes.
 *
 * Accepts input with or without `=` padding.
 *
 * @throws IllegalArgumentException if the input is not valid base64url.
 */
@InternalAuth0Api
public fun String.decodeBase64Url(): ByteArray = base64Url.decode(this)

/**
 * Encodes raw bytes to a base64url string without `=` padding.
 */
@InternalAuth0Api
public fun ByteArray.encodeBase64Url(): String = base64UrlNoPad.encode(this)
