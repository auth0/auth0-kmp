package com.auth0.kmp.credentials

import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.error.parseAuth0ErrorBody

internal fun TransportError.toCredentialsManagerError(): CredentialsManagerError = when (this) {
    TransportError.NoInternet,
    TransportError.Timeout -> CredentialsManagerError.Network(this)

    is TransportError.Server -> parseAuth0ErrorBody(body)
        ?.let { CredentialsManagerError.ApiError(it.error, it.errorDescription ?: it.error, status) }
        ?: CredentialsManagerError.Unknown(this)

    is TransportError.Serialization,
    is TransportError.Unknown -> CredentialsManagerError.Unknown(this)
}
