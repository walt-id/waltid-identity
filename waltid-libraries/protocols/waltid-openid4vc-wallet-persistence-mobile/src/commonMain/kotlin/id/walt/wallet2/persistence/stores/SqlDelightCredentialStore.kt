package id.walt.wallet2.persistence.stores

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.wallet2.persistence.db.Credentials
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQLDelight-backed implementation of [WalletCredentialStore] for mobile wallets.
 *
 * @param queries SQLDelight queries for wallet persistence tables.
 */
public class SqlDelightCredentialStore(
    private val queries: WalletPersistenceQueries,
) : WalletCredentialStore {

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
        queries.insertCredential(
            id = entry.id,
            serialized_credential = rawString,
            format = entry.credential.format,
            label = entry.label,
            added_at = entry.addedAt?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds(),
        )
    }

    /**
     * Removes a credential by wallet-local identifier.
     */
    override suspend fun removeCredential(id: String): Boolean {
        val exists = queries.selectCredentialById(id).executeAsOneOrNull() != null
        if (exists) queries.deleteCredentialById(id)
        return exists
    }

    private suspend fun Credentials.toStoredCredential(): StoredCredential {
        val (_, credential) = CredentialParser.detectAndParse(serialized_credential)
        return StoredCredential(
            id = id,
            credential = credential,
            label = label,
            addedAt = Instant.fromEpochMilliseconds(added_at),
        )
    }
}

internal fun DigitalCredential.serializedForWalletPersistence(): String =
    (this as? SelectivelyDisclosableVerifiableCredential)?.signedWithDisclosures?.takeIf { it.isNotBlank() }
        ?: signed?.takeIf { it.isNotBlank() }
        ?: credentialData.toString()
