package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureManager
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.wallet2.auth.OSSWallet2AccountStore
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.persistence.ExposedCredentialStore
import id.walt.wallet2.persistence.ExposedDidStore
import id.walt.wallet2.persistence.ExposedKeyStore
import id.walt.wallet2.persistence.ExposedWalletStore
import id.walt.wallet2.server.WalletResolver
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.stores.WalletStore
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.asFlow
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * OSS [WalletResolver] and route registration.
 *
 * Uses [InMemoryWalletStore] by default. When the `wallet2-persistence` feature is
 * enabled, [main] replaces [walletStore] with [ExposedWalletStore] AND calls
 * [initPersistentStoreRegistry] with the same [Database] so that named store lookups
 * ([resolveKeyStore] etc.) also return persistent [ExposedKeyStore]/[ExposedCredentialStore]/
 * [ExposedDidStore] instances backed by the same database.
 *
 * Without [initPersistentStoreRegistry], keys and credentials generated after wallet
 * creation would only live in the in-memory maps and be lost on restart, even though
 * the wallet descriptor itself was persisted to SQL.
 */
object OSSWallet2Service {

    /**
     * Pluggable wallet lifecycle store.
     *
     * Replace this with a persistent [WalletStore] implementation to survive
     * restarts — e.g. an Exposed/SQLite or Exposed/Postgres backed store.
     * The swap is a single assignment; no other code needs to change.
     */
    var walletStore: WalletStore = InMemoryWalletStore()

    // Named store caches. When persistence is active these hold Exposed-backed instances;
    // otherwise InMemory stores are placed here at wallet-creation time.
    private val namedKeyStores = ConcurrentHashMap<String, WalletKeyStore>()
    private val namedCredentialStores = ConcurrentHashMap<String, WalletCredentialStore>()
    private val namedDidStores = ConcurrentHashMap<String, WalletDidStore>()

    // Non-null when the wallet2-persistence feature is enabled. Used to create
    // Exposed-backed store instances on demand for any store ID not yet in the cache.
    private var persistenceDb: Database? = null

    /**
     * Call this at startup (after [walletStore] is set to [ExposedWalletStore]) with the
     * same [Database] instance so that named store lookups return persistent stores.
     *
     * Idempotent: calling again with a different database replaces the reference,
     * which is useful in tests that spin up isolated databases.
     */
    fun initPersistentStoreRegistry(db: Database) {
        persistenceDb = db
    }

    val resolver: WalletResolver = object : WalletResolver {

        override val publicBaseUrl: Url
            get() = runCatching {
                ConfigManager.getConfig<OSSWallet2ServiceConfig>().publicBaseUrl
            }.getOrElse { Url("http://localhost:4000") }

        override val walletStore: WalletStore
            get() = OSSWallet2Service.walletStore

        // Named store resolution.
        //
        // When persistence is enabled:
        //   - Return the cached instance if present (fast path).
        //   - Otherwise create a new Exposed-backed store for that ID, cache it, and
        //     return it. This handles wallets that were created in a previous process
        //     run: their store IDs exist in the DB but are not yet in the in-process cache.
        //
        // When persistence is NOT enabled, only entries registered via storeKeyStore
        // at wallet-creation time are returned (original in-memory behaviour).

        override suspend fun resolveKeyStore(storeId: String): WalletKeyStore? {
            namedKeyStores[storeId]?.let { return it }
            val db = persistenceDb ?: return null
            val store = ExposedKeyStore(storeId, db)
            namedKeyStores[storeId] = store
            return store
        }

        override suspend fun storeKeyStore(storeId: String, store: WalletKeyStore) {
            namedKeyStores[storeId] = store
        }

        override fun listKeyStoreIds() = namedKeyStores.keys.toList().asFlow()

        override suspend fun resolveCredentialStore(storeId: String): WalletCredentialStore? {
            namedCredentialStores[storeId]?.let { return it }
            val db = persistenceDb ?: return null
            val store = ExposedCredentialStore(storeId, db)
            namedCredentialStores[storeId] = store
            return store
        }

        override suspend fun storeCredentialStore(storeId: String, store: WalletCredentialStore) {
            namedCredentialStores[storeId] = store
        }

        override fun listCredentialStoreIds() = namedCredentialStores.keys.toList().asFlow()

        override suspend fun resolveDidStore(storeId: String): WalletDidStore? {
            namedDidStores[storeId]?.let { return it }
            val db = persistenceDb ?: return null
            val store = ExposedDidStore(storeId, db)
            namedDidStores[storeId] = store
            return store
        }

        override suspend fun storeDidStore(storeId: String, store: WalletDidStore) {
            namedDidStores[storeId] = store
        }

        override fun listDidStoreIds() = namedDidStores.keys.toList().asFlow()

        override suspend fun resolveStoreId(store: Any): String? =
            namedKeyStores.entries.firstOrNull { it.value === store }?.key
                ?: namedCredentialStores.entries.firstOrNull { it.value === store }?.key
                ?: namedDidStores.entries.firstOrNull { it.value === store }?.key

        // When auth is enabled, account↔wallet mappings are owned by OSSWallet2AccountStore
        // so that GET /auth/account/wallets and wallet ownership enforcement stay in sync.
        override suspend fun linkWalletToAccount(accountId: String, walletId: String) {
            if (FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)) {
                OSSWallet2AccountStore.linkWalletToAccount(accountId, walletId)
            } else {
                walletStore.linkWalletToAccount(accountId, walletId)
            }
        }

        override suspend fun getWalletIdsForAccount(accountId: String): List<String>? {
            return if (FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)) {
                // Return the list (possibly empty) so the route handler enforces ownership.
                // Returning null would disable ownership enforcement for accounts with no wallets.
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
            // Wallet routes are protected when auth is enabled:
            // - A valid token is required to access any wallet route
            // - Account ownership is enforced via getAccountId
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
