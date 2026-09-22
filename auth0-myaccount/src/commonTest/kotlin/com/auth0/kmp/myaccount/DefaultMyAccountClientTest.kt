package com.auth0.kmp.myaccount

import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
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
import com.auth0.kmp.myaccount.model.PushAuthenticationMethod
import com.auth0.kmp.myaccount.model.PushEnrollmentChallenge
import com.auth0.kmp.myaccount.model.RecoveryCodeAuthenticationMethod
import com.auth0.kmp.myaccount.model.RecoveryCodeEnrollmentChallenge
import com.auth0.kmp.myaccount.model.TotpAuthenticationMethod
import com.auth0.kmp.myaccount.model.TotpEnrollmentChallenge
import com.auth0.kmp.networking.request.HttpMethod
import com.auth0.kmp.networking.retry.Backoff
import com.auth0.kmp.networking.retry.RetryPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration

private const val METHODS_PATH = "/me/v1/authentication-methods"

class DefaultMyAccountClientTest {

    // --- Enrollment challenges: request body (the wire contract) + header-id mapping ---

    @Test
    fun totpEnrollmentChallenge_postsTotpType_andMapsChallenge() = runTest {
        val (c, net) = client(Result.Success(totpChallengeJson()), location("AM_totp"))

        val result = c.totpEnrollmentChallenge()

        val req = net.lastRequest!!
        assertEquals(HttpMethod.POST, req.method)
        assertEquals(METHODS_PATH, req.path)
        assertEquals("totp", bodyOf(req).str("type"))
        assertIs<Result.Success<TotpEnrollmentChallenge>>(result)
        assertEquals("AM_totp", result.data.authenticationMethodId)
        assertEquals("sess", result.data.authSession)
        assertEquals("ABCDEF", result.data.manualInputCode)
    }

    @Test
    fun pushEnrollmentChallenge_postsPushType_andMapsBarcode() = runTest {
        val (c, net) = client(Result.Success(pushChallengeJson()), location("AM_push"))

        val result = c.pushNotificationEnrollmentChallenge()

        assertEquals("push-notification", bodyOf(net.lastRequest!!).str("type"))
        assertIs<Result.Success<PushEnrollmentChallenge>>(result)
        assertEquals("AM_push", result.data.authenticationMethodId)
        assertEquals("otpauth://x", result.data.barcodeUri)
    }

    @Test
    fun emailEnrollmentChallenge_postsEmailAndType() = runTest {
        val (c, net) = client(Result.Success(emailChallengeJson()), location("AM_email"))

        val result = c.emailEnrollmentChallenge(email = "a@b.com")

        val body = bodyOf(net.lastRequest!!)
        assertEquals("email", body.str("type"))
        assertEquals("a@b.com", body.str("email"))
        assertIs<Result.Success<EmailEnrollmentChallenge>>(result)
        assertEquals("AM_email", result.data.authenticationMethodId)
    }

    @Test
    fun phoneEnrollmentChallenge_postsPhoneBody_andMapsHeaderId() = runTest {
        val (c, net) = client(Result.Success(phoneChallengeJson()), location("AM_phone"))

        val result = c.phoneEnrollmentChallenge(
            phoneNumber = "+15551234567",
            preferredAuthenticationMethod = PhoneAuthenticationMethodType.SMS,
        )

        val body = bodyOf(net.lastRequest!!)
        assertEquals("phone", body.str("type"))
        assertEquals("+15551234567", body.str("phone_number"))
        assertEquals("sms", body.str("preferred_authentication_method"))
        assertIs<Result.Success<PhoneEnrollmentChallenge>>(result)
        assertEquals("AM_phone", result.data.authenticationMethodId)
        assertEquals("sess", result.data.authSession)
    }

    @Test
    fun phoneEnrollmentChallenge_omitsPreferredMethod_whenNull() = runTest {
        val (c, net) = client(Result.Success(phoneChallengeJson()), location("AM_phone"))

        c.phoneEnrollmentChallenge(phoneNumber = "+15551234567", preferredAuthenticationMethod = null)

        assertNull(bodyOf(net.lastRequest!!)["preferred_authentication_method"])
    }

    @Test
    fun recoveryCodeEnrollmentChallenge_postsType_andMapsRecoveryCode() = runTest {
        val (c, net) = client(Result.Success(recoveryCodeChallengeJson()), location("AM_rc"))

        val result = c.recoveryCodeEnrollmentChallenge()

        assertEquals("recovery-code", bodyOf(net.lastRequest!!).str("type"))
        assertIs<Result.Success<RecoveryCodeEnrollmentChallenge>>(result)
        assertEquals("REC-123", result.data.recoveryCode)
    }

    @Test
    fun passwordEnrollmentChallenge_postsTypeAndIdentity_andMapsPolicy() = runTest {
        val (c, net) = client(Result.Success(passwordChallengeJson()), location("AM_pwd"))

        val result = c.passwordEnrollmentChallenge(userIdentityId = "uid", connection = "db")

        val body = bodyOf(net.lastRequest!!)
        assertEquals("password", body.str("type"))
        assertEquals("uid", body.str("identity_user_id"))
        assertEquals("db", body.str("connection"))
        assertIs<Result.Success<PasswordEnrollmentChallenge>>(result)
        assertEquals("AM_pwd", result.data.authenticationMethodId)
        assertEquals(8, result.data.passwordPolicy.complexity.minLength)
        assertEquals(5, result.data.passwordPolicy.history.size)
    }

    @Test
    fun passkeyEnrollmentChallenge_postsPasskeyType_andMapsChallenge() = runTest {
        val (c, net) = client(Result.Success(passkeyChallengeJson()), location("AM_pk"))

        val result = c.passkeyEnrollmentChallenge(userIdentityId = "uid", connection = "db")

        assertEquals("passkey", bodyOf(net.lastRequest!!).str("type"))
        assertIs<Result.Success<PasskeyEnrollmentChallenge>>(result)
        assertEquals("AM_pk", result.data.authenticationMethodId)
        assertEquals("ch", result.data.authParamsPublicKey.challenge)
    }

    // --- Verify enrollment: verify path with the interpolated id + body contract ---

    @Test
    fun verifyTotpEnrollment_postsOtpToVerifyPath() = runTest {
        val (c, net) = client(Result.Success(totpMethodJson()))

        val result = c.verifyTotpEnrollment("AM123", authSession = "sess", otpCode = "123456")

        val req = net.lastRequest!!
        assertEquals(HttpMethod.POST, req.method)
        assertEquals("$METHODS_PATH/AM123/verify", req.path)
        val body = bodyOf(req)
        assertEquals("sess", body.str("auth_session"))
        assertEquals("123456", body.str("otp_code"))
        assertIs<Result.Success<TotpAuthenticationMethod>>(result)
        assertEquals("AM123", result.data.id)
    }

    @Test
    fun verifyPushEnrollment_postsAuthSessionOnly() = runTest {
        val (c, net) = client(Result.Success(pushMethodJson()))

        val result = c.verifyPushNotificationEnrollment("AM123", authSession = "sess")

        val body = bodyOf(net.lastRequest!!)
        assertEquals("sess", body.str("auth_session"))
        assertNull(body["otp_code"])
        assertIs<Result.Success<PushAuthenticationMethod>>(result)
    }

    @Test
    fun verifyEmailEnrollment_postsOtp() = runTest {
        val (c, net) = client(Result.Success(emailMethodJson()))

        val result = c.verifyEmailEnrollment("AM123", authSession = "sess", otpCode = "999")

        assertEquals("999", bodyOf(net.lastRequest!!).str("otp_code"))
        assertIs<Result.Success<EmailAuthenticationMethod>>(result)
        assertEquals("a@b.com", result.data.email)
    }

    @Test
    fun verifyPhoneEnrollment_postsOtp() = runTest {
        val (c, net) = client(Result.Success(phoneMethodJson()))

        val result = c.verifyPhoneEnrollment("AM123", authSession = "sess", otpCode = "424242")

        assertEquals("424242", bodyOf(net.lastRequest!!).str("otp_code"))
        assertIs<Result.Success<PhoneAuthenticationMethod>>(result)
        assertEquals(PhoneAuthenticationMethodType.SMS, result.data.preferredAuthenticationMethod)
    }

    @Test
    fun verifyRecoveryCodeEnrollment_postsAuthSessionOnly() = runTest {
        val (c, net) = client(Result.Success(recoveryCodeMethodJson()))

        val result = c.verifyRecoveryCodeEnrollment("AM123", authSession = "sess")

        val body = bodyOf(net.lastRequest!!)
        assertEquals("sess", body.str("auth_session"))
        assertNull(body["new_password"])
        assertIs<Result.Success<RecoveryCodeAuthenticationMethod>>(result)
    }

    @Test
    fun verifyPasswordEnrollment_postsNewPassword() = runTest {
        val (c, net) = client(Result.Success(passwordMethodJson()))

        val result = c.verifyPasswordEnrollment("AM123", authSession = "sess", newPassword = "s3cret!")

        val body = bodyOf(net.lastRequest!!)
        assertEquals("sess", body.str("auth_session"))
        assertEquals("s3cret!", body.str("new_password"))
        assertIs<Result.Success<PasswordAuthenticationMethod>>(result)
        assertEquals("uid", result.data.identityUserId)
    }

    @Test
    fun verifyPasskeyEnrollment_postsAuthnResponseFromCredential() = runTest {
        val (c, net) = client(Result.Success(passkeyMethodJson()))

        val result = c.verifyPasskeyEnrollment(
            credential = publicKeyCredentials(id = "cred-1"),
            challenge = passkeyChallenge(id = "AM123", authSession = "sess"),
        )

        val req = net.lastRequest!!
        assertEquals("$METHODS_PATH/AM123/verify", req.path)
        val body = bodyOf(req)
        assertEquals("sess", body.str("auth_session"))
        assertEquals("cred-1", body["authn_response"]!!.jsonObject.str("id"))
        assertIs<Result.Success<PasskeyAuthenticationMethod>>(result)
    }

    // --- Shared machinery (tested once, not per factor) ---

    @Test
    fun challenge_missingLocationHeader_failsWithMalformedResponse() = runTest {
        val (c, _) = client(Result.Success(totpChallengeJson()), headers = emptyMap())

        val result = c.totpEnrollmentChallenge()

        assertIs<Result.Failure<MyAccountError>>(result)
        assertIs<MyAccountError.MalformedResponse>(result.error)
    }

    @Test
    fun challenge_readsUrlDecodedLastSegmentOfLocation_caseInsensitively() = runTest {
        val (c, _) = client(
            outcome = Result.Success(totpChallengeJson()),
            headers = mapOf("location" to listOf("https://x/me/v1/authentication-methods/am%20123")),
        )

        val result = c.totpEnrollmentChallenge()

        assertIs<Result.Success<TotpEnrollmentChallenge>>(result)
        assertEquals("am 123", result.data.authenticationMethodId)
    }

    @Test
    fun request_usesBearerAuthHeader_whenNotDpopBound() = runTest {
        val (c, net) = client(Result.Success(totpChallengeJson()), location("AM1"), useDPoP = false)

        c.totpEnrollmentChallenge()

        assertEquals("Bearer test-token", net.lastRequest!!.headers["Authorization"])
    }

    @Test
    fun request_usesDpopAuthHeader_whenDpopBound() = runTest {
        val (c, net) = client(Result.Success(totpChallengeJson()), location("AM1"), useDPoP = true)

        c.totpEnrollmentChallenge()

        assertEquals("DPoP test-token", net.lastRequest!!.headers["Authorization"])
    }

    @Test
    fun options_mergeParametersIntoBody_andForwardHeaders() = runTest {
        val (c, net) = client(Result.Success(totpChallengeJson()), location("AM1"))

        c.totpEnrollmentChallenge(
            options = RequestOptions(
                parameters = mapOf("extra" to "value"),
                headers = mapOf("X-Test" to "1"),
            ),
        )

        val req = net.lastRequest!!
        assertEquals("value", bodyOf(req).str("extra"))
        assertEquals("1", req.headers["X-Test"])
    }

    @Test
    fun options_forwardRetryPolicyToNetworkClient() = runTest {
        val (c, net) = client(Result.Success(totpChallengeJson()), location("AM1"))
        val policy = RetryPolicy(
            maxAttempts = 2,
            backoff = Backoff.Fixed(Duration.ZERO),
            retryOn = { true },
        )

        c.totpEnrollmentChallenge(options = RequestOptions(retryPolicy = policy))

        assertSame(policy, net.lastRetryPolicy)
    }

    @Test
    fun challenge_propagatesTransportFailure_asMyAccountError() = runTest {
        val (c, _) = client(Result.Failure(TransportError.NoInternet))

        val result = c.totpEnrollmentChallenge()

        assertIs<Result.Failure<MyAccountError>>(result)
        assertIs<MyAccountError.Network>(result.error)
    }
}
