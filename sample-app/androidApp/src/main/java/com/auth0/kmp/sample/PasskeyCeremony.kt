package com.auth0.kmp.sample

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.auth0.kmp.authentication.model.AuthParamsPublicKey
import com.auth0.kmp.authentication.model.AuthnParamsPublicKey
import com.auth0.kmp.authentication.model.PublicKeyCredentials
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// Runs the on-device WebAuthn ceremony through the AndroidX Credential Manager.
// The SDK is HTTP-only for passkeys: it hands us the server's challenge and
// expects the finished credential back, so the sample owns the platform ceremony.
//
// The SDK exposes challenge options as clean, re-keyed domain types (e.g.
// relyingParty), so this maps them to the W3C PublicKeyCredential*Options JSON
// (e.g. "rp") the Credential Manager's requestJson expects. The credential the
// authenticator returns is already the shape the SDK wants (parse JSON ->
// PublicKeyCredentials). The system passkey UI is anchored to the Activity passed
// on each call, so no Context is retained.
object PasskeyCeremony {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    /**
     * Registers a new passkey for the user described by a signup challenge.
     *
     * @param activity the Activity to anchor the system passkey UI to.
     * @param options the public-key creation options from the registration challenge.
     */
    suspend fun register(
        activity: Context,
        options: AuthnParamsPublicKey,
    ): PublicKeyCredentials {
        val response = CredentialManager.create(activity).createCredential(
            context = activity,
            request = CreatePublicKeyCredentialRequest(requestJson = options.toRequestJson()),
        ) as CreatePublicKeyCredentialResponse
        return parseCredential(response.registrationResponseJson)
    }

    /**
     * Asserts an existing passkey for a login challenge.
     *
     * @param activity the Activity to anchor the system passkey UI to.
     * @param options the public-key request options from the login challenge.
     */
    suspend fun authenticate(
        activity: Context,
        options: AuthParamsPublicKey,
    ): PublicKeyCredentials {
        val response = CredentialManager.create(activity).getCredential(
            context = activity,
            request = GetCredentialRequest(
                listOf(GetPublicKeyCredentialOption(requestJson = options.toRequestJson())),
            ),
        )
        val credential = response.credential as PublicKeyCredential
        return parseCredential(credential.authenticationResponseJson)
    }

    // Maps the SDK's typed login options to the W3C
    // PublicKeyCredentialRequestOptions JSON the platform expects.
    private fun AuthParamsPublicKey.toRequestJson(): String =
        buildJsonObject {
            put("challenge", challenge)
            put("rpId", rpId)
            put("timeout", timeout)
            put("userVerification", userVerification)
        }.toString()

    // Maps the SDK's typed creation options to the W3C
    // PublicKeyCredentialCreationOptions JSON the platform expects.
    // The SDK's relyingParty is re-keyed to the W3C "rp".
    private fun AuthnParamsPublicKey.toRequestJson(): String =
        buildJsonObject {
            put("challenge", challenge)
            putJsonObject("rp") {
                put("id", relyingParty.id)
                put("name", relyingParty.name)
            }
            putJsonObject("user") {
                put("id", user.id)
                put("name", user.name)
                put("displayName", user.displayName)
            }
            putJsonArray("pubKeyCredParams") {
                pubKeyCredParams.forEach {
                    addJsonObject {
                        put("type", it.type)
                        put("alg", it.alg)
                    }
                }
            }
            put("timeout", timeout)
            putJsonObject("authenticatorSelection") {
                put("residentKey", authenticatorSelection.residentKey)
                put("userVerification", authenticatorSelection.userVerification)
            }
        }.toString()

    private fun parseCredential(responseJson: String): PublicKeyCredentials {
        // The platform emits clientExtensionResults as an empty object when no
        // credProps was produced (typical on assertion), but the SDK model
        // requires credProps inside it. Drop the key when it has no credProps so
        // the surrounding credential still deserializes.
        val root = json.parseToJsonElement(responseJson) as JsonObject
        val ext = root["clientExtensionResults"] as? JsonObject
        val cleaned = if (ext != null && ext.containsKey("credProps")) {
            root
        } else {
            JsonObject(root - "clientExtensionResults")
        }
        return json.decodeFromJsonElement(PublicKeyCredentials.serializer(), cleaned)
    }
}
