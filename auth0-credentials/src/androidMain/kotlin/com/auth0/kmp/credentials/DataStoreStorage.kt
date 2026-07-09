package com.auth0.kmp.credentials

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

internal class DataStoreStorage(
    private val dataStore: DataStore<Preferences>,
) : Storage {

    override suspend fun retrieve(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun store(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }
}
