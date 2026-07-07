package com.auth0.kmp.core.useragent

import com.auth0.kmp.core.SDK_VERSION
import com.auth0.kmp.core.primitives.encodeBase64Url
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Default [UserAgent] for this SDK.
 *
 * Reports [name] `auth0-kmp` and the build's [version] by default. A library that
 * wraps this SDK can substitute its own identity with [withLibrary].
 *
 * @param name the library name reported in the header.
 * @param version the library version reported in the header.
 * @param env platform runtime descriptors reported under the header's `env`.
 */
public class Auth0UserAgent internal constructor(
    private val name: String = DEFAULT_NAME,
    private val version: String = SDK_VERSION,
    private val env: Map<String, String> = platformEnv(),
) : UserAgent {

    override val headerName: String = HEADER_NAME

    override val value: String = buildJsonObject {
        put("name", name)
        put("version", version)
        put("env", JsonObject(env.mapValues { JsonPrimitive(it.value) }))
    }.toString().encodeToByteArray().encodeBase64Url()

    /**
     * Returns a [UserAgent] identifying a wrapping [library]/[libraryVersion],
     * keeping this SDK's own name and version under the header's `env`.
     *
     * @param library the wrapping library's name.
     * @param libraryVersion the wrapping library's version.
     */
    fun withLibrary(library: String, libraryVersion: String): Auth0UserAgent =
        Auth0UserAgent(
            name = library,
            version = libraryVersion,
            env = env + (name to version),
        )

    companion object {
        /** The HTTP header used to report the client library. */
        const val HEADER_NAME: String = "Auth0-Client"

        /** The default library name for this SDK. */
        const val DEFAULT_NAME: String = "auth0-kmp"

        /** Returns the default user agent identifying this SDK. */
        fun default(): Auth0UserAgent = Auth0UserAgent()
    }
}
