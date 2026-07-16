package com.auth0.kmp.credentials

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.auth0.kmp.core.context.ApplicationContextHolder
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

private const val DATASTORE_NAME = "auth0_credentials"
private const val KEYSET_NAME = "auth0_credentials_keyset"
private const val KEYSET_PREFS_FILE = "auth0_credentials_keyset_prefs"
private const val MASTER_KEY_URI = "android-keystore://auth0_credentials_master_key"

private val Context.credentialsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = DATASTORE_NAME)

private val sharedAead: Aead by lazy { buildAead(ApplicationContextHolder.context) }

internal actual fun createStorage(): Storage {
    val context = ApplicationContextHolder.context
    val persistence = DataStoreStorage(context.credentialsDataStore)
    return EncryptedStorage(persistence) { sharedAead }
}

private fun buildAead(context: Context): Aead {
    AeadConfig.register()
    val keysetHandle = AndroidKeysetManager.Builder()
        .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_FILE)
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri(MASTER_KEY_URI)
        .build()
        .keysetHandle
    return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}
