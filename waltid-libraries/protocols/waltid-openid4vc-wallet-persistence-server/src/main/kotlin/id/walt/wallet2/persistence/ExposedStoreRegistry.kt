package id.walt.wallet2.persistence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert

class ExposedStoreRegistry(private val db: Database) {
    suspend fun resolveKeyStore(storeId: String): ExposedKeyStore? =
        if (keyStoreExists(storeId)) ExposedKeyStore(storeId, db) else null

    suspend fun createKeyStore(storeId: String): ExposedKeyStore {
        suspendTransaction(db) {
            Wallet2Tables.KeyStores.upsert { it[Wallet2Tables.KeyStores.id] = storeId }
        }
        return ExposedKeyStore(storeId, db)
    }

    fun listKeyStoreIds(): Flow<String> = flow {
        suspendTransaction(db) {
            Wallet2Tables.KeyStores.selectAll().map { it[Wallet2Tables.KeyStores.id] }
        }.forEach { emit(it) }
    }

    suspend fun resolveCredentialStore(storeId: String): ExposedCredentialStore? =
        if (credentialStoreExists(storeId)) ExposedCredentialStore(storeId, db) else null

    suspend fun createCredentialStore(storeId: String): ExposedCredentialStore {
        suspendTransaction(db) {
            Wallet2Tables.CredentialStores.upsert { it[Wallet2Tables.CredentialStores.id] = storeId }
        }
        return ExposedCredentialStore(storeId, db)
    }

    fun listCredentialStoreIds(): Flow<String> = flow {
        suspendTransaction(db) {
            Wallet2Tables.CredentialStores.selectAll().map { it[Wallet2Tables.CredentialStores.id] }
        }.forEach { emit(it) }
    }

    suspend fun resolveDidStore(storeId: String): ExposedDidStore? =
        if (didStoreExists(storeId)) ExposedDidStore(storeId, db) else null

    suspend fun createDidStore(storeId: String): ExposedDidStore {
        suspendTransaction(db) {
            Wallet2Tables.DidStores.upsert { it[Wallet2Tables.DidStores.id] = storeId }
        }
        return ExposedDidStore(storeId, db)
    }

    fun listDidStoreIds(): Flow<String> = flow {
        suspendTransaction(db) {
            Wallet2Tables.DidStores.selectAll().map { it[Wallet2Tables.DidStores.id] }
        }.forEach { emit(it) }
    }

    private suspend fun keyStoreExists(storeId: String): Boolean = suspendTransaction(db) {
        Wallet2Tables.KeyStores.selectAll().where { Wallet2Tables.KeyStores.id eq storeId }.any()
    }

    private suspend fun credentialStoreExists(storeId: String): Boolean = suspendTransaction(db) {
        Wallet2Tables.CredentialStores.selectAll().where { Wallet2Tables.CredentialStores.id eq storeId }.any()
    }

    private suspend fun didStoreExists(storeId: String): Boolean = suspendTransaction(db) {
        Wallet2Tables.DidStores.selectAll().where { Wallet2Tables.DidStores.id eq storeId }.any()
    }
}
