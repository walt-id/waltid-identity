package id.walt.wallet2.stores.inmemory

import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.status.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [WalletCredentialStore].
 */
class InMemoryCredentialStore : WalletCredentialStore, CredentialStatusSnapshotStore {

    private val mutex = Mutex()
    private val credentials = mutableMapOf<String, StoredCredential>()
    private val statuses = mutableMapOf<String, PersistedCredentialStatus>()

    override suspend fun getCredential(id: String): StoredCredential? =
        mutex.withLock { credentials[id] }

    override suspend fun listCredentials(): Flow<StoredCredential> =
        mutex.withLock { credentials.values.toList() }.asFlow()

    override suspend fun addCredential(entry: StoredCredential) {
        mutex.withLock {
            if (credentials[entry.id]?.statusFingerprint() != entry.statusFingerprint()) statuses.remove(entry.id)
            credentials[entry.id] = entry
        }
    }

    override suspend fun removeCredential(id: String): Boolean =
        mutex.withLock { statuses.remove(id); credentials.remove(id) != null }

    override suspend fun readCredentialStatus(credentialId: String): PersistedCredentialStatus? =
        mutex.withLock { statuses[credentialId] }

    override suspend fun saveCredentialStatusIfCurrent(credential: StoredCredential, status: PersistedCredentialStatus): Boolean =
        mutex.withLock {
            if (credentials[credential.id]?.statusFingerprint() != credential.statusFingerprint()) false
            else { statuses[credential.id] = status; true }
        }

    override suspend fun removeCredentialStatusIfCurrent(credential: StoredCredential): Boolean = mutex.withLock {
        if (credentials[credential.id]?.statusFingerprint() != credential.statusFingerprint()) false
        else { statuses.remove(credential.id); true }
    }
}
