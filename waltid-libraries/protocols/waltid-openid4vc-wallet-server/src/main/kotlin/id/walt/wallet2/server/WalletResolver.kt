package id.walt.wallet2.server

import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.stores.WalletStore
import io.ktor.http.*

/**
 * Storage-backend abstraction used by [Wallet2RouteHandler].
 *
 * The OSS service implements this with [id.walt.wallet2.stores.inmemory.InMemoryWalletStore]
 * by default; deployers can swap in any [WalletStore] implementation for persistence.
 *
 * The Enterprise service provides its own implementation backed by the MongoDB
 * resource tree, with wallet lifecycle delegated to WalletServiceInit.
 *
 * Named store resolution (resolveKeyStore etc.) is only needed when
 * POST /wallet supplies explicit storeIds. For the simple default case
 * (empty body) the route handler creates in-memory stores directly.
 */
interface WalletResolver {

    /** Public base URL of this service instance, used in response URLs. */
    val publicBaseUrl: Url

    /**
     * The pluggable wallet lifecycle store.
     *
     * All wallet CRUD operations in [Wallet2RouteHandler] delegate here,
     * making it trivial for deployers to swap in a persistent implementation
     * without touching any route or handler logic.
     */
    val walletStore: WalletStore

    // Convenience delegators — route handler code calls these directly
    suspend fun resolveWallet(walletId: String): Wallet? = walletStore.loadWallet(walletId)
    suspend fun storeWallet(wallet: Wallet) = walletStore.saveWallet(wallet)
    suspend fun deleteWallet(walletId: String) = walletStore.deleteWallet(walletId)
    suspend fun listWalletIds(): List<String> = walletStore.listWalletIds()
    suspend fun linkWalletToAccount(accountId: String, walletId: String) =
        walletStore.linkWalletToAccount(accountId, walletId)
    suspend fun getWalletIdsForAccount(accountId: String): List<String>? =
        walletStore.getWalletIdsForAccount(accountId)

    // ---------------------------------------------------------------------------
    // Named store management — used when POST /wallet references stores by ID,
    // or when POST /stores/* creates a named store.
    // Default implementations return null / do nothing (simple deployments that
    // only use auto-created in-memory stores never need these).
    // ---------------------------------------------------------------------------

    suspend fun resolveKeyStore(storeId: String): WalletKeyStore? = null
    suspend fun storeKeyStore(storeId: String, store: WalletKeyStore) {}
    suspend fun listKeyStoreIds(): List<String> = emptyList()

    suspend fun resolveCredentialStore(storeId: String): WalletCredentialStore? = null
    suspend fun storeCredentialStore(storeId: String, store: WalletCredentialStore) {}
    suspend fun listCredentialStoreIds(): List<String> = emptyList()

    suspend fun resolveDidStore(storeId: String): WalletDidStore? = null
    suspend fun storeDidStore(storeId: String, store: WalletDidStore) {}
    suspend fun listDidStoreIds(): List<String> = emptyList()
}
