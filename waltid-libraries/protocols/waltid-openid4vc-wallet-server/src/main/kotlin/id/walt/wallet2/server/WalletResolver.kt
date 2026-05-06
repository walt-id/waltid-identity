package id.walt.wallet2.server

import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import io.ktor.http.*

/**
 * Storage-backend abstraction used by [Wallet2RouteHandler].
 *
 * The OSS service implements this with in-memory maps.
 * The Enterprise service implements it with its MongoDB resource tree,
 * mapping named store IDs to KMS / DID store / credential store references.
 *
 * Named store resolution (resolveKeyStore etc.) is only needed when
 * POST /wallet supplies explicit storeIds. For the simple default case
 * (empty body) the route handler creates in-memory stores directly.
 */
interface WalletResolver {

    /** Public base URL of this service instance, used in response URLs. */
    val publicBaseUrl: Url

    suspend fun resolveWallet(walletId: String): Wallet?

    suspend fun storeWallet(wallet: Wallet)

    suspend fun deleteWallet(walletId: String)

    suspend fun listWalletIds(): List<String>

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

    /**
     * Links a wallet to an account. Called after wallet creation when auth is enabled.
     * Default is a no-op (used when auth is disabled).
     */
    suspend fun linkWalletToAccount(accountId: String, walletId: String) {}

    /**
     * Returns the wallet IDs owned by a given account ID.
     * Used when auth enforcement is enabled. Returns all wallets by default
     * (i.e., when auth is disabled, every wallet is accessible).
     */
    suspend fun getWalletIdsForAccount(accountId: String): List<String>? = null
}
