package com.auth0.kmp.core.dpop

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The DPoP collaborators shared by every client built for a single account.
 *
 * @property proofGenerator builds DPoP proofs and the `dpop_jkt` thumbprint from the
 *   account's keypair.
 * @property nonceStore holds the latest server-issued DPoP nonce for the account.
 * @property keygenLock serializes first-time keypair materialization. Suspend callers must
 *   hold it around [DPoPProofGenerator.jkt], [DPoPProofGenerator.generate], and
 *   [DPoPProofGenerator.shouldGenerateProof], whose keypair-creating paths are not safe to
 *   run concurrently.
 */
@InternalAuth0Api
@OptIn(ExperimentalAtomicApi::class)
public class DPoPCollaborators(
    public val proofGenerator: DPoPProofGenerator,
    public val nonceStore: DPoPNonceStore,
    public val keygenLock: Mutex,
)

/**
 * Supplies the [DPoPCollaborators] for an account, building them once and sharing that
 * instance across all the account's clients.
 *
 * Use the shared [Default] instance so every client for the same account observes the same
 * keypair, proof generator, and nonce; construct a separate instance only for tests that want
 * an isolated slot table.
 */
@InternalAuth0Api
@OptIn(ExperimentalAtomicApi::class)
public class DPoPRegistry {

    private val slots = AtomicReference<Map<String, DPoPCollaborators>>(emptyMap())

    /**
     * Returns the [DPoPCollaborators] for [account], creating and caching them on first use.
     *
     * The same instance is returned for every call with matching account coordinates.
     */
    public fun collaboratorsFor(account: Auth0Account): DPoPCollaborators {
        val key = "${account.clientId}|${account.domain}"
        while (true) {
            val current = slots.load()
            current[key]?.let { return it }
            val created = buildCollaborators(account)
            if (slots.compareAndSet(current, current + (key to created))) return created
        }
    }

    private fun buildCollaborators(account: Auth0Account): DPoPCollaborators {
        val keyStore = createDPoPKeyStore("$KEY_TAG_PREFIX${account.clientId}")
        return DPoPCollaborators(
            proofGenerator = DPoPProofGenerator(keyStore),
            nonceStore = DPoPNonceStore(),
            keygenLock = Mutex(),
        )
    }

    companion object {
        private const val KEY_TAG_PREFIX = "com.auth0.kmp.dpop."

        /** Process-wide registry shared by clients built from the same account coordinates. */
        public val Default: DPoPRegistry = DPoPRegistry()
    }
}
