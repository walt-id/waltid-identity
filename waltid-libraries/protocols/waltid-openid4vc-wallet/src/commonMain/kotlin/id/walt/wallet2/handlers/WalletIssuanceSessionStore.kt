package id.walt.wallet2.handlers

/** Kind of sensitive continuation record retained by an issuance session. */
enum class WalletIssuanceSessionRecordKind {
    ACTIVE_SESSION,
    DEFERRED_CREDENTIAL,
}

/**
 * Opaque issuance continuation stored outside the protocol engine.
 *
 * [payload] can contain authorization codes, PKCE material, access tokens, and deferred
 * transaction identifiers. Implementations must encrypt it at rest.
 */
data class WalletIssuanceSessionRecord(
    val id: String,
    val sessionId: String,
    val kind: WalletIssuanceSessionRecordKind,
    val payload: String,
    val updatedAtEpochMilliseconds: Long,
)

/**
 * Durable storage boundary for issuance continuations.
 *
 * Mobile SDK factories provide an encrypted SQLDelight implementation. Callers constructing the
 * protocol engine directly may omit the store and receive process-local continuation semantics.
 */
interface WalletIssuanceSessionStore {
    suspend fun get(id: String): WalletIssuanceSessionRecord?

    suspend fun list(): List<WalletIssuanceSessionRecord>

    suspend fun put(record: WalletIssuanceSessionRecord)

    suspend fun remove(id: String): Boolean
}
