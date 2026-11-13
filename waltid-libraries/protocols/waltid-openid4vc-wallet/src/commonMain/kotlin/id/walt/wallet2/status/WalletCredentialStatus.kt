package id.walt.wallet2.status

import id.walt.credentials.status.CredentialStatus
import id.walt.credentials.status.CredentialStatusPresentation
import id.walt.credentials.status.assess
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
data class WalletCredentialRead(
    val credential: StoredCredential,
    val reference: WalletCredentialReference,
    val status: CredentialStatus,
    val evaluatedAt: Instant,
) {
    private val assessment = status.assess(evaluatedAt)
    val presentation: CredentialStatusPresentation get() = assessment.presentation
}

data class WalletCredentialMetadataRead(
    val credential: StoredCredentialMetadata,
    val reference: WalletCredentialReference,
    val status: CredentialStatus,
    val evaluatedAt: Instant,
) {
    private val assessment = status.assess(evaluatedAt)
    val presentation: CredentialStatusPresentation get() = assessment.presentation
}

/** Domain snapshots keep detached collections; transports use explicit response DTOs. */
class WalletCredentialStatusSnapshot(
    val evaluatedAt: Instant,
    credentials: List<WalletCredentialStatus>,
    refreshing: Set<WalletCredentialReference> = emptySet(),
    val nextRefreshAt: Instant? = null,
) {
    private val values = credentials.toList()
    private val active = refreshing.toSet()
    val credentials: List<WalletCredentialStatus> get() = values.toList()
    val refreshing: Set<WalletCredentialReference> get() = active.toSet()
    init {
        require(values.all { it.evaluatedAt == evaluatedAt })
        require(values.map { it.reference }.distinct().size == values.size)
    }
    override fun equals(other: Any?) = other is WalletCredentialStatusSnapshot && evaluatedAt == other.evaluatedAt &&
        values == other.values && active == other.active && nextRefreshAt == other.nextRefreshAt
    override fun hashCode() = listOf(evaluatedAt, values, active, nextRefreshAt).hashCode()
}

data class WalletCredentialStatus(
    val reference: WalletCredentialReference,
    val status: CredentialStatus,
    val evaluatedAt: Instant,
) {
    private val assessment = status.assess(evaluatedAt)
    val presentation: CredentialStatusPresentation get() = assessment.presentation
}

@Serializable
enum class CredentialStatusRefreshMode { IF_DUE, FORCE }

@Serializable
enum class CredentialStatusRefreshOutcome { COMPLETED, UNCHANGED, DEFERRED, REMOVED }

@Serializable
data class CredentialStatusRefreshResult(
    val reference: WalletCredentialReference,
    val outcome: CredentialStatusRefreshOutcome,
    val status: CredentialStatus,
)

/** Minimal protected metadata, never a signed list body or an observation history. */
@Serializable
data class PersistedCredentialStatus(
    val schemaVersion: Int = 2,
    val credentialFingerprint: String,
    val trustPolicyId: String?,
    val metadata: CredentialStatus,
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
