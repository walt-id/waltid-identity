package id.walt.wallet2.stores.inmemory

import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.WalletStore

/**
 * In-memory [WalletStore] implementation.
 *
 * Shipped in the base library and used as the OSS default.
 * All data is lost on process restart — no setup required.
 *
 * For persistence, replace this with your own [WalletStore] implementation
 * and supply it via [id.walt.wallet2.server.WalletResolver].
 */
class InMemoryWalletStore : WalletStore {

    private val wallets = mutableMapOf<String, Wallet>()
    private val accountWallets = mutableMapOf<String, MutableList<String>>()

    override suspend fun loadWallet(walletId: String): Wallet? = wallets[walletId]

    override suspend fun saveWallet(wallet: Wallet) {
        wallets[wallet.id] = wallet
    }

    override suspend fun deleteWallet(walletId: String) {
        wallets.remove(walletId)
    }

    override suspend fun listWalletIds(): List<String> = wallets.keys.toList()

    override suspend fun linkWalletToAccount(accountId: String, walletId: String) {
        accountWallets.getOrPut(accountId) { mutableListOf() }.add(walletId)
    }

    override suspend fun getWalletIdsForAccount(accountId: String): List<String>? =
        accountWallets[accountId]?.toList()
}
