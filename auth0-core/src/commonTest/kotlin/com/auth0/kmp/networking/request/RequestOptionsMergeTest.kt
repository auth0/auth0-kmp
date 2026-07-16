package com.auth0.kmp.networking.request

import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalAuth0Api::class)
class RequestOptionsMergeTest {

    @Test
    fun foldsHeadersAndQuery_sdkWinsOnDuplicateKeys() {
        val request = NetworkRequest(
            method = HttpMethod.GET,
            path = "/userinfo",
            headers = mapOf("k" to "sdk"),
            query = mapOf("q" to "sdk"),
        )
        val options = RequestOptions(
            parameters = mapOf("q" to "caller", "p" to "cp"),
            headers = mapOf("k" to "caller", "extra" to "e"),
        )

        val merged = request.mergedWith(options)

        assertEquals("sdk", merged.headers["k"])
        assertEquals("e", merged.headers["extra"])
        assertEquals("sdk", merged.query["q"])
        assertEquals("cp", merged.query["p"])
    }
}
