package id.walt.wallet2.persistence.stores

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.wallet2.data.HolderKeyBinding
import id.walt.wallet2.data.HolderKeyBindingErrorCode
import id.walt.wallet2.data.HolderKeyBindingException
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.status.CredentialStatusSnapshotStore
import id.walt.wallet2.status.PersistedCredentialStatus
import id.walt.wallet2.status.statusFingerprint
import id.walt.wallet2.persistence.db.Credentials
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Instant

private val json = Json { ignoreUnknownKeys = true }

/**
 * SQLDelight-backed implementation of [WalletCredentialStore] for mobile wallets.
 *
 * @param queries SQLDelight queries for wallet persistence tables.
 */
public class SqlDelightCredentialStore(
    private val queries: WalletPersistenceQueries,
) : WalletCredentialStore, CredentialStatusSnapshotStore {

    /** Reads minimal protected metadata for the current stored credential. */
    override suspend fun readCredentialStatus(credentialId: String): PersistedCredentialStatus? =
        queries.selectCredentialStatus(credentialId).executeAsOneOrNull()?.let {
            runCatching { json.decodeFromString(PersistedCredentialStatus.serializer(), it) }.getOrNull()
        }

    /** Writes metadata only while the stored signed credential still matches. */
    override suspend fun saveCredentialStatusIfCurrent(credential: StoredCredential, status: PersistedCredentialStatus): Boolean {
        require(status.credentialFingerprint == credential.statusFingerprint())
        return queries.transactionWithResult {
            queries.saveCredentialStatusIfCurrent(snapshot = json.encodeToString(PersistedCredentialStatus.serializer(), status),
                credential_id = credential.id, expected_credential = credential.credential.serializedForWalletPersistence())
            queries.credentialStatusChanges().executeAsOne() == 1L
        }
    }

    /** Removes metadata only while the stored signed credential still matches. */
    override suspend fun removeCredentialStatusIfCurrent(credential: StoredCredential): Boolean = queries.transactionWithResult {
        val current = queries.selectCredentialById(credential.id).executeAsOneOrNull()
        if (current?.serialized_credential != credential.credential.serializedForWalletPersistence()) return@transactionWithResult false
        queries.deleteCredentialStatus(credential.id)
        true
    }

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

    /**
     * Stores a credential and its display metadata.
     */
    override suspend fun addCredential(entry: StoredCredential) {
        val rawString = entry.credential.serializedForWalletPersistence()
        queries.transaction {
            if (queries.selectCredentialById(entry.id).executeAsOneOrNull()?.serialized_credential != rawString) queries.deleteCredentialStatus(entry.id)
        queries.insertCredential(
            id = entry.id,
            serialized_credential = rawString,
            format = entry.credential.format,
            label = entry.label,
            added_at = entry.addedAt?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds(),
            metadata = entry.metadata?.let { json.encodeToString(JsonObject.serializer(), it) },
            holder_key_binding = entry.holderKeyBinding?.let {
                json.encodeToString(HolderKeyBinding.serializer(), it)
            },
        )
        }
    }

    /**
     * Removes a credential by wallet-local identifier.
     */
    override suspend fun removeCredential(id: String): Boolean {
        val exists = queries.selectCredentialById(id).executeAsOneOrNull() != null
        if (exists) queries.transaction {
            queries.deleteCredentialStatus(id)
            queries.deleteCredentialById(id)
        }
        return exists
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
