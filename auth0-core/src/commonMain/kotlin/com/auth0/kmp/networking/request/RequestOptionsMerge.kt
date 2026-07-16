package com.auth0.kmp.networking.request

import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.annotation.InternalAuth0Api

/**
 * Folds the pure-transport knobs of [options] (headers and query parameters) into
 * this request. SDK-set values take precedence over caller-supplied entries with
 * the same key. [RequestOptions.retryPolicy] is not folded here — it is a separate
 * argument to the network call.
 */
@InternalAuth0Api
internal fun NetworkRequest.mergedWith(options: RequestOptions): NetworkRequest =
    copy(
        headers = options.headers + headers,
        query = options.parameters + query,
    )
