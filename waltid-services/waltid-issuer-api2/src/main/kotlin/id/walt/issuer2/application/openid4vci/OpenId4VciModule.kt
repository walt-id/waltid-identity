package id.walt.issuer2.application.openid4vci

import id.walt.crypto.keys.KeyManager
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.supportsJwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.issuer2.config.CredentialEncryptionKeyConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationServiceConfig
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.core.PushedAuthorizationConfig
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.par.PARRepository
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.RefreshTokenRepository
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenVerifier
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenIssuer
import id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenIssuer
import id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenVerifier
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtVerificationKeyResolver
import id.walt.openid4vci.tokens.jwt.Crypto2JwtSigningKey
import id.walt.openid4vci.tokens.jwt.Crypto2JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.Crypto2JwtVerificationKey
import id.walt.openid4vci.tokens.jwt.Crypto2JwtVerificationKeyResolver
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultCredentialRequestValidator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class OpenId4VciModule(
    val oauth2Provider: OAuth2Provider,
    val preAuthorizedCodeIssuer: PreAuthorizedCodeIssuer,
) {

    companion object {
        fun create(
            config: Issuer2ServiceConfig,
            authorizationCodeRepository: AuthorizationCodeRepository,
            preAuthorizedCodeRepository: PreAuthorizedCodeRepository,
            parRepository: PARRepository,
            refreshTokenRepository: RefreshTokenRepository,
        ): OpenId4VciModule {
            val crypto2TokenKey = resolveCrypto2TokenKey(config)
            val signingKeyResolver = JwtSigningKeyResolver {
                KeyManager.resolveSerializedKey(config.ciTokenKey)
            }
            val verificationKeyResolver = JwtVerificationKeyResolver {
                KeyManager.resolveSerializedKey(config.ciTokenKey)
            }
            val preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(
                repository = preAuthorizedCodeRepository,
                anonymousAccessSupported = config.supportsPreAuthAnonymous(),
            )

            val accessTokenIssuer = crypto2TokenKey?.let { signingKey ->
                JwtAccessTokenIssuer.crypto2(Crypto2JwtSigningKeyResolver { signingKey })
            } ?: JwtAccessTokenIssuer(signingKeyResolver)
            val accessTokenVerifier = crypto2TokenKey?.let { signingKey ->
                JwtAccessTokenVerifier.crypto2(
                    Crypto2JwtVerificationKeyResolver {
                        Crypto2JwtVerificationKey(signingKey.key, setOf(signingKey.algorithm))
                    }
                )
            } ?: JwtAccessTokenVerifier(verificationKeyResolver)
            val refreshTokenIssuer = crypto2TokenKey?.let { signingKey ->
                JwtRefreshTokenIssuer.crypto2(Crypto2JwtSigningKeyResolver { signingKey })
            } ?: JwtRefreshTokenIssuer(signingKeyResolver)
            val refreshTokenVerifier = crypto2TokenKey?.let { signingKey ->
                JwtRefreshTokenVerifier.crypto2(
                    Crypto2JwtVerificationKeyResolver {
                        Crypto2JwtVerificationKey(signingKey.key, setOf(signingKey.algorithm))
                    }
                )
            } ?: JwtRefreshTokenVerifier(verificationKeyResolver)
            val provider = buildOAuth2Provider(
                OAuth2ProviderConfig(

                    authorizationServerIssuer = config.openId4VciBaseUrl(),
                    authorizationRequestValidator = DefaultAuthorizationRequestValidator(),
                    authorizationEndpointHandlers = AuthorizationEndpointHandlers(),
                    tokenEndpointHandlers = TokenEndpointHandlers(),
                    credentialEndpointHandlers = CredentialEndpointHandlers(),

                    accessTokenRequestValidator = DefaultAccessTokenRequestValidator(),
                    credentialRequestValidator = DefaultCredentialRequestValidator(),

                    authorizationCodeRepository = authorizationCodeRepository,
                    preAuthorizedCodeRepository = preAuthorizedCodeRepository,
                    refreshTokenRepository = refreshTokenRepository,

                    pushedAuthorizationConfig = PushedAuthorizationConfig(
                        repository = parRepository,
                        enforcePushedAuthorizationRequests = config.enforcePushedAuthorizationRequests,
                    ),
                    clientAuthenticationServiceConfig = createClientAuthenticationServiceConfig(config),
                    credentialRequestDecryptor = config.credentialEncryptionKey
                        ?.let(CredentialEncryptionKeyConfig::requestDecryptor),


                    accessTokenIssuer = accessTokenIssuer,
                    accessTokenVerifier = accessTokenVerifier,

                    refreshTokenIssuer = refreshTokenIssuer,
                    refreshTokenVerifier = refreshTokenVerifier,

                    preAuthorizedCodeIssuer = preAuthorizedCodeIssuer,
                )
            )

            return OpenId4VciModule(
                oauth2Provider = provider,
                preAuthorizedCodeIssuer = preAuthorizedCodeIssuer,
            )
        }

        private fun createClientAuthenticationServiceConfig(config: Issuer2ServiceConfig): ClientAuthenticationServiceConfig {
            val clientAuthenticationConfig = config.clientAuthenticationConfig
                ?: return ClientAuthenticationServiceConfig()
            return runBlocking {
                clientAuthenticationConfig.toClientAuthenticationServiceConfig()
            }
        }

        internal fun resolveCrypto2TokenKey(config: Issuer2ServiceConfig): Crypto2JwtSigningKey? = runBlocking {
            val serializedObject = Json.parseToJsonElement(config.ciTokenKey).jsonObject
            if (config.ciTokenStoredKey == null && serializedObject["type"]?.jsonPrimitive?.content != "jwk") {
                return@runBlocking null
            }
            val legacyKey = KeyManager.resolveSerializedKey(serializedObject)
            val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
            val stored = config.ciTokenStoredKey?.let(StoredKeyCodec::decodeFromString)
                ?: V1KeyMigration().migrate(
                    recordId = KeyId(legacyKey.getKeyId()),
                    serialized = serializedObject,
                    usages = tokenKeyUsages,
                )
            require(stored.usages == tokenKeyUsages) {
                "Configured crypto2 token key usages must be exactly $tokenKeyUsages"
            }
            val key = runtime.restore(stored)
            val algorithm = JwsAlgorithm.parse(legacyKey.keyType.jwsAlg)
            require(key.usages == tokenKeyUsages) {
                "Configured crypto2 token key usages must be exactly $tokenKeyUsages"
            }
            require(key.capabilities.signer != null && key.capabilities.verifier != null) {
                "Configured crypto2 token key must support signing and verification"
            }
            require(key.id.value == legacyKey.getKeyId()) { "Configured crypto2 token key ID does not match legacy token key" }
            require(key.spec.supportsJwsAlgorithm(algorithm)) { "Configured crypto2 token key algorithm does not match legacy token key" }
            val legacyPublicJwk = EncodedKey.Jwk(
                BinaryData(Json.encodeToString(legacyKey.getPublicKey().exportJWKObject()).encodeToByteArray()),
                privateMaterial = false,
            )
            val crypto2PublicJwk = requireNotNull(key.capabilities.publicKeyExporter) {
                "Configured crypto2 token key does not export public material"
            }.exportPublicKey().toPublicJwk(key.spec)
            require(Jwk.sha256Thumbprint(legacyPublicJwk) == Jwk.sha256Thumbprint(crypto2PublicJwk)) {
                "Configured crypto2 token key does not match legacy token key"
            }
            Crypto2JwtSigningKey(key, algorithm, legacyKey.getKeyId())
        }

        private val tokenKeyUsages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
    }
}
