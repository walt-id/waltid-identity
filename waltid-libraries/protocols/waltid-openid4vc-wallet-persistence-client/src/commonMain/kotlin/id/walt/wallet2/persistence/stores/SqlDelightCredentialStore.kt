package id.walt.wallet2.persistence.stores

import id.walt.credentials.CredentialParser
import id.walt.wallet2.persistence.db.Credentials
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

class SqlDelightCredentialStore(
    private val queries: WalletPersistenceQueries,
) : WalletCredentialStore {

    override suspend fun getCredential(id: String): StoredCredential? {
        val row = queries.selectCredentialById(id).executeAsOneOrNull() ?: return null
        return row.toStoredCredential()
    }

    override suspend fun listCredentials(): Flow<StoredCredential> = flow {
        queries.selectAllCredentials().executeAsList().forEach { row ->
            emit(row.toStoredCredential())
        }
    }

    override suspend fun addCredential(entry: StoredCredential) {
        val rawString = entry.credential.signed ?: entry.credential.credentialData.toString()
        queries.insertCredential(
            id = entry.id,
            serialized_credential = rawString,
            format = entry.credential.format,
            label = entry.label,
            added_at = entry.addedAt?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds(),
        )
    }

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
