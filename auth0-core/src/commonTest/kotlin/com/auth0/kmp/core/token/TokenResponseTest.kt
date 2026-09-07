package com.auth0.kmp.core.token

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private class FixedTestClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}

@OptIn(InternalAuth0Api::class, ExperimentalTime::class)
class TokenResponseTest {

    private fun response(
        idToken: String? = "idt",
        issuedTokenType: String? = "urn:ietf:params:oauth:token-type:session_transfer",
    ) = TokenResponse(
        accessToken = "stt",
        idToken = idToken,
        tokenType = "Bearer",
        expiresIn = 3600,
        refreshToken = "rt",
        scope = null,
        issuedTokenType = issuedTokenType,
    )

    @Test
    fun toSsoCredentials_mapsAllFields_withClockExpiry() {
        val sso = response().toSsoCredentials(FixedTestClock(Instant.fromEpochSeconds(1_000)))

        assertEquals("stt", sso.sessionTransferToken)
        assertEquals("urn:ietf:params:oauth:token-type:session_transfer", sso.issuedTokenType)
        assertEquals("idt", sso.idToken)
        assertEquals("rt", sso.refreshToken)
        assertEquals(Instant.fromEpochSeconds(1_000 + 3600), sso.expiresAt)
    }

    @Test
    fun toSsoCredentials_missingIssuedTokenType_throwsSerialization() {
        assertFailsWith<SerializationException> {
            response(issuedTokenType = null).toSsoCredentials(FixedTestClock(Instant.fromEpochSeconds(0)))
        }
    }

    @Test
    fun toSsoCredentials_missingIdToken_throwsSerialization() {
        assertFailsWith<SerializationException> {
            response(idToken = null).toSsoCredentials(FixedTestClock(Instant.fromEpochSeconds(0)))
        }
    }
}
