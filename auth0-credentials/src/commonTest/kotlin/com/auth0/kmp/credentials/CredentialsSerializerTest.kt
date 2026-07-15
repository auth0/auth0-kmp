package com.auth0.kmp.credentials

import com.auth0.kmp.core.model.Credentials
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class CredentialsSerializerTest {

    @Test
    fun round_trips_all_fields() {
        val credentials = Credentials(
            accessToken = "access-token",
            idToken = "id-token",
            tokenType = "Bearer",
            expiresAt = Instant.fromEpochSeconds(1_700_000_000),
            refreshToken = "refresh-token",
            scope = "openid profile email",
        )

        val decoded = CredentialsSerializer.decode(CredentialsSerializer.encode(credentials))

        assertEquals(credentials, decoded.credentials)
        assertEquals(null, decoded.dpopThumbprint)
    }

    @Test
    fun round_trips_when_optional_fields_are_null() {
        val credentials = Credentials(
            accessToken = "access-token",
            idToken = "id-token",
            tokenType = "Bearer",
            expiresAt = Instant.fromEpochSeconds(0),
            refreshToken = null,
            scope = null,
        )

        val decoded = CredentialsSerializer.decode(CredentialsSerializer.encode(credentials))

        assertEquals(credentials, decoded.credentials)
    }

    @Test
    fun round_trips_dpop_thumbprint() {
        val credentials = Credentials(
            accessToken = "access-token",
            idToken = "id-token",
            tokenType = "DPoP",
            expiresAt = Instant.fromEpochSeconds(1_700_000_000),
            refreshToken = "refresh-token",
            scope = "openid",
        )

        val decoded = CredentialsSerializer.decode(
            CredentialsSerializer.encode(credentials, dpopThumbprint = "the-jkt"),
        )

        assertEquals(credentials, decoded.credentials)
        assertEquals("the-jkt", decoded.dpopThumbprint)
    }

    @Test
    fun decode_throws_on_malformed_value() {
        assertFailsWith<SerializationException> {
            CredentialsSerializer.decode("not-valid-json")
        }
    }
}
