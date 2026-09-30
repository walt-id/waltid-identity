package id.walt.wallet2.handlers

import kotlinx.serialization.Serializable

/** Kind of sensitive continuation record retained by an issuance session. */
@Serializable
enum class WalletIssuanceSessionRecordKind {
    ACTIVE_SESSION,
    DEFERRED_CREDENTIAL,
}

/**
 * Opaque issuance continuation stored outside the protocol engine.
 *
 * [payload] can contain authorization codes, PKCE material, access tokens, and deferred
 * transaction identifiers and received credentials awaiting local storage. Implementations must
 * protect its confidentiality and integrity at rest.
 */
@Serializable
data class WalletIssuanceSessionRecord(
    val id: String,
    val sessionId: String,
    val kind: WalletIssuanceSessionRecordKind,
    val payload: String,
    val updatedAtEpochMilliseconds: Long,
)

/**
 * Durable storage boundary for one wallet's issuance continuations. A store instance must be
 * scoped to its wallet; listing or clearing it must never expose another wallet's records.
 *
 * Implementations are an authoritative security boundary and must reject records whose integrity
 * cannot be established. Mobile SDK factories provide an integrity-protected, encrypted SQLDelight
 * implementation. Callers constructing the protocol engine directly may omit the store and receive
 * process-local continuation semantics.
 */
interface WalletIssuanceSessionStore {
    suspend fun get(id: String): WalletIssuanceSessionRecord?

    suspend fun list(): List<WalletIssuanceSessionRecord>

    suspend fun put(record: WalletIssuanceSessionRecord)

    suspend fun remove(id: String): Boolean
}

/**
 * Atomic continuation updates, coordinated across connections to the backing store.
 *
 * Implement this capability for cross-runtime claim exclusion. The released
 * [WalletIssuanceSessionStore] remains supported with serialized updates within one wallet runtime;
 * its persisted snapshots alone do not exclude concurrent writers in another runtime.
 */
interface AtomicWalletIssuanceSessionStore : WalletIssuanceSessionStore {
    /**
     * Atomically replaces a record only when every stored field equals [expected]. Returns false
     * for a missing or changed record. A null replacement removes the matching record. A non-null
     * replacement must have the same ID. Implementations must
     * coordinate across all connections to the backing store, not just this adapter instance.
     */
    suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?): Boolean
}
