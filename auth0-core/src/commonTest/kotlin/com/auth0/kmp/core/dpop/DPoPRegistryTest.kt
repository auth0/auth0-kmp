package com.auth0.kmp.core.dpop

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(InternalAuth0Api::class)
class DPoPRegistryTest {

    @Test
    fun returns_same_collaborators_for_the_same_account() {
        val registry = DPoPRegistry()
        val account = Auth0Account(clientId = "cid", domain = "test.auth0.com")

        assertSame(
            registry.collaboratorsFor(account),
            registry.collaboratorsFor(account),
        )
    }

    @Test
    fun returns_distinct_collaborators_for_different_accounts() {
        val registry = DPoPRegistry()
        val first = registry.collaboratorsFor(Auth0Account(clientId = "cid-1", domain = "test.auth0.com"))
        val second = registry.collaboratorsFor(Auth0Account(clientId = "cid-2", domain = "test.auth0.com"))

        assertNotSame(first, second)
    }
}
