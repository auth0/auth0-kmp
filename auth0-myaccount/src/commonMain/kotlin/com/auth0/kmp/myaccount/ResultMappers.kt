package com.auth0.kmp.myaccount

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.result.fold
import com.auth0.kmp.myaccount.error.MyAccountError
import com.auth0.kmp.myaccount.response.ProblemDetailsResponse
import com.auth0.kmp.myaccount.response.toApiError
import com.auth0.kmp.networking.transport.json

internal fun <T> Result<T, TransportError>.toMyAccountResult(): Result<T, MyAccountError> =
    fold({ Result.Success(it) }, { Result.Failure(it.toMyAccountError()) })

internal fun TransportError.toMyAccountError(): MyAccountError = when (this) {
    TransportError.NoInternet,
    TransportError.Timeout -> MyAccountError.Network(this)

    is TransportError.Server -> body
        ?.let { runCatching { json.decodeFromString<ProblemDetailsResponse>(it) }.getOrNull() }
        ?.toApiError()
        ?: MyAccountError.Unknown(this)

    is TransportError.Serialization,
    is TransportError.Unknown -> MyAccountError.Unknown(this)
}
