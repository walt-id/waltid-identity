package id.walt.wallet2.persistence.stores

import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class SqlDelightDidStore(
    private val queries: WalletPersistenceQueries,
) : WalletDidStore {

    override suspend fun getDid(did: String): WalletDidEntry? {
        val row = queries.selectDidByDid(did).executeAsOneOrNull() ?: return null
        return WalletDidEntry(did = row.did, document = Json.decodeFromString<JsonObject>(row.document))
    }

    override suspend fun listDids(): Flow<WalletDidEntry> = flow {
        queries.selectAllDids().executeAsList().forEach { row ->
            emit(WalletDidEntry(did = row.did, document = Json.decodeFromString<JsonObject>(row.document)))
        }
    }

    override suspend fun addDid(entry: WalletDidEntry) {
        queries.insertDid(
            did = entry.did,
            document = Json.encodeToString(JsonObject.serializer(), entry.document),
        )
    }

    override suspend fun removeDid(did: String): Boolean {
        val exists = queries.selectDidByDid(did).executeAsOneOrNull() != null
        if (exists) queries.deleteDidByDid(did)
        return exists
    }
}
