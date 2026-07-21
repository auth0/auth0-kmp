package com.auth0.kmp.authentication.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalAuth0Api::class)
class PasskeyLoginChallengeResponseMapperTest {

    @Test
    fun toPasskeyLoginChallenge_mapsEveryFieldToItsOwnValue() {
        val challenge = PasskeyLoginChallengeResponse(
            authSession = "session-1",
            authParamsPublicKey = AuthParamsPublicKeyResponse(
                challenge = "challenge-1",
                rpId = "rpid-1",
                timeout = 111L,
                userVerification = "uv-1",
            ),
        ).toPasskeyLoginChallenge()

        assertEquals("session-1", challenge.authSession)
        assertEquals("challenge-1", challenge.authParamsPublicKey.challenge)
        assertEquals("rpid-1", challenge.authParamsPublicKey.rpId)
        assertEquals(111L, challenge.authParamsPublicKey.timeout)
        assertEquals("uv-1", challenge.authParamsPublicKey.userVerification)
    }
}
