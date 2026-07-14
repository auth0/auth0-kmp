package com.auth0.kmp.core.dpop

/**
 * A deterministic in-memory [DPoPKeyStore] for exercising the proof engine.
 *
 * The public JWK and signature bytes are fixed and configurable; call counters and the
 * last signed input are recorded so tests can assert the engine's interaction with the
 * store. Set [failOn*] to make a call throw, verifying error mapping.
 */
internal class FakeDPoPKeyStore(
    private var hasKey: Boolean = true,
    val jwk: DPoPJwk = DPoPJwk(x = "test-x-coordinate", y = "test-y-coordinate"),
    val signature: ByteArray = ByteArray(64) { it.toByte() },
) : DPoPKeyStore {

    var hasKeyCallCount: Int = 0
        private set
    var publicJwkCallCount: Int = 0
        private set
    var signCallCount: Int = 0
        private set
    var clearCallCount: Int = 0
        private set
    var lastSignInput: ByteArray? = null
        private set

    /** When set, [publicJwk] throws a [DPoPException] carrying this error. */
    var failPublicJwkWith: DPoPError? = null

    /** When set, [sign] throws a [DPoPException] carrying this error. */
    var failSignWith: DPoPError? = null

    /** When set, [sign] throws this raw throwable (to test [DPoPError.Unknown] mapping). */
    var signThrowable: Throwable? = null

    override fun hasKey(): Boolean {
        hasKeyCallCount++
        return hasKey
    }

    override fun publicJwk(): DPoPJwk {
        publicJwkCallCount++
        failPublicJwkWith?.let { throw DPoPException(it) }
        hasKey = true
        return jwk
    }

    override fun sign(data: ByteArray): ByteArray {
        signCallCount++
        lastSignInput = data
        signThrowable?.let { throw it }
        failSignWith?.let { throw DPoPException(it) }
        return signature
    }

    override fun clear() {
        clearCallCount++
        hasKey = false
    }
}
