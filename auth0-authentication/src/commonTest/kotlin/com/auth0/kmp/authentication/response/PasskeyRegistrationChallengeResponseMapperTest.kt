package com.auth0.kmp.authentication.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalAuth0Api::class)
class PasskeyRegistrationChallengeResponseMapperTest {

    @Test
    fun toPasskeyRegistrationChallenge_mapsEveryFieldToItsOwnValue() {
        val challenge = PasskeyRegistrationChallengeResponse(
            authSession = "session-1",
            authParamsPublicKey = AuthnParamsPublicKeyResponse(
                authenticatorSelection = AuthenticatorSelectionResponse(
                    residentKey = "residentkey-1",
                    userVerification = "uv-1",
                ),
                challenge = "challenge-1",
                pubKeyCredParams = listOf(PubKeyCredParamResponse(alg = -7, type = "type-1")),
                relyingParty = RelyingPartyResponse(id = "rp-id-1", name = "rp-name-1"),
                timeout = 222L,
                user = PasskeyUserResponse(
                    displayName = "displayname-1",
                    id = "user-id-1",
                    name = "user-name-1",
                ),
            ),
        ).toPasskeyRegistrationChallenge()

        val pk = challenge.authParamsPublicKey
        assertEquals("session-1", challenge.authSession)
        assertEquals("residentkey-1", pk.authenticatorSelection.residentKey)
        assertEquals("uv-1", pk.authenticatorSelection.userVerification)
        assertEquals("challenge-1", pk.challenge)
        assertEquals(-7, pk.pubKeyCredParams[0].alg)
        assertEquals("type-1", pk.pubKeyCredParams[0].type)
        assertEquals("rp-id-1", pk.relyingParty.id)
        assertEquals("rp-name-1", pk.relyingParty.name)
        assertEquals(222L, pk.timeout)
        assertEquals("displayname-1", pk.user.displayName)
        assertEquals("user-id-1", pk.user.id)
        assertEquals("user-name-1", pk.user.name)
    }
}
