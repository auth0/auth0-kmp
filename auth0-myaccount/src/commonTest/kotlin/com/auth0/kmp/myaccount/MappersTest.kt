package com.auth0.kmp.myaccount

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.myaccount.model.CredentialDeviceType
import com.auth0.kmp.myaccount.model.PhoneAuthenticationMethodType
import com.auth0.kmp.myaccount.model.ProfileFieldAccess
import com.auth0.kmp.myaccount.model.ProfileFieldPolicy
import com.auth0.kmp.myaccount.model.ProfileFieldSource
import com.auth0.kmp.myaccount.response.CredentialDeviceTypeResponse
import com.auth0.kmp.myaccount.response.MyAccountProfileResponse
import com.auth0.kmp.myaccount.response.PasswordAuthenticationMethodResponse
import com.auth0.kmp.myaccount.response.PhoneAuthenticationMethodTypeResponse
import com.auth0.kmp.myaccount.response.ProfileFieldPolicyResponse
import com.auth0.kmp.myaccount.response.TotpAuthenticationMethodResponse
import com.auth0.kmp.myaccount.response.toCredentialDeviceType
import com.auth0.kmp.myaccount.response.toMyAccountProfile
import com.auth0.kmp.myaccount.response.toPasswordAuthenticationMethod
import com.auth0.kmp.myaccount.response.toPhoneAuthenticationMethodType
import com.auth0.kmp.myaccount.response.toProfileFieldPolicy
import com.auth0.kmp.myaccount.response.toTotpAuthenticationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(InternalAuth0Api::class)
class MappersTest {

    @Test
    fun toTotpAuthenticationMethod_parsesCreatedAt_andToleratesNullOptionals() {
        val method = TotpAuthenticationMethodResponse(
            id = "am1",
            type = "totp",
            createdAt = "2026-01-02T03:04:05Z",
            usage = listOf("mfa"),
            confirmed = null,
            lastAuthAt = null,
            name = null,
        ).toTotpAuthenticationMethod()

        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), method.createdAt)
        assertNull(method.lastAuthAt)
        assertNull(method.confirmed)
        assertNull(method.name)
    }

    @Test
    fun toTotpAuthenticationMethod_parsesLastAuthAt_whenPresent() {
        val method = TotpAuthenticationMethodResponse(
            id = "am1",
            type = "totp",
            createdAt = "2026-01-02T03:04:05Z",
            usage = listOf("mfa"),
            confirmed = true,
            lastAuthAt = "2026-03-04T05:06:07Z",
            name = "My authenticator",
        ).toTotpAuthenticationMethod()

        assertEquals(Instant.parse("2026-03-04T05:06:07Z"), method.lastAuthAt)
        assertEquals(true, method.confirmed)
        assertEquals("My authenticator", method.name)
    }

    @Test
    fun toPasswordAuthenticationMethod_parsesLastPasswordReset_orNull() {
        val withReset = PasswordAuthenticationMethodResponse(
            id = "am1",
            type = "password",
            createdAt = "2026-01-02T03:04:05Z",
            usage = listOf("password"),
            identityUserId = "uid",
            lastPasswordReset = "2026-02-03T04:05:06Z",
        ).toPasswordAuthenticationMethod()

        assertEquals("uid", withReset.identityUserId)
        assertEquals(Instant.parse("2026-02-03T04:05:06Z"), withReset.lastPasswordReset)

        val withoutReset = PasswordAuthenticationMethodResponse(
            id = "am1",
            type = "password",
            createdAt = "2026-01-02T03:04:05Z",
            usage = listOf("password"),
            identityUserId = null,
            lastPasswordReset = null,
        ).toPasswordAuthenticationMethod()

        assertNull(withoutReset.identityUserId)
        assertNull(withoutReset.lastPasswordReset)
    }

    @Test
    fun toPhoneAuthenticationMethodType_mapsBothChannels() {
        assertEquals(
            PhoneAuthenticationMethodType.SMS,
            PhoneAuthenticationMethodTypeResponse.SMS.toPhoneAuthenticationMethodType(),
        )
        assertEquals(
            PhoneAuthenticationMethodType.VOICE,
            PhoneAuthenticationMethodTypeResponse.VOICE.toPhoneAuthenticationMethodType(),
        )
    }

    @Test
    fun toCredentialDeviceType_mapsBothVariants() {
        assertEquals(
            CredentialDeviceType.SINGLE_DEVICE,
            CredentialDeviceTypeResponse.SINGLE_DEVICE.toCredentialDeviceType(),
        )
        assertEquals(
            CredentialDeviceType.MULTI_DEVICE,
            CredentialDeviceTypeResponse.MULTI_DEVICE.toCredentialDeviceType(),
        )
    }

    @Test
    fun toMyAccountProfile_parsesTimestamps_orNull() {
        val withTimestamps = MyAccountProfileResponse(
            createdAt = "2026-01-02T03:04:05Z",
            updatedAt = "2026-03-04T05:06:07Z",
        ).toMyAccountProfile()

        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), withTimestamps.createdAt)
        assertEquals(Instant.parse("2026-03-04T05:06:07Z"), withTimestamps.updatedAt)

        val without = MyAccountProfileResponse().toMyAccountProfile()

        assertNull(without.createdAt)
        assertNull(without.updatedAt)
    }

    @Test
    fun toMyAccountProfile_mapsReadOnlyFields_andKeepsPolicyNullWhenAbsent() {
        val profile = MyAccountProfileResponse(
            userId = "auth0|1",
            emailVerified = true,
            phoneVerified = false,
        ).toMyAccountProfile()

        assertEquals("auth0|1", profile.userId)
        assertEquals(true, profile.emailVerified)
        assertEquals(false, profile.phoneVerified)
        assertNull(profile.profilePolicy)
    }

    @Test
    fun toMyAccountProfile_keepsJsonPointerKeysOfPolicy() {
        val profile = MyAccountProfileResponse(
            profilePolicy = mapOf("/given_name" to ProfileFieldPolicyResponse(access = "read_write")),
        ).toMyAccountProfile()

        assertEquals(setOf("/given_name"), profile.profilePolicy!!.keys)
    }

    @Test
    fun toProfileFieldPolicy_mapsEveryKnownAccessAndSource() {
        assertEquals(
            ProfileFieldPolicy(ProfileFieldAccess.READ_ONLY, ProfileFieldSource.IDP, "managed_by_identity_provider"),
            ProfileFieldPolicyResponse("read_only", "idp", "managed_by_identity_provider").toProfileFieldPolicy(),
        )
        assertEquals(
            ProfileFieldPolicy(ProfileFieldAccess.READ_WRITE, ProfileFieldSource.USER, null),
            ProfileFieldPolicyResponse("read_write", "user").toProfileFieldPolicy(),
        )
        assertEquals(
            ProfileFieldSource.SYSTEM,
            ProfileFieldPolicyResponse(source = "system").toProfileFieldPolicy().source,
        )
    }

    @Test
    fun toProfileFieldPolicy_unknownOrMissingValues_becomeNull() {
        val policy = ProfileFieldPolicyResponse(access = "READ_ONLY", source = "").toProfileFieldPolicy()

        assertNull(policy.access)
        assertNull(policy.source)
        assertNull(ProfileFieldPolicyResponse().toProfileFieldPolicy().access)
    }
}
