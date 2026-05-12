package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureManager
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.wallet2.auth.OSSWallet2AccountStore
import id.walt.wallet2.OSSWallet2Service.walletStore
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.server.WalletResolver
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.stores.WalletStore
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import java.util.concurrent.ConcurrentHashMap

/**
 * OSS [WalletResolver] and route registration.
 *
 * Uses [InMemoryWalletStore] by default. To add persistence, replace
 * [walletStore] with your own [WalletStore] implementation before the
 * service starts — no other changes needed.
 *
 * All route-handler logic lives in [Wallet2RouteHandler] inside
 * waltid-openid4vc-wallet-server. The Enterprise service provides its own
 * [WalletResolver] backed by the MongoDB resource tree without changing any
 * handler logic.
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

        override suspend fun resolveKeyStore(storeId: String) = namedKeyStores[storeId]
        override suspend fun storeKeyStore(storeId: String, store: WalletKeyStore) { namedKeyStores[storeId] = store }
        override suspend fun listKeyStoreIds() = namedKeyStores.keys.toList()

        override suspend fun resolveCredentialStore(storeId: String) = namedCredentialStores[storeId]
        override suspend fun storeCredentialStore(storeId: String, store: WalletCredentialStore) { namedCredentialStores[storeId] = store }
        override suspend fun listCredentialStoreIds() = namedCredentialStores.keys.toList()

        override suspend fun resolveDidStore(storeId: String) = namedDidStores[storeId]
        override suspend fun storeDidStore(storeId: String, store: WalletDidStore) { namedDidStores[storeId] = store }
        override suspend fun listDidStoreIds() = namedDidStores.keys.toList()

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
