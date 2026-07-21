package com.auth0.kmp.core.token

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.serialization.json.JsonObject

@InternalAuth0Api
public interface TokenGrant {

    /**
     * The `/oauth/token` request body for this grant, including `grant_type` and
     * `client_id`. Values are usually strings; a grant may nest a JSON object
     * where the endpoint requires one, such as a passkey assertion.
     */
    public val parameters: JsonObject
}
