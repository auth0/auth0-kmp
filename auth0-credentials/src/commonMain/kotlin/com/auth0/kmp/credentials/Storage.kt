package com.auth0.kmp.credentials

/**
 * A secure, keyed string key–value store for persisting SDK data on-device.
 * Consumers may supply their own implementation.
 */
public interface Storage {

    /**
     * Returns the value stored under [key], or `null` if none is stored.
     *
     * @param key the entry key.
     */
    public suspend fun retrieve(key: String): String?

    /**
     * Stores [value] under [key], replacing any existing value.
     *
     * @param key the entry key.
     * @param value the value to store.
     */
    public suspend fun store(key: String, value: String)

    /**
     * Removes the value stored under [key], if any.
     *
     * @param key the entry key.
     */
    public suspend fun remove(key: String)
}
