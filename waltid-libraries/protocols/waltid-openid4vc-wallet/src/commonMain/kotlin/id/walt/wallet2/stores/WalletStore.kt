package id.walt.wallet2.stores

import id.walt.wallet2.data.Wallet

/**
 * Pluggable wallet lifecycle store.
 *
 * Handles creation, retrieval, deletion and listing of [Wallet] instances.
 * Item-level stores (keys, credentials, DIDs) remain independent — this
 * interface only manages the wallet shell and account-to-wallet mappings.
 *
 * Implementations:
 * - [id.walt.wallet2.stores.inmemory.InMemoryWalletStore] — default, ships in
 *   the base library, no setup required, lost on restart
 * - OSS persistent — bring your own (e.g. Exposed + SQLite/Postgres); plug in
 *   via [id.walt.wallet2.server.WalletResolver.walletStore]
 * - Enterprise — MongoDB-backed implementation in waltid-enterprise-api,
 *   delegates wallet creation to WalletServiceInit
 */
interface WalletStore {

    /** Retrieve a wallet by its ID. Returns null if not found. */
    suspend fun loadWallet(walletId: String): Wallet?

    /**
     * Persist a wallet.
     *
     * For in-memory implementations this is a simple map put.
     * For persistent implementations this should be idempotent (upsert).
     * For Enterprise this provisions a new WalletServiceReference node.
     */
    suspend fun saveWallet(wallet: Wallet)

    /** Remove a wallet by ID. No-op if not found. */
    suspend fun deleteWallet(walletId: String)

    /** List all wallet IDs known to this store. */
    suspend fun listWalletIds(): List<String>

    // ---------------------------------------------------------------------------
    // Account ↔ wallet mapping — used when auth is enabled.
    // Default implementations are no-ops (auth-disabled deployments).
    // ---------------------------------------------------------------------------

    /**
     * Associate [walletId] with [accountId].
     * Called automatically after wallet creation when auth is enabled.
     */
    suspend fun linkWalletToAccount(accountId: String, walletId: String) {}

    /**
     * Return all wallet IDs owned by [accountId], or null if the concept of
     * account ownership is not applicable (e.g. Enterprise uses its own
     * permission system and returns null to skip OSS-style enforcement).
     */
    suspend fun getWalletIdsForAccount(accountId: String): List<String>? = null
}
