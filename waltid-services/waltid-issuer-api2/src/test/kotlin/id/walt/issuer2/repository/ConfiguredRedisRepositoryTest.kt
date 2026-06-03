package id.walt.issuer2.repository

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.persistence.PersistenceConfiguration
import id.walt.commons.persistence.PersistenceNode
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredPreAuthorizedCodeRepository
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRecord
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

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

        val session = testSession(suffix)
        val authorizationCode = testAuthorizationCode(suffix)
        val preAuthorizedCode = testPreAuthorizedCode(suffix)

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
        } finally {
            sessionRepository.remove(session.sessionId)
            authorizationCodeRepository.consume(authorizationCode.code)
            preAuthorizedCodeRepository.consume(preAuthorizedCode.code)
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
}