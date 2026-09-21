package com.auth0.kmp.myaccount

import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.core.result.flatMap
import com.auth0.kmp.myaccount.error.MyAccountError
import com.auth0.kmp.myaccount.model.EmailAuthenticationMethod
import com.auth0.kmp.myaccount.model.EmailEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PasskeyAuthenticationMethod
import com.auth0.kmp.myaccount.model.PasskeyEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PasswordAuthenticationMethod
import com.auth0.kmp.myaccount.model.PasswordEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PhoneAuthenticationMethod
import com.auth0.kmp.myaccount.model.PhoneAuthenticationMethodType
import com.auth0.kmp.myaccount.model.PhoneEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PublicKeyCredentials
import com.auth0.kmp.myaccount.model.PushAuthenticationMethod
import com.auth0.kmp.myaccount.model.PushEnrollmentChallenge
import com.auth0.kmp.myaccount.model.RecoveryCodeAuthenticationMethod
import com.auth0.kmp.myaccount.model.RecoveryCodeEnrollmentChallenge
import com.auth0.kmp.myaccount.model.TotpAuthenticationMethod
import com.auth0.kmp.myaccount.model.TotpEnrollmentChallenge
import com.auth0.kmp.myaccount.response.EmailAuthenticationMethodResponse
import com.auth0.kmp.myaccount.response.EmailEnrollmentChallengeResponse
import com.auth0.kmp.myaccount.response.PasskeyAuthenticationMethodResponse
import com.auth0.kmp.myaccount.response.PasskeyEnrollmentChallengeResponse
import com.auth0.kmp.myaccount.response.PasswordAuthenticationMethodResponse
import com.auth0.kmp.myaccount.response.PasswordEnrollmentChallengeResponse
import com.auth0.kmp.myaccount.response.PhoneAuthenticationMethodResponse
import com.auth0.kmp.myaccount.response.PhoneEnrollmentChallengeResponse
import com.auth0.kmp.myaccount.response.PushAuthenticationMethodResponse
import com.auth0.kmp.myaccount.response.PushEnrollmentChallengeResponse
import com.auth0.kmp.myaccount.response.RecoveryCodeAuthenticationMethodResponse
import com.auth0.kmp.myaccount.response.RecoveryCodeEnrollmentChallengeResponse
import com.auth0.kmp.myaccount.response.TotpAuthenticationMethodResponse
import com.auth0.kmp.myaccount.response.TotpEnrollmentChallengeResponse
import com.auth0.kmp.myaccount.response.toEmailAuthenticationMethod
import com.auth0.kmp.myaccount.response.toEmailEnrollmentChallenge
import com.auth0.kmp.myaccount.response.toPasskeyAuthenticationMethod
import com.auth0.kmp.myaccount.response.toPasskeyEnrollmentChallenge
import com.auth0.kmp.myaccount.response.toPasswordAuthenticationMethod
import com.auth0.kmp.myaccount.response.toPasswordEnrollmentChallenge
import com.auth0.kmp.myaccount.response.toPhoneAuthenticationMethod
import com.auth0.kmp.myaccount.response.toPhoneEnrollmentChallenge
import com.auth0.kmp.myaccount.response.toPushAuthenticationMethod
import com.auth0.kmp.myaccount.response.toPushEnrollmentChallenge
import com.auth0.kmp.myaccount.response.toRecoveryCodeAuthenticationMethod
import com.auth0.kmp.myaccount.response.toRecoveryCodeEnrollmentChallenge
import com.auth0.kmp.myaccount.response.toTotpAuthenticationMethod
import com.auth0.kmp.myaccount.response.toTotpEnrollmentChallenge
import com.auth0.kmp.myaccount.request.toPasskeyEnrollmentCredentialRequest
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.transport.json
import io.ktor.http.decodeURLPart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@OptIn(InternalAuth0Api::class)
internal class DefaultMyAccountClient(
    private val accessToken: String,
    private val useDPoP: Boolean,
    private val networkClient: NetworkClient,
) : MyAccountClient {

    override suspend fun passkeyEnrollmentChallenge(
        userIdentityId: String?,
        connection: String?,
        options: RequestOptions,
    ): Result<PasskeyEnrollmentChallenge, MyAccountError> {
        val body = jsonBody(options) {
            put("type", "passkey")
            userIdentityId?.let { put("identity_user_id", it) }
            connection?.let { put("connection", it) }
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) { responseBody, headers ->
            json.decodeFromString<PasskeyEnrollmentChallengeResponse>(responseBody) to
                headers.locationLastSegment()
        }
            .toMyAccountResult()
            .flatMap { (response, authenticationMethodId) ->
                if (authenticationMethodId == null) {
                    Result.Failure(MyAccountError.MalformedResponse(MISSING_ID_MESSAGE))
                } else {
                    Result.Success(response.toPasskeyEnrollmentChallenge(authenticationMethodId))
                }
            }
    }

    override suspend fun verifyPasskeyEnrollment(
        credential: PublicKeyCredentials,
        challenge: PasskeyEnrollmentChallenge,
        options: RequestOptions,
    ): Result<PasskeyAuthenticationMethod, MyAccountError> {
        val body = jsonBody(options) {
            put("auth_session", challenge.authSession)
            put("authn_response", json.encodeToJsonElement(credential.toPasskeyEnrollmentCredentialRequest()))
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods/${challenge.authenticationMethodId}/verify",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) {
            json.decodeFromString<PasskeyAuthenticationMethodResponse>(it).toPasskeyAuthenticationMethod()
        }.toMyAccountResult()
    }

    override suspend fun totpEnrollmentChallenge(
        options: RequestOptions,
    ): Result<TotpEnrollmentChallenge, MyAccountError> {
        val body = jsonBody(options) {
            put("type", "totp")
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) { responseBody, headers ->
            json.decodeFromString<TotpEnrollmentChallengeResponse>(responseBody) to
                headers.locationLastSegment()
        }
            .toMyAccountResult()
            .flatMap { (response, authenticationMethodId) ->
                if (authenticationMethodId == null) {
                    Result.Failure(MyAccountError.MalformedResponse(MISSING_ID_MESSAGE))
                } else {
                    Result.Success(response.toTotpEnrollmentChallenge(authenticationMethodId))
                }
            }
    }

    override suspend fun verifyTotpEnrollment(
        authenticationMethodId: String,
        authSession: String,
        otpCode: String,
        options: RequestOptions,
    ): Result<TotpAuthenticationMethod, MyAccountError> {
        val body = jsonBody(options) {
            put("auth_session", authSession)
            put("otp_code", otpCode)
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods/$authenticationMethodId/verify",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) {
            json.decodeFromString<TotpAuthenticationMethodResponse>(it).toTotpAuthenticationMethod()
        }.toMyAccountResult()
    }

    override suspend fun pushNotificationEnrollmentChallenge(
        options: RequestOptions,
    ): Result<PushEnrollmentChallenge, MyAccountError> {
        val body = jsonBody(options) {
            put("type", "push-notification")
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) { responseBody, headers ->
            json.decodeFromString<PushEnrollmentChallengeResponse>(responseBody) to
                headers.locationLastSegment()
        }
            .toMyAccountResult()
            .flatMap { (response, authenticationMethodId) ->
                if (authenticationMethodId == null) {
                    Result.Failure(MyAccountError.MalformedResponse(MISSING_ID_MESSAGE))
                } else {
                    Result.Success(response.toPushEnrollmentChallenge(authenticationMethodId))
                }
            }
    }

    override suspend fun verifyPushNotificationEnrollment(
        authenticationMethodId: String,
        authSession: String,
        options: RequestOptions,
    ): Result<PushAuthenticationMethod, MyAccountError> {
        val body = jsonBody(options) {
            put("auth_session", authSession)
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods/$authenticationMethodId/verify",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) {
            json.decodeFromString<PushAuthenticationMethodResponse>(it).toPushAuthenticationMethod()
        }.toMyAccountResult()
    }

    override suspend fun emailEnrollmentChallenge(
        email: String,
        options: RequestOptions,
    ): Result<EmailEnrollmentChallenge, MyAccountError> {
        val body = jsonBody(options) {
            put("type", "email")
            put("email", email)
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) { responseBody, headers ->
            json.decodeFromString<EmailEnrollmentChallengeResponse>(responseBody) to
                headers.locationLastSegment()
        }
            .toMyAccountResult()
            .flatMap { (response, authenticationMethodId) ->
                if (authenticationMethodId == null) {
                    Result.Failure(MyAccountError.MalformedResponse(MISSING_ID_MESSAGE))
                } else {
                    Result.Success(response.toEmailEnrollmentChallenge(authenticationMethodId))
                }
            }
    }

    override suspend fun verifyEmailEnrollment(
        authenticationMethodId: String,
        authSession: String,
        otpCode: String,
        options: RequestOptions,
    ): Result<EmailAuthenticationMethod, MyAccountError> {
        val body = jsonBody(options) {
            put("auth_session", authSession)
            put("otp_code", otpCode)
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods/$authenticationMethodId/verify",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) {
            json.decodeFromString<EmailAuthenticationMethodResponse>(it).toEmailAuthenticationMethod()
        }.toMyAccountResult()
    }

    override suspend fun phoneEnrollmentChallenge(
        phoneNumber: String,
        preferredAuthenticationMethod: PhoneAuthenticationMethodType?,
        options: RequestOptions,
    ): Result<PhoneEnrollmentChallenge, MyAccountError> {
        val body = jsonBody(options) {
            put("type", "phone")
            put("phone_number", phoneNumber)
            preferredAuthenticationMethod?.let {
                put("preferred_authentication_method", it.toApiValue())
            }
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) { responseBody, headers ->
            json.decodeFromString<PhoneEnrollmentChallengeResponse>(responseBody) to
                headers.locationLastSegment()
        }
            .toMyAccountResult()
            .flatMap { (response, authenticationMethodId) ->
                if (authenticationMethodId == null) {
                    Result.Failure(MyAccountError.MalformedResponse(MISSING_ID_MESSAGE))
                } else {
                    Result.Success(response.toPhoneEnrollmentChallenge(authenticationMethodId))
                }
            }
    }

    override suspend fun verifyPhoneEnrollment(
        authenticationMethodId: String,
        authSession: String,
        otpCode: String,
        options: RequestOptions,
    ): Result<PhoneAuthenticationMethod, MyAccountError> {
        val body = jsonBody(options) {
            put("auth_session", authSession)
            put("otp_code", otpCode)
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods/$authenticationMethodId/verify",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) {
            json.decodeFromString<PhoneAuthenticationMethodResponse>(it).toPhoneAuthenticationMethod()
        }.toMyAccountResult()
    }

    override suspend fun recoveryCodeEnrollmentChallenge(
        options: RequestOptions,
    ): Result<RecoveryCodeEnrollmentChallenge, MyAccountError> {
        val body = jsonBody(options) {
            put("type", "recovery-code")
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) { responseBody, headers ->
            json.decodeFromString<RecoveryCodeEnrollmentChallengeResponse>(responseBody) to
                headers.locationLastSegment()
        }
            .toMyAccountResult()
            .flatMap { (response, authenticationMethodId) ->
                if (authenticationMethodId == null) {
                    Result.Failure(MyAccountError.MalformedResponse(MISSING_ID_MESSAGE))
                } else {
                    Result.Success(response.toRecoveryCodeEnrollmentChallenge(authenticationMethodId))
                }
            }
    }

    override suspend fun verifyRecoveryCodeEnrollment(
        authenticationMethodId: String,
        authSession: String,
        options: RequestOptions,
    ): Result<RecoveryCodeAuthenticationMethod, MyAccountError> {
        val body = jsonBody(options) {
            put("auth_session", authSession)
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods/$authenticationMethodId/verify",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) {
            json.decodeFromString<RecoveryCodeAuthenticationMethodResponse>(it)
                .toRecoveryCodeAuthenticationMethod()
        }.toMyAccountResult()
    }

    override suspend fun passwordEnrollmentChallenge(
        userIdentityId: String?,
        connection: String?,
        options: RequestOptions,
    ): Result<PasswordEnrollmentChallenge, MyAccountError> {
        val body = jsonBody(options) {
            put("type", "password")
            userIdentityId?.let { put("identity_user_id", it) }
            connection?.let { put("connection", it) }
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) { responseBody, headers ->
            json.decodeFromString<PasswordEnrollmentChallengeResponse>(responseBody) to
                headers.locationLastSegment()
        }
            .toMyAccountResult()
            .flatMap { (response, authenticationMethodId) ->
                if (authenticationMethodId == null) {
                    Result.Failure(MyAccountError.MalformedResponse(MISSING_ID_MESSAGE))
                } else {
                    Result.Success(response.toPasswordEnrollmentChallenge(authenticationMethodId))
                }
            }
    }

    override suspend fun verifyPasswordEnrollment(
        authenticationMethodId: String,
        authSession: String,
        newPassword: String,
        options: RequestOptions,
    ): Result<PasswordAuthenticationMethod, MyAccountError> {
        val body = jsonBody(options) {
            put("auth_session", authSession)
            put("new_password", newPassword)
        }

        val request = NetworkRequest(
            method = HttpMethod.POST,
            path = "/me/v1/authentication-methods/$authenticationMethodId/verify",
            headers = authHeaders(options),
            body = body,
        )

        return networkClient.request(request, options.retryPolicy) {
            json.decodeFromString<PasswordAuthenticationMethodResponse>(it)
                .toPasswordAuthenticationMethod()
        }.toMyAccountResult()
    }

    private fun PhoneAuthenticationMethodType.toApiValue(): String =
        when (this) {
            PhoneAuthenticationMethodType.SMS -> "sms"
            PhoneAuthenticationMethodType.VOICE -> "voice"
        }

    private fun authHeaders(options: RequestOptions): Map<String, String> {
        val scheme = if (useDPoP) "DPoP" else "Bearer"
        return options.headers + ("Authorization" to "$scheme $accessToken")
    }

    private inline fun jsonBody(
        options: RequestOptions,
        build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): String = json.encodeToString(
        buildJsonObject {
            options.parameters.forEach { (key, value) -> put(key, value) }
            build()
        },
    )
}

/**
 * Reported as [MyAccountError.MalformedResponse] when a successful challenge
 * response does not carry the authentication method identifier the SDK reads
 * from the `Location` header.
 */
private const val MISSING_ID_MESSAGE =
    "The authentication method identifier was missing from the response."

/**
 * Returns the last path segment of the `Location` header, URL-decoded, or `null`
 * when the header is absent or has no path. Header names are matched
 * case-insensitively.
 */
private fun Map<String, List<String>>.locationLastSegment(): String? =
    entries.firstOrNull { it.key.equals("Location", ignoreCase = true) }
        ?.value
        ?.firstOrNull()
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotEmpty() }
        ?.decodeURLPart()
