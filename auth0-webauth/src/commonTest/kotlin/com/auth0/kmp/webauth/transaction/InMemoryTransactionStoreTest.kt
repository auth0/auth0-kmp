package com.auth0.kmp.webauth.transaction

import com.auth0.kmp.webauth.pkce.Pkce
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InMemoryTransactionStoreTest {

    private fun transaction(state: String) = AuthorizeTransaction(
        state = state,
        nonce = "nonce-for-$state",
        pkce = Pkce.generate(),
        redirectUri = "myapp://callback",
    )

    @Test
    fun clear_removes_transaction_for_matching_state() {
        val store = InMemoryTransactionStore()
        store.save(transaction("state-1"))
        store.clear("state-1")
        assertNull(store.current())
        assertFalse(store.hasActiveTransaction())
    }

    @Test
    fun clear_is_noop_for_non_matching_state() {
        val store = InMemoryTransactionStore()
        val txn = transaction("state-1")
        store.save(txn)
        store.clear("other")
        assertSame(txn, store.current())
    }

    @Test
    fun hasActiveTransaction_tracks_lifecycle() {
        val store = InMemoryTransactionStore()
        assertFalse(store.hasActiveTransaction())
        store.save(transaction("state-1"))
        assertTrue(store.hasActiveTransaction())
        store.clear("state-1")
        assertFalse(store.hasActiveTransaction())
    }

    @Test
    fun current_returns_active_transaction() {
        val store = InMemoryTransactionStore()
        assertNull(store.current())
        val txn = transaction("state-1")
        store.save(txn)
        assertSame(txn, store.current())
    }

    @Test
    fun save_replaces_previous_transaction() {
        val store = InMemoryTransactionStore()
        val first = transaction("state-A")
        val second = transaction("state-B")
        store.save(first)
        store.save(second)
        assertSame(second, store.current())
    }
}
