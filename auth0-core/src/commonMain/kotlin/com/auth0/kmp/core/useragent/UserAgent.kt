package com.auth0.kmp.core.useragent

/**
 * Identifies the client library making a request, carried in the `Auth0-Client`
 * header so Auth0 can attribute traffic to the SDK and version that sent it.
 */
public interface UserAgent {

    /** The header name under which [value] is sent. */
    public val headerName: String

    /** The encoded header value describing this client. */
    public val value: String
}
