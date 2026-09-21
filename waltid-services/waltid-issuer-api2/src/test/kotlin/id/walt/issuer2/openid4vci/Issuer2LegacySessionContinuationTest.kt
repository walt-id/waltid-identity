package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.commons.persistence.Persistence
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.*
import id.walt.issuer2.configurePlugins
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.models.toPublicJson
import id.walt.issuer2.repository.*
import id.walt.issuer2.repository.fixtures.PreBatchIssuanceSession
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.testsupport.*
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenIssuer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class Issuer2LegacySessionContinuationTest {
    @AfterEach
    fun clear() = clearIssuer2TestEnvironment()

    @Test
    fun `pre-upgrade offers and signed tokens continue from flat snapshots and legacy crypto2 sidecars`() = testApplication {
        loadIssuer2ConfigFiles()
        val attestation = createIssuer2ClientAttestationTestMaterial()
        val config = ConfigManager.getConfig<Issuer2ServiceConfig>().copy(clientAuthenticationConfig = attestation.clientAuthenticationConfig)
        val namespace = "legacy-continuation-${java.util.UUID.randomUUID()}"
        val raw = ConfiguredPersistence<String>(namespace, 5.minutes, { it }, { it })
        val keys = ConfiguredPersistence<String>("$namespace-keys", 5.minutes, { it }, { it })
        val repository = ConfiguredIssuanceSessionRepository(SerializedSessions(raw), keys)
        val module = Issuer2Module(config, ConfigManager.getConfig(), ConfigManager.getConfig(), issuanceSessionRepository = repository)
        // Seed the module's actual store: separate in-memory ConfiguredPersistence instances
        // do not share records even when they have the same discriminator.
        val authorizationCodes = Issuer2Module::class.java.getDeclaredField("authorizationCodeRepository").let {
            it.isAccessible = true
            it.get(module) as ConfiguredAuthorizationCodeRepository
        }
        application {
            install(ContentNegotiation) { json(issuer2TestJson) }
            installIssuer2AuthenticationForTests()
            configurePlugins()
            routing {
                module.managementController.register(this)
                module.openId4VciController.register(this)
            }
        }
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client, attestationAssembler = attestation.attestationAssembler)
        val tokenKey = KeyManager.resolveSerializedKey(Json.parseToJsonElement(config.ciTokenKey).jsonObject)
        for (authMethod in AuthenticationMethod.entries) for (tokenBeforeUpgrade in listOf(false, true)) for (legacySidecar in listOf(false, true)) {
            val offer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE.copy(authMethod = authMethod))
            val current = assertNotNull(repository.get(offer.offerId))
            val baseline = Json.decodeFromJsonElement<PreBatchIssuanceSession>(current.toPublicJson())
            val resolved = wallet.resolve(offer)
            suspend fun exchange(): String = if (authMethod == AuthenticationMethod.PRE_AUTHORIZED) {
                wallet.exchangePreAuthorizedCode(resolved, null).access_token
            } else {
                // Resume at the persisted authorization-code boundary. The record and claims
                // have the unchanged pre-batch shape, so this does not require a new login.
                val code = java.util.UUID.randomUUID().toString()
                authorizationCodes.save(DefaultAuthorizationCodeRecord(
                    code = code, clientId = "issuer2-wallet-test", redirectUri = "https://wallet.example/callback",
                    grantedScopes = setOf(baseline.credentialConfigurationId), grantedAudience = emptySet(),
                    session = DefaultSession(subject = offer.offerId, expiresAt = mapOf(TokenType.ACCESS_TOKEN to current.expiresAt)),
                    expiresAt = current.expiresAt,
                ))
                wallet.exchangeAuthorizationCode(resolved, code).access_token
            }
            val earlierToken = if (tokenBeforeUpgrade) {
                val issued = exchange()
                // The pre-batch signer used these same claims without authorization_details.
                // Preserve the actual signature key, expiry, grant/session correlation and client.
                val claims = issued.decodeJws().payload - "authorization_details"
                JwtAccessTokenIssuer(resolver = { tokenKey }).issue(claims)
            } else null
            raw.set(offer.offerId, Json.encodeToString(PreBatchIssuanceSession.serializer(), baseline))
            val encodedKey = assertNotNull(current.issuanceRequests.single().crypto2IssuerStoredKey)
            keys.set(offer.offerId, if (legacySidecar) encodedKey else buildJsonObject {
                put(baseline.credentialConfigurationId, encodedKey)
            }.toString())
            val reopened = ConfiguredIssuanceSessionRepository(SerializedSessions(raw), keys)
            val loaded = assertNotNull(reopened.get(offer.offerId))
            assertNull(loaded.authorizedCredentialIdentifiers)
            assertEquals(current.issuanceRequests.single().issuerKey, loaded.issuanceRequests.single().issuerKey)
            assertEquals(current.expiresAt, loaded.expiresAt)
            val token = earlierToken ?: exchange()
            assertJwtVcJsonCredentialPayload(wallet.requestCredential(resolved, token))
            val completed = assertNotNull(reopened.get(offer.offerId))
            assertEquals(IssuanceSessionStatus.SUCCESSFUL, completed.status)
            assertFalse(completed.isClosed)
            assertEquals(setOf(baseline.credentialConfigurationId), completed.issuanceResults.keys)
            assertEquals(listOf(baseline.credentialConfigurationId), completed.authorizedCredentialIdentifiers)
            assertTrue(Json.parseToJsonElement(assertNotNull(raw[offer.offerId])).jsonObject.containsKey("issuanceRequests"))
        }
    }

    private class SerializedSessions(private val raw: Persistence<String>) : Persistence<IssuanceSession>(raw.discriminator, raw.defaultExpiration) {
        override fun get(id: String) = raw[id]?.let(IssuanceSessionStorageCodec::decode)
        override fun getAndRemove(id: String) = raw.getAndRemove(id)?.let(IssuanceSessionStorageCodec::decode)
        override fun set(id: String, value: IssuanceSession) = set(id, value, null)
        override fun set(id: String, value: IssuanceSession, ttl: Duration?) = raw.set(id, Json.encodeToString(value), ttl)
        override fun remove(id: String) = raw.remove(id)
        override fun contains(id: String) = id in raw
        override fun listAllKeys() = raw.listAllKeys()
        override fun getAll() = raw.getAll().map(IssuanceSessionStorageCodec::decode)
        override fun listSize(id: String) = raw.listSize(id)
        override fun listAdd(id: String, value: IssuanceSession, ttl: Duration?) = raw.listAdd(id, Json.encodeToString(value), ttl)
    }
}
