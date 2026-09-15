package id.walt.wallet2.auth

import id.walt.commons.config.ConfigManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.preferredJwsAlgorithm
import id.walt.crypto2.jose.supportsJwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.Account
import id.walt.ktorauthnz.accounts.EditableAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.auth.getEffectiveRequestAuthToken
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.methods.OIDC
import id.walt.ktorauthnz.methods.config.OidcAuthConfiguration
import id.walt.ktorauthnz.methods.registerAuthenticationMethod
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import id.walt.wallet2.OSSWallet2AuthConfig
import id.walt.wallet2.server.WalletResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.ktorauthnz.flows.AuthFlow

private val authLog = KotlinLogging.logger {}

// ---------------------------------------------------------------------------
// In-memory account store
// ---------------------------------------------------------------------------

/**
 * In-memory [EditableAccountStore] for the OSS wallet service.
 * Manages user accounts and authentication credentials only.
 * Wallet ownership (account-to-wallet mapping) is managed by the [id.walt.wallet2.stores.WalletStore].
 * All data is lost on restart. Replace with an Exposed/SQLite-backed implementation for production.
 */
object OSSWallet2AccountStore : EditableAccountStore {

    private val accounts = ConcurrentHashMap<String, Account>()
    private val identifierToAccountId = ConcurrentHashMap<AccountIdentifier, String>()
    private val identifierStoredData = ConcurrentHashMap<AccountIdentifier, ConcurrentHashMap<String, AuthMethodStoredData>>()
    private val accountStoredData = ConcurrentHashMap<String, ConcurrentHashMap<String, AuthMethodStoredData>>()

    fun createAccount(email: String): Account {
        val account = Account(id = Uuid.random().toString(), name = email)
        accounts[account.id] = account
        return account
    }

    override suspend fun addAccountIdentifierToAccount(accountId: String, newAccountIdentifier: AccountIdentifier) {
        identifierToAccountId[newAccountIdentifier] = accountId
    }

    override suspend fun removeAccountIdentifierFromAccount(accountIdentifier: AccountIdentifier) {
        identifierToAccountId.remove(accountIdentifier)
    }

    override suspend fun addAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData) =
        updateAccountIdentifierStoredData(accountIdentifier, method, data)

    override suspend fun addAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) =
        updateAccountStoredData(accountId, method, data)

    override suspend fun updateAccountIdentifierStoredData(
        accountIdentifier: AccountIdentifier,
        method: String,
        data: AuthMethodStoredData
    ) {
        identifierStoredData.getOrPut(accountIdentifier) { ConcurrentHashMap() }[method] = data.transformSavable()
    }

    override suspend fun updateAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) {
        accountStoredData.getOrPut(accountId) { ConcurrentHashMap() }[method] = data.transformSavable()
    }

    override suspend fun deleteAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String) {
        identifierStoredData[accountIdentifier]?.remove(method)
    }

    override suspend fun deleteAccountStoredData(accountId: String, method: String) {
        accountStoredData[accountId]?.remove(method)
    }

    override suspend fun lookupStoredDataForAccount(accountId: String, method: AuthenticationMethod): AuthMethodStoredData? =
        accountStoredData[accountId]?.get(method.id)

    override suspend fun lookupStoredDataForAccountIdentifier(
        identifier: AccountIdentifier,
        method: AuthenticationMethod
    ): AuthMethodStoredData? =
        identifierStoredData[identifier]?.get(method.id)

    override suspend fun hasStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): Boolean =
        identifierStoredData[identifier]?.containsKey(method.id) == true

    fun getEmailForAccount(accountId: String): String? =
        identifierToAccountId.entries
            .firstOrNull { it.value == accountId && it.key is EmailIdentifier }
            ?.key
            ?.let { (it as? EmailIdentifier)?.email }

    override suspend fun lookupAccountUuid(identifier: AccountIdentifier): String? =
        identifierToAccountId[identifier]
}

// ---------------------------------------------------------------------------
// Auth route models
// ---------------------------------------------------------------------------

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class AccountInfoResponse(
    val accountId: String,
    val email: String,
    val walletIds: List<String>,
)

// ---------------------------------------------------------------------------
// Auth module wiring
// ---------------------------------------------------------------------------

/**
 * Configures [KtorAuthnzManager] for JWT-based session tokens and hooks into
 * [AuthenticationServiceModule] so the Ktor Authentication plugin is installed
 * exactly once by the WebService wrapper.
 *
 * Reads [OSSWallet2AuthConfig] from the config manager:
 * - [OSSWallet2AuthConfig.signingStoredKey]: preferred crypto2 key used to sign and verify JWT
 *   session tokens. Sufficient on its own, and the only way to configure a managed key.
 * - [OSSWallet2AuthConfig.signingKey]: legacy sidecar and in-memory migration source.
 * - [OSSWallet2AuthConfig.tokenExpiry]: JWT `exp` lifetime as a [Duration].
 * - [OSSWallet2AuthConfig.oidc]: optional OIDC authentication configuration.
 *
 * [cryptoRuntime] resolves the configured StoredKey. The default carries software providers only;
 * pass a runtime holding the matching [id.walt.crypto2.providers.ManagedKeyProvider] to run auth off
 * a KMS/HSM key.
 *
 * Returns the loaded [OSSWallet2AuthConfig] so the caller can pass the route-specific
 * authentication configuration to [registerWallet2AuthRoutes].
 *
 * Called from Main.kt when the auth optional feature is enabled.
 */
suspend fun Application.configureWallet2Auth(
    cryptoRuntime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
): OSSWallet2AuthConfig {
    val config = ConfigManager.getConfig<OSSWallet2AuthConfig>()

    val signingKey = resolveWallet2AuthSigningKey(config, cryptoRuntime)

    KtorAuthnzManager.accountStore = OSSWallet2AccountStore
    KtorAuthnzManager.tokenHandler = JwtTokenHandler.crypto2(
        signingKey = signingKey.key,
        algorithm = signingKey.algorithm,
    )

    AuthenticationServiceModule.AuthenticationServiceConfig.customAuthentication = {
        ktorAuthnz("ktor-authnz") { }
    }

    authLog.info { "Wallet2 auth configured: JWT tokens (keySpec=${signingKey.key.spec}, expiry=${config.tokenExpiry})" }
    return config
}

/**
 * Resolves the auth signing key through [cryptoRuntime].
 *
 * A standalone [OSSWallet2AuthConfig.signingStoredKey] is sufficient and is the only way to configure
 * a managed (KMS/HSM) key - pass a runtime carrying the matching provider for those. The legacy
 * [OSSWallet2AuthConfig.signingKey], when present, is migrated in memory and cross-checked against
 * the StoredKey; it also fixes the JWS algorithm, which is otherwise taken from the StoredKey.
 */
internal suspend fun resolveWallet2AuthSigningKey(
    config: OSSWallet2AuthConfig,
    cryptoRuntime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
): Wallet2AuthSigningKey {
    val legacyKey = config.signingKey?.key
    val storedKey = config.signingStoredKey?.let(StoredKeyCodec::decodeFromString)
        ?: V1KeyMigration().migrate(
            recordId = KeyId(requireNotNull(legacyKey).getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
            usages = walletAuthKeyUsages,
        )
    require(storedKey.usages == walletAuthKeyUsages) {
        "Wallet auth signing StoredKey usages must be exactly $walletAuthKeyUsages"
    }
    val key = cryptoRuntime.restore(storedKey)
    require(key.usages == walletAuthKeyUsages) {
        "Wallet auth signing StoredKey usages must be exactly $walletAuthKeyUsages"
    }
    require(key.capabilities.signer != null && key.capabilities.verifier != null) {
        "Wallet auth signing StoredKey must support signing and verification"
    }
    val algorithm = legacyKey?.let { JwsAlgorithm.parse(it.keyType.jwsAlg) } ?: key.preferredJwsAlgorithm()
    legacyKey?.let { validateWallet2AuthSidecar(it, key, algorithm) }
    return Wallet2AuthSigningKey(key, algorithm)
}

private suspend fun validateWallet2AuthSidecar(
    legacyKey: Key,
    crypto2Key: Crypto2Key,
    algorithm: JwsAlgorithm,
) {
    require(crypto2Key.id.value == legacyKey.getKeyId()) {
        "Wallet auth signingStoredKey ID does not match signingKey"
    }
    require(crypto2Key.spec.supportsJwsAlgorithm(algorithm)) {
        "Wallet auth signingStoredKey algorithm does not match signingKey"
    }
    val legacyPublicJwk = EncodedKey.Jwk(
        data = BinaryData(Json.encodeToString(legacyKey.getPublicKey().exportJWKObject()).encodeToByteArray()),
        privateMaterial = false,
    )
    val crypto2PublicJwk = requireNotNull(crypto2Key.capabilities.publicKeyExporter) {
        "Wallet auth signingStoredKey does not export public material"
    }.exportPublicKey().toPublicJwk(crypto2Key.spec)
    require(Jwk.sha256Thumbprint(legacyPublicJwk) == Jwk.sha256Thumbprint(crypto2PublicJwk)) {
        "Wallet auth signingStoredKey does not match signingKey"
    }
}

internal data class Wallet2AuthSigningKey(
    val key: Crypto2Key,
    val algorithm: JwsAlgorithm,
)

private val walletAuthKeyUsages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)

/**
 * Registers /auth/[*] routes.
 * Should be called inside the main routing block when auth is enabled.
 *
 * @param tokenExpiry JWT token lifetime embedded into the [AuthFlow].
 *   Defaults to 24 hours. Pass the value from [configureWallet2Auth] to keep it in sync.
 * @param walletResolver The resolver used for wallet ownership lookups (account/wallets routes).
 *   Must be the same resolver that the wallet routes use so both read from the same store.
 * @param oidcConfig Optional OIDC configuration. When present, OIDC authentication routes are registered.
 */
fun Route.registerWallet2AuthRoutes(
    tokenExpiry: Duration = 24.hours,
    walletResolver: WalletResolver,
    oidcConfig: OidcAuthConfiguration? = null,
) {
    route("/auth") {

        post("/register") {
            val req = call.receive<RegisterRequest>()
            val existing = OSSWallet2AccountStore.lookupAccountUuid(EmailIdentifier(req.email))
            if (existing != null) {
                return@post call.respond(HttpStatusCode.Conflict, "Account '${req.email}' already exists")
            }
            val account = OSSWallet2AccountStore.createAccount(req.email)
            val identifier = EmailIdentifier(req.email)
            OSSWallet2AccountStore.addAccountIdentifierToAccount(account.id, identifier)
            OSSWallet2AccountStore.addAccountIdentifierStoredData(
                identifier, EmailPass.id,
                EmailPassStoredData(req.password)
            )
            authLog.info { "Registered account ${account.id} for ${req.email}" }
            call.respond(HttpStatusCode.Created, mapOf("accountId" to account.id))
        }

        // expiration is an ISO-8601 duration string parsed by AuthFlow.parsedDuration.
        val emailPassFlow = id.walt.ktorauthnz.flows.AuthFlow(
            method = EmailPass.id,
            success = true,
            expiration = tokenExpiry.toIsoString()
        )
        registerAuthenticationMethod(EmailPass, authContext = {
            AuthContext(
                implicitSessionGeneration = true,
                initialFlow = emailPassFlow
            )
        })
        oidcConfig?.let { config ->
            val oidcFlow = AuthFlow(
                method = OIDC.id,
                config = Json.encodeToJsonElement(
                    OidcAuthConfiguration.serializer(),
                    config
                ).jsonObject,
                success = true,
                expiration = tokenExpiry.toIsoString()
            )

            registerAuthenticationMethod(OIDC, authContext = {
                AuthContext(
                    implicitSessionGeneration = true,
                    initialFlow = oidcFlow
                )
            })
        }

        post("/logout") {
            val token = call.getEffectiveRequestAuthToken()
            if (token != null) {
                KtorAuthnzManager.tokenHandler.dropToken(token)
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "logged out"))
        }

        authenticate("ktor-authnz") {
            get("/account") {
                val accountId = call.getAuthenticatedAccount()
                val walletIds = walletResolver.getWalletIdsForAccount(accountId) ?: emptyList()
                call.respond(
                    AccountInfoResponse(
                        accountId = accountId,
                        email = OSSWallet2AccountStore.getEmailForAccount(accountId) ?: "",
                        walletIds = walletIds,
                    )
                )
            }

            get("/account/wallets") {
                val accountId = call.getAuthenticatedAccount()
                call.respond(walletResolver.getWalletIdsForAccount(accountId) ?: emptyList<String>())
            }

            post("/account/wallets/{walletId}") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Manual wallet linking is disabled; wallets are linked when created"),
                )
            }
        }
    }
}
