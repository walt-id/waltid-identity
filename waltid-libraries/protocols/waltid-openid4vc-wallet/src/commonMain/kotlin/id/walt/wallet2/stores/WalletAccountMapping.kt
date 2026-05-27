package id.walt.wallet2.stores

/**
 * Optional account-to-wallet ownership mapping.
 *
 * Implemented only when user authentication is enabled. The default no-op
 * behaviour means auth-disabled deployments (stateless, store-per-wallet,
 * or externally managed auth) require zero changes to adopt wallet2.
 *
 * The OSS [InMemoryWalletStore] implements this directly for the full-standalone
 * use case. Persistent implementations provide a separate table/collection.
 * The Enterprise omits this entirely (its permission system handles ownership).
 */
interface WalletAccountMapping {

    /**
     * Associate [walletId] with [accountId].
     * Called automatically after wallet creation when auth is enabled.
     * Default: no-op.
     */
    suspend fun linkWalletToAccount(accountId: String, walletId: String) {}

    /**
     * Return all wallet IDs owned by [accountId].
     *
     * Returns `null` to signal that account-based ownership enforcement is
     * not applicable — the caller should fall back to unrestricted access.
     * Returns an empty list when ownership is enforced but the account has
     * no wallets yet.
     *
     * Default: returns null (no enforcement).
     */
    suspend fun getWalletIdsForAccount(accountId: String): List<String>? = null
}
