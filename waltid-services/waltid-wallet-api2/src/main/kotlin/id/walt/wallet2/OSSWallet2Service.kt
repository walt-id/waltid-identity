package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureManager
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.wallet2.auth.OSSWallet2AccountStore
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.server.StoreFactory
import id.walt.wallet2.server.WalletResolver
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.stores.WalletStore
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.asFlow

/**
 * OSS [WalletResolver] and route registration.
 *
 * Uses [InMemoryWalletStore] and in-memory store factories by default.
 *
 * When the `wallet2-persistence` feature is enabled, [main] swaps in:
 * - [walletStore] = [id.walt.wallet2.persistence.ExposedWalletStore]
 * - [keyStoreFactory] / [credentialStoreFactory] / [didStoreFactory] = Exposed-backed factories
 *
 * This ensures that keys, credentials, and DIDs are persisted to the same database as the
 * wallet descriptors, and survive restarts.
 *
 * The three named-store caches ([namedKeyStores] etc.) act as an in-process cache on top of
 * whatever the factories produce, avoiding redundant object creation per request.
 */
object OSSWallet2Service {

    var walletStore: WalletStore = InMemoryWalletStore()

    // Store factories - swapped at startup when persistence is enabled.
    // The route handler uses these (via the resolver) to create new stores for auto-created
    // and named stores, so the right store type is created regardless of whether the store ID
    // was supplied by the user or generated automatically.
    var keyStoreFactory: StoreFactory<WalletKeyStore> = StoreFactory { InMemoryKeyStore() }
    var credentialStoreFactory: StoreFactory<WalletCredentialStore> = StoreFactory { InMemoryCredentialStore() }
    var didStoreFactory: StoreFactory<WalletDidStore> = StoreFactory { InMemoryDidStore() }

    // In-process cache: storeId -> store instance.
    // computeIfAbsent ensures that on restart, a store for any ID known to the DB is
    // created on first access without requiring a separate init call.
    private val namedKeyStores = ConcurrentHashMap<String, WalletKeyStore>()
    private val namedCredentialStores = ConcurrentHashMap<String, WalletCredentialStore>()
    private val namedDidStores = ConcurrentHashMap<String, WalletDidStore>()

    val resolver: WalletResolver = object : WalletResolver {

        override val publicBaseUrl: Url
            get() = runCatching {
                ConfigManager.getConfig<OSSWallet2ServiceConfig>().publicBaseUrl
            }.getOrElse { Url("http://localhost:4000") }

        override val walletStore: WalletStore
            get() = OSSWallet2Service.walletStore

        override val keyStoreFactory: StoreFactory<WalletKeyStore>
            get() = OSSWallet2Service.keyStoreFactory

        override val credentialStoreFactory: StoreFactory<WalletCredentialStore>
            get() = OSSWallet2Service.credentialStoreFactory

        override val didStoreFactory: StoreFactory<WalletDidStore>
            get() = OSSWallet2Service.didStoreFactory

        // Named store resolution via computeIfAbsent: if the store is not yet cached,
        // create it using the factory (which may be Exposed-backed for persistence).
        // This handles wallets created in previous process runs whose store IDs exist in
        // the DB but are not yet in the in-process cache.
        override suspend fun resolveKeyStore(storeId: String): WalletKeyStore =
            namedKeyStores.computeIfAbsent(storeId) { keyStoreFactory.create(storeId) }

        override suspend fun storeKeyStore(storeId: String, store: WalletKeyStore) {
            namedKeyStores[storeId] = store
        }

        override fun listKeyStoreIds() = namedKeyStores.keys.toList().asFlow()

        override suspend fun resolveCredentialStore(storeId: String): WalletCredentialStore =
            namedCredentialStores.computeIfAbsent(storeId) { credentialStoreFactory.create(storeId) }

        override suspend fun storeCredentialStore(storeId: String, store: WalletCredentialStore) {
            namedCredentialStores[storeId] = store
        }

        override fun listCredentialStoreIds() = namedCredentialStores.keys.toList().asFlow()

        override suspend fun resolveDidStore(storeId: String): WalletDidStore =
            namedDidStores.computeIfAbsent(storeId) { didStoreFactory.create(storeId) }

        override suspend fun storeDidStore(storeId: String, store: WalletDidStore) {
            namedDidStores[storeId] = store
        }

        override fun listDidStoreIds() = namedDidStores.keys.toList().asFlow()

        override suspend fun resolveStoreId(store: Any): String? =
            namedKeyStores.entries.firstOrNull { it.value === store }?.key
                ?: namedCredentialStores.entries.firstOrNull { it.value === store }?.key
                ?: namedDidStores.entries.firstOrNull { it.value === store }?.key

        override suspend fun linkWalletToAccount(accountId: String, walletId: String) {
            if (FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)) {
                OSSWallet2AccountStore.linkWalletToAccount(accountId, walletId)
            } else {
                walletStore.linkWalletToAccount(accountId, walletId)
            }
        }

        override suspend fun getWalletIdsForAccount(accountId: String): List<String>? {
            return if (FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)) {
                OSSWallet2AccountStore.getWalletsForAccount(accountId)
            } else {
                walletStore.getWalletIdsForAccount(accountId)
            }
        }
    }

    fun Route.registerRoutes() {
        val authEnabled = runCatching {
            FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)
        }.getOrElse { false }

        if (authEnabled) {
            authenticate("ktor-authnz") {
                val getAccountId: suspend RoutingCall.() -> String? =
                    { runCatching { this.getAuthenticatedAccount() }.getOrNull() }
                registerWallet2Routes(resolver, getAccountId)
            }
        } else {
            registerWallet2Routes(resolver, getAccountId = null)
        }
    }
}
