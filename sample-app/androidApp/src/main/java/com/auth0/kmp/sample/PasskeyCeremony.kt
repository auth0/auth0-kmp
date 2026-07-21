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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// Runs the on-device WebAuthn ceremony through the AndroidX Credential Manager.
// The SDK is HTTP-only for passkeys: it hands us the server's challenge and
// expects the finished credential back, so the sample owns the platform ceremony.
//
// Both directions round-trip through W3C JSON: the SDK's challenge options are
// already the shape the platform wants (serialize -> requestJson), and the
// credential the authenticator returns is the shape the SDK wants (parse JSON ->
// PublicKeyCredentials), so no field re-keying is needed. The system passkey UI
// is anchored to the Activity passed on each call, so no Context is retained.
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
            request = CreatePublicKeyCredentialRequest(requestJson = json.encodeToString(options)),
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
                listOf(GetPublicKeyCredentialOption(requestJson = json.encodeToString(options))),
            ),
        )
        val credential = response.credential as PublicKeyCredential
        return parseCredential(credential.authenticationResponseJson)
    }

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
