package com.auth0.kmp.authentication.error

import com.auth0.kmp.core.error.TransportError
import kotlin.test.Test
import kotlin.test.assertEquals

class TransportErrorMappingTest {

    @Test
    fun noInternet_mapsToNetwork() {
        assertEquals(
            AuthenticationError.Network(TransportError.NoInternet),
            TransportError.NoInternet.toAuthenticationError(),
        )
    }

    @Test
    fun timeout_mapsToNetwork() {
        assertEquals(
            AuthenticationError.Network(TransportError.Timeout),
            TransportError.Timeout.toAuthenticationError(),
        )
    }

    @Test
    fun serverWithParseableBody_mapsToApiError() {
        val server = TransportError.Server(
            403,
            """{"error":"invalid_grant","error_description":"Wrong creds"}""",
        )

        assertEquals(
            AuthenticationError.ApiError("invalid_grant", "Wrong creds", 403),
            server.toAuthenticationError(),
        )
    }

    @Test
    fun serverWithoutDescription_fallsBackToCode() {
        val server = TransportError.Server(429, """{"error":"too_many_attempts"}""")

        assertEquals(
            AuthenticationError.ApiError("too_many_attempts", "too_many_attempts", 429),
            server.toAuthenticationError(),
        )
    }

    @Test
    fun serverWithUnparseableBody_mapsToUnknown() {
        val server = TransportError.Server(502, "<html>502</html>")

        assertEquals(AuthenticationError.Unknown(server), server.toAuthenticationError())
    }

    @Test
    fun serverWithNullBody_mapsToUnknown() {
        val server = TransportError.Server(500, null)

        assertEquals(AuthenticationError.Unknown(server), server.toAuthenticationError())
    }

    @Test
    fun serverWithJsonMissingError_mapsToUnknown() {
        val server = TransportError.Server(400, """{"message":"bad"}""")

        assertEquals(AuthenticationError.Unknown(server), server.toAuthenticationError())
    }

    @Test
    fun serialization_mapsToUnknown() {
        val error = TransportError.Serialization("boom")

        assertEquals(AuthenticationError.Unknown(error), error.toAuthenticationError())
    }

    @Test
    fun transportUnknown_mapsToUnknown() {
        val error = TransportError.Unknown("weird")

        assertEquals(AuthenticationError.Unknown(error), error.toAuthenticationError())
    }
}
