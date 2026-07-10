package id.walt.wallet2.persistence.stores

import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * SQLDelight-backed implementation of [WalletDidStore] for mobile wallets.
 *
 * @param queries SQLDelight queries for wallet persistence tables.
 */
public class SqlDelightDidStore(
    private val queries: WalletPersistenceQueries,
) : WalletDidStore {

    /**
     * Loads a DID document by DID.
     */
    override suspend fun getDid(did: String): WalletDidEntry? {
        val row = queries.selectDidByDid(did).executeAsOneOrNull() ?: return null
        return WalletDidEntry(did = row.did, document = Json.decodeFromString<JsonObject>(row.document))
    }

    /**
     * Emits all DID documents stored in the mobile database.
     */
    override suspend fun listDids(): Flow<WalletDidEntry> = flow {
        queries.selectAllDids().executeAsList().forEach { row ->
            emit(WalletDidEntry(did = row.did, document = Json.decodeFromString<JsonObject>(row.document)))
        }
    }

    /**
     * Stores or replaces a DID document.
     */
    override suspend fun addDid(entry: WalletDidEntry) {
        queries.insertDid(
            did = entry.did,
            document = Json.encodeToString(JsonObject.serializer(), entry.document),
        )
    }

    /**
     * Removes a DID document by DID.
     */
    override suspend fun removeDid(did: String): Boolean {
        val exists = queries.selectDidByDid(did).executeAsOneOrNull() != null
        if (exists) queries.deleteDidByDid(did)
        return exists
    }
}
