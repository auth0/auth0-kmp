package com.auth0.kmp.webauth.transaction

/**
 * Holds the in-flight [AuthorizeTransaction] for the span of a browser login,
 * keyed by its `state`.
 */
internal interface TransactionStore {

    /** Stores [transaction] as the active login. */
    fun save(transaction: AuthorizeTransaction)

    /**
     * Removes the active transaction if its `state` equals [state].
     *
     * @param state the `state` of the transaction to remove.
     */
    fun clear(state: String)

    /** Returns `true` while a login is in progress. */
    fun hasActiveTransaction(): Boolean

    /** Returns the active transaction, or `null` if none is in progress. */
    fun current(): AuthorizeTransaction?
}
