package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.*
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.repository.*
import id.walt.issuer2.service.*
import id.walt.issuer2.service.openid4vci.*
import id.walt.issuer2.testsupport.*
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.deferred.*
import id.walt.openid4vci.repository.authorization.InMemoryAuthorizationCodeRepository
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.preauthorized.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import kotlin.test.*
import kotlin.time.Clock

class DeferredCheckpointRecoveryTest {
    @AfterEach fun clear() = clearIssuer2TestEnvironment()

    @Test fun initialSigningFailureCanRetryWithFreshProof() = runTest { verifyRecovery("signing") }
    @Test fun responseFailureAfterCheckpointDoesNotSignAgain() = runTest { verifyRecovery("response") }
    @Test fun failureAfterFinalizationRecoversWithoutRepeatedIssuance() = runTest { verifyRecovery("consume") }

    private suspend fun verifyRecovery(failure: String) {
        loadIssuer2ConfigFiles()
        val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>()
        val metadataConfig = ConfigManager.getConfig<Issuer2MetadataConfig>()
        val profiles = CredentialProfileService(ConfigManager.getConfig<Issuer2ProfilesConfig>(), metadataConfig)
        val backing = ConfiguredIssuanceSessionRepository()
        var fail = true
        val repository = object : IssuanceSessionRepository by backing {
            override suspend fun compareAndSet(expected: IssuanceSession, replacement: IssuanceSession): Boolean {
                if (failure == "consume" && fail && replacement.deferredState?.transactions?.values?.any { it.consumed } == true) {
                    fail = false
                    error("Temporary storage failure after issuance finalized")
                }
                return backing.compareAndSet(expected, replacement)
            }
        }
        val sessions = IssuanceSessionService(repository)
        val module = OpenId4VciModule.create(serviceConfig, InMemoryAuthorizationCodeRepository(), InMemoryPreAuthorizedCodeRepository(), InMemoryPARRepository(), InMemoryRefreshTokenRepository())
        val metadata = MetadataService(serviceConfig, metadataConfig, profiles, sessions, module.preAuthorizedCodeIssuer.anonymousAccessSupported)
        val notifications = IssuanceNotificationService()
        var signingCalls = 0
        // Intercept only the failure boundary; all parsing, proof validation, signing and persistence use real implementations.
        val provider = Proxy.newProxyInstance(OAuth2Provider::class.java.classLoader, arrayOf(OAuth2Provider::class.java)) { _, method, args ->
            if (method.name == "createCredentialResponse") {
                signingCalls++
                if (failure == "signing" && fail) { fail = false; error("Temporary signing failure") }
            }
            if (method.name == "writeCredentialResponse" && failure == "response" && fail) {
                fail = false
                CredentialResponseHttp(status = 500, payload = buildJsonObject { put("error", "server_error") })
            } else try { method.invoke(module.oauth2Provider, *(args ?: emptyArray())) }
            catch (e: InvocationTargetException) { throw e.targetException }
        } as OAuth2Provider
        fun protocol() = OpenId4VciProtocolService(
            oauth2Provider = provider, sessionService = IssuanceSessionService(repository), profileService = profiles,
            metadataService = metadata, notificationService = notifications, credentialNonceService = module.credentialNonceService,
        )
        val offers = CredentialOfferService(profiles, sessions, module.preAuthorizedCodeIssuer, serviceConfig, notifications)
        val receipt = offers.createCredentialOffer(Issuer2RequestExamples.PROFILE_DEFERRED_OFFER.copy(profileId = "identityCredentialSdJwt"), "create")
        val offer = assertNotNull(offers.getCredentialOffer(receipt.offerId, "resolve"))
        val token = protocol().processTokenRequest(mapOf(
            "grant_type" to listOf("urn:ietf:params:oauth:grant-type:pre-authorized_code"),
            "pre-authorized_code" to listOf(assertNotNull(offer.grants?.preAuthorizedCode?.preAuthorizedCode)),
        ), requestId = "token").payload.getValue("access_token").jsonPrimitive.content
        DeferredIssuance(sessions.deferredStore).decide(receipt.offerId, DeferredStatus.READY)
        val holder = JWKKey.generate(KeyType.secp256r1)
        suspend fun request(key: JWKKey = holder): JsonObject {
            val nonce = protocol().processNonceRequest("nonce").payload.getValue("c_nonce")
            val proof = key.signJws(buildJsonObject {
                put("aud", metadata.issuerBaseUrl()); put("iat", Clock.System.now().epochSeconds); put("nonce", nonce)
            }.toString().encodeToByteArray(), headers = mapOf("typ" to JsonPrimitive("openid4vci-proof+jwt"), "jwk" to key.getPublicKey().exportJWKObject()))
            return buildJsonObject {
                put("credential_configuration_id", offer.credentialConfigurationIds.single())
                putJsonObject("proofs") { putJsonArray("jwt") { add(proof) } }
            }
        }
        suspend fun submit(body: JsonObject) = protocol().processCredentialRequest(listOf("Bearer $token"), emptyList(), body, "credential")
        if (failure == "consume") assertFailsWith<IllegalStateException> { submit(request()) }
        else assertEquals(500, submit(request()).status)
        val transaction = assertNotNull(sessions.getSession(receipt.offerId).deferredState).transactions.values.single()
        assertFalse(transaction.consumed)
        if (failure != "signing") assertNotNull(transaction.response)
        val mismatched = submit(request(JWKKey.generate(KeyType.secp256r1)))
        assertEquals(400, mismatched.status)
        assertEquals(transaction.id, sessions.getSession(receipt.offerId).deferredState!!.transactions.values.single().id)
        val invalid = submit(buildJsonObject {
            put("credential_configuration_id", offer.credentialConfigurationIds.single())
            putJsonObject("proofs") { putJsonArray("jwt") { add("invalid") } }
        })
        assertEquals(400, invalid.status, "Repeated Credential Requests must revalidate proofs")
        val recovered = submit(request())
        assertEquals(200, recovered.status, recovered.payload.toString())
        assertEquals(if (failure == "signing") 2 else 1, signingCalls)
        val final = sessions.getSession(receipt.offerId)
        assertEquals(1, final.issuanceResults.size)
        assertTrue(final.deferredState!!.transactions.getValue(transaction.id).consumed)
        assertEquals(400, submit(request()).status)
    }
}
