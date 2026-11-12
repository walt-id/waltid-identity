package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JweUtils
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.*
import id.walt.issuer2.configurePlugins
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceResult
import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.issuer2.notifications.IssuanceNotifications
import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.issuer2.repository.*
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredRefreshTokenRepository
import id.walt.issuer2.testsupport.*
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import id.walt.openid4vci.clientauth.attestation.VerifiedClientAttestation
import id.walt.openid4vci.deferred.DeferredTransaction
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.proofs.attestation.*
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class DeferredAttestationExpiryTest {
    private val notificationServer = Issuer2TestNotificationServer()

    @AfterEach fun clear() {
        notificationServer.stopServer()
        clearIssuer2TestEnvironment()
    }

    @Test fun preAuthorizedExpiredWiaIsDeniedAfterRefresh() = verify(false, "wia")
    @Test fun authorizationCodeExpiredWiaIsDeniedAfterRefresh() = verify(true, "wia")
    @Test fun preAuthorizedOneExpiredKaDeniesThreeKeys() = verify(false, "ka")
    @Test fun authorizationCodeOneExpiredKaDeniesThreeKeys() = verify(true, "ka")
    @Test fun preAuthorizedValidEvidenceIssuesAllThreeKeys() = verify(false, "valid")
    @Test fun authorizationCodeValidEvidenceIssuesAllThreeKeys() = verify(true, "valid")
    @Test fun checkpointCannotBeDeliveredAfterWiaExpiry() = verify(false, "checkpoint")
    @Test fun missingRequiredEvidenceFailsClosed() = verify(false, "missing")
    @Test fun preAuthorizedExpiredOriginalWiaIsDeniedBeforeAcceptance() = verify(false, "acceptance")
    @Test fun authorizationCodeExpiredOriginalWiaIsDeniedBeforeAcceptance() = verify(true, "acceptance")
    @Test fun encryptionFailureKeepsSignedCheckpointForRetry() = verify(false, "encryption")
    @Test fun savedIssuanceResultIsPreservedWhenRecoveryDeniesExpiredEvidence() = verify(false, "saved-result")

    private fun verify(authorizationCode: Boolean, scenario: String) = testApplication {
        loadIssuer2ConfigFiles()
        notificationServer.startServer()
        val clientAttestation = createIssuer2ClientAttestationTestMaterial()
        val attester = JWKKey.generate(KeyType.secp256r1)
        val encryptionKey = if (scenario == "encryption") JWKKey.generate(KeyType.secp256r1) else null
        val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>().copy(
            clientAuthenticationConfig = clientAttestation.clientAuthenticationConfig,
            batchCredentialIssuance = BatchCredentialIssuance(3),
            keyAttestationConfig = KeyAttestationConfig(KeyAttestationVerificationMethod.StaticJwk(attester.getPublicKey().exportJWKObject())),
            credentialEncryptionKey = encryptionKey?.let { KeySerialization.serializeKey(it) },
        )
        val metadataConfig = ConfigManager.getConfig<Issuer2MetadataConfig>().let { metadata ->
            metadata.copy(credentialConfigurations = metadata.credentialConfigurations.mapValues { (_, value) ->
                JsonObject(value.jsonObject + ("proof_types_supported" to buildJsonObject {
                    putJsonObject("jwt") {
                        put("proof_signing_alg_values_supported", JsonArray(listOf(JsonPrimitive("ES256"))))
                        putJsonObject("key_attestations_required") {}
                    }
                }))
            })
        }
        val backing = ConfiguredIssuanceSessionRepository()
        var expiredAtCheckpoint = false
        var interruptedAfterResult = false
        fun expire(transaction: DeferredTransaction): DeferredTransaction {
            val accepted = transaction.accepted
            val expiration = Clock.System.now().epochSeconds - 120
            return transaction.copy(accepted = when (scenario) {
                "ka" -> accepted.copy(proofs = accepted.proofs!!.mapIndexed { index, proof ->
                    if (index != 1) proof else proof.copy(keyAttestation = proof.keyAttestation!!.let {
                        it.copy(lifetime = it.lifetime.copy(issuedAt = expiration - 300, expiresAt = expiration))
                    })
                })
                "missing" -> accepted.copy(clientAttestation = null, proofs = null)
                else -> accepted.copy(clientAttestation = accepted.clientAttestation!!.let {
                    it.copy(lifetime = it.lifetime.copy(issuedAt = expiration - 300, expiresAt = expiration))
                })
            })
        }
        val repository = object : IssuanceSessionRepository by backing {
            override suspend fun compareAndSet(expected: IssuanceSession, replacement: IssuanceSession): Boolean {
                val state = replacement.deferredState
                if (scenario == "checkpoint" && !expiredAtCheckpoint && state?.transactions?.values?.any { it.response != null } == true) {
                    expiredAtCheckpoint = true
                    return backing.compareAndSet(expected, replacement.copy(deferredState = state.copy(
                        transactions = state.transactions.mapValues { (_, transaction) -> expire(transaction) },
                    )))
                }
                val saved = backing.compareAndSet(expected, replacement)
                if (saved && scenario == "saved-result" && !interruptedAfterResult && replacement.issuanceResults.isNotEmpty()) {
                    interruptedAfterResult = true
                    throw CancellationException("Interrupted after issuance result was saved")
                }
                return saved
            }
        }
        val module = Issuer2Module(serviceConfig, metadataConfig, ConfigManager.getConfig(), issuanceSessionRepository = repository)
        val authorizationCodes = Issuer2Module::class.java.getDeclaredField("authorizationCodeRepository").let {
            it.isAccessible = true
            it.get(module) as ConfiguredAuthorizationCodeRepository
        }
        application {
            install(ContentNegotiation) { json(issuer2TestJson) }
            installIssuer2AuthenticationForTests()
            configurePlugins()
            routing { module.managementController.register(this); module.openId4VciController.register(this) }
        }
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client)
        val offer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_DEFERRED_OFFER.copy(
            profileId = "identityCredentialSdJwt",
            authMethod = if (authorizationCode) AuthenticationMethod.AUTHORIZED else AuthenticationMethod.PRE_AUTHORIZED,
            issuerStateMode = if (authorizationCode) IssuerStateMode.INCLUDE else null,
            runtimeOverrides = CredentialOfferRuntimeOverrides(notifications = IssuanceNotifications(
                webhook = IssuanceNotifications.WebhookNotification(notificationServer.webhookUrl()),
            )),
        ))
        val resolved = wallet.resolve(offer)
        var instance = JWKKey.generate(KeyType.secp256r1)
        suspend fun token(parameters: Parameters): JsonObject {
            val headers = clientAttestation.attestationAssembler.buildAttestationHeaders(
                instanceKey = instance, clientId = "issuer2-wallet-test", audience = resolved.authorizationServerMetadata.issuer,
            )
            val response = client.submitForm(assertNotNull(resolved.authorizationServerMetadata.tokenEndpoint), parameters) {
                header(ClientAttestationHeaders.CLIENT_ATTESTATION, headers.attestationJwt)
                header(ClientAttestationHeaders.CLIENT_ATTESTATION_POP, headers.popJwt)
            }
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            return response.body()
        }
        val code = UUID.randomUUID().toString()
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        if (authorizationCode) {
            val session = assertNotNull(repository.get(offer.offerId))
            authorizationCodes.save(DefaultAuthorizationCodeRecord(
                code, "issuer2-wallet-test", "https://wallet.example/callback", setOf(configurationId), emptySet(),
                DefaultSession(subject = offer.offerId, expiresAt = mapOf(TokenType.ACCESS_TOKEN to session.expiresAt)), session.expiresAt,
            ))
        }
        val initial = token(Parameters.build {
            append("client_id", "issuer2-wallet-test")
            if (authorizationCode) {
                append("grant_type", "authorization_code"); append("code", code)
                append("redirect_uri", "https://wallet.example/callback")
            } else {
                append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
                append("pre-authorized_code", assertNotNull(resolved.offer.grants?.preAuthorizedCode?.preAuthorizedCode))
            }
        })
        var accessToken = initial.getValue("access_token").jsonPrimitive.content
        if (scenario == "acceptance") {
            // Advance only the original verified evidence; token and fresh refresh authentication remain valid.
            val refreshTokens = Issuer2Module::class.java.getDeclaredField("refreshTokenRepository").let {
                it.isAccessible = true
                it.get(module) as ConfiguredRefreshTokenRepository
            }
            @Suppress("UNCHECKED_CAST")
            val records = ConfiguredRefreshTokenRepository::class.java.getDeclaredField("records").let {
                it.isAccessible = true
                it.get(refreshTokens) as ConfiguredPersistence<Any>
            }
            val signature = initial.getValue("refresh_token").jsonPrimitive.content.substringAfterLast('.')
            val record = Json.parseToJsonElement(records.encoding(assertNotNull(records[signature]))).jsonObject
            val session = record.getValue("session").jsonObject
            val attributes = session.getValue("customAttributes").jsonObject
            val original = Json.decodeFromString<VerifiedClientAttestation>(attributes.getValue("walt_client_attestation").jsonPrimitive.content)
            val expiration = Clock.System.now().epochSeconds - 120
            val expired = original.copy(lifetime = original.lifetime.copy(issuedAt = expiration - 300, expiresAt = expiration))
            val updated = JsonObject(record + ("session" to JsonObject(session + ("customAttributes" to JsonObject(
                attributes + ("walt_client_attestation" to JsonPrimitive(Json.encodeToString(expired))),
            )))))
            records.set(signature, records.decoding(updated.toString()), 5.minutes)
            instance = JWKKey.generate(KeyType.secp256r1)
            accessToken = token(Parameters.build {
                append("grant_type", "refresh_token"); append("client_id", "issuer2-wallet-test")
                append("refresh_token", initial.getValue("refresh_token").jsonPrimitive.content)
            }).getValue("access_token").jsonPrimitive.content
        }
        val nonce = client.post(assertNotNull(resolved.issuerMetadata.nonceEndpoint)).body<JsonObject>().getValue("c_nonce")
        val now = Clock.System.now().epochSeconds
        val proofs = List(3) {
            val holder = JWKKey.generate(KeyType.secp256r1)
            val publicJwk = holder.getPublicKey().exportJWKObject()
            val attestation = attester.signJws(buildJsonObject {
                put("iat", now); put("exp", now + 300); put("nonce", nonce)
                put("attested_keys", JsonArray(listOf(publicJwk)))
            }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("key-attestation+jwt")))
            holder.signJws(buildJsonObject {
                put("iss", "issuer2-wallet-test"); put("aud", resolved.issuerMetadata.credentialIssuer)
                put("iat", now); put("nonce", nonce)
            }.toString().encodeToByteArray(), mapOf(
                "typ" to JsonPrimitive("openid4vci-proof+jwt"), "jwk" to holder.getPublicKey().exportJWKObject(),
                "key_attestation" to JsonPrimitive(attestation),
            ))
        }
        val pending = client.post(resolved.issuerMetadata.credentialEndpoint) {
            bearerAuth(accessToken); contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("credential_configuration_id", configurationId)
                putJsonObject("proofs") { put("jwt", JsonArray(proofs.map(::JsonPrimitive))) }
            })
        }
        if (scenario == "acceptance") {
            assertEquals(HttpStatusCode.BadRequest, pending.status, pending.bodyAsText())
            val error = pending.body<JsonObject>()
            assertEquals("credential_request_denied", error.getValue("error").jsonPrimitive.content)
            assertFalse("transaction_id" in error)
            assertTrue(assertNotNull(repository.get(offer.offerId)?.deferredState).transactions.isEmpty())
            val failure = notificationServer.awaitEvent(offer.offerId, IssuanceSessionEvent.CREDENTIAL_REQUEST_SD_JWT_VC_FAILED)
            assertEquals("credential_request_denied", failure.error)
            assertEquals(1, notificationServer.getReceivedUpdates().count {
                it.target == offer.offerId && it.event.startsWith("credential_request_") && it.event.endsWith("_failed")
            }, "A rejected Credential Request must emit one failure event before returning")
            return@testApplication
        }
        assertEquals(HttpStatusCode.Accepted, pending.status, pending.bodyAsText())
        val transactionId = pending.body<JsonObject>().getValue("transaction_id").jsonPrimitive.content
        val accepted = assertNotNull(repository.get(offer.offerId)?.deferredState).transactions.getValue(transactionId).accepted
        val wia = assertNotNull(accepted.clientAttestation)
        assertEquals("issuer2-wallet-test", wia.subject)
        val publicCoordinates = setOf("kty", "crv", "x", "y")
        assertEquals(instance.getPublicKey().exportJWKObject().filterKeys { it in publicCoordinates },
            wia.confirmationJwk.filterKeys { it in publicCoordinates })
        assertEquals(true, accepted.clientAttestationRequired)
        assertEquals(3, accepted.bindings.size)
        assertEquals(List(3) { now + 300 }, accepted.proofs!!.map { it.keyAttestation!!.lifetime.expiresAt })
        if (scenario !in setOf("valid", "checkpoint", "encryption", "saved-result")) {
            val session = assertNotNull(repository.get(offer.offerId))
            val state = assertNotNull(session.deferredState)
            assertTrue(repository.compareAndSet(session, session.copy(deferredState = state.copy(
                version = state.version + 1, transactions = state.transactions.mapValues { (_, tx) -> expire(tx) },
            ))))
        }
        val beforeRefresh = assertNotNull(repository.get(offer.offerId)?.deferredState).transactions.getValue(transactionId).accepted
        instance = JWKKey.generate(KeyType.secp256r1)
        val refreshed = token(Parameters.build {
            append("grant_type", "refresh_token"); append("client_id", "issuer2-wallet-test")
            append("refresh_token", initial.getValue("refresh_token").jsonPrimitive.content)
        }).getValue("access_token").jsonPrimitive.content
        assertEquals(beforeRefresh, assertNotNull(repository.get(offer.offerId)?.deferredState).transactions.getValue(transactionId).accepted, "Refresh must preserve the accepted transaction evidence")
        assertEquals(HttpStatusCode.NoContent, client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete").status)
        val walletEncryptionKey = if (scenario == "encryption") JWKKey.generate(KeyType.secp256r1) else null
        suspend fun poll(validEncryption: Boolean = true): HttpResponse {
            val walletEncryptionJwk = walletEncryptionKey?.getPublicKey()?.exportJWKObject()
            val payload = buildJsonObject {
                put("transaction_id", transactionId)
                if (walletEncryptionJwk != null) putJsonObject("credential_response_encryption") {
                    // The off-curve point passes request parsing but fails key agreement after signing.
                    val coordinates = if (validEncryption) emptyMap() else mapOf(
                        "x" to JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
                        "y" to JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
                    )
                    put("jwk", JsonObject(walletEncryptionJwk + coordinates + mapOf(
                        "alg" to JsonPrimitive("ECDH-ES"), "use" to JsonPrimitive("enc"),
                    )))
                    put("enc", "A128GCM")
                }
            }
            val jwe = encryptionKey?.let { JweUtils.toJWE(payload, it.getPublicKey().exportJWK(), "ECDH-ES", "A128GCM",
                headerParams = mapOf("kid" to JsonPrimitive(it.getKeyId()))) }
            return client.post(assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)) {
                bearerAuth(refreshed)
                contentType(if (jwe == null) ContentType.Application.Json else ContentType.parse("application/jwt"))
                if (jwe == null) setBody(payload) else setBody(jwe)
            }
        }
        if (scenario == "encryption") {
            val failed = poll(validEncryption = false)
            assertEquals(HttpStatusCode.BadRequest, failed.status, failed.bodyAsText())
            assertEquals("invalid_encryption_parameters", failed.body<JsonObject>().getValue("error").jsonPrimitive.content)
            val checkpoint = assertNotNull(repository.get(offer.offerId)?.deferredState).transactions.getValue(transactionId)
            assertNotNull(checkpoint.response)
            assertFalse(checkpoint.denied)
            assertFalse(checkpoint.consumed)
            assertTrue(assertNotNull(repository.get(offer.offerId)).issuanceResults.isEmpty())
            val retried = poll()
            assertEquals(HttpStatusCode.OK, retried.status, retried.bodyAsText())
            val payload = JweUtils.parseJWE(retried.bodyAsText(), assertNotNull(walletEncryptionKey).exportJWK()).payload
            assertEquals(Json.encodeToJsonElement(checkpoint.response!!.credentials), payload.getValue("credentials"))
            val completed = assertNotNull(repository.get(offer.offerId))
            assertEquals(1, completed.issuanceResults.size)
            assertEquals(checkpoint.response, completed.deferredState!!.transactions.getValue(transactionId).response)
            assertTrue(completed.deferredState!!.transactions.getValue(transactionId).consumed)
            return@testApplication
        }
        var savedResults = emptyMap<String, IssuanceResult>()
        if (scenario == "saved-result") {
            assertTrue(poll().status != HttpStatusCode.OK)
            assertTrue(interruptedAfterResult)
            val stored = assertNotNull(repository.get(offer.offerId))
            savedResults = stored.issuanceResults
            assertTrue(savedResults.isNotEmpty())
            val state = assertNotNull(stored.deferredState)
            assertNotNull(state.transactions.getValue(transactionId).response)
            assertTrue(repository.compareAndSet(stored, stored.copy(deferredState = state.copy(
                version = state.version + 1, transactions = state.transactions.mapValues { (_, transaction) -> expire(transaction) },
            ))))
        }
        if (scenario == "valid") {
            val issued = poll()
            assertEquals(HttpStatusCode.OK, issued.status, issued.bodyAsText())
            assertEquals(3, issued.body<JsonObject>().getValue("credentials").jsonArray.size)
        } else {
            repeat(2) {
                val denied = poll()
                assertEquals(HttpStatusCode.BadRequest, denied.status, denied.bodyAsText())
                assertEquals("credential_request_denied", denied.body<JsonObject>().getValue("error").jsonPrimitive.content)
            }
            val session = assertNotNull(repository.get(offer.offerId))
            assertEquals(savedResults, session.issuanceResults)
            val transaction = assertNotNull(session.deferredState).transactions.getValue(transactionId)
            assertTrue(transaction.denied)
            assertFalse(transaction.consumed)
            if (scenario in setOf("checkpoint", "saved-result")) assertNotNull(transaction.response) else assertNull(transaction.response)
            notificationServer.awaitEvent(offer.offerId, IssuanceSessionEvent.CREDENTIAL_REQUEST_SD_JWT_VC_FAILED)
            assertEquals(1, notificationServer.getReceivedUpdates().count {
                it.target == offer.offerId && it.event == IssuanceSessionEvent.CREDENTIAL_REQUEST_SD_JWT_VC_FAILED.value &&
                    it.error == "credential_request_denied"
            }, "A permanently denied transaction must emit one failure event across repeated polls")
        }
    }
}
