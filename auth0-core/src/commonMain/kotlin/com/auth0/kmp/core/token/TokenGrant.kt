package com.auth0.kmp.core.token

import com.auth0.kmp.core.annotation.InternalAuth0Api

@InternalAuth0Api
public interface TokenGrant {

    /**
     * The `/oauth/token` form parameters for this grant, including `grant_type`
     * and `client_id`.
     */
    public val parameters: Map<String, String>
}
