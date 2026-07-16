package com.auth0.kmp.core.token

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.retry.RetryPolicy

/**
 * Exchanges an OAuth [TokenGrant] at `/oauth/token` for fresh [Credentials].
 */
@InternalAuth0Api
public interface TokenClient {

    /**
     * Exchanges [grant] at `/oauth/token` for fresh [Credentials].
     *
     * @param grant the grant whose [TokenGrant.parameters] form the request body.
     * @param headers extra headers to send with this request.
     * @param retryPolicy how the token request is retried on failure.
     * @return [Result.Success] with the fresh credentials, or [Result.Failure]
     *   with the mapped [TransportError].
     */
    public suspend fun fetchToken(
        grant: TokenGrant,
        headers: Map<String, String> = emptyMap(),
        retryPolicy: RetryPolicy = RetryPolicy.None,
    ): Result<Credentials, TransportError>
}
