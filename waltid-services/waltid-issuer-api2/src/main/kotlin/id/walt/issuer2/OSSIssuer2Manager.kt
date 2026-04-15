@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package id.walt.issuer2

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.CredentialProfilesConfig
import id.walt.issuer2.config.OSSIssuer2ServiceConfig
import id.walt.issuer2.models.*
import id.walt.issuer2.oauth.InMemoryAuthorizationCodeRepository
import id.walt.issuer2.oauth.InMemoryPreAuthorizedCodeRecord
import id.walt.issuer2.oauth.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.CredentialOfferGrants
import id.walt.openid4vci.offers.PreAuthorizedCodeGrant
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.tokens.AccessTokenService
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultCredentialRequestValidator
import io.klogging.logger
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val log = logger("OSSIssuer2Manager")

object OSSIssuer2Manager {

    private val serviceConfig: OSSIssuer2ServiceConfig by lazy {
        ConfigManager.getConfig()
    }

    private val profilesConfig: CredentialProfilesConfig by lazy {
        ConfigManager.getConfig()
    }

    val sessions = ConcurrentHashMap<String, IssuanceSession>()

    val authorizationCodeRepository = InMemoryAuthorizationCodeRepository()
    val preAuthorizedCodeRepository = InMemoryPreAuthorizedCodeRepository()

    private var tokenKey: Key? = null

    private val accessTokenService: AccessTokenService by lazy {
        object : AccessTokenService {
            override suspend fun createAccessToken(claims: Map<String, Any?>): String {
                val key = getTokenKey()
                val payload = buildMap<String, kotlinx.serialization.json.JsonElement> {
                    claims.forEach { (k, v) ->
                        when (v) {
                            is String -> put(k, JsonPrimitive(v))
                            is Number -> put(k, JsonPrimitive(v))
                            is Boolean -> put(k, JsonPrimitive(v))
                            null -> {}
                            else -> put(k, JsonPrimitive(v.toString()))
                        }
                    }
                    put("iss", JsonPrimitive(serviceConfig.baseUrl))
                    put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
                }
                val payloadJson = JsonObject(payload)
                return key.signJws(
                    payloadJson.toString().encodeToByteArray(),
                    mapOf("typ" to JsonPrimitive("at+jwt"))
                )
            }
        }
    }

    private val preAuthorizedCodeIssuer by lazy {
        DefaultPreAuthorizedCodeIssuer(preAuthorizedCodeRepository)
    }

    val oauth2Provider: OAuth2Provider by lazy {
        val config = OAuth2ProviderConfig(
            authorizationRequestValidator = DefaultAuthorizationRequestValidator(),
            authorizationEndpointHandlers = AuthorizationEndpointHandlers(),
            authorizationCodeRepository = authorizationCodeRepository,
            accessTokenRequestValidator = DefaultAccessTokenRequestValidator(),
            tokenEndpointHandlers = TokenEndpointHandlers(),
            accessTokenService = accessTokenService,
            preAuthorizedCodeRepository = preAuthorizedCodeRepository,
            preAuthorizedCodeIssuer = preAuthorizedCodeIssuer,
            credentialRequestValidator = DefaultCredentialRequestValidator(),
            credentialEndpointHandlers = CredentialEndpointHandlers(),
        )
        buildOAuth2Provider(config)
    }

    private suspend fun getTokenKey(): Key {
        if (tokenKey == null) {
            tokenKey = serviceConfig.tokenKey?.let { KeyManager.resolveSerializedKey(it) }
                ?: throw IllegalStateException("Token key not configured in issuer-service.conf")
        }
        return tokenKey!!
    }

    fun getBaseUrl(): String = serviceConfig.baseUrl

    fun getProfiles(): List<CredentialProfileConfig> = profilesConfig.profiles

    fun getProfile(profileId: String): CredentialProfileConfig? =
        profilesConfig.profiles.find { it.profileId == profileId }

    fun getCredentialConfigurations(): Map<String, CredentialConfiguration> =
        profilesConfig.credentialConfigurations

    fun getCredentialConfiguration(configId: String): CredentialConfiguration? =
        profilesConfig.credentialConfigurations[configId]

    fun getIssuerDisplay(): List<IssuerDisplay>? = profilesConfig.issuerDisplay

    fun getCredentialIssuerMetadata(): CredentialIssuerMetadata {
        val baseUrl = serviceConfig.baseUrl
        return CredentialIssuerMetadata(
            credentialIssuer = baseUrl,
            authorizationServers = listOf(baseUrl),
            credentialEndpoint = "$baseUrl/credential",
            nonceEndpoint = "$baseUrl/nonce",
            deferredCredentialEndpoint = null,
            notificationEndpoint = null,
            credentialConfigurationsSupported = profilesConfig.credentialConfigurations,
            display = profilesConfig.issuerDisplay,
        )
    }

    fun getAuthorizationServerMetadata(): AuthorizationServerMetadata {
        val baseUrl = serviceConfig.baseUrl
        return AuthorizationServerMetadata(
            issuer = baseUrl,
            authorizationEndpoint = "$baseUrl/authorize",
            tokenEndpoint = "$baseUrl/token",
            jwksUri = "$baseUrl/jwks",
            responseTypesSupported = setOf("code"),
            grantTypesSupported = setOf(
                "authorization_code",
                "urn:ietf:params:oauth:grant-type:pre-authorized_code"
            ),
            tokenEndpointAuthMethodsSupported = setOf("none"),
            preAuthorizedGrantAnonymousAccessSupported = true,
        )
    }

    suspend fun createCredentialOffer(request: CredentialOfferCreateRequest): IssuanceSession {
        val profile = getProfile(request.profileId)
            ?: throw IllegalArgumentException("Profile not found: ${request.profileId}")

        val credentialConfig = getCredentialConfiguration(profile.credentialConfigurationId)
            ?: throw IllegalArgumentException("Credential configuration not found: ${profile.credentialConfigurationId}")

        val baseUrl = serviceConfig.baseUrl
        val expiresAt = Clock.System.now().plus(request.expiresInSeconds.seconds)

        val issuerKey = request.runtimeOverrides?.issuerKey ?: profile.issuerKey
        val issuerDid = request.runtimeOverrides?.issuerDid ?: profile.issuerDid
        val credentialData = request.runtimeOverrides?.credentialData ?: profile.credentialData
        val mapping = request.runtimeOverrides?.mapping ?: profile.mapping

        val issuanceRequest = IssuanceSessionRequest(
            profileId = profile.profileId,
            credentialConfigurationId = profile.credentialConfigurationId,
            issuerKeyId = profile.profileId,
            issuerKey = issuerKey,
            issuerDid = issuerDid,
            x5Chain = request.runtimeOverrides?.x5Chain ?: profile.x5Chain,
            credentialData = credentialData,
            mapping = mapping,
            selectiveDisclosureJson = profile.selectiveDisclosure?.let {
                kotlinx.serialization.json.Json.encodeToJsonElement(
                    id.walt.sdjwt.SDMap.serializer(),
                    it
                ) as? JsonObject
            },
        )

        val session = when (request.authMethod) {
            AuthenticationMethod.PRE_AUTHORIZED -> {
                createPreAuthorizedSession(
                    profile = profile,
                    credentialConfig = credentialConfig,
                    issuanceRequest = issuanceRequest,
                    baseUrl = baseUrl,
                    expiresAt = expiresAt,
                    txCode = request.txCode,
                    txCodeValue = request.txCodeValue,
                    valueMode = request.valueMode,
                )
            }
            AuthenticationMethod.AUTHORIZED -> {
                createAuthorizedSession(
                    profile = profile,
                    credentialConfig = credentialConfig,
                    issuanceRequest = issuanceRequest,
                    baseUrl = baseUrl,
                    expiresAt = expiresAt,
                    valueMode = request.valueMode,
                )
            }
        }

        sessions[session.id] = session
        log.info { "Created issuance session: ${session.id} for profile: ${profile.profileId}" }
        return session
    }

    private suspend fun createPreAuthorizedSession(
        profile: CredentialProfileConfig,
        credentialConfig: CredentialConfiguration,
        issuanceRequest: IssuanceSessionRequest,
        baseUrl: String,
        expiresAt: kotlinx.datetime.Instant,
        txCode: id.walt.openid4vci.offers.TxCode?,
        txCodeValue: String?,
        valueMode: CredentialOfferValueMode,
    ): IssuanceSession {
        val sessionId = Uuid.random().toString()
        val scope = credentialConfig.scope ?: profile.credentialConfigurationId

        val session = DefaultSession()
            .withSubject(sessionId)
            .withExpiresAt(TokenType.ACCESS_TOKEN, kotlin.time.Clock.System.now().plus(300.seconds))

        val issueResult = preAuthorizedCodeIssuer.issue(
            PreAuthorizedCodeIssueRequest(
                clientId = null,
                txCode = txCode,
                txCodeValue = txCodeValue,
                scopes = setOf(scope),
                audience = setOf(baseUrl),
                session = session,
            )
        )

        val preAuthRecord = InMemoryPreAuthorizedCodeRecord(
            code = issueResult.code,
            clientId = null,
            txCode = txCode,
            txCodeValue = issueResult.txCodeValue?.let { hashTxCode(it) },
            grantedScopes = setOf(scope),
            grantedAudience = setOf(baseUrl),
            session = session,
            expiresAt = issueResult.expiresAt,
            credentialNonce = issueResult.credentialNonce,
            credentialNonceExpiresAt = issueResult.credentialNonceExpiresAt,
            sessionId = sessionId,
        )

        preAuthorizedCodeRepository.save(preAuthRecord)

        val credentialOffer = CredentialOffer(
            credentialIssuer = baseUrl,
            credentialConfigurationIds = listOf(profile.credentialConfigurationId),
            grants = CredentialOfferGrants(
                preAuthorizedCode = PreAuthorizedCodeGrant(
                    preAuthorizedCode = issueResult.code,
                    txCode = txCode,
                )
            )
        )

        val offerJson = kotlinx.serialization.json.Json.encodeToString(
            CredentialOffer.serializer(),
            credentialOffer
        )

        val credentialOfferUri = when (valueMode) {
            CredentialOfferValueMode.BY_REFERENCE ->
                "openid-credential-offer://?credential_offer_uri=${java.net.URLEncoder.encode("$baseUrl/credential-offer?id=$sessionId", "UTF-8")}"
            CredentialOfferValueMode.BY_VALUE ->
                "openid-credential-offer://?credential_offer=${java.net.URLEncoder.encode(offerJson, "UTF-8")}"
        }

        return IssuanceSession(
            id = sessionId,
            profileId = profile.profileId,
            credentialConfigurationId = profile.credentialConfigurationId,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            credentialOffer = credentialOffer,
            credentialOfferUri = credentialOfferUri,
            issuanceRequest = issuanceRequest,
            txCode = txCode,
            txCodeValue = issueResult.txCodeValue,
            expiresAt = expiresAt,
        )
    }

    private fun createAuthorizedSession(
        profile: CredentialProfileConfig,
        credentialConfig: CredentialConfiguration,
        issuanceRequest: IssuanceSessionRequest,
        baseUrl: String,
        expiresAt: kotlinx.datetime.Instant,
        valueMode: CredentialOfferValueMode,
    ): IssuanceSession {
        val sessionId = Uuid.random().toString()

        val credentialOffer = CredentialOffer(
            credentialIssuer = baseUrl,
            credentialConfigurationIds = listOf(profile.credentialConfigurationId),
            grants = CredentialOfferGrants(
                authorizationCode = id.walt.openid4vci.offers.AuthorizationCodeGrant(
                    issuerState = sessionId,
                )
            )
        )

        val offerJson = kotlinx.serialization.json.Json.encodeToString(
            CredentialOffer.serializer(),
            credentialOffer
        )

        val credentialOfferUri = when (valueMode) {
            CredentialOfferValueMode.BY_REFERENCE ->
                "openid-credential-offer://?credential_offer_uri=${java.net.URLEncoder.encode("$baseUrl/credential-offer?id=$sessionId", "UTF-8")}"
            CredentialOfferValueMode.BY_VALUE ->
                "openid-credential-offer://?credential_offer=${java.net.URLEncoder.encode(offerJson, "UTF-8")}"
        }

        return IssuanceSession(
            id = sessionId,
            profileId = profile.profileId,
            credentialConfigurationId = profile.credentialConfigurationId,
            authMethod = AuthenticationMethod.AUTHORIZED,
            credentialOffer = credentialOffer,
            credentialOfferUri = credentialOfferUri,
            issuanceRequest = issuanceRequest,
            expiresAt = expiresAt,
        )
    }

    fun getSession(sessionId: String): IssuanceSession? = sessions[sessionId]

    fun updateSessionStatus(sessionId: String, status: IssuanceSessionStatus) {
        sessions[sessionId]?.let { session ->
            sessions[sessionId] = session.copy(status = status)
        }
    }

    private fun hashTxCode(txCode: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(txCode.encodeToByteArray())
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
