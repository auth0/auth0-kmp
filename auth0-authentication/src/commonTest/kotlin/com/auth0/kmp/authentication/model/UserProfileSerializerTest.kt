package com.auth0.kmp.authentication.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserProfileSerializerTest {

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
              "address": {"country": "UK"},
              "updated_at": "2026-01-01T00:00:00Z",
              "custom_flag": "yes"
            }
        """.trimIndent()

        val profile = json.decodeFromString(UserProfile.serializer(), payload)

        assertEquals("auth0|123", profile.sub)
        assertEquals("Ada Lovelace", profile.name)
        assertEquals("Ada", profile.givenName)
        assertEquals("ada@example.com", profile.email)
        assertEquals(true, profile.emailVerified)
        assertEquals(false, profile.phoneNumberVerified)
        assertEquals(mapOf("country" to "UK"), profile.address)
        assertEquals("2026-01-01T00:00:00Z", profile.updatedAt)

        // Unknown claim must land in the catch-all, not be dropped.
        assertEquals(JsonPrimitive("yes"), profile.customClaims["custom_flag"])
        // Known keys must NOT leak into the catch-all.
        assertTrue("sub" !in profile.customClaims)
        assertTrue("email" !in profile.customClaims)
        assertTrue("address" !in profile.customClaims)
    }

    @Test
    fun serialize_writesSnakeCaseKnownClaims_andSpreadsCustomClaims() {
        val profile = UserProfile(
            sub = "auth0|123",
            givenName = "Ada",
            emailVerified = true,
            customClaims = mapOf("custom_flag" to JsonPrimitive("yes")),
        )

        val obj = Json.parseToJsonElement(
            json.encodeToString(UserProfile.serializer(), profile),
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
        val original = UserProfile(
            sub = "auth0|123",
            name = "Ada Lovelace",
            email = "ada@example.com",
            emailVerified = true,
            address = mapOf("country" to "UK"),
            customClaims = mapOf("custom_flag" to JsonPrimitive("yes")),
        )

        val decoded = json.decodeFromString(
            UserProfile.serializer(),
            json.encodeToString(UserProfile.serializer(), original),
        )

        assertEquals(original, decoded)
    }
}
