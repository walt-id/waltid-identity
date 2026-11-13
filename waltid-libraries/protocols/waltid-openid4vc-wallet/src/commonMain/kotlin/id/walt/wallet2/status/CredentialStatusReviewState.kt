package id.walt.wallet2.status

import id.walt.credentials.status.CredentialStatus

/**
 * Opaque, non-serializable evidence for one active host review context. It contains no wallet,
 * store, authorization object or list body. Hosts must bound its lifetime and bind it to the same
 * authorized tenant, wallet lifecycle and store configuration before restoring it.
 */
class CredentialStatusReviewState internal constructor(
    internal val walletId: String,
    internal val trustPolicyId: String,
    entries: List<CredentialStatusReviewEntry>,
) {
    internal val entries = entries.toList()
    val recordCount: Int get() = entries.size
}

internal data class CredentialStatusReviewEntry(
    val reference: WalletCredentialReference,
    val fingerprint: String,
    val metadata: CredentialStatus,
    val nonPersistentReferenceIds: Set<String>,
    val persisted: PersistedCredentialStatus?,
)
