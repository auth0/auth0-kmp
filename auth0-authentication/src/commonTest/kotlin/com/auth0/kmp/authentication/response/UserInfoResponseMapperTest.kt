package com.auth0.kmp.authentication.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.model.Address
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(InternalAuth0Api::class)
class UserInfoResponseMapperTest {

    @Test
    fun toUserInfo_parsesEpochSecondsUpdatedAt() {
        val info = UserInfoResponse(sub = "auth0|1", updatedAt = "1767225600").toUserInfo()

        assertEquals(Instant.fromEpochSeconds(1767225600), info.updatedAt)
    }

    @Test
    fun toUserInfo_parsesIso8601UpdatedAt() {
        val info = UserInfoResponse(sub = "auth0|1", updatedAt = "2026-01-01T00:00:00Z").toUserInfo()

        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), info.updatedAt)
    }

    @Test
    fun toUserInfo_nullUpdatedAt_staysNull() {
        val info = UserInfoResponse(sub = "auth0|1", updatedAt = null).toUserInfo()

        assertNull(info.updatedAt)
    }

    @Test
    fun toUserInfo_unparseableUpdatedAt_becomesNull() {
        val info = UserInfoResponse(sub = "auth0|1", updatedAt = "not-a-date").toUserInfo()

        assertNull(info.updatedAt)
    }

    @Test
    fun toUserInfo_carriesTypedFieldsAndCustomClaims() {
        val info = UserInfoResponse(
            sub = "auth0|1",
            email = "a@b.com",
            customClaims = mapOf("https://claim/roles" to JsonPrimitive("admin")),
        ).toUserInfo()

        assertEquals("auth0|1", info.sub)
        assertEquals("a@b.com", info.email)
        assertEquals(JsonPrimitive("admin"), info.customClaims["https://claim/roles"])
    }

    @Test
    fun toUserInfo_carriesTypedAddress() {
        val info = UserInfoResponse(
            sub = "auth0|1",
            address = AddressResponse(streetAddress = "10 Main St", country = "UK"),
        ).toUserInfo()

        assertEquals(Address(streetAddress = "10 Main St", country = "UK"), info.address)
    }
}
