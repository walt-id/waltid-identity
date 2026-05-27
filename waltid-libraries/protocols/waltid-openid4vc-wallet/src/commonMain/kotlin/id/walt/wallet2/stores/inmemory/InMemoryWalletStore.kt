package id.walt.wallet2.stores.inmemory

import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletDescriptor
import id.walt.wallet2.stores.WalletStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * In-memory [WalletStore] implementation.
 *
 * Shipped in the base library and used as the OSS default.
 * All data is lost on process restart — no setup required.
 *
 * Stores live [Wallet] objects directly alongside their [WalletDescriptor].
 * The [WalletResolver] assembles [Wallet] instances from descriptors for
 * persistent backends; for in-memory we keep the wallet object directly for
 * efficiency and expose it via [getWallet].
 *
 * For persistence, replace this with a [WalletStore] implementation backed
 * by Exposed/SQL or another datastore.
 */
class InMemoryWalletStore : WalletStore {

    private val wallets = mutableMapOf<String, Wallet>()
    private val descriptors = mutableMapOf<String, WalletDescriptor>()
    private val accountWallets = mutableMapOf<String, MutableList<String>>()

    /** Direct wallet access — used by the in-memory WalletResolver path. */
    fun getWallet(walletId: String): Wallet? = wallets[walletId]

    /** Direct wallet storage — used when creating a wallet in the route handler. */
    fun putWallet(wallet: Wallet) {
        wallets[wallet.id] = wallet
    }

    override suspend fun loadDescriptor(walletId: String): WalletDescriptor? =
        descriptors[walletId]

    override suspend fun saveDescriptor(descriptor: WalletDescriptor) {
        descriptors[descriptor.id] = descriptor
    }

    override suspend fun deleteWallet(walletId: String) {
        wallets.remove(walletId)
        descriptors.remove(walletId)
    }

    override fun listWalletIds(): Flow<String> = wallets.keys.toList().asFlow()

    override suspend fun linkWalletToAccount(accountId: String, walletId: String) {
        accountWallets.getOrPut(accountId) { mutableListOf() }.add(walletId)
    }

    override suspend fun getWalletIdsForAccount(accountId: String): Flow<String>? =
        accountWallets[accountId]?.toList()?.asFlow()
}
