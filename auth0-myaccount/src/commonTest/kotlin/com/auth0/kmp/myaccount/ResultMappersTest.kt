package com.auth0.kmp.myaccount

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.myaccount.error.MyAccountError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResultMappersTest {

    @Test
    fun noInternetAndTimeout_mapToNetwork() {
        assertIs<MyAccountError.Network>(TransportError.NoInternet.toMyAccountError())
        assertIs<MyAccountError.Network>(TransportError.Timeout.toMyAccountError())
    }

    @Test
    fun server_withProblemDetailsBody_mapsToApiError() {
        val server = TransportError.Server(
            status = 403,
            headers = emptyMap(),
            body = """{"type":"about:blank","title":"Forbidden","detail":"nope","status":403}""",
        )

        val error = server.toMyAccountError()

        assertIs<MyAccountError.ApiError>(error)
        assertEquals("Forbidden", error.title)
        assertEquals("nope", error.detail)
        assertEquals(403, error.status)
        assertEquals(emptyList(), error.validationErrors)
    }

    @Test
    fun server_withUnparseableBody_mapsToUnknown() {
        val server = TransportError.Server(status = 500, headers = emptyMap(), body = "not json")

        val error = server.toMyAccountError()

        assertIs<MyAccountError.Unknown>(error)
        assertEquals(server, error.cause)
    }

    @Test
    fun server_withNullBody_mapsToUnknown() {
        val server = TransportError.Server(status = 500, headers = emptyMap(), body = null)

        assertIs<MyAccountError.Unknown>(server.toMyAccountError())
    }

    @Test
    fun serializationAndUnknown_mapToUnknown() {
        assertIs<MyAccountError.Unknown>(TransportError.Serialization("bad").toMyAccountError())
        assertIs<MyAccountError.Unknown>(TransportError.Unknown("boom").toMyAccountError())
    }
}
