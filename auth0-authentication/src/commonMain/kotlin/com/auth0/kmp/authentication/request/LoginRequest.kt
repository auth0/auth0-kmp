package com.auth0.kmp.authentication.request

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LoginRequest(
    val username: String,
    val password: String,
    val realm: String,
    @SerialName("client_id") val clientId: String,
    val scope: String,
    val audience: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("grant_type")
    val grantType: String = "http://auth0.com/oauth/grant-type/password-realm",
)
