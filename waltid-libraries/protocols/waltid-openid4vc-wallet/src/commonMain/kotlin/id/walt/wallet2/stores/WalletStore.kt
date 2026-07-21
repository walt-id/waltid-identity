package id.walt.wallet2.stores

import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletDescriptor
import kotlinx.coroutines.flow.Flow

/**
 * Pluggable wallet lifecycle store.
 *
 * Stores and retrieves [WalletDescriptor] — the serializable configuration of
 * a wallet (store IDs, static key/DID). The live [Wallet] object with runtime
 * store instances is assembled by [id.walt.wallet2.server.WalletResolver] after
 * loading the descriptor.
 *
 * Also extends [WalletAccountMapping] for optional account-ownership tracking
 * when auth is enabled. Default implementations are no-ops.
 *
 * Implementations:
 * - [inmemory.InMemoryWalletStore] — default, no setup, lost on restart
 * - Exposed/SQL — pluggable persistent implementation (waltid-openid4vc-wallet-persistence)
 * - Enterprise — MongoDB-backed via WalletServiceInit (in waltid-enterprise-api)
 */
interface WalletStore : WalletAccountMapping {

    /** Retrieve a wallet descriptor by its ID. Returns null if not found. */
    suspend fun loadDescriptor(walletId: String): WalletDescriptor?

    /**
     * Persist a wallet descriptor. Should be idempotent (upsert semantics).
     */
    suspend fun saveDescriptor(descriptor: WalletDescriptor)

    /** Remove a wallet by ID. No-op if not found. */
    suspend fun deleteWallet(walletId: String)

    /** Stream all wallet IDs known to this store. */
    suspend fun listWalletIds(): Flow<String>

    /**
     * Returns a live [Wallet] object for [walletId] if this store keeps live objects in memory
     * (e.g. [id.walt.wallet2.stores.inmemory.InMemoryWalletStore]), or null otherwise.
     *
     * Used by [id.walt.wallet2.server.WalletResolver] to avoid descriptor serialization when the
     * wallet is already in memory. Persistent stores leave this at the default (null), causing
     * the resolver to fall back to [loadDescriptor] + assembly. This removes the need for the
     * resolver to downcast to [id.walt.wallet2.stores.inmemory.InMemoryWalletStore].
     */
    suspend fun loadWallet(walletId: String): Wallet? = null

    /**
     * Stores a live [Wallet] object directly when the store keeps live objects in memory.
     * Returns true if the store handled the storage (in-memory path), false if it is a no-op
     * (persistent stores, which use [saveDescriptor] instead).
     * See [loadWallet] for rationale.
     */
    suspend fun saveWallet(wallet: Wallet): Boolean = false
}

