package id.walt.wallet2.persistence.stores

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.wallet2.data.HolderKeyBinding
import id.walt.wallet2.data.HolderKeyBindingErrorCode
import id.walt.wallet2.data.HolderKeyBindingException
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialInventory
import id.walt.wallet2.data.WalletCredentialSummary
import id.walt.wallet2.data.toCredentialSummary
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.status.CredentialStatusInventory
import id.walt.wallet2.persistence.db.Credentials
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Instant

private val json = Json { ignoreUnknownKeys = true }
private const val MAX_BACKFILL_ROWS = 32
private const val MAX_BACKFILL_CHARS = 512 * 1024

/**
 * SQLDelight-backed implementation of [WalletCredentialStore] for mobile wallets.
 */
public class SqlDelightCredentialStore private constructor(
    private val queries: WalletPersistenceQueries,
    status: SqlDelightStatusInventory,
) : WalletCredentialStore, WalletCredentialInventory, CredentialStatusInventory by status {
    /** Opens the foreground credential store in the already-open database. */
    public constructor(queries: WalletPersistenceQueries) : this(queries, SqlDelightStatusInventory(queries))

    /**
     * Loads a stored credential by wallet-local identifier.
     */
    override suspend fun getCredential(id: String): StoredCredential? {
        val row = queries.selectCredentialById(id).executeAsOneOrNull() ?: return null
        return row.toStoredCredential()
    }

    /**
     * Emits all stored credentials from the mobile database.
     */
    override suspend fun listCredentials(): Flow<StoredCredential> = flow {
        queries.selectAllCredentials().executeAsList().forEach { row ->
            emit(row.toStoredCredential())
        }
    }

    /** Backfill old rows with bounded transactions. A replacement cannot inherit the previous row's display fields. */
    override suspend fun credentialSummaries(): List<WalletCredentialSummary> {
        val pending = mutableListOf<Pair<WalletCredentialSummary, String>>()
        var pendingChars = 0
        fun flush() {
            if (pending.isEmpty()) return
            queries.transaction {
                for ((summary, expected) in pending) {
                    queries.saveCredentialInventoryIfCurrent(summary.issuer, summary.subject, summary.typeMetadataJson,
                        summary.id, expected)
                }
            }
            pending.clear()
            pendingChars = 0
        }

        val summaries = queries.selectCredentialSummaries().executeAsList().mapNotNull { row ->
            val types = row.type_metadata
            if (types != null) {
                WalletCredentialSummary(row.id, row.format, row.issuer, row.subject, row.label,
                    Instant.fromEpochMilliseconds(row.added_at).toString(), types, row.metadata)
            } else {
                val original = queries.selectCredentialById(row.id).executeAsOneOrNull() ?: return@mapNotNull null
                val summary = original.toStoredCredential().toCredentialSummary()
                val signed = original.serialized_credential
                if (pending.size >= MAX_BACKFILL_ROWS || signed.length > MAX_BACKFILL_CHARS - pendingChars) flush()
                pending.add(summary to signed)
                pendingChars += signed.length
                if (pendingChars >= MAX_BACKFILL_CHARS) flush()
                summary
            }
        }
        flush()
        return summaries
    }

    /**
     * Stores a credential and its display metadata.
     */
    override suspend fun addCredential(entry: StoredCredential) {
        val rawString = entry.credential.serializedForWalletPersistence()
        val summary = entry.toCredentialSummary()
        queries.transaction {
            queries.deleteCredentialStatusForReplacement(entry.id, rawString)
            queries.insertCredential(
                id = entry.id,
                serialized_credential = rawString,
                format = entry.credential.format,
                label = entry.label,
                added_at = entry.addedAt?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds(),
                metadata = summary.metadataJson,
                holder_key_binding = entry.holderKeyBinding?.let { json.encodeToString(HolderKeyBinding.serializer(), it) },
            )
            queries.saveCredentialInventory(entry.id, summary.issuer, summary.subject, summary.typeMetadataJson)
        }
    }

    /**
     * Removes a credential by wallet-local identifier.
     */
    override suspend fun removeCredential(id: String): Boolean = queries.transactionWithResult {
        queries.deleteCredentialStatus(id)
        queries.deleteCredentialById(id)
        queries.credentialStatusChanges().executeAsOne() == 1L
    }

    private suspend fun Credentials.toStoredCredential(): StoredCredential {
        val (_, credential) = CredentialParser.detectAndParse(serialized_credential)
        return StoredCredential(
            id = id,
            credential = credential,
            label = label,
            addedAt = Instant.fromEpochMilliseconds(added_at),
            metadata = metadata?.let { json.decodeFromString(JsonObject.serializer(), it) },
            holderKeyBinding = holder_key_binding?.let { decodeHolderKeyBinding(it) },
        )
    }

    private fun Credentials.decodeHolderKeyBinding(serialized: String): HolderKeyBinding = try {
        json.decodeFromString(HolderKeyBinding.serializer(), serialized)
    } catch (cause: Exception) {
        throw HolderKeyBindingException(
            code = HolderKeyBindingErrorCode.BINDING_INVALID,
            credentialId = id,
            message = "Credential '$id' has an invalid persisted holder-key binding",
            cause = cause,
        )
    }
}

internal fun DigitalCredential.serializedForWalletPersistence(): String =
    (this as? SelectivelyDisclosableVerifiableCredential)?.signedWithDisclosures?.takeIf { it.isNotBlank() }
        ?: signed?.takeIf { it.isNotBlank() }
        ?: credentialData.toString()
