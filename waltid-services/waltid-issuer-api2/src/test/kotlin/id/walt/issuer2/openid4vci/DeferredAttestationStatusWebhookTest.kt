package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.*
import id.walt.issuer2.configurePlugins
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.issuer2.notifications.IssuanceNotifications
import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.issuer2.repository.*
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.testsupport.*
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.attestation.AttestationStatusPolicy
import id.walt.openid4vci.clientauth.*
import id.walt.openid4vci.clientauth.attestation.verifier.*
import id.walt.openid4vci.metadata.issuer.BatchCredentialIssuance
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.proofs.attestation.*
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.statuslist.codec.TokenStatusListCodec
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.test.*
import kotlin.time.Clock

class DeferredAttestationStatusWebhookTest {
    private val notificationServer = Issuer2TestNotificationServer()

    @AfterEach fun clear() {
        notificationServer.stopServer()
        clearIssuer2TestEnvironment()
    }

    @Test fun preAuthorizedInvalidWiaBeforeAcceptanceNotifies() = verify(false, "wia", "acceptance")
    @Test fun authorizationCodeInvalidWiaBeforeAcceptanceNotifies() = verify(true, "wia", "acceptance")
    @Test fun preAuthorizedInvalidThirdKaBeforeAcceptanceNotifies() = verify(false, "ka", "acceptance")
    @Test fun authorizationCodeInvalidThirdKaBeforeAcceptanceNotifies() = verify(true, "ka", "acceptance")
    @Test fun preAuthorizedRevokedWiaNotifiesOnceAcrossPolls() = verify(false, "wia", "collection")
    @Test fun authorizationCodeRevokedWiaNotifiesOnceAcrossPolls() = verify(true, "wia", "collection")
    @Test fun preAuthorizedRevokedThirdKaNotifiesOnceAcrossPolls() = verify(false, "ka", "collection")
    @Test fun authorizationCodeRevokedThirdKaNotifiesOnceAcrossPolls() = verify(true, "ka", "collection")
    @Test fun revocationAfterSigningNotifiesOnceAcrossPolls() = verify(false, "ka", "checkpoint")
    @Test fun unavailableLookupRetriesWithoutPermanentDenialNotification() = verify(false, "wia", "unavailable")
    @Test fun suspendedAttestationRetriesWithoutPermanentDenialNotification() = verify(false, "wia", "suspended")
    @Test fun authorizationCodeLookupFailureReturnsPendingAndCanRetry() = verify(true, "wia", "unavailable")
    @Test fun authorizationCodeSuspensionReturnsPendingAndCanRetry() = verify(true, "wia", "suspended")

    private fun assertDenialNotification(offerId: String) {
        val failure = notificationServer.awaitEvent(offerId, IssuanceSessionEvent.CREDENTIAL_REQUEST_SD_JWT_VC_FAILED)
        assertEquals("credential_request_denied", failure.error)
        assertFalse(failure.requestId.isNullOrBlank())
        assertFalse(failure.errorDescription.isNullOrBlank())
        val updates = notificationServer.getReceivedUpdates().filter { it.target == offerId }
        assertEquals(1, updates.count { it.event == IssuanceSessionEvent.CREDENTIAL_REQUEST_SD_JWT_VC_FAILED.value })
        assertFalse(updates.any { it.event == IssuanceSessionEvent.CREDENTIAL_REQUEST_SD_JWT_VC_SUCCEEDED.value })
    }

    private fun verify(authorizationCode: Boolean, kind: String, stage: String) = testApplication {
        loadIssuer2ConfigFiles()
        notificationServer.startServer()
        val wiaAttester = JWKKey.generate(KeyType.secp256r1)
        val kaAttester = JWKKey.generate(KeyType.secp256r1)
        val publisher = StatusPublisher(wiaAttester, kaAttester)
        publisher.start()
        try {
            val policy = AttestationStatusPolicy(requireStatusReference = true)
            val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>().copy(
                clientAuthenticationConfig = ClientAuthenticationConfig(listOf(ClientAuthenticationMethodConfig.ClientAttestation(
                    ClientAttestationVerifierConfig(ClientAttestationVerificationMethod.StaticJwk(wiaAttester.getPublicKey().exportJWKObject()), statusPolicy = policy),
                ))),
                batchCredentialIssuance = BatchCredentialIssuance(3),
                keyAttestationConfig = KeyAttestationConfig(KeyAttestationVerificationMethod.StaticJwk(kaAttester.getPublicKey().exportJWKObject()), statusPolicy = policy),
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
            var revokedAtCheckpoint = false
            val repository = object : IssuanceSessionRepository by backing {
                override suspend fun compareAndSet(expected: IssuanceSession, replacement: IssuanceSession): Boolean {
                    val saved = backing.compareAndSet(expected, replacement)
                    if (saved && stage == "checkpoint" && !revokedAtCheckpoint && replacement.deferredState?.transactions?.values?.any { it.response != null } == true) {
                        revokedAtCheckpoint = true
                        publisher.states.set(3, 1)
                    }
                    return saved
                }
            }
            val module = Issuer2Module(serviceConfig, metadataConfig, ConfigManager.getConfig(), issuanceSessionRepository = repository)
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
            val configurationId = resolved.offer.credentialConfigurationIds.single()
            val instance = JWKKey.generate(KeyType.secp256r1)
            val instanceJwk = instance.getPublicKey().exportJWKObject()
            val now = Clock.System.now().epochSeconds
            val clientId = "issuer2-wallet-test"
            val wia = wiaAttester.signJws(buildJsonObject {
                put("sub", clientId); put("iat", now); put("exp", now + 300)
                put("status", publisher.reference("wia", 0))
                putJsonObject("cnf") { put("jwk", instanceJwk) }
            }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("oauth-client-attestation+jwt")))
            val pop = instance.signJws(buildJsonObject {
                put("aud", resolved.authorizationServerMetadata.issuer); put("iat", now); put("jti", UUID.randomUUID().toString())
            }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("oauth-client-attestation-pop+jwt")))
            val code = UUID.randomUUID().toString()
            if (authorizationCode) {
                val session = assertNotNull(repository.get(offer.offerId))
                val codes = Issuer2Module::class.java.getDeclaredField("authorizationCodeRepository").let {
                    it.isAccessible = true
                    it.get(module) as ConfiguredAuthorizationCodeRepository
                }
                codes.save(DefaultAuthorizationCodeRecord(
                    code, clientId, "https://wallet.example/callback", setOf(configurationId), emptySet(),
                    DefaultSession(subject = offer.offerId, expiresAt = mapOf(TokenType.ACCESS_TOKEN to session.expiresAt)), session.expiresAt,
                ))
            }
            val tokenResponse = client.submitForm(assertNotNull(resolved.authorizationServerMetadata.tokenEndpoint), Parameters.build {
                append("client_id", clientId)
                if (authorizationCode) {
                    append("grant_type", "authorization_code"); append("code", code)
                    append("redirect_uri", "https://wallet.example/callback")
                } else {
                    append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
                    append("pre-authorized_code", assertNotNull(resolved.offer.grants?.preAuthorizedCode?.preAuthorizedCode))
                }
            }) {
                header("OAuth-Client-Attestation", wia); header("OAuth-Client-Attestation-PoP", pop)
            }
            assertEquals(HttpStatusCode.OK, tokenResponse.status, tokenResponse.bodyAsText())
            val accessToken = tokenResponse.body<JsonObject>().getValue("access_token").jsonPrimitive.content
            val nonce = client.post(assertNotNull(resolved.issuerMetadata.nonceEndpoint)).body<JsonObject>().getValue("c_nonce")
            val proofs = List(3) { index ->
                val holder = JWKKey.generate(KeyType.secp256r1)
                val jwk = holder.getPublicKey().exportJWKObject()
                val ka = kaAttester.signJws(buildJsonObject {
                    put("iat", now); put("exp", now + 300); put("nonce", nonce)
                    put("status", publisher.reference("ka", index))
                    put("attested_keys", JsonArray(listOf(jwk)))
                }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("key-attestation+jwt")))
                holder.signJws(buildJsonObject {
                    put("iss", clientId); put("aud", resolved.issuerMetadata.credentialIssuer); put("iat", now); put("nonce", nonce)
                }.toString().encodeToByteArray(), mapOf(
                    "typ" to JsonPrimitive("openid4vci-proof+jwt"), "jwk" to jwk, "key_attestation" to JsonPrimitive(ka),
                ))
            }
            val revokedIndex = if (kind == "wia") 0 else 3
            if (stage == "acceptance") publisher.states.set(revokedIndex, 1)
            val pending = client.post(resolved.issuerMetadata.credentialEndpoint) {
                bearerAuth(accessToken); contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("credential_configuration_id", configurationId)
                    putJsonObject("proofs") { put("jwt", JsonArray(proofs.map(::JsonPrimitive))) }
                })
            }
            if (stage == "acceptance") {
                assertEquals(HttpStatusCode.BadRequest, pending.status, pending.bodyAsText())
                val error = pending.body<JsonObject>()
                assertEquals("credential_request_denied", error.getValue("error").jsonPrimitive.content)
                assertFalse("transaction_id" in error)
                val session = assertNotNull(repository.get(offer.offerId))
                assertTrue(assertNotNull(session.deferredState).transactions.isEmpty())
                assertTrue(session.issuanceResults.isEmpty())
                assertDenialNotification(offer.offerId)
                return@testApplication
            }
            assertEquals(HttpStatusCode.Accepted, pending.status, pending.bodyAsText())
            val transactionId = pending.body<JsonObject>().getValue("transaction_id").jsonPrimitive.content
            assertEquals(HttpStatusCode.NoContent, client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete").status)
            when (stage) {
                "collection" -> publisher.states.set(revokedIndex, 1)
                "unavailable" -> publisher.unavailable.set(true)
                "suspended" -> publisher.states.set(revokedIndex, 2)
            }
            suspend fun poll() = client.post(assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)) {
                bearerAuth(accessToken); contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("transaction_id", transactionId) })
            }
            if (stage in setOf("unavailable", "suspended")) {
                val blocked = poll()
                assertEquals(HttpStatusCode.Accepted, blocked.status, blocked.bodyAsText())
                val pendingBody = blocked.body<JsonObject>()
                assertEquals(transactionId, pendingBody.getValue("transaction_id").jsonPrimitive.content)
                assertTrue(pendingBody.getValue("interval").jsonPrimitive.long > 0)
                assertFalse("credentials" in pendingBody)
                assertFalse("error" in pendingBody)
                assertFalse(notificationServer.getReceivedUpdates().any {
                    it.target == offer.offerId && it.event.startsWith("credential_request_")
                })
                val stored = assertNotNull(repository.get(offer.offerId))
                assertTrue(stored.issuanceResults.isEmpty())
                assertFalse(stored.deferredState!!.transactions.getValue(transactionId).denied)
                publisher.unavailable.set(false); publisher.states.set(revokedIndex, 0)
                val retried = poll()
                assertEquals(HttpStatusCode.OK, retried.status, retried.bodyAsText())
                assertEquals(3, retried.body<JsonObject>().getValue("credentials").jsonArray.size)
                notificationServer.awaitEvent(offer.offerId, IssuanceSessionEvent.CREDENTIAL_REQUEST_SD_JWT_VC_SUCCEEDED)
                return@testApplication
            }
            repeat(2) {
                val denied = poll()
                assertEquals(HttpStatusCode.BadRequest, denied.status, denied.bodyAsText())
                assertEquals("credential_request_denied", denied.body<JsonObject>().getValue("error").jsonPrimitive.content)
            }
            val stored = assertNotNull(repository.get(offer.offerId))
            assertTrue(stored.issuanceResults.isEmpty())
            assertTrue(stored.deferredState!!.transactions.getValue(transactionId).denied)
            if (stage == "checkpoint") assertNotNull(stored.deferredState!!.transactions.getValue(transactionId).response)
            assertDenialNotification(offer.offerId)
        } finally { publisher.close() }
    }

    private class StatusPublisher(private val wia: Key, private val ka: Key) {
        val states = AtomicIntegerArray(4)
        val unavailable = AtomicBoolean()
        private lateinit var base: String
        private val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                get("/{kind}") {
                    if (unavailable.get()) {
                        call.respondText("Unavailable", status = HttpStatusCode.ServiceUnavailable)
                    } else {
                        val kind = call.parameters["kind"]!!
                        val entries = if (kind == "wia") byteArrayOf(states.get(0).toByte()) else ByteArray(3) { states.get(it + 1).toByte() }
                        val now = Clock.System.now().epochSeconds
                        val jwt = (if (kind == "wia") wia else ka).signJws(buildJsonObject {
                            put("sub", "$base/$kind"); put("iat", now); put("exp", now + 300); put("ttl", 60)
                            putJsonObject("status_list") { put("bits", 8); put("lst", TokenStatusListCodec.encode(entries)) }
                        }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("statuslist+jwt")))
                        call.respondText(jwt, ContentType.parse("application/statuslist+jwt"))
                    }
                }
            }
        }
        suspend fun start() {
            server.start(wait = false)
            base = "http://127.0.0.1:${server.engine.resolvedConnectors().single().port}"
        }
        fun reference(kind: String, index: Int) = buildJsonObject {
            putJsonObject("status_list") { put("uri", "$base/$kind"); put("idx", index) }
        }
        fun close() { server.stop(0, 1000) }
    }
}
