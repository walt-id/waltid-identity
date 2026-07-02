package id.walt.issuer2.repository

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.persistence.PersistenceConfiguration
import id.walt.commons.persistence.PersistenceNode
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredPARRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredPreAuthorizedCodeRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredRefreshTokenRepository
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.openid4vci.repository.par.DefaultPARRecord
import id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRecord
import id.walt.openid4vci.repository.refresh.DefaultRefreshTokenRecord
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Tag("redis")
@EnabledIfEnvironmentVariable(named = "ISSUER2_REDIS_HOST", matches = ".+")
class ConfiguredRedisRepositoryTest {

    @Test
    fun configuredRepositoriesCanUseRedisPersistence() = runTest {
        configureRedisPersistence()

        val suffix = Clock.System.now().toEpochMilliseconds().toString()
        val sessionRepository = ConfiguredIssuanceSessionRepository()
        val authorizationCodeRepository = ConfiguredAuthorizationCodeRepository()
        val preAuthorizedCodeRepository = ConfiguredPreAuthorizedCodeRepository()
        val parRepository = ConfiguredPARRepository()
        val refreshTokenRepository = ConfiguredRefreshTokenRepository()

        val session = testSession(suffix)
        val authorizationCode = testAuthorizationCode(suffix)
        val preAuthorizedCode = testPreAuthorizedCode(suffix)
        val pushedAuthorizationRequest = testPushedAuthorizationRequest(suffix)
        val refreshToken = testRefreshToken(suffix)
        val rotatedRefreshToken = testRefreshToken("$suffix-rotated")

        try {
            sessionRepository.save(session)
            assertEquals(session, sessionRepository.get(session.sessionId))

            authorizationCodeRepository.save(authorizationCode)
            assertEquals(authorizationCode, authorizationCodeRepository.consume(authorizationCode.code))
            assertNull(authorizationCodeRepository.consume(authorizationCode.code))

            preAuthorizedCodeRepository.save(preAuthorizedCode)
            assertEquals(preAuthorizedCode, preAuthorizedCodeRepository.get(preAuthorizedCode.code))
            assertEquals(preAuthorizedCode, preAuthorizedCodeRepository.consume(preAuthorizedCode.code))
            assertNull(preAuthorizedCodeRepository.get(preAuthorizedCode.code))

            parRepository.save(pushedAuthorizationRequest)
            assertEquals(
                pushedAuthorizationRequest,
                parRepository.consume(pushedAuthorizationRequest.requestId, Clock.System.now()),
            )
            assertNull(parRepository.consume(pushedAuthorizationRequest.requestId, Clock.System.now()))

            refreshTokenRepository.save(refreshToken)
            assertEquals(refreshToken, refreshTokenRepository.get(refreshToken.tokenSignature))
            assertEquals(
                refreshToken,
                refreshTokenRepository.rotate(refreshToken.tokenSignature, rotatedRefreshToken),
            )
            assertFalse(refreshTokenRepository.get(refreshToken.tokenSignature)!!.active)
            assertEquals(rotatedRefreshToken, refreshTokenRepository.get(rotatedRefreshToken.tokenSignature))
        } finally {
            sessionRepository.remove(session.sessionId)
            authorizationCodeRepository.consume(authorizationCode.code)
            preAuthorizedCodeRepository.consume(preAuthorizedCode.code)
            parRepository.consume(pushedAuthorizationRequest.requestId, Clock.System.now())
        }
    }

    private fun configureRedisPersistence() {
        ConfigManager.preclear()
        FeatureManager.preclear()

        val host = requireNotNull(System.getenv("ISSUER2_REDIS_HOST"))
        val port = System.getenv("ISSUER2_REDIS_PORT")?.toIntOrNull() ?: 6379
        val user = System.getenv("ISSUER2_REDIS_USER")?.takeIf { it.isNotBlank() }
        val password = System.getenv("ISSUER2_REDIS_PASSWORD")?.takeIf { it.isNotBlank() }

        ConfigManager.preloadAndRegisterConfig(
            "persistence",
            PersistenceConfiguration(
                type = "redis",
                nodes = listOf(PersistenceNode(host = host, port = port)),
                user = user,
                password = password,
            )
        )
        ConfigManager.loadConfigs()
        FeatureManager.enabledFeatures.add(CommonsFeatureCatalog.persistenceFeature.name)
    }

    private fun testSession(suffix: String) = IssuanceSession(
        sessionId = "redis-session-$suffix",
        profileId = "profile-id",
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        credentialConfigurationId = "identity_credential",
        issuerKey = buildJsonObject {
            put("type", "jwk")
        },
        credentialData = buildJsonObject {
            put("given_name", "Jane")
            put("family_name", "Doe")
        },
        issuerDid = "did:web:issuer.example",
        expiresAt = Clock.System.now().plus(5.minutes),
    )

    private fun testAuthorizationCode(suffix: String) = DefaultAuthorizationCodeRecord(
        code = "redis-auth-code-$suffix",
        clientId = "wallet-client",
        redirectUri = "https://wallet.example/callback",
        grantedScopes = setOf("openid"),
        grantedAudience = setOf("issuer2"),
        session = DefaultSession(subject = "subject"),
        expiresAt = Clock.System.now().plus(5.minutes),
    )

    private fun testPreAuthorizedCode(suffix: String) = DefaultPreAuthorizedCodeRecord(
        code = "redis-pre-auth-code-$suffix",
        clientId = null,
        txCode = TxCode(inputMode = "numeric", length = 6, description = "PIN"),
        txCodeValue = "hashed-pin",
        grantedScopes = setOf("openid"),
        grantedAudience = setOf("issuer2"),
        session = DefaultSession(subject = "subject"),
        expiresAt = Clock.System.now().plus(5.minutes),
        credentialNonce = "nonce",
        credentialNonceExpiresAt = Clock.System.now().plus(5.minutes),
        issuanceSessionId = "redis-session-$suffix",
    )

    private fun testPushedAuthorizationRequest(suffix: String): DefaultPARRecord {
        val now = Clock.System.now()
        return DefaultPARRecord(
            requestId = "redis-par-$suffix",
            requestParameters = mapOf(
                "client_id" to listOf("wallet-client"),
                "response_type" to listOf("code"),
                "redirect_uri" to listOf("https://wallet.example/callback"),
                "scope" to listOf("openid"),
            ),
            createdAt = now,
            expiresAt = now.plus(5.minutes),
        )
    }

    private fun testRefreshToken(suffix: String): DefaultRefreshTokenRecord {
        val refreshTokenExpiresAt = timestampOffset(5.minutes)
        val session = DefaultSession(
            subject = "subject",
            expiresAt = mapOf(
                TokenType.ACCESS_TOKEN to timestampOffset(5.minutes),
                TokenType.REFRESH_TOKEN to refreshTokenExpiresAt,
            ),
            customAttributes = mapOf("issuance_session_id" to "redis-session-$suffix"),
        )
        val client = DefaultClient(
            id = "wallet-client",
            redirectUris = listOf("https://wallet.example/callback"),
            grantTypes = setOf("authorization_code", "refresh_token"),
            responseTypes = setOf("code"),
            scopes = setOf("openid"),
            audience = setOf("issuer2"),
        )
        val accessTokenRequest = DefaultAccessTokenRequest(
            id = "redis-access-token-request-$suffix",
            requestedAt = timestampOffset(0.minutes),
            client = client,
            grantTypes = setOf("authorization_code"),
            handledGrantTypes = setOf("authorization_code"),
            requestedScopes = setOf("openid"),
            grantedScopes = setOf("openid"),
            requestedAudience = setOf("issuer2"),
            grantedAudience = setOf("issuer2"),
            requestForm = mapOf(
                "grant_type" to listOf("authorization_code"),
                "client_id" to listOf(client.id),
            ),
            session = session,
            issClaim = "http://localhost",
        )

        return DefaultRefreshTokenRecord(
            tokenSignature = "redis-refresh-token-$suffix",
            active = true,
            accessTokenRequest = accessTokenRequest,
            accessTokenSignature = "redis-access-token-$suffix",
            clientId = client.id,
            grantedScopes = setOf("openid"),
            grantedAudience = setOf("issuer2"),
            session = session,
            expiresAt = refreshTokenExpiresAt,
        )
    }

    private fun timestampOffset(duration: kotlin.time.Duration): Instant =
        Instant.fromEpochMilliseconds(Clock.System.now().plus(duration).toEpochMilliseconds())
}
