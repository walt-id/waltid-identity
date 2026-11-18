package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.commons.persistence.InMemoryPersistence
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
import id.walt.openid4vci.metadata.issuer.BatchCredentialIssuance
import id.walt.sdjwt.SDJwt
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
import kotlin.time.Duration

class DeferredCheckpointRecoveryTest {
    private val catalogues = mutableListOf<IssuerTypeMetadataCatalogue>()

    @AfterEach
    fun clear() {
        try {
            catalogues.forEach { it.close() }
        } finally {
            clearIssuer2TestEnvironment()
        }
    }

    @Test fun initialSigningFailureCanRetryWithFreshProof() = runTest { verifyRecovery("signing") }
    @Test fun responseFailureAfterCheckpointDoesNotSignAgain() = runTest { verifyRecovery("response") }
    @Test fun failureAfterFinalizationRecoversWithoutRepeatedIssuance() = runTest { verifyRecovery("consume") }
    @Test fun deferredBatchRetainsStatusReservationsAfterGenerationFailure() = runTest { verifyRecovery("signing", preallocated = true) }
    @Test fun deferredBatchRetainsPreparedCredentialsAfterResponseFailure() = runTest { verifyRecovery("response", preallocated = true) }
    @Test fun deferredBatchRetainsStatusesAfterFinalizationFailure() = runTest { verifyRecovery("consume", preallocated = true) }
    @Test fun immediateBatchRetainsStatusReservationsAfterGenerationFailure() = runTest { verifyRecovery("signing", preallocated = true, deferred = false) }
    @Test fun immediateBatchRetainsPreparedCredentialsAfterResponseFailure() = runTest { verifyRecovery("response", preallocated = true, deferred = false) }
    @Test fun immediateBatchRetainsStatusesAfterFinalizationFailure() = runTest { verifyRecovery("consume", preallocated = true, deferred = false) }

    private suspend fun verifyRecovery(failure: String, preallocated: Boolean = false, deferred: Boolean = true) {
        loadIssuer2ConfigFiles()
        val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>().let {
            if (preallocated) it.copy(batchCredentialIssuance = BatchCredentialIssuance(2)) else it
        }
        val metadataConfig = ConfigManager.getConfig<Issuer2MetadataConfig>()
        val groups = JsonArray(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
            .runtimeOverrides!!.credentialStatus!!.jsonArray.take(2))
        val profilesConfig = ConfigManager.getConfig<Issuer2ProfilesConfig>().let { configured ->
            if (!preallocated) configured else configured.copy(profiles = configured.profiles.mapValues { (id, profile) ->
                if (id == "identityCredentialSdJwt") profile.copy(credentialStatus = groups) else profile
            })
        }
        val profiles = CredentialProfileService(profilesConfig, metadataConfig)
        val statusStorage = InMemoryPersistence<CredentialStatusConsumption>("recovery-statuses", Duration.INFINITE)
        val statusRepository = ConfiguredCredentialStatusConsumptionRepository(statusStorage)
        val backing = ConfiguredIssuanceSessionRepository()
        var fail = true
        val repository = object : IssuanceSessionRepository by backing {
            override suspend fun compareAndSet(expected: IssuanceSession, replacement: IssuanceSession): Boolean {
                val completesImmediate = !deferred &&
                    expected.credentialStatusIssuance?.operations?.isNotEmpty() == true &&
                    replacement.credentialStatusIssuance?.operations?.isEmpty() == true &&
                    replacement.issuanceResults.isNotEmpty()
                if (failure == "consume" && fail &&
                    (replacement.deferredState?.transactions?.values?.any { it.consumed } == true || completesImmediate)) {
                    fail = false
                    error("Temporary storage failure after issuance finalized")
                }
                return backing.compareAndSet(expected, replacement)
            }
        }
        val sessions = IssuanceSessionService(repository)
        val typeMetadata = IssuerTypeMetadataCatalogue(
            serviceConfig, metadataConfig, InMemoryPersistence("types", Duration.INFINITE),
        ).also(catalogues::add)
        val module = OpenId4VciModule.create(
            serviceConfig, InMemoryAuthorizationCodeRepository(), InMemoryPreAuthorizedCodeRepository(),
            InMemoryPARRepository(), InMemoryRefreshTokenRepository(), metadataResolver = typeMetadata,
        )
        val metadata = MetadataService(
            serviceConfig, metadataConfig, profiles, sessions, typeMetadata,
            preAuthorizedGrantAnonymousAccessSupported = module.preAuthorizedCodeIssuer.anonymousAccessSupported,
        )
        val notifications = IssuanceNotificationService()
        var signingCalls = 0
        // Intercept only the failure boundary; all parsing, proof validation, signing and persistence use real implementations.
        val provider = Proxy.newProxyInstance(OAuth2Provider::class.java.classLoader, arrayOf(OAuth2Provider::class.java)) { _, method, args ->
            if (method.name == "createCredentialResponse") {
                signingCalls++
                if (failure == "signing" && fail && !preallocated) { fail = false; error("Temporary signing failure") }
            }
            if (method.name == "writeCredentialResponse" && failure == "response" && fail) {
                fail = false
                CredentialResponseHttp(status = 500, payload = buildJsonObject { put("error", "server_error") })
            } else try {
                val result = method.invoke(module.oauth2Provider, *(args ?: emptyArray()))
                if (method.name == "createCredentialResponse" && failure == "signing" && preallocated && fail) {
                    fail = false
                    error("Temporary failure after status allocation and credential generation")
                }
                result
            }
            catch (e: InvocationTargetException) { throw e.targetException }
        } as OAuth2Provider
        fun protocol() = OpenId4VciProtocolService(
            oauth2Provider = provider, sessionService = IssuanceSessionService(repository), profileService = profiles,
            metadataService = metadata, notificationService = notifications, credentialNonceService = module.credentialNonceService,
            credentialStatusConsumption = statusRepository,
        )
        val offers = CredentialOfferService(profiles, sessions, module.preAuthorizedCodeIssuer, serviceConfig, notifications, typeMetadata)
        val receipt = offers.createCredentialOffer(Issuer2RequestExamples.PROFILE_DEFERRED_OFFER.copy(
            profileId = "identityCredentialSdJwt", deferred = deferred,
        ), "create")
        val offer = assertNotNull(offers.getCredentialOffer(receipt.offerId, "resolve"))
        val token = protocol().processTokenRequest(mapOf(
            "grant_type" to listOf("urn:ietf:params:oauth:grant-type:pre-authorized_code"),
            "pre-authorized_code" to listOf(assertNotNull(offer.grants?.preAuthorizedCode?.preAuthorizedCode)),
        ), requestId = "token").payload.getValue("access_token").jsonPrimitive.content
        if (deferred) DeferredIssuance(sessions.deferredStore).decide(receipt.offerId, DeferredStatus.READY)
        val holder = JWKKey.generate(KeyType.secp256r1)
        val secondHolder = JWKKey.generate(KeyType.secp256r1)
        suspend fun request(key: JWKKey = holder): JsonObject {
            val nonce = protocol().processNonceRequest("nonce").payload.getValue("c_nonce")
            suspend fun proof(proofKey: JWKKey) = proofKey.signJws(buildJsonObject {
                put("aud", metadata.issuerBaseUrl()); put("iat", Clock.System.now().epochSeconds); put("nonce", nonce)
            }.toString().encodeToByteArray(), headers = mapOf("typ" to JsonPrimitive("openid4vci-proof+jwt"), "jwk" to proofKey.getPublicKey().exportJWKObject()))
            val proofs = listOfNotNull(proof(key), if (preallocated) proof(secondHolder) else null)
            return buildJsonObject {
                put("credential_configuration_id", offer.credentialConfigurationIds.single())
                putJsonObject("proofs") { put("jwt", JsonArray(proofs.map(::JsonPrimitive))) }
            }
        }
        suspend fun submit(body: JsonObject) = protocol().processCredentialRequest(listOf("Bearer $token"), emptyList(), body, "credential")
        if (failure == "consume" && deferred) assertFailsWith<IllegalStateException> { submit(request()) }
        else assertEquals(500, submit(request()).status)
        val transaction = if (deferred) assertNotNull(sessions.getSession(receipt.offerId).deferredState).transactions.values.single() else null
        if (transaction != null) {
            assertFalse(transaction.consumed)
            if (failure != "signing") assertNotNull(transaction.response)
        } else {
            val operation = sessions.getSession(receipt.offerId).credentialStatusIssuance!!.operations.values.single()
            if (failure != "signing") assertNotNull(operation.response)
        }
        val mismatched = submit(request(JWKKey.generate(KeyType.secp256r1)))
        assertEquals(400, mismatched.status)
        if (transaction != null) assertEquals(transaction.id, sessions.getSession(receipt.offerId).deferredState!!.transactions.values.single().id)
        val invalid = submit(buildJsonObject {
            put("credential_configuration_id", offer.credentialConfigurationIds.single())
            putJsonObject("proofs") { putJsonArray("jwt") { add("invalid") } }
        })
        assertEquals(400, invalid.status, "Repeated Credential Requests must revalidate proofs")
        val recovered = submit(request())
        assertEquals(200, recovered.status, recovered.payload.toString())
        assertEquals(if (failure == "signing") 2 else 1, signingCalls)
        if (preallocated) {
            val statuses = recovered.payload.getValue("credentials").jsonArray.map {
                SDJwt.parse(it.jsonObject.getValue("credential").jsonPrimitive.content).fullPayload.getValue("status")
            }
            assertEquals(groups.toList(), statuses)
            assertEquals(2, statusStorage[GLOBAL_STATUS_CONSUMPTION_ID]!!.usedReferences.size)
        }
        val final = sessions.getSession(receipt.offerId)
        assertEquals(1, final.issuanceResults.size)
        if (transaction != null) {
            assertTrue(final.deferredState!!.transactions.getValue(transaction.id).consumed)
            assertEquals(400, submit(request()).status)
        } else {
            assertTrue(final.credentialStatusIssuance!!.operations.isEmpty())
            assertNull(final.credentialStatusIssuance.leaseId)
        }
    }
}
