package id.walt.wallet2.server

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDescriptor
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.stores.WalletStore
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Factory for creating named store instances.
 *
 * The default implementations create in-memory stores. When persistence is enabled,
 * replace these with factories that produce [id.walt.wallet2.persistence.ExposedKeyStore] etc.
 * backed by the same database.
 *
 * Declaring as a `fun interface` allows concise lambda syntax:
 * ```kotlin
 * keyStoreFactory = { id -> ExposedKeyStore(id, db) }
 * ```
 */
/**
 * Factory for creating named store instances, expressed as a plain function type.
 *
 * Using a typealias keeps the API surface minimal - callers use standard lambda syntax
 * `{ id -> ExposedKeyStore(id, db) }` and invoke it with `factory(storeId)`.
 */
typealias StoreFactory<T> = (storeId: String) -> T

/**
 * Storage-backend abstraction used by [Wallet2RouteHandler].
 *
 * Bridges the [WalletStore] (descriptor-based persistence) with the live [Wallet]
 * runtime object (store instances). All wallet CRUD in [Wallet2RouteHandler] calls
 * through here, keeping route logic decoupled from storage details.
 *
 * For in-memory stores, wallet objects are kept in memory directly.
 * For persistent stores, [resolveWallet] assembles a [Wallet] from the persisted
 * [WalletDescriptor] by resolving each named store ID via [resolveKeyStore] etc.
 *
 * The three [StoreFactory] properties control what kind of store is auto-created when
 * a new wallet is created without explicit store IDs, and when a named store is created
 * via the `/stores/{storeId}` routes. Swap them for Exposed-backed factories to get persistence.
 */
interface WalletResolver {

    /** Public base URL of this service instance, used in response URLs. */
    val publicBaseUrl: Url

    /**
     * The pluggable wallet lifecycle store.
     * Swap this for a persistent implementation without changing any handler logic.
     */
    val walletStore: WalletStore

    /** Factory for key stores. Default: in-memory. Replace with an Exposed-backed factory for persistence. */
    val keyStoreFactory: StoreFactory<WalletKeyStore>
        get() = { InMemoryKeyStore() }

    /** Factory for credential stores. Default: in-memory. */
    val credentialStoreFactory: StoreFactory<WalletCredentialStore>
        get() = { InMemoryCredentialStore() }

    /** Factory for DID stores. Default: in-memory. */
    val didStoreFactory: StoreFactory<WalletDidStore>
        get() = { InMemoryDidStore() }

    /**
     * Resolves a [Wallet] by ID.
     *
     * If the store keeps live [Wallet] objects (e.g. in-memory), returns one directly via
     * [WalletStore.loadWallet]. Otherwise loads the [WalletDescriptor] and assembles the
     * [Wallet] by resolving each store ID via [resolveKeyStore]/[resolveCredentialStore]/[resolveDidStore].
     */
    suspend fun resolveWallet(walletId: String): Wallet? =
        walletStore.loadWallet(walletId) ?: walletStore.loadDescriptor(walletId)?.let { assembleWallet(it) }

    /**
     * Persists a newly created [Wallet].
     *
     * Delegates to [WalletStore.saveWallet] first. If the store handles it (returns true, e.g.
     * in-memory), we are done. Otherwise serializes the wallet to a [WalletDescriptor] and saves
     * that via [WalletStore.saveDescriptor] (persistent stores).
     */
    suspend fun storeWallet(wallet: Wallet) {
        if (walletStore.saveWallet(wallet)) return
        val serializedStaticKey = wallet.staticKey?.let { KeySerialization.serializeKey(it) }
        val descriptor = WalletDescriptor(
            id = wallet.id,
            keyStoreIds = wallet.keyStores.mapNotNull { resolveStoreId(it) },
            credentialStoreIds = wallet.credentialStores.mapNotNull { resolveStoreId(it) },
            didStoreId = wallet.didStore?.let { resolveStoreId(it) },
            serializedStaticKey = serializedStaticKey,
            staticDid = wallet.staticDid,
            defaultKeyId = wallet.defaultKeyId,
            defaultDidId = wallet.defaultDidId,
        )
        walletStore.saveDescriptor(descriptor)
    }

    suspend fun deleteWallet(walletId: String) = walletStore.deleteWallet(walletId)
    suspend fun listWalletIds(): Flow<String> = walletStore.listWalletIds()
    suspend fun linkWalletToAccount(accountId: String, walletId: String) =
        walletStore.linkWalletToAccount(accountId, walletId)
    suspend fun getWalletIdsForAccount(accountId: String): List<String>? =
        walletStore.getWalletIdsForAccount(accountId)

    // ---------------------------------------------------------------------------
    // Named store management - needed when POST /wallet references storeIds,
    // or when POST /stores/{storeId} creates named stores.
    // Persistent implementations provide their own registry (e.g. a stores table).
    // Default: no-op / null (simple deployments that auto-create stores via the factories).
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
     */
    suspend fun assembleWallet(descriptor: WalletDescriptor): Wallet {
        val keyStores = descriptor.keyStoreIds.mapNotNull { resolveKeyStore(it) }
        val credentialStores = descriptor.credentialStoreIds.mapNotNull { resolveCredentialStore(it) }
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
            staticDid = descriptor.staticDid,
            defaultKeyId = descriptor.defaultKeyId,
            defaultDidId = descriptor.defaultDidId,
        )
    }

    /** Resolves the registered storeId for a given store instance, if any. */
    suspend fun resolveStoreId(store: Any): String? = null

    /**
     * Persists updated [defaultKeyId] / [defaultDidId] for an existing wallet.
     *
     * For in-memory stores the live [Wallet] object is replaced with a copy containing the new
     * defaults (via [WalletStore.loadWallet]/[WalletStore.saveWallet]). For persistent stores the
     * descriptor is loaded, updated, and saved.
     */
    suspend fun setWalletDefaults(walletId: String, defaultKeyId: String?, defaultDidId: String?) {
        val liveWallet = walletStore.loadWallet(walletId)
        if (liveWallet != null) {
            walletStore.saveWallet(liveWallet.copy(
                defaultKeyId = defaultKeyId ?: liveWallet.defaultKeyId,
                defaultDidId = defaultDidId ?: liveWallet.defaultDidId,
            ))
            return
        }
        val descriptor = walletStore.loadDescriptor(walletId) ?: return
        walletStore.saveDescriptor(descriptor.copy(
            defaultKeyId = defaultKeyId ?: descriptor.defaultKeyId,
            defaultDidId = defaultDidId ?: descriptor.defaultDidId,
        ))
    }
}
