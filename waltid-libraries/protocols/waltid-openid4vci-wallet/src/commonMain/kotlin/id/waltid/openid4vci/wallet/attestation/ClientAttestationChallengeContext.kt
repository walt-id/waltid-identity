package id.waltid.openid4vci.wallet.attestation

import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import io.ktor.client.HttpClient
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Client attestation freshness state for one client instance and authorization server.
 * The owner scopes this context to its wallet, server, client ID and instance key. Challenges are
 * opaque: their lifetime and reuse policy belong to the server, not to the wallet.
 */
class ClientAttestationChallengeContext(
    initialChallenge: String? = null,
    initialDpopNonce: String? = null,
    private val onUpdate: suspend (challenge: String?, dpopNonce: String?) -> Unit = { _, _ -> },
    private val load: suspend () -> WalletAttestationChallengeResult? = { null },
    /** Atomically merges supplied fields and returns authoritative state; true means seed missing fields only. */
    private val persist: (suspend (challenge: String?, dpopNonce: String?, onlyIfAbsent: Boolean) -> WalletAttestationChallengeResult)? = null,
) {
    private val mutex = Mutex()
    private data class Freshness(val challenge: String?, val dpopNonce: String?)
    private val freshness = MutableStateFlow(Freshness(
        initialChallenge?.takeIf(String::isNotBlank), initialDpopNonce?.takeIf(String::isNotBlank)))
    private var discovered = false
    val challenge: String? get() = freshness.value.challenge
    val dpopNonce: String? get() = freshness.value.dpopNonce

    /** Discover freshness only when this context does not already hold a challenge. */
    suspend fun prepare(httpClient: HttpClient, challengeEndpoint: String?, useDpop: Boolean = false) = mutex.withLock {
        reload()
        if (challenge == null && !discovered && challengeEndpoint != null) {
            val result = WalletAttestationChallengeRequestBuilder(httpClient).requestChallengeDetailed(challengeEndpoint, useDpop)
            reload()
            update(result.attestationChallenge, result.dpopNonce)
            discovered = true
        }
    }

    /** Observe every response, including successful PAR/token responses, before another proof. */
    suspend fun observe(headers: Headers) = mutex.withLock {
        reload()
        update(headers[CLIENT_ATTESTATION_CHALLENGE], headers[DPOP_NONCE_HEADER])
    }

    /** Older session/preview snapshots may seed missing values, never replace newer freshness. */
    suspend fun restore(challenge: String?, dpopNonce: String?) = mutex.withLock {
        reload()
        update(challenge, dpopNonce, onlyIfAbsent = true)
    }

    private suspend fun reload() {
        load()?.let { freshness.value = Freshness(it.attestationChallenge, it.dpopNonce) }
    }

    private suspend fun update(nextChallenge: String?, nextDpopNonce: String?, onlyIfAbsent: Boolean = false) {
        val suppliedChallenge = nextChallenge?.takeIf(String::isNotBlank)
        val suppliedNonce = nextDpopNonce?.takeIf(String::isNotBlank)
        if (suppliedChallenge == null && suppliedNonce == null) return
        val saved = persist?.invoke(suppliedChallenge, suppliedNonce, onlyIfAbsent)
        val next = saved?.let { Freshness(it.attestationChallenge, it.dpopNonce) } ?: Freshness(
            if (onlyIfAbsent) challenge ?: suppliedChallenge else suppliedChallenge ?: challenge,
            if (onlyIfAbsent) dpopNonce ?: suppliedNonce else suppliedNonce ?: dpopNonce,
        )
        if (next == freshness.value) return
        onUpdate(next.challenge, next.dpopNonce)
        freshness.value = next
    }

    override fun toString(): String = "ClientAttestationChallengeContext(<redacted>)"
}

/** One challenge recovery per logical PAR/token exchange; never retry with a static proof. */
internal class ClientAttestationChallengeRetry {
    private var retried = false

    fun shouldRetry(status: HttpStatusCode, error: String?, headers: Headers, canRebuildProof: Boolean): Boolean {
        if (retried || !canRebuildProof || status != HttpStatusCode.BadRequest ||
            error != "use_attestation_challenge" || headers[CLIENT_ATTESTATION_CHALLENGE].isNullOrBlank()
        ) return false
        retried = true
        return true
    }
}
