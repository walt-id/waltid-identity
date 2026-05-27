package id.walt.wallet2.stores

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
    fun listWalletIds(): Flow<String>

    /** Convenience: collect all wallet IDs as a list. */
    suspend fun listWalletIdsAsList(): Flow<String> = listWalletIds()
}

