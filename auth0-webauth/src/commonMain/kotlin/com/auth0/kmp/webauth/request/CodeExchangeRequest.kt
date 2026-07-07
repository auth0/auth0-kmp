package com.auth0.kmp.webauth.request

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CodeExchangeRequest(
    val code: String,
    @SerialName("code_verifier") val codeVerifier: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("client_id") val clientId: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("grant_type")
    val grantType: String = "authorization_code",
)
