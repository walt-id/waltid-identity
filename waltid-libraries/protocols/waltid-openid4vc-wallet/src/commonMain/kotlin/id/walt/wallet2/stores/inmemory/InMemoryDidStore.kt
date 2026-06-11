package id.walt.wallet2.stores.inmemory

import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * In-memory [id.walt.wallet2.data.WalletDidStore].
 */
class InMemoryDidStore : WalletDidStore {

    private val dids = mutableMapOf<String, WalletDidEntry>()

    override suspend fun getDid(did: String): WalletDidEntry? = dids[did]

    override fun listDids(): Flow<WalletDidEntry> =
        dids.values.toList().asFlow()

    override suspend fun addDid(entry: WalletDidEntry) {
        dids[entry.did] = entry
    }

    override suspend fun removeDid(did: String): Boolean =
        dids.remove(did) != null
}
