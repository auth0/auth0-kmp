package com.auth0.kmp.credentials

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataStoreStorageTest {

    // In-memory DataStore so tests need no files, Context, or Keystore.
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }

    private fun storage() = DataStoreStorage(FakePreferencesDataStore())

    @Test
    fun store_then_retrieve_returns_original_value() = runTest {
        val storage = storage()
        storage.store("k", "hello")
        assertEquals("hello", storage.retrieve("k"))
    }

    @Test
    fun retrieve_returns_null_for_missing_key() = runTest {
        assertNull(storage().retrieve("absent"))
    }

    @Test
    fun remove_deletes_a_stored_value() = runTest {
        val storage = storage()
        storage.store("k", "v")
        storage.remove("k")
        assertNull(storage.retrieve("k"))
    }

    @Test
    fun store_overwrites_an_existing_value() = runTest {
        val storage = storage()
        storage.store("k", "first")
        storage.store("k", "second")
        assertEquals("second", storage.retrieve("k"))
    }

    @Test
    fun keys_are_isolated_from_each_other() = runTest {
        val storage = storage()
        storage.store("a", "va")
        storage.store("b", "vb")
        assertEquals("va", storage.retrieve("a"))
        assertEquals("vb", storage.retrieve("b"))
    }
}
