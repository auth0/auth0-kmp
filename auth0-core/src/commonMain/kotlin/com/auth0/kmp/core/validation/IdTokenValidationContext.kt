package com.auth0.kmp.core.validation

import com.auth0.kmp.core.annotation.InternalAuth0Api

/**
 * Per-request inputs that enable the optional ID token claim checks.
 *
 * Each property gates one check: the check runs only when its property is
 * non-null. A default-constructed context enables none of them, leaving only
 * the always-on claim checks (`iss`, `sub`, `aud`, `exp`, `iat`).
 *
 * @property nonce the value sent on the authorization request; when present the
 *   token's `nonce` claim must equal it.
 * @property maxAge the maximum authentication age in seconds; when present the
 *   token's `auth_time` claim must be within this age.
 * @property organization the expected organization; when present it is matched
 *   against the token's `org_id` claim if it begins with `org_`, otherwise
 *   against the `org_name` claim.
 */
@InternalAuth0Api
public data class IdTokenValidationContext(
    val nonce: String? = null,
    val maxAge: Long? = null,
    val organization: String? = null,
)
