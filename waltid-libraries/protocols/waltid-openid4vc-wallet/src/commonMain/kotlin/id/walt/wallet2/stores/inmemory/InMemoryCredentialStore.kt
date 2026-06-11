package id.walt.wallet2.stores.inmemory

import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * In-memory [WalletCredentialStore].
 */
class InMemoryCredentialStore : WalletCredentialStore {

    private val credentials = mutableMapOf<String, StoredCredential>()

    override suspend fun getCredential(id: String): StoredCredential? =
        credentials[id]

    override fun listCredentials(): Flow<StoredCredential> =
        credentials.values.toList().asFlow()

    override suspend fun addCredential(entry: StoredCredential) {
        credentials[entry.id] = entry
    }

    override suspend fun removeCredential(id: String): Boolean =
        credentials.remove(id) != null
}
