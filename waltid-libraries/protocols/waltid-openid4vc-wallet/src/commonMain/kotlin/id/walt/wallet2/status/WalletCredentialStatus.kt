package id.walt.wallet2.status

import id.walt.credentials.status.CredentialStatusMetadata
import id.walt.credentials.status.CredentialStatusPresentation
import id.walt.credentials.status.presentation
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.StoredCredentialMetadata
import kotlinx.serialization.Serializable
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.time.Instant

/** A record is scoped to its configured store, even if custom stores use overlapping IDs. */
@Serializable
data class WalletCredentialReference(val storeIndex: Int, val credentialId: String) {
    init { require(storeIndex >= 0 && credentialId.isNotBlank()) }
}

/** New composed read APIs preserve the published low-level storage model and constructors. */
@Serializable
data class WalletCredentialRead(
    val credential: StoredCredential,
    val reference: WalletCredentialReference,
    val status: CredentialStatusMetadata,
    val evaluatedAt: Instant,
    val presentation: CredentialStatusPresentation = status.presentation(evaluatedAt),
)

@Serializable
data class WalletCredentialMetadataRead(
    val credential: StoredCredentialMetadata,
    val reference: WalletCredentialReference,
    val status: CredentialStatusMetadata,
    val evaluatedAt: Instant,
    val presentation: CredentialStatusPresentation = status.presentation(evaluatedAt),
)

@Serializable
data class WalletCredentialStatusSnapshot(
    val evaluatedAt: Instant,
    val credentials: List<WalletCredentialStatus>,
    val refreshing: Set<WalletCredentialReference> = emptySet(),
    /** Earliest useful IfDue check under current freshness/backoff; hosts decide whether to schedule it. */
    val nextRefreshAt: Instant? = null,
)

@Serializable
data class WalletCredentialStatus(
    val reference: WalletCredentialReference,
    val status: CredentialStatusMetadata,
    val presentation: CredentialStatusPresentation,
)

@Serializable
enum class CredentialStatusRefreshMode { IF_DUE, FORCE }

@Serializable
enum class CredentialStatusRefreshOutcome { COMPLETED, UNCHANGED, DEFERRED, REMOVED }

@Serializable
data class CredentialStatusRefreshResult(
    val reference: WalletCredentialReference,
    val outcome: CredentialStatusRefreshOutcome,
    val status: CredentialStatusMetadata,
)

/** Minimal protected metadata, never a signed list body or an observation history. */
@Serializable
data class PersistedCredentialStatus(
    val schemaVersion: Int = 1,
    val credentialFingerprint: String,
    val trustPolicyId: String?,
    val metadata: CredentialStatusMetadata,
)

/** Optional capability: implementing a custom credential store gains no new required methods. */
interface CredentialStatusSnapshotStore {
    suspend fun readCredentialStatus(credentialId: String): PersistedCredentialStatus?

    /** Atomically check that the credential still exists with these signed bytes before writing. */
    suspend fun saveCredentialStatusIfCurrent(credential: StoredCredential, status: PersistedCredentialStatus): Boolean

    suspend fun removeCredentialStatusIfCurrent(credential: StoredCredential): Boolean
}

/** Metadata changes do not invalidate status; replacement of the signed credential does. */
fun StoredCredential.statusFingerprint(): String =
    SHA256().digest("${credential.format}|${credential.signed}".encodeToByteArray()).toHexString()
