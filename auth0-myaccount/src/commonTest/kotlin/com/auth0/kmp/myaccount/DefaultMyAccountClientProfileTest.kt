package com.auth0.kmp.myaccount

import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.myaccount.error.MyAccountError
import com.auth0.kmp.myaccount.error.ValidationError
import com.auth0.kmp.myaccount.model.MyAccountProfile
import com.auth0.kmp.myaccount.model.ProfileFieldAccess
import com.auth0.kmp.myaccount.model.ProfileFieldPolicy
import com.auth0.kmp.myaccount.model.ProfileFieldSource
import com.auth0.kmp.myaccount.model.UpdateUserProfileRequest
import com.auth0.kmp.networking.request.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

private const val PROFILE_PATH = "/me/v1/profile"

class DefaultMyAccountClientProfileTest {

    // --- GET: wire contract ---

    @Test
    fun getUserProfile_sendsGetToProfilePath_withNoQueryByDefault() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.getUserProfile()

        val req = net.lastRequest!!
        assertEquals(HttpMethod.GET, req.method)
        assertEquals(PROFILE_PATH, req.path)
        assertEquals(emptyMap(), req.query)
        assertNull(req.body)
    }

    @Test
    fun getUserProfile_emptyFields_sendsNoQuery_evenWithIncludeFields() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.getUserProfile(fields = emptyList(), includeFields = true)

        assertEquals(emptyMap(), net.lastRequest!!.query)
    }

    @Test
    fun getUserProfile_nullFields_ignoresIncludeFields() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.getUserProfile(fields = null, includeFields = false)

        assertEquals(emptyMap(), net.lastRequest!!.query)
    }

    @Test
    fun getUserProfile_joinsFieldsWithCommas() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.getUserProfile(fields = listOf("given_name", "picture", "profile_policy"))

        assertEquals(
            mapOf("fields" to "given_name,picture,profile_policy"),
            net.lastRequest!!.query,
        )
    }

    @Test
    fun getUserProfile_includeFieldsTrue_isSentAsTrue() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.getUserProfile(fields = listOf("name"), includeFields = true)

        assertEquals(mapOf("fields" to "name", "include_fields" to "true"), net.lastRequest!!.query)
    }

    @Test
    fun getUserProfile_includeFieldsFalse_isSentAsFalse() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.getUserProfile(fields = listOf("name"), includeFields = false)

        assertEquals(mapOf("fields" to "name", "include_fields" to "false"), net.lastRequest!!.query)
    }

    @Test
    fun getUserProfile_usesBearerAuthHeader() = runTest {
        val (c, net) = client(Result.Success(profileJson()), useDPoP = false)

        c.getUserProfile()

        assertEquals("Bearer test-token", net.lastRequest!!.headers["Authorization"])
    }

    @Test
    fun getUserProfile_usesDpopAuthHeader_whenDpopBound() = runTest {
        val (c, net) = client(Result.Success(profileJson()), useDPoP = true)

        c.getUserProfile()

        assertEquals("DPoP test-token", net.lastRequest!!.headers["Authorization"])
    }

    @Test
    fun getUserProfile_optionsParametersGoToQuery_andSdkValuesWin() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.getUserProfile(
            fields = listOf("name"),
            options = RequestOptions(
                parameters = mapOf("extra" to "1", "fields" to "ignored"),
                headers = mapOf("X-Test" to "1"),
            ),
        )

        val req = net.lastRequest!!
        assertEquals(mapOf("extra" to "1", "fields" to "name"), req.query)
        assertEquals("1", req.headers["X-Test"])
    }

    // --- GET: mapping ---

    @Test
    fun getUserProfile_mapsEveryField() = runTest {
        val (c, _) = client(Result.Success(profileJson()))

        val result = c.getUserProfile()

        assertIs<Result.Success<MyAccountProfile>>(result)
        assertEquals(
            MyAccountProfile(
                userId = "auth0|123",
                givenName = "Jane",
                familyName = "Doe",
                name = "Jane Doe",
                nickname = "jane",
                picture = "https://example.com/jane.png",
                email = "jane@example.com",
                emailVerified = true,
                phoneNumber = "+15551234567",
                phoneVerified = false,
                username = "janed",
                createdAt = Instant.parse("2026-01-02T03:04:05Z"),
                updatedAt = Instant.parse("2026-03-04T05:06:07Z"),
                profilePolicy = mapOf(
                    "/given_name" to ProfileFieldPolicy(
                        access = ProfileFieldAccess.READ_WRITE,
                        source = ProfileFieldSource.USER,
                        reason = null,
                    ),
                    "/email" to ProfileFieldPolicy(
                        access = ProfileFieldAccess.READ_ONLY,
                        source = ProfileFieldSource.IDP,
                        reason = "managed_by_identity_provider",
                    ),
                ),
            ),
            result.data,
        )
    }

    @Test
    fun getUserProfile_readOnlyFieldsAreNull_whenAbsent() = runTest {
        val (c, _) = client(Result.Success(profileWithoutReadOnlyFieldsJson()))

        val result = c.getUserProfile()

        assertIs<Result.Success<MyAccountProfile>>(result)
        assertNull(result.data.userId)
        assertNull(result.data.emailVerified)
        assertNull(result.data.phoneVerified)
        assertEquals("Jane", result.data.givenName)
    }

    @Test
    fun getUserProfile_profilePolicyIsNull_whenAbsent() = runTest {
        val (c, _) = client(Result.Success(profileWithoutReadOnlyFieldsJson()))

        val result = c.getUserProfile(fields = listOf("given_name"))

        assertIs<Result.Success<MyAccountProfile>>(result)
        assertNull(result.data.profilePolicy)
    }

    @Test
    fun getUserProfile_unknownAccessAndSource_becomeNull_andProfileStillDecodes() = runTest {
        val json = """{"given_name":"Jane","profile_policy":{"/given_name":""" +
            """{"access":"write_only","source":"quantum","reason":"new_reason"}}}"""
        val (c, _) = client(Result.Success(json))

        val result = c.getUserProfile()

        assertIs<Result.Success<MyAccountProfile>>(result)
        assertEquals("Jane", result.data.givenName)
        assertEquals(
            ProfileFieldPolicy(access = null, source = null, reason = "new_reason"),
            result.data.profilePolicy!!["/given_name"],
        )
    }

    @Test
    fun getUserProfile_emptyObject_mapsToEmptyProfile() = runTest {
        val (c, _) = client(Result.Success("{}"))

        val result = c.getUserProfile()

        assertIs<Result.Success<MyAccountProfile>>(result)
        assertEquals(MyAccountProfile(), result.data)
    }

    // --- PATCH: wire contract ---

    @Test
    fun updateUserProfile_sendsPatchToProfilePath_withBearerAuth() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.updateUserProfile(UpdateUserProfileRequest(givenName = "Jane"))

        val req = net.lastRequest!!
        assertEquals(HttpMethod.PATCH, req.method)
        assertEquals(PROFILE_PATH, req.path)
        assertEquals("Bearer test-token", req.headers["Authorization"])
        assertEquals(emptyMap(), req.query)
    }

    @Test
    fun updateUserProfile_usesDpopAuthHeader_whenDpopBound() = runTest {
        val (c, net) = client(Result.Success(profileJson()), useDPoP = true)

        c.updateUserProfile(UpdateUserProfileRequest(givenName = "Jane"))

        assertEquals("DPoP test-token", net.lastRequest!!.headers["Authorization"])
    }

    @Test
    fun updateUserProfile_sendsOnlyNonNullProperties() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.updateUserProfile(UpdateUserProfileRequest(givenName = "Jane", nickname = "jane"))

        val body = bodyOf(net.lastRequest!!)
        assertEquals(setOf("given_name", "nickname"), body.keys)
        assertEquals("Jane", body.str("given_name"))
        assertEquals("jane", body.str("nickname"))
    }

    @Test
    fun updateUserProfile_sendsAllFiveProperties() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.updateUserProfile(
            UpdateUserProfileRequest(
                givenName = "Jane",
                familyName = "Doe",
                name = "Jane Doe",
                nickname = "jane",
                picture = "https://example.com/jane.png",
            ),
        )

        val body = bodyOf(net.lastRequest!!)
        assertEquals(
            setOf("given_name", "family_name", "name", "nickname", "picture"),
            body.keys,
        )
        assertEquals("Jane", body.str("given_name"))
        assertEquals("Doe", body.str("family_name"))
        assertEquals("Jane Doe", body.str("name"))
        assertEquals("jane", body.str("nickname"))
        assertEquals("https://example.com/jane.png", body.str("picture"))
    }

    @Test
    fun updateUserProfile_emptyRequest_stillSendsEmptyObject() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        val result = c.updateUserProfile(UpdateUserProfileRequest())

        assertEquals(1, net.callCount)
        assertEquals("{}", net.lastRequest!!.body)
        assertIs<Result.Success<MyAccountProfile>>(result)
    }

    @Test
    fun updateUserProfile_neverSendsReadOnlyFields() = runTest {
        val (c, net) = client(Result.Success(profileJson()))

        c.updateUserProfile(UpdateUserProfileRequest(givenName = "Jane"))

        val body = bodyOf(net.lastRequest!!)
        assertNull(body["user_id"])
        assertNull(body["email_verified"])
        assertNull(body["phone_verified"])
    }

    @Test
    fun updateUserProfile_mapsFullUpdatedProfile() = runTest {
        val (c, _) = client(Result.Success(profileJson()))

        val result = c.updateUserProfile(UpdateUserProfileRequest(givenName = "Jane"))

        assertIs<Result.Success<MyAccountProfile>>(result)
        assertEquals("Jane", result.data.givenName)
        assertEquals(Instant.parse("2026-03-04T05:06:07Z"), result.data.updatedAt)
        assertEquals(2, result.data.profilePolicy!!.size)
    }

    // --- Errors ---

    @Test
    fun getUserProfile_403_mapsToApiError() = runTest {
        val body = problemJson(403, "https://auth0.com/api-errors/A0E-403-0003", "Not Entitled")
        val (c, _) = client(Result.Failure(TransportError.Server(403, emptyMap(), body)))

        val result = c.getUserProfile()

        assertIs<Result.Failure<MyAccountError>>(result)
        val error = assertIs<MyAccountError.ApiError>(result.error)
        assertEquals(403, error.status)
        assertEquals("Not Entitled", error.title)
        assertEquals(emptyList(), error.validationErrors)
    }

    @Test
    fun getUserProfile_429_mapsToApiError() = runTest {
        val body = problemJson(429, "https://auth0.com/api-errors/A0E-429-0001", "Too Many Requests")
        val (c, _) = client(Result.Failure(TransportError.Server(429, emptyMap(), body)))

        val result = c.getUserProfile()

        assertIs<Result.Failure<MyAccountError>>(result)
        val error = assertIs<MyAccountError.ApiError>(result.error)
        assertEquals(429, error.status)
        assertEquals("Too Many Requests", error.title)
        assertEquals("https://auth0.com/api-errors/A0E-429-0001", error.type)
    }

    @Test
    fun updateUserProfile_400ReadOnlyField_exposesValidationErrors() = runTest {
        val body = problemJson(
            400,
            "https://auth0.com/api-errors/A0E-400-0008",
            "Read-only Field",
            """[{"pointer":"/given_name","detail":"Field is read-only","source":"body"}]""",
        )
        val (c, _) = client(Result.Failure(TransportError.Server(400, emptyMap(), body)))

        val result = c.updateUserProfile(UpdateUserProfileRequest(givenName = "Jane"))

        assertIs<Result.Failure<MyAccountError>>(result)
        val error = assertIs<MyAccountError.ApiError>(result.error)
        assertEquals(400, error.status)
        assertEquals("Read-only Field", error.title)
        assertEquals(
            listOf(ValidationError(detail = "Field is read-only", pointer = "/given_name", source = "body")),
            error.validationErrors,
        )
    }

    @Test
    fun updateUserProfile_400InvalidValue_exposesBothValidationErrors() = runTest {
        val body = problemJson(
            400,
            "https://auth0.com/api-errors/A0E-400-0003",
            "Invalid Request Payload",
            """[{"pointer":"/picture","detail":"must be a valid URL","source":"body"},""" +
                """{"pointer":"/picture","detail":"must match format uri","source":"body"}]""",
        )
        val (c, _) = client(Result.Failure(TransportError.Server(400, emptyMap(), body)))

        val result = c.updateUserProfile(UpdateUserProfileRequest(picture = "not a url"))

        assertIs<Result.Failure<MyAccountError>>(result)
        val error = assertIs<MyAccountError.ApiError>(result.error)
        assertEquals(2, error.validationErrors.size)
        assertEquals(listOf("/picture", "/picture"), error.validationErrors.map { it.pointer })
    }

    @Test
    fun updateUserProfile_429_mapsToApiError() = runTest {
        val body = problemJson(429, "https://auth0.com/api-errors/A0E-429-0001", "Too Many Requests")
        val (c, _) = client(Result.Failure(TransportError.Server(429, emptyMap(), body)))

        val result = c.updateUserProfile(UpdateUserProfileRequest(nickname = "jane"))

        assertIs<Result.Failure<MyAccountError>>(result)
        assertEquals(429, assertIs<MyAccountError.ApiError>(result.error).status)
    }

    @Test
    fun profile_networkFailure_mapsToNetworkError() = runTest {
        val (c, _) = client(Result.Failure(TransportError.NoInternet))

        val get = c.getUserProfile()
        val update = c.updateUserProfile(UpdateUserProfileRequest(nickname = "jane"))

        assertIs<MyAccountError.Network>(assertIs<Result.Failure<MyAccountError>>(get).error)
        assertIs<MyAccountError.Network>(assertIs<Result.Failure<MyAccountError>>(update).error)
    }
}
