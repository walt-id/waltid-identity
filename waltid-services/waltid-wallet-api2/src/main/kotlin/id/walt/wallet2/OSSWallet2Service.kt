package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureManager
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.persistence.ExposedCredentialStore
import id.walt.wallet2.persistence.ExposedDidStore
import id.walt.wallet2.persistence.ExposedKeyStore
import id.walt.wallet2.persistence.ExposedStoreRegistry
import id.walt.wallet2.persistence.ExposedWalletStore
import id.walt.wallet2.server.StoreFactory
import id.walt.wallet2.server.WalletResolver
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.stores.WalletStore
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import id.walt.x509.CertificateDer
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.GenericHttpWalletAttestationProvider
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.asFlow
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * OSS [WalletResolver] and route registration.
 *
 * Uses [InMemoryWalletStore] by default. Persistent startup calls
 * [configurePersistence] so wallet descriptors and all named stores use one database.
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

    /**
     * Pluggable wallet lifecycle store.
     *
     * Persistent implementations should be installed through [configurePersistence]
     * so descriptors and named stores use the same backend.
     */
    var walletStore: WalletStore = InMemoryWalletStore()
    private var persistentStoreRegistry: ExposedStoreRegistry? = null

    // Store factories - swapped at startup when persistence is enabled.
    // The route handler uses these (via the resolver) to create new stores for auto-created
    // and named stores, so the right store type is created regardless of whether the store ID
    // was supplied by the user or generated automatically.
    var keyStoreFactory: StoreFactory<WalletKeyStore> = { InMemoryKeyStore() }
    var credentialStoreFactory: StoreFactory<WalletCredentialStore> = { InMemoryCredentialStore() }
    var didStoreFactory: StoreFactory<WalletDidStore> = { InMemoryDidStore() }

    // In-process cache: storeId -> store instance.
    // computeIfAbsent ensures that on restart, a store for any ID known to the DB is
    // created on first access without requiring a separate init call.
    private val namedKeyStores = ConcurrentHashMap<String, WalletKeyStore>()
    private val namedCredentialStores = ConcurrentHashMap<String, WalletCredentialStore>()
    private val namedDidStores = ConcurrentHashMap<String, WalletDidStore>()

    val resolver: WalletResolver = object : WalletResolver {

        override val publicBaseUrl: Url
            get() = ConfigManager.getConfig<OSSWallet2ServiceConfig>().publicBaseUrl

        override val walletStore: WalletStore
            get() = OSSWallet2Service.walletStore

        override val keyStoreFactory: StoreFactory<WalletKeyStore>
            get() = OSSWallet2Service.keyStoreFactory

        override val credentialStoreFactory: StoreFactory<WalletCredentialStore>
            get() = OSSWallet2Service.credentialStoreFactory

        override val didStoreFactory: StoreFactory<WalletDidStore>
            get() = OSSWallet2Service.didStoreFactory

        override suspend fun resolveKeyStore(storeId: String): WalletKeyStore? {
            val registry = persistentStoreRegistry
            return if (registry != null) registry.resolveKeyStore(storeId)
            else namedKeyStores.computeIfAbsent(storeId) { keyStoreFactory(storeId) }
        }
        override suspend fun storeKeyStore(storeId: String, store: WalletKeyStore) {
            if (persistentStoreRegistry != null) {
                require(store is ExposedKeyStore && store.storeId == storeId) {
                    "Persistent key stores must be created through createKeyStore"
                }
            } else namedKeyStores[storeId] = store
        }
        override suspend fun createKeyStore(storeId: String): WalletKeyStore =
            persistentStoreRegistry?.createKeyStore(storeId)
                ?: keyStoreFactory(storeId).also { namedKeyStores[storeId] = it }
        override fun listKeyStoreIds() =
            persistentStoreRegistry?.listKeyStoreIds() ?: namedKeyStores.keys.asFlow()

        override suspend fun resolveCredentialStore(storeId: String): WalletCredentialStore? {
            val registry = persistentStoreRegistry
            return if (registry != null) registry.resolveCredentialStore(storeId)
            else namedCredentialStores.computeIfAbsent(storeId) { credentialStoreFactory(storeId) }
        }
        override suspend fun storeCredentialStore(storeId: String, store: WalletCredentialStore) {
            if (persistentStoreRegistry != null) {
                require(store is ExposedCredentialStore && store.storeId == storeId) {
                    "Persistent credential stores must be created through createCredentialStore"
                }
            } else namedCredentialStores[storeId] = store
        }
        override suspend fun createCredentialStore(storeId: String): WalletCredentialStore =
            persistentStoreRegistry?.createCredentialStore(storeId)
                ?: credentialStoreFactory(storeId).also { namedCredentialStores[storeId] = it }
        override fun listCredentialStoreIds() =
            persistentStoreRegistry?.listCredentialStoreIds() ?: namedCredentialStores.keys.asFlow()

        override suspend fun resolveDidStore(storeId: String): WalletDidStore? {
            val registry = persistentStoreRegistry
            return if (registry != null) registry.resolveDidStore(storeId)
            else namedDidStores.computeIfAbsent(storeId) { didStoreFactory(storeId) }
        }
        override suspend fun storeDidStore(storeId: String, store: WalletDidStore) {
            if (persistentStoreRegistry != null) {
                require(store is ExposedDidStore && store.storeId == storeId) {
                    "Persistent DID stores must be created through createDidStore"
                }
            } else namedDidStores[storeId] = store
        }
        override suspend fun createDidStore(storeId: String): WalletDidStore =
            persistentStoreRegistry?.createDidStore(storeId)
                ?: didStoreFactory(storeId).also { namedDidStores[storeId] = it }
        override fun listDidStoreIds() =
            persistentStoreRegistry?.listDidStoreIds() ?: namedDidStores.keys.asFlow()

        override suspend fun resolveStoreId(store: Any): String? =
            (store as? ExposedKeyStore)?.storeId
                ?: (store as? ExposedCredentialStore)?.storeId
                ?: (store as? ExposedDidStore)?.storeId
                ?: namedKeyStores.entries.firstOrNull { it.value === store }?.key
                ?: namedCredentialStores.entries.firstOrNull { it.value === store }?.key
                ?: namedDidStores.entries.firstOrNull { it.value === store }?.key

        // Account-wallet ownership always lives in walletStore (InMemoryWalletStore or
        // ExposedWalletStore). When auth is enabled, OSSWallet2AccountStore handles user
        // credentials; wallet ownership is a separate concern that belongs to the wallet store.
        override suspend fun linkWalletToAccount(accountId: String, walletId: String) =
            walletStore.linkWalletToAccount(accountId, walletId)

        override suspend fun getWalletIdsForAccount(accountId: String): List<String>? =
            walletStore.getWalletIdsForAccount(accountId)
    }

    fun configurePersistence(db: Database) {
        walletStore = ExposedWalletStore(db)
        persistentStoreRegistry = ExposedStoreRegistry(db)
        keyStoreFactory = { id -> ExposedKeyStore(id, db) }
        credentialStoreFactory = { id -> ExposedCredentialStore(id, db) }
        didStoreFactory = { id -> ExposedDidStore(id, db) }
        namedKeyStores.clear()
        namedCredentialStores.clear()
        namedDidStores.clear()
    }

    fun configureInMemory(store: WalletStore = InMemoryWalletStore()) {
        walletStore = store
        persistentStoreRegistry = null
        keyStoreFactory = { InMemoryKeyStore() }
        credentialStoreFactory = { InMemoryCredentialStore() }
        didStoreFactory = { InMemoryDidStore() }
        namedKeyStores.clear()
        namedCredentialStores.clear()
        namedDidStores.clear()
    }

    fun Route.registerRoutes() {
        val attestationAssembler = createAttestationAssembler()
        val clientIdTrustConfiguration = configuredClientIdTrustConfiguration()
        val authEnabled = runCatching {
            FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)
        }.getOrElse { false }

        if (authEnabled) {
            authenticate("ktor-authnz") {
                val getAccountId: suspend RoutingCall.() -> String? =
                    { runCatching { this.getAuthenticatedAccount() }.getOrNull() }
                registerWallet2Routes(
                    resolver = resolver,
                    getAccountId = getAccountId,
                    attestationAssembler = attestationAssembler,
                    clientIdTrustConfiguration = clientIdTrustConfiguration,
                )
            }
        } else {
            registerWallet2Routes(
                resolver = resolver,
                getAccountId = null,
                attestationAssembler = attestationAssembler,
                clientIdTrustConfiguration = clientIdTrustConfiguration,
            )
        }
    }

    private fun ClientIdTrustConfig.toDomain() = ClientIdTrustConfiguration(
        x509TrustAnchors = x509TrustAnchors.map(CertificateDer::fromPEMEncodedString),
        trustedVerifierAttestationIssuers = trustedVerifierAttestationIssuers,
        preRegisteredClients = preRegisteredClients,
    )

    internal fun configuredClientIdTrustConfiguration(): ClientIdTrustConfiguration =
        ConfigManager.getConfig<OSSWallet2ServiceConfig>().clientIdTrust.toDomain()

    private fun createAttestationAssembler(): ClientAttestationAssembler? {
        val config = ConfigManager.getConfig<OSSWallet2ServiceConfig>().attestationConfig ?: return null

        return ClientAttestationAssembler(
            GenericHttpWalletAttestationProvider(
                attesterUrl = config.attesterUrl,
                requestBodyTemplate = config.requestBody,
            )
        )
    }
}
