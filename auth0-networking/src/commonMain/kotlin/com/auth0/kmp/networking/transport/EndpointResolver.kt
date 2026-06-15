package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.Auth0Account
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.Url
import io.ktor.http.appendPathSegments

/**
 * Builds absolute request URLs from an [Auth0Account]'s domain.
 *
 * The domain is normalized and validated once at construction; a malformed or
 * non-HTTPS domain fails fast with [IllegalArgumentException].
 *
 * @param account the tenant/application coordinates whose domain is resolved against.
 */
internal class EndpointResolver(account: Auth0Account) {

    private val baseUrl: Url

    init {
        val normalized = account.domain.lowercase()
        require(!normalized.startsWith("http://")) {
            "Invalid domain url: '${account.domain}'. Only HTTPS domain URLs are supported. If no scheme is passed, HTTPS will be used."
        }
        val withScheme = if (normalized.startsWith("https://")) normalized else "https://$normalized"
        val host = withScheme.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
        require(host.isNotBlank()) { "Invalid domain url: '${account.domain}'" }
        baseUrl = try {
            Url(withScheme)
        } catch (e: URLParserException) {
            throw IllegalArgumentException("Invalid domain url: '${account.domain}'", e)
        }
    }

    /**
     * Resolves [path] (relative, e.g. `/oauth/token`) against the account domain.
     *
     * @param path the request path relative to the Auth0 domain.
     * @return the absolute URL string.
     */
    fun resolve(path: String): String =
        URLBuilder(baseUrl).appendPathSegments(path.split('/').filter { it.isNotEmpty() }).buildString()
}
