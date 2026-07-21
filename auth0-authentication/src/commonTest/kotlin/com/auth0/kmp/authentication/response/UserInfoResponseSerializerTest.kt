package com.auth0.kmp.authentication.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAuth0Api::class)
class UserInfoResponseSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deserialize_routesKnownClaimsToFields_andUnknownToCustomClaims() {
        val payload = """
            {
              "sub": "auth0|123",
              "name": "Ada Lovelace",
              "given_name": "Ada",
              "email": "ada@example.com",
              "email_verified": true,
              "phone_number_verified": false,
              "address": {"country": "UK", "street_address": "10 Main St"},
              "updated_at": "2026-01-01T00:00:00Z",
              "custom_flag": "yes"
            }
        """.trimIndent()

        val profile = json.decodeFromString(UserInfoResponse.serializer(), payload)

        assertEquals("auth0|123", profile.sub)
        assertEquals("Ada Lovelace", profile.name)
        assertEquals("Ada", profile.givenName)
        assertEquals("ada@example.com", profile.email)
        assertEquals(true, profile.emailVerified)
        assertEquals(false, profile.phoneNumberVerified)
        assertEquals("UK", profile.address?.country)
        // snake_case address member must map to the typed camelCase field.
        assertEquals("10 Main St", profile.address?.streetAddress)
        assertEquals("2026-01-01T00:00:00Z", profile.updatedAt)

        // Unknown claim must land in the catch-all, not be dropped.
        assertEquals(JsonPrimitive("yes"), profile.customClaims["custom_flag"])
        // Known keys must NOT leak into the catch-all.
        assertTrue("sub" !in profile.customClaims)
        assertTrue("email" !in profile.customClaims)
        assertTrue("address" !in profile.customClaims)
    }

    @Test
    fun deserialize_explicitJsonNull_mapsToKotlinNull_notStringNull() {
        val payload = """
            {
              "sub": "auth0|123",
              "name": null,
              "nickname": null
            }
        """.trimIndent()

        val profile = json.decodeFromString(UserInfoResponse.serializer(), payload)

        assertEquals("auth0|123", profile.sub)
        // An explicit JSON null must decode to Kotlin null, not the literal "null".
        assertNull(profile.name)
        assertNull(profile.nickname)
    }

    @Test
    fun serialize_writesSnakeCaseKnownClaims_andSpreadsCustomClaims() {
        val profile = UserInfoResponse(
            sub = "auth0|123",
            givenName = "Ada",
            emailVerified = true,
            customClaims = mapOf("custom_flag" to JsonPrimitive("yes")),
        )

        val obj = Json.parseToJsonElement(
            json.encodeToString(UserInfoResponse.serializer(), profile),
        ).jsonObject

        assertEquals("auth0|123", obj["sub"]?.jsonPrimitive?.content)
        assertEquals("Ada", obj["given_name"]?.jsonPrimitive?.content)
        assertEquals(true, obj["email_verified"]?.jsonPrimitive?.booleanOrNull)
        // customClaims spread as top-level keys, not nested.
        assertEquals("yes", obj["custom_flag"]?.jsonPrimitive?.content)
        // Null typed fields are omitted.
        assertTrue("name" !in obj)
    }

    @Test
    fun roundTrip_preservesKnownAndCustomClaims() {
        val original = UserInfoResponse(
            sub = "auth0|123",
            name = "Ada Lovelace",
            email = "ada@example.com",
            emailVerified = true,
            address = AddressResponse(country = "UK"),
            customClaims = mapOf("custom_flag" to JsonPrimitive("yes")),
        )

        val decoded = json.decodeFromString(
            UserInfoResponse.serializer(),
            json.encodeToString(UserInfoResponse.serializer(), original),
        )

        assertEquals(original, decoded)
    }
}
