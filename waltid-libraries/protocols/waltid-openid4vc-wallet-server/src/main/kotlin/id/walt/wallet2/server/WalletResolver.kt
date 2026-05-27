package id.walt.wallet2.server

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDescriptor
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.stores.WalletStore
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Storage-backend abstraction used by [Wallet2RouteHandler].
 *
 * Bridges the [WalletStore] (descriptor-based persistence) with the live [Wallet]
 * runtime object (store instances). All wallet CRUD in [Wallet2RouteHandler] calls
 * through here, keeping route logic decoupled from storage details.
 *
 * For the [InMemoryWalletStore] default, wallet objects are kept in memory directly.
 * For persistent stores, [resolveWallet] assembles a [Wallet] from the persisted
 * [WalletDescriptor] by resolving each named store ID via [resolveKeyStore] etc.
 */
interface WalletResolver {

    /** Public base URL of this service instance, used in response URLs. */
    val publicBaseUrl: Url

    /**
     * The pluggable wallet lifecycle store.
     * Swap this for a persistent implementation without changing any handler logic.
     */
    val walletStore: WalletStore

    /**
     * Resolves a [Wallet] by ID.
     *
     * For [InMemoryWalletStore]: returns the live wallet object directly.
     * For persistent stores: loads the [WalletDescriptor] and assembles the
     * [Wallet] by resolving each store ID via [resolveKeyStore]/[resolveCredentialStore]/[resolveDidStore].
     */
    suspend fun resolveWallet(walletId: String): Wallet? {
        val inMemory = walletStore as? InMemoryWalletStore
        if (inMemory != null) return inMemory.getWallet(walletId)

        val descriptor = walletStore.loadDescriptor(walletId) ?: return null
        return assembleWallet(descriptor)
    }

    /**
     * Persists a newly created [Wallet].
     *
     * For [InMemoryWalletStore]: stores the live object directly.
     * For persistent stores: saves the [WalletDescriptor] derived from the wallet.
     */
    suspend fun storeWallet(wallet: Wallet) {
        val inMemory = walletStore as? InMemoryWalletStore
        if (inMemory != null) {
            inMemory.putWallet(wallet)
            return
        }
        val serializedStaticKey = wallet.staticKey?.let { KeySerialization.serializeKey(it) }
        val descriptor = WalletDescriptor(
            id = wallet.id,
            keyStoreIds = wallet.keyStores.mapNotNull { resolveStoreId(it) },
            credentialStoreIds = wallet.credentialStores.mapNotNull { resolveStoreId(it) },
            didStoreId = wallet.didStore?.let { resolveStoreId(it) },
            serializedStaticKey = serializedStaticKey,
            staticDid = wallet.staticDid
        )
        walletStore.saveDescriptor(descriptor)
    }

    suspend fun deleteWallet(walletId: String) = walletStore.deleteWallet(walletId)
    fun listWalletIds(): Flow<String> = walletStore.listWalletIds()
    suspend fun linkWalletToAccount(accountId: String, walletId: String) =
        walletStore.linkWalletToAccount(accountId, walletId)
    suspend fun getWalletIdsForAccount(accountId: String): Flow<String>? =
        walletStore.getWalletIdsForAccount(accountId)

    // ---------------------------------------------------------------------------
    // Named store management — needed when POST /wallet references storeIds,
    // or when POST /stores/* creates named stores.
    // Persistent implementations provide their own registry (e.g. a stores table).
    // Default: no-op / null (simple deployments that auto-create in-memory stores).
    // ---------------------------------------------------------------------------

    suspend fun resolveKeyStore(storeId: String): WalletKeyStore? = null
    suspend fun storeKeyStore(storeId: String, store: WalletKeyStore) {}
    fun listKeyStoreIds(): Flow<String> = emptyFlow()

    suspend fun resolveCredentialStore(storeId: String): WalletCredentialStore? = null
    suspend fun storeCredentialStore(storeId: String, store: WalletCredentialStore) {}
    fun listCredentialStoreIds(): Flow<String> = emptyFlow()

    suspend fun resolveDidStore(storeId: String): WalletDidStore? = null
    suspend fun storeDidStore(storeId: String, store: WalletDidStore) {}
    fun listDidStoreIds(): Flow<String> = emptyFlow()

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Assembles a live [Wallet] from a [WalletDescriptor] by resolving each store ID.
     * Falls back to a fresh in-memory store for any store ID that cannot be resolved.
     */
    suspend fun assembleWallet(descriptor: WalletDescriptor): Wallet {
        val keyStores = descriptor.keyStoreIds.mapNotNull { resolveKeyStore(it) }
            .ifEmpty { emptyList() }
        val credentialStores = descriptor.credentialStoreIds.mapNotNull { resolveCredentialStore(it) }
            .ifEmpty { emptyList() }
        val didStore = descriptor.didStoreId?.let { resolveDidStore(it) }
        val staticKey = descriptor.serializedStaticKey?.let {
            runCatching { KeyManager.resolveSerializedKey(it) }.getOrNull()
        }
        return Wallet(
            id = descriptor.id,
            keyStores = keyStores,
            credentialStores = credentialStores,
            didStore = didStore,
            staticKey = staticKey,
            staticDid = descriptor.staticDid
        )
    }

    /** Resolves the registered storeId for a given store instance, if any. */
    suspend fun resolveStoreId(store: Any): String? = null
}
