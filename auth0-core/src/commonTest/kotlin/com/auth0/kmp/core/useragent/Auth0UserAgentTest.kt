package com.auth0.kmp.core.useragent

import com.auth0.kmp.core.SDK_VERSION
import com.auth0.kmp.core.primitives.decodeBase64Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Auth0UserAgentTest {

    private fun UserAgent.decoded(): JsonObject =
        Json.parseToJsonElement(value.decodeBase64Url().decodeToString()).jsonObject

    private fun agent(
        name: String = Auth0UserAgent.DEFAULT_NAME,
        version: String = SDK_VERSION,
        env: Map<String, String> = mapOf("os" to "test"),
    ) = Auth0UserAgent(name = name, version = version, env = env)

    @Test
    fun default_headerName_is_Auth0Client() {
        assertEquals("Auth0-Client", Auth0UserAgent().headerName)
    }

    @Test
    fun default_name_and_version() {
        val json = Auth0UserAgent().decoded()
        assertEquals("auth0-kmp", json["name"]!!.jsonPrimitive.content)
        assertEquals(SDK_VERSION, json["version"]!!.jsonPrimitive.content)
    }

    @Test
    fun value_is_base64url_no_padding() {
        val value = Auth0UserAgent().value
        assertTrue(Regex("^[A-Za-z0-9_-]+$").matches(value), "unexpected chars in: $value")
    }

    @Test
    fun value_decodes_to_valid_json_with_env_object() {
        val json = agent().decoded()
        assertTrue("name" in json)
        assertTrue("version" in json)
        assertEquals("test", json["env"]!!.jsonObject["os"]!!.jsonPrimitive.content)
    }

    @Test
    fun custom_name_and_version_are_reported() {
        val json = agent(name = "acme", version = "9.9").decoded()
        assertEquals("acme", json["name"]!!.jsonPrimitive.content)
        assertEquals("9.9", json["version"]!!.jsonPrimitive.content)
    }

    @Test
    fun withLibrary_reports_wrapper_and_preserves_sdk_in_env() {
        val json = agent(version = "1.2.3")
            .withLibrary("acme-sdk", "2.0")
            .decoded()
        assertEquals("acme-sdk", json["name"]!!.jsonPrimitive.content)
        assertEquals("2.0", json["version"]!!.jsonPrimitive.content)
        assertEquals("1.2.3", json["env"]!!.jsonObject["auth0-kmp"]!!.jsonPrimitive.content)
    }

    @Test
    fun withLibrary_keeps_original_platform_env_entries() {
        val json = agent(env = mapOf("os" to "test"))
            .withLibrary("acme-sdk", "2.0")
            .decoded()
        assertEquals("test", json["env"]!!.jsonObject["os"]!!.jsonPrimitive.content)
    }
}
