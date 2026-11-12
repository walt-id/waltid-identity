package id.walt.openid4vci.deferred

/** Trusted persisted claim supplied by the embedding issuer, never decoded from wallet input. */
data class DeferredCredentialResponseContext(
    val sessionId: String,
    val transaction: DeferredTransaction,
    val deferredIssuance: DeferredIssuance,
)
