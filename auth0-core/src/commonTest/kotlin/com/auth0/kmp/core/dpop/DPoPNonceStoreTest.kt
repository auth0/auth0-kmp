package com.auth0.kmp.core.dpop

import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(InternalAuth0Api::class)
class DPoPNonceStoreTest {

    // 18
    @Test
    fun current_is_null_initially() {
        assertNull(DPoPNonceStore().current())
    }

    // 19
    @Test
    fun current_returns_last_updated_value() {
        val store = DPoPNonceStore()
        store.update("nonce-1")

        assertEquals("nonce-1", store.current())
    }

    // 20
    @Test
    fun update_twice_keeps_latest_value() {
        val store = DPoPNonceStore()
        store.update("nonce-1")
        store.update("nonce-2")

        assertEquals("nonce-2", store.current())
    }

    // 21
    @Test
    fun clear_resets_to_null() {
        val store = DPoPNonceStore()
        store.update("nonce-1")
        store.clear()

        assertNull(store.current())
    }
}
