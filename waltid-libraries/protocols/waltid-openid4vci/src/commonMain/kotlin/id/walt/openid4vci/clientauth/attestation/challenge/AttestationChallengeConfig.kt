package id.walt.openid4vci.clientauth.attestation.challenge

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/** Presence enables challenge issuance, advertisement, and attestation authentication enforcement. */
@Serializable
data class AttestationChallengeConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val lifetimeSeconds: Long = 300,
) {
    init {
        require(lifetimeSeconds > 0) { "lifetimeSeconds must be positive" }
    }
}
