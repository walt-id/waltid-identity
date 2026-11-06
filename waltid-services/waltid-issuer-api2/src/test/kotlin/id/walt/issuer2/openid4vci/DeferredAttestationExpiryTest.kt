package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.*
import id.walt.issuer2.configurePlugins
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.repository.*
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.testsupport.*
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import id.walt.openid4vci.deferred.DeferredTransaction
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.proofs.attestation.*
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*
import kotlin.time.Clock

class DeferredAttestationExpiryTest {
    @AfterEach fun clear() = clearIssuer2TestEnvironment()

    @Test fun preAuthorizedExpiredWiaIsDeniedAfterRefresh() = verify(false, "wia")
    @Test fun authorizationCodeExpiredWiaIsDeniedAfterRefresh() = verify(true, "wia")
    @Test fun preAuthorizedOneExpiredKaDeniesThreeKeys() = verify(false, "ka")
    @Test fun authorizationCodeOneExpiredKaDeniesThreeKeys() = verify(true, "ka")
    @Test fun preAuthorizedValidEvidenceIssuesAllThreeKeys() = verify(false, "valid")
    @Test fun authorizationCodeValidEvidenceIssuesAllThreeKeys() = verify(true, "valid")
    @Test fun checkpointCannotBeDeliveredAfterWiaExpiry() = verify(false, "checkpoint")
    @Test fun missingRequiredEvidenceFailsClosed() = verify(false, "missing")

    private fun verify(authorizationCode: Boolean, scenario: String) = testApplication {
        loadIssuer2ConfigFiles()
        val clientAttestation = createIssuer2ClientAttestationTestMaterial()
        val attester = JWKKey.generate(KeyType.secp256r1)
        val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>().copy(
            clientAuthenticationConfig = clientAttestation.clientAuthenticationConfig,
            batchCredentialIssuance = BatchCredentialIssuance(3),
            keyAttestationConfig = KeyAttestationConfig(KeyAttestationVerificationMethod.StaticJwk(attester.getPublicKey().exportJWKObject())),
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
                return backing.compareAndSet(expected, replacement)
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
        val accessToken = initial.getValue("access_token").jsonPrimitive.content
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
        if (scenario !in setOf("valid", "checkpoint")) {
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
        suspend fun poll() = client.post(assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)) {
            bearerAuth(refreshed); contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("transaction_id", transactionId) })
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
            assertTrue(session.issuanceResults.isEmpty())
            val transaction = assertNotNull(session.deferredState).transactions.getValue(transactionId)
            assertTrue(transaction.denied)
            assertFalse(transaction.consumed)
            if (scenario == "checkpoint") assertNotNull(transaction.response) else assertNull(transaction.response)
        }
    }
}
