package com.auth0.kmp.authentication.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalAuth0Api::class)
class DatabaseUserResponseMapperTest {

    @Test
    fun toDatabaseUser_mapsEveryFieldToItsOwnValue() {
        val user = DatabaseUserResponse(
            id = "id-1",
            email = "email-1",
            emailVerified = true,
            username = "username-1",
            phoneNumber = "phone-1",
            phoneVerified = false,
            givenName = "given-1",
            familyName = "family-1",
            name = "name-1",
            nickname = "nickname-1",
            picture = "picture-1",
        ).toDatabaseUser()

        assertEquals("id-1", user.id)
        assertEquals("email-1", user.email)
        assertEquals(true, user.emailVerified)
        assertEquals("username-1", user.username)
        assertEquals("phone-1", user.phoneNumber)
        assertEquals(false, user.phoneVerified)
        assertEquals("given-1", user.givenName)
        assertEquals("family-1", user.familyName)
        assertEquals("name-1", user.name)
        assertEquals("nickname-1", user.nickname)
        assertEquals("picture-1", user.picture)
    }
}
