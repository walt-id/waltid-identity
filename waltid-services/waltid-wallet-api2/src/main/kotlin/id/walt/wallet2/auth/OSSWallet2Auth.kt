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
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.auth.getEffectiveRequestAuthToken
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.methods.registerAuthenticationMethod
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import id.walt.wallet2.OSSWallet2AuthConfig
import id.walt.wallet2.OSSWallet2Service
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import id.walt.crypto2.keys.Key as Crypto2Key

private val authLog = KotlinLogging.logger {}

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
 *
 * [cryptoRuntime] resolves the configured StoredKey. The default carries software providers only;
 * pass a runtime holding the matching [id.walt.crypto2.providers.ManagedKeyProvider] to run auth off
 * a KMS/HSM key.
 *
 * Returns the loaded [OSSWallet2AuthConfig] so the caller can pass
 * [OSSWallet2AuthConfig.tokenExpiry] to [registerWallet2AuthRoutes].
 *
 * Called from Main.kt when the auth optional feature is enabled.
 *
 * The ktor-authnz provider is also registered from [id.walt.wallet2.main] before
 * [id.walt.commons.web.WebService] installs Authentication. This assignment stays so
 * in-process tests that skip [main] still hook the provider when they call this
 * function before the plugin is installed.
 */
suspend fun Application.configureWallet2Auth(
    cryptoRuntime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
): OSSWallet2AuthConfig {
    val config = ConfigManager.getConfig<OSSWallet2AuthConfig>()

    val signingKey = resolveWallet2AuthSigningKey(config, cryptoRuntime)

    KtorAuthnzManager.accountStore = OSSWallet2Service.accountStore
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
 */
fun Route.registerWallet2AuthRoutes(tokenExpiry: Duration = 24.hours, walletResolver: WalletResolver) {
    val accountStore = OSSWallet2Service.accountStore
    route("/auth") {

        post("/register") {
            val req = call.receive<RegisterRequest>()
            val accountId = accountStore.register(EmailIdentifier(req.email), req.password)
                ?: return@post call.respond(HttpStatusCode.Conflict, "Account '${req.email}' already exists")

            authLog.info { "Registered account $accountId" }
            call.respond(HttpStatusCode.Created, mapOf("accountId" to accountId))
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
                        email = accountStore.lookupAccountIdentifiers(accountId)
                            .filterIsInstance<EmailIdentifier>().firstOrNull()?.email.orEmpty(),
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
