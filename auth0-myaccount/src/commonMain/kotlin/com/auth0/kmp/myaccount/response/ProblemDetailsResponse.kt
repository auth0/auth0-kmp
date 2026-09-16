package com.auth0.kmp.myaccount.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.myaccount.error.MyAccountError
import com.auth0.kmp.myaccount.error.ValidationError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@InternalAuth0Api
public data class ProblemDetailsResponse(
    @SerialName("type") val type: String,
    @SerialName("title") val title: String,
    @SerialName("detail") val detail: String,
    @SerialName("status") val status: Int,
    @SerialName("validation_errors") val validationErrors: List<ValidationErrorResponse> = emptyList(),
)

@Serializable
@InternalAuth0Api
public data class ValidationErrorResponse(
    @SerialName("detail") val detail: String,
    @SerialName("pointer") val pointer: String? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("field") val field: String? = null,
)

@InternalAuth0Api
public fun ProblemDetailsResponse.toApiError(): MyAccountError.ApiError =
    MyAccountError.ApiError(
        type = type,
        title = title,
        detail = detail,
        status = status,
        validationErrors = validationErrors.map { it.toValidationError() },
    )

@InternalAuth0Api
public fun ValidationErrorResponse.toValidationError(): ValidationError =
    ValidationError(
        detail = detail,
        pointer = pointer,
        source = source,
        field = field,
    )
