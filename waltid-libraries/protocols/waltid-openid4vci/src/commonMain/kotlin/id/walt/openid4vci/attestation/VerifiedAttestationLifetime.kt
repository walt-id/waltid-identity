package id.walt.openid4vci.attestation

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Lifetime and skew accepted by the verifier; no nonce or proof freshness state. */
@Serializable
data class VerifiedAttestationLifetime(
    val issuedAt: Long? = null,
    val expiresAt: Long? = null,
    val notBefore: Double? = null,
    val clockSkewSeconds: Long,
) {
    init { require(clockSkewSeconds >= 0) }

    // Client authentication has historically accepted the expiration second including skew.
    fun clientAttestationValidAt(now: Instant): Boolean =
        expiresAt != null && now.epochSeconds - clockSkewSeconds <= expiresAt &&
            (issuedAt == null || issuedAt <= now.epochSeconds + clockSkewSeconds)

    // Key attestations expire at the boundary; standalone attestations may omit exp.
    fun keyAttestationValidAt(now: Instant): Boolean =
        issuedAt != null && issuedAt <= now.epochSeconds + clockSkewSeconds &&
            (expiresAt == null || expiresAt > now.epochSeconds - clockSkewSeconds && expiresAt > issuedAt) &&
            (notBefore == null || notBefore.isFinite() &&
                notBefore <= now.epochSeconds.toDouble() + now.nanosecondsOfSecond / 1_000_000_000.0 + clockSkewSeconds)
}
