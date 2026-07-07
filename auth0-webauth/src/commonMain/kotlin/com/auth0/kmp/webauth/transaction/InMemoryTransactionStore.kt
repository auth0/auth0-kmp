package com.auth0.kmp.webauth.transaction

internal class InMemoryTransactionStore : TransactionStore {

    private var active: AuthorizeTransaction? = null

    override fun save(transaction: AuthorizeTransaction) {
        active = transaction
    }

    override fun clear(state: String) {
        if (active?.state == state) active = null
    }

    override fun hasActiveTransaction(): Boolean = active != null

    override fun current(): AuthorizeTransaction? = active
}
