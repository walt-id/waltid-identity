package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.server.WalletResolver
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import io.ktor.http.*
import io.ktor.server.routing.*
import java.util.concurrent.ConcurrentHashMap

/**
 * OSS in-memory [WalletResolver] and route registration.
 *
 * The only OSS-specific file that touches Ktor server directly.
 * All route-handler logic lives in [Wallet2RouteHandler] inside
 * waltid-openid4vc-wallet-server. The Enterprise service provides its own
 * [WalletResolver] backed by MongoDB without changing any handler logic.
 */
object OSSWallet2Service {

    private val wallets = ConcurrentHashMap<String, Wallet>()
    private val namedKeyStores = ConcurrentHashMap<String, WalletKeyStore>()
    private val namedCredentialStores = ConcurrentHashMap<String, WalletCredentialStore>()
    private val namedDidStores = ConcurrentHashMap<String, WalletDidStore>()
    private val accountWallets = ConcurrentHashMap<String, MutableList<String>>()

    val resolver: WalletResolver = object : WalletResolver {

        override val publicBaseUrl: Url
            get() = runCatching {
                ConfigManager.getConfig<OSSWallet2ServiceConfig>().publicBaseUrl
            }.getOrElse { Url("http://localhost:4000") }

        override suspend fun resolveWallet(walletId: String) = wallets[walletId]
        override suspend fun storeWallet(wallet: Wallet) { wallets[wallet.id] = wallet }
        override suspend fun deleteWallet(walletId: String) { wallets.remove(walletId) }
        override suspend fun listWalletIds() = wallets.keys.toList()

        override suspend fun resolveKeyStore(storeId: String) = namedKeyStores[storeId]
        override suspend fun storeKeyStore(storeId: String, store: WalletKeyStore) { namedKeyStores[storeId] = store }
        override suspend fun listKeyStoreIds() = namedKeyStores.keys.toList()

        override suspend fun resolveCredentialStore(storeId: String) = namedCredentialStores[storeId]
        override suspend fun storeCredentialStore(storeId: String, store: WalletCredentialStore) { namedCredentialStores[storeId] = store }
        override suspend fun listCredentialStoreIds() = namedCredentialStores.keys.toList()

        override suspend fun resolveDidStore(storeId: String) = namedDidStores[storeId]
        override suspend fun storeDidStore(storeId: String, store: WalletDidStore) { namedDidStores[storeId] = store }
        override suspend fun listDidStoreIds() = namedDidStores.keys.toList()

        override suspend fun linkWalletToAccount(accountId: String, walletId: String) {
            accountWallets.getOrPut(accountId) { mutableListOf() }.add(walletId)
        }

        override suspend fun getWalletIdsForAccount(accountId: String) =
            accountWallets[accountId]?.toList()
    }

    fun Route.registerRoutes() {
        val authEnabled = runCatching {
            id.walt.commons.featureflag.FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)
        }.getOrElse { false }

        val getAccountId: (suspend RoutingCall.() -> String?)? = if (authEnabled) {
            { runCatching { this.getAuthenticatedAccount() }.getOrNull() }
        } else null

        registerWallet2Routes(resolver, getAccountId)
    }
}
