package com.auth0.kmp.credentials

import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.error.TransportError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CredentialsManagerErrorMapperTest {

    @Test
    fun no_internet_maps_to_network() {
        val error = TransportError.NoInternet.toCredentialsManagerError()

        val network = assertIs<CredentialsManagerError.Network>(error)
        assertEquals(TransportError.NoInternet, network.cause)
    }

    @Test
    fun timeout_maps_to_network() {
        val error = TransportError.Timeout.toCredentialsManagerError()

        val network = assertIs<CredentialsManagerError.Network>(error)
        assertEquals(TransportError.Timeout, network.cause)
    }

    @Test
    fun server_with_auth0_error_body_maps_to_api_error() {
        val body = """{"error":"invalid_grant","error_description":"refresh token is invalid"}"""
        val error = TransportError.Server(status = 403, body = body).toCredentialsManagerError()

        val apiError = assertIs<CredentialsManagerError.ApiError>(error)
        assertEquals("invalid_grant", apiError.code)
        assertEquals("refresh token is invalid", apiError.errorDescription)
        assertEquals(403, apiError.statusCode)
    }

    @Test
    fun server_with_unparseable_body_maps_to_unknown() {
        val transport = TransportError.Server(status = 500, body = "not-json")
        val error = transport.toCredentialsManagerError()

        val unknown = assertIs<CredentialsManagerError.Unknown>(error)
        assertEquals(transport, unknown.cause)
    }

    @Test
    fun serialization_maps_to_unknown() {
        val transport = TransportError.Serialization("boom")
        val error = transport.toCredentialsManagerError()

        val unknown = assertIs<CredentialsManagerError.Unknown>(error)
        assertEquals(transport, unknown.cause)
    }

    @Test
    fun unknown_maps_to_unknown() {
        val transport = TransportError.Unknown("boom")
        val error = transport.toCredentialsManagerError()

        val unknown = assertIs<CredentialsManagerError.Unknown>(error)
        assertEquals(transport, unknown.cause)
    }
}
