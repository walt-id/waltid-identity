package id.walt.wallet2.persistence

import id.walt.wallet2.data.WalletDescriptor
import id.walt.wallet2.stores.WalletStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowimport org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

/**
 * Exposed-backed [WalletStore].
 *
 * Persists [WalletDescriptor] across the wallet, junction, and store-registration tables.
 * Also implements [id.walt.wallet2.stores.WalletAccountMapping] for account-ownership tracking.
 */
class ExposedWalletStore(private val db: Database) : WalletStore {

    override suspend fun loadDescriptor(walletId: String): WalletDescriptor? =
        newSuspendedTransaction(db = db) {
            val walletRow = Wallet2Tables.Wallets.selectAll()
                .where { Wallet2Tables.Wallets.id eq walletId }
                .firstOrNull() ?: return@newSuspendedTransaction null

            val keyStoreIds = Wallet2Tables.WalletKeyStores.selectAll()
                .where { Wallet2Tables.WalletKeyStores.walletId eq walletId }
                .orderBy(Wallet2Tables.WalletKeyStores.position)
                .map { it[Wallet2Tables.WalletKeyStores.storeId] }

            val credentialStoreIds = Wallet2Tables.WalletCredentialStores.selectAll()
                .where { Wallet2Tables.WalletCredentialStores.walletId eq walletId }
                .orderBy(Wallet2Tables.WalletCredentialStores.position)
                .map { it[Wallet2Tables.WalletCredentialStores.storeId] }

            val didStoreId = Wallet2Tables.WalletDidStores.selectAll()
                .where { Wallet2Tables.WalletDidStores.walletId eq walletId }
                .firstOrNull()
                ?.get(Wallet2Tables.WalletDidStores.storeId)

            WalletDescriptor(
                id = walletId,
                keyStoreIds = keyStoreIds,
                credentialStoreIds = credentialStoreIds,
                didStoreId = didStoreId,
                serializedStaticKey = walletRow[Wallet2Tables.Wallets.serializedStaticKey],
                staticDid = walletRow[Wallet2Tables.Wallets.staticDid]
            )
        }

    override suspend fun saveDescriptor(descriptor: WalletDescriptor) {
        newSuspendedTransaction(db = db) {
            // Upsert wallet row
            Wallet2Tables.Wallets.upsert {
                it[Wallet2Tables.Wallets.id] = descriptor.id
                it[Wallet2Tables.Wallets.serializedStaticKey] = descriptor.serializedStaticKey
                it[Wallet2Tables.Wallets.staticDid] = descriptor.staticDid
            }

            // Ensure named store rows exist
            descriptor.keyStoreIds.forEach { storeId ->
                Wallet2Tables.KeyStores.upsert { it[Wallet2Tables.KeyStores.id] = storeId }
            }
            descriptor.credentialStoreIds.forEach { storeId ->
                Wallet2Tables.CredentialStores.upsert { it[Wallet2Tables.CredentialStores.id] = storeId }
            }
            descriptor.didStoreId?.let { storeId ->
                Wallet2Tables.DidStores.upsert { it[Wallet2Tables.DidStores.id] = storeId }
            }

            // Replace junction rows (delete + insert to maintain order)
            Wallet2Tables.WalletKeyStores.deleteWhere { Wallet2Tables.WalletKeyStores.walletId eq descriptor.id }
            descriptor.keyStoreIds.forEachIndexed { pos, storeId ->
                Wallet2Tables.WalletKeyStores.insert {
                    it[Wallet2Tables.WalletKeyStores.walletId] = descriptor.id
                    it[Wallet2Tables.WalletKeyStores.storeId] = storeId
                    it[Wallet2Tables.WalletKeyStores.position] = pos
                }
            }

            Wallet2Tables.WalletCredentialStores.deleteWhere { Wallet2Tables.WalletCredentialStores.walletId eq descriptor.id }
            descriptor.credentialStoreIds.forEachIndexed { pos, storeId ->
                Wallet2Tables.WalletCredentialStores.insert {
                    it[Wallet2Tables.WalletCredentialStores.walletId] = descriptor.id
                    it[Wallet2Tables.WalletCredentialStores.storeId] = storeId
                    it[Wallet2Tables.WalletCredentialStores.position] = pos
                }
            }

            Wallet2Tables.WalletDidStores.deleteWhere { Wallet2Tables.WalletDidStores.walletId eq descriptor.id }
            descriptor.didStoreId?.let { storeId ->
                Wallet2Tables.WalletDidStores.insert {
                    it[Wallet2Tables.WalletDidStores.walletId] = descriptor.id
                    it[Wallet2Tables.WalletDidStores.storeId] = storeId
                }
            }
        }
    }

    override suspend fun deleteWallet(walletId: String) {
        newSuspendedTransaction(db = db) {
            Wallet2Tables.WalletKeyStores.deleteWhere { Wallet2Tables.WalletKeyStores.walletId eq walletId }
            Wallet2Tables.WalletCredentialStores.deleteWhere { Wallet2Tables.WalletCredentialStores.walletId eq walletId }
            Wallet2Tables.WalletDidStores.deleteWhere { Wallet2Tables.WalletDidStores.walletId eq walletId }
            Wallet2Tables.AccountWallets.deleteWhere { Wallet2Tables.AccountWallets.walletId eq walletId }
            Wallet2Tables.Wallets.deleteWhere { Wallet2Tables.Wallets.id eq walletId }
        }
    }

    override fun listWalletIds(): Flow<String> = flow {
        val ids = newSuspendedTransaction(db = db) {
            Wallet2Tables.Wallets.selectAll().map { it[Wallet2Tables.Wallets.id] }
        }
        ids.forEach { emit(it) }
    }

    override suspend fun linkWalletToAccount(accountId: String, walletId: String) {
        newSuspendedTransaction(db = db) {
            Wallet2Tables.AccountWallets.upsert {
                it[Wallet2Tables.AccountWallets.accountId] = accountId
                it[Wallet2Tables.AccountWallets.walletId] = walletId
            }
        }
    }

    override suspend fun getWalletIdsForAccount(accountId: String): Flow<String> = flow {
        val ids = newSuspendedTransaction(db = db) {
            Wallet2Tables.AccountWallets.selectAll()
                .where { Wallet2Tables.AccountWallets.accountId eq accountId }
                .map { it[Wallet2Tables.AccountWallets.walletId] }
        }
        ids.forEach { emit(it) }
    }
}
