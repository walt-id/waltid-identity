package id.walt.issuer2.repository

import id.walt.openid4vci.deferred.*
import id.walt.openid4vci.requests.credential.AcceptedCredentialRequest
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.issuer2.service.IssuanceSessionService
import kotlinx.coroutines.*
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.JsonPrimitive
import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.persistence.PersistenceConfiguration
import id.walt.commons.persistence.PersistenceNode
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.domain.IssuanceRequest
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
import id.walt.openid4vci.requests.authorization.DefaultAuthorizationRequest
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@Tag("redis")
@EnabledIfEnvironmentVariable(named = "ISSUER2_REDIS_HOST", matches = ".+")
class ConfiguredRedisRepositoryTest {

    @Test
    fun redisRetentionDoesNotShortenExistingDeadline() = runTest {
        val host = requireNotNull(System.getenv("ISSUER2_REDIS_HOST"))
        val port = System.getenv("ISSUER2_REDIS_PORT")?.toInt() ?: 6379
        redis.clients.jedis.JedisPooled(host, port).use { redis ->
            val namespace = "deferred-retention-${java.util.UUID.randomUUID()}"
            val persistence = id.walt.commons.persistence.RedisPersistence<String>(namespace, 5.minutes, { it }, { it }, redis)
            try {
                persistence.set("key", "private-key", 1.minutes)
                kotlin.test.assertTrue(persistence.retainForAtLeast("key", 7.days))
                kotlin.test.assertTrue(persistence.retainForAtLeast("key", 1.minutes))
                kotlin.test.assertTrue(redis.pttl("$namespace:key") > 6.days.inWholeMilliseconds)
                assertFalse(persistence.retainForAtLeast("missing", 1.minutes))
            } finally { persistence.remove("key") }
        }
    }

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
            // Compared against the saved session because save() attaches the crypto2 sidecar.
            val savedSession = sessionRepository.save(session)
            val restartedSessionRepository = ConfiguredIssuanceSessionRepository()
            assertEquals(savedSession, restartedSessionRepository.get(session.sessionId))
            assertEquals(savedSession, restartedSessionRepository.take(session.sessionId))
            assertNull(sessionRepository.take(session.sessionId))

            authorizationCodeRepository.save(authorizationCode)
            assertEquals(authorizationCode, authorizationCodeRepository.consume(authorizationCode.code))
            assertNull(authorizationCodeRepository.consume(authorizationCode.code))

            preAuthorizedCodeRepository.save(preAuthorizedCode)
            val restartedPreAuthorizedCodeRepository = ConfiguredPreAuthorizedCodeRepository()
            assertEquals(preAuthorizedCode, restartedPreAuthorizedCodeRepository.get(preAuthorizedCode.code))
            assertEquals(preAuthorizedCode, restartedPreAuthorizedCodeRepository.consume(preAuthorizedCode.code))
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

    @Test
    fun deferredTransactionsKeepBindingsAndResponseAcrossRestartAndConcurrentPolling() = runTest {
        configureRedisPersistence()
        val repository = ConfiguredIssuanceSessionRepository()
        val original = testSession("deferred-${java.util.UUID.randomUUID()}").copy(
            deferredStatus = DeferredStatus.PENDING, deferredState = DeferredIssuanceState(),
        )
        repository.save(original)
        try {
            val first = DeferredIssuance(IssuanceSessionService(repository).deferredStore)
            first.start(original.sessionId, Clock.System.now() + 7.days)
            val selected = original.issuanceRequests.single()
            val accepted = AcceptedCredentialRequest(
                DefaultCredentialRequest(
                    client = DefaultClient("client", emptyList(), emptySet(), emptySet()),
                    credentialIdentifier = selected.credentialIdentifier, credentialConfigurationId = null,
                    proofs = null, credentialResponseEncryption = null, accessTokenClientId = "client",
                ), selected.credentialIdentifier, selected.credentialConfigurationId,
                CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "https://example.com/type"), emptyList(),
            )
            val transactions = coroutineScope {
                (1..12).map { async(Dispatchers.IO) { first.accept(original.sessionId, accepted) } }.awaitAll()
            }
            val transaction = transactions.first()
            assertEquals(1, transactions.map { it.id }.distinct().size)
            first.decide(original.sessionId, DeferredStatus.READY)
            val restartedRepository = ConfiguredIssuanceSessionRepository()
            val restarted = DeferredIssuance(IssuanceSessionService(restartedRepository).deferredStore)
            assertEquals(transaction, restarted.accept(original.sessionId, accepted))
            val claims = coroutineScope { (1..12).map { index -> async(Dispatchers.IO) {
                (if (index % 2 == 0) first else restarted).collect(original.sessionId, transaction.id, setOf(selected.credentialIdentifier), "client")
            } }.awaitAll() }
            val claim = claims.filterIsInstance<DeferredIssuance.Collection.Claimed>().single().transaction
            val response = CredentialResponse(listOf(IssuedCredential(JsonPrimitive("signed-once"))))
            first.checkpoint(original.sessionId, claim, response)
            first.release(original.sessionId, claim)
            val recovered = assertIs<DeferredIssuance.Collection.Claimed>(restarted.collect(original.sessionId, transaction.id, setOf(selected.credentialIdentifier), "client")).transaction
            assertEquals(response, recovered.response)
            assertEquals(accepted, recovered.accepted)
            assertNotNull(restartedRepository.get(original.sessionId)!!.issuanceRequests.single().crypto2IssuerStoredKey)
            restarted.consume(original.sessionId, recovered)
            assertEquals("invalid_transaction_id", assertFailsWith<DeferredProtocolException> {
                first.collect(original.sessionId, transaction.id, setOf(selected.credentialIdentifier), "client")
            }.error.error)
        } finally { repository.remove(original.sessionId) }
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

    private suspend fun testSession(suffix: String) = IssuanceSession(
        sessionId = "redis-session-$suffix",
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        issuanceRequests = listOf(
            IssuanceRequest(
                credentialIdentifier = "credential-$suffix",
                profileId = "profile-id",
                credentialConfigurationId = "identity_credential",
                issuerKey = KeySerialization.serializeKeyToJson(JWKKey.generate(KeyType.secp256r1)).jsonObject,
                credentialData = buildJsonObject {
                    put("given_name", "Jane")
                    put("family_name", "Doe")
                },
                issuerDid = "did:web:issuer.example",
            )
        ),
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
        issuanceSessionId = "redis-session-$suffix",
    )

    private fun testPushedAuthorizationRequest(suffix: String): DefaultPARRecord {
        val now = Clock.System.now()
        return DefaultPARRecord(
            requestId = "redis-par-$suffix",
            authorizationRequest = DefaultAuthorizationRequest(
                client = DefaultClient("wallet-client", listOf("https://wallet.example/callback"), setOf("authorization_code"), setOf("code")),
                responseTypes = setOf("code"), redirectUri = "https://wallet.example/callback", state = null,
                requestedScopes = setOf("openid"),
                requestForm = mapOf(
                    "client_id" to listOf("wallet-client"),
                    "response_type" to listOf("code"),
                    "redirect_uri" to listOf("https://wallet.example/callback"),
                    "scope" to listOf("openid"),
                ),
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
            dpopJwkThumbprint = "verified-wallet-thumbprint",
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
