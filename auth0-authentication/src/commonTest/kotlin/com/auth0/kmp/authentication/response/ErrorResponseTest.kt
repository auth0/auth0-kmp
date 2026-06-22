package com.auth0.kmp.authentication.response

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ErrorResponseTest {

    @Test
    fun parses_wellFormedOAuthError() {
        val body = """{"error":"invalid_grant","error_description":"Wrong email or password."}"""

        assertEquals(
            ErrorResponse("invalid_grant", "Wrong email or password."),
            parseAuth0ErrorBody(body),
        )
    }

    @Test
    fun parses_errorPresent_descriptionAbsent() {
        val body = """{"error":"too_many_attempts"}"""

        assertEquals(ErrorResponse("too_many_attempts", null), parseAuth0ErrorBody(body))
    }

    @Test
    fun parses_ignoringUnknownKeys() {
        val body = """{"error":"mfa_required","mfa_token":"abc","foo":1}"""

        assertEquals(ErrorResponse("mfa_required", null), parseAuth0ErrorBody(body))
    }

    @Test
    fun returnsNull_onNullBody() {
        assertNull(parseAuth0ErrorBody(null))
    }

    @Test
    fun returnsNull_onBlankBody() {
        assertNull(parseAuth0ErrorBody("   "))
    }

    @Test
    fun returnsNull_onEmptyJsonObject() {
        assertNull(parseAuth0ErrorBody("{}"))
    }

    @Test
    fun returnsNull_whenErrorFieldMissing() {
        assertNull(parseAuth0ErrorBody("""{"error_description":"hi"}"""))
    }

    @Test
    fun returnsNull_onMalformedBody() {
        assertNull(parseAuth0ErrorBody("<html>502 Bad Gateway</html>"))
    }
}
