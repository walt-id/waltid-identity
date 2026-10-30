package id.walt.issuer2.openid4vci

import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.models.*
import id.walt.issuer2.testsupport.*
import id.walt.openid4vci.deferred.DeferredIssuanceConfig
import id.walt.openid4vci.metadata.issuer.BatchCredentialIssuance
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.proofs.Proofs
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class Issuer2DeferredIssuanceTest {
    private suspend fun io.ktor.client.HttpClient.sessionJson(id: String): JsonObject = get("/issuer2/sessions/$id").body()
    @AfterEach fun clear() = clearIssuer2TestEnvironment()

    @Test fun pendingApprovalRefreshedTokenBatchAndConsumption() = testApplication {
        val attestation = createIssuer2ClientAttestationTestMaterial()
        installIssuer2WithConfigFiles(configureServiceConfig = {
            it.copy(clientAuthenticationConfig = attestation.clientAuthenticationConfig, batchCredentialIssuance = BatchCredentialIssuance(3), deferredIssuance = DeferredIssuanceConfig(17))
        })
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client, attestationAssembler = attestation.attestationAssembler)
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE
        val offer = client.createCredentialOffer(example.copy(deferred = true))
        val resolved = wallet.resolve(offer)
        val endpoint = assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)
        val token = wallet.exchangePreAuthorizedCode(resolved, txCode = null)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val firstProof = wallet.buildJwtProofs(resolved.issuerMetadata, configurationId, clientId = "issuer2-wallet-test")
        val secondProof = wallet.buildJwtProofs(resolved.issuerMetadata, configurationId, clientId = "issuer2-wallet-test")
        val response = client.post(resolved.issuerMetadata.credentialEndpoint) {
            bearerAuth(token.access_token); contentType(ContentType.Application.Json)
            setBody(credentialRequest(configurationId, Proofs(jwt = firstProof.jwt.orEmpty() + secondProof.jwt.orEmpty())))
        }
        assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())
        val pending = response.body<JsonObject>()
        val transaction = pending.getValue("transaction_id").jsonPrimitive.content
        assertEquals(17, pending.getValue("interval").jsonPrimitive.int)
        assertNull(pending["credentials"])
        suspend fun poll(accessToken: String = token.access_token, id: String = transaction) = client.post(endpoint) {
            bearerAuth(accessToken); contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("transaction_id", id) })
        }
        assertEquals(HttpStatusCode.Accepted, poll().status)
        val deadline = client.sessionJson(offer.offerId).getValue("expiresAt")
        repeat(2) { assertEquals(HttpStatusCode.NoContent, client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete").status) }
        val approved = client.sessionJson(offer.offerId)
        assertEquals("ACTIVE", approved["status"]?.jsonPrimitive?.content ?: "ACTIVE")
        assertEquals("READY", approved.getValue("deferredStatus").jsonPrimitive.content)
        assertNull(approved["deferredState"])
        assertNull(approved["issuedCredentialFormat"])
        assertEquals(HttpStatusCode.Conflict, client.post("/issuer2/credential-offers/${offer.offerId}/deferred/deny").status)
        val refreshed = wallet.refreshAccessToken(resolved, assertNotNull(token.refresh_token))
        assertEquals(deadline, client.sessionJson(offer.offerId).getValue("expiresAt"))
        val polls = kotlinx.coroutines.coroutineScope { (1..4).map { async { poll(refreshed.access_token) } }.awaitAll() }
        val issued = polls.single { it.status == HttpStatusCode.OK }
        assertEquals(2, issued.body<JsonObject>().getValue("credentials").jsonArray.size)
        assertEquals("SUCCESSFUL", client.sessionJson(offer.offerId).getValue("status").jsonPrimitive.content)
        assertEquals("invalid_transaction_id", poll().body<JsonObject>().getValue("error").jsonPrimitive.content)
        assertEquals("invalid_transaction_id", poll(id = "unknown").body<JsonObject>().getValue("error").jsonPrimitive.content)
        assertEquals(HttpStatusCode.NoContent, client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete").status)
        assertEquals(HttpStatusCode.Unauthorized, poll(accessToken = "invalid").status)
    }

    @Test fun encryptedInitialRequestAndDeferredCollectionUseWalletEncryption() = testApplication {
        val issuerKey = id.walt.crypto.keys.jwk.JWKKey.generate(id.walt.crypto.keys.KeyType.secp256r1)
        var walletKey = id.walt.crypto.keys.jwk.JWKKey.generate(id.walt.crypto.keys.KeyType.secp256r1)
        val encryptionKey = id.walt.crypto.keys.KeySerialization.serializeKey(issuerKey)
        installIssuer2WithConfigFiles(configureServiceConfig = { it.copy(credentialEncryptionKey = encryptionKey) })
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client)
        val offer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_DEFERRED_OFFER)
        val resolved = wallet.resolve(offer)
        val token = wallet.exchangePreAuthorizedCode(resolved, null)
        val configuration = resolved.offer.credentialConfigurationIds.single()
        suspend fun encryption() = buildJsonObject {
            put("jwk", JsonObject(walletKey.getPublicKey().exportJWKObject() + mapOf(
                "alg" to JsonPrimitive("ECDH-ES"), "use" to JsonPrimitive("enc"),
            )))
            put("enc", "A128GCM")
        }
        suspend fun encryptedPost(endpoint: String, payload: JsonObject): Pair<HttpStatusCode, JsonObject> {
            val jwe = id.walt.crypto.utils.JweUtils.toJWE(
                JsonObject(payload + ("credential_response_encryption" to encryption())),
                issuerKey.getPublicKey().exportJWK(), "ECDH-ES", "A128GCM",
                headerParams = mapOf("kid" to JsonPrimitive(issuerKey.getKeyId())),
            )
            val response = client.post(endpoint) {
                bearerAuth(token.access_token); contentType(ContentType.parse("application/jwt")); setBody(jwe)
            }
            assertEquals("no-store", response.headers["Cache-Control"])
            assertEquals("application/jwt", response.contentType()?.withoutParameters()?.toString(), response.bodyAsText())
            return response.status to id.walt.crypto.utils.JweUtils.parseJWE(response.bodyAsText(), walletKey.exportJWK()).payload
        }
        val credentialPayload = credentialRequest(configuration, wallet.buildJwtProofs(resolved.issuerMetadata, configuration))
        val pending = encryptedPost(resolved.issuerMetadata.credentialEndpoint, credentialPayload)
        assertEquals(HttpStatusCode.Accepted, pending.first)
        walletKey = id.walt.crypto.keys.jwk.JWKKey.generate(id.walt.crypto.keys.KeyType.secp256r1)
        val retry = encryptedPost(resolved.issuerMetadata.credentialEndpoint, credentialPayload)
        assertEquals(HttpStatusCode.Accepted, retry.first)
        assertEquals(pending.second.getValue("transaction_id"), retry.second.getValue("transaction_id"))
        val poll = buildJsonObject { put("transaction_id", pending.second.getValue("transaction_id")) }
        val endpoint = assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)
        assertEquals(HttpStatusCode.Accepted, encryptedPost(endpoint, poll).first)
        assertEquals(HttpStatusCode.NoContent, client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete").status)
        val issued = encryptedPost(endpoint, poll)
        assertEquals(HttpStatusCode.OK, issued.first)
        assertTrue(issued.second.getValue("credentials").jsonArray.isNotEmpty())
    }

    @Test fun completionBeforeCredentialRequestIssuesImmediately() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client)
        val offer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_DEFERRED_OFFER)
        assertEquals(HttpStatusCode.NoContent, client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete").status)
        val resolved = wallet.resolve(offer)
        val token = wallet.exchangePreAuthorizedCode(resolved, txCode = null)
        val response = wallet.requestCredential(resolved, token.access_token)
        assertNotNull(response["credentials"])
        assertEquals("SUCCESSFUL", client.sessionJson(offer.offerId).getValue("status").jsonPrimitive.content)
    }

    @Test fun denialAndManagementConflicts() = testApplication {
        val attestation = createIssuer2ClientAttestationTestMaterial()
        installIssuer2WithConfigFiles(configureServiceConfig = { it.copy(clientAuthenticationConfig = attestation.clientAuthenticationConfig) })
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client, attestationAssembler = attestation.attestationAssembler)
        val ordinary = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE)
        assertEquals(HttpStatusCode.Conflict, client.post("/issuer2/credential-offers/${ordinary.offerId}/deferred/complete").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/issuer2/credential-offers/missing/deferred/complete").status)
        val offer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_DEFERRED_OFFER)
        val resolved = wallet.resolve(offer)
        val token = wallet.exchangePreAuthorizedCode(resolved, txCode = null)
        val configuration = resolved.offer.credentialConfigurationIds.single()
        // Invalid proof never creates a continuation.
        val invalid = client.post(resolved.issuerMetadata.credentialEndpoint) {
            bearerAuth(token.access_token); contentType(ContentType.Application.Json)
            setBody(credentialRequest(configuration, Proofs(jwt = listOf("invalid"))))
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        val proof = wallet.buildJwtProofs(resolved.issuerMetadata, configuration, clientId = "issuer2-wallet-test")
        val pending = client.post(resolved.issuerMetadata.credentialEndpoint) {
            bearerAuth(token.access_token); contentType(ContentType.Application.Json)
            setBody(credentialRequest(configuration, proof))
        }.body<JsonObject>()
        repeat(2) { assertEquals(HttpStatusCode.NoContent, client.post("/issuer2/credential-offers/${offer.offerId}/deferred/deny").status) }
        assertEquals(HttpStatusCode.Conflict, client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete").status)
        val refreshed = wallet.refreshAccessToken(resolved, assertNotNull(token.refresh_token))
        val denied = client.post(assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)) {
            bearerAuth(refreshed.access_token); contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("transaction_id", pending.getValue("transaction_id")) })
        }
        assertEquals("credential_request_denied", denied.body<JsonObject>().getValue("error").jsonPrimitive.content)
        assertEquals("UNSUCCESSFUL", client.sessionJson(offer.offerId).getValue("status").jsonPrimitive.content)
        assertEquals("DENIED", client.sessionJson(offer.offerId).getValue("deferredStatus").jsonPrimitive.content)
    }

    @Test fun multipleTransactionsRespectRefreshNarrowingAndPartialCollection() = testApplication {
        val attestation = createIssuer2ClientAttestationTestMaterial()
        installIssuer2WithConfigFiles(configureServiceConfig = { it.copy(clientAuthenticationConfig = attestation.clientAuthenticationConfig) })
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client, attestationAssembler = attestation.attestationAssembler)
        val scenarios = listOf(Issuer2CredentialScenarios.identitySdJwt, Issuer2CredentialScenarios.isoMdl)
        val offer = client.createCredentialOffer(MultiCredentialOfferCreateRequest(
            scenarios.map { CredentialOfferCredential(it.profileId) }, AuthenticationMethod.PRE_AUTHORIZED, deferred = true,
        ))
        val resolved = wallet.resolve(offer)
        val token = wallet.exchangePreAuthorizedCode(resolved, null)
        val transactions = scenarios.map { scenario ->
            val proof = wallet.buildJwtProofs(resolved.issuerMetadata, scenario.credentialConfigurationId, clientId = "issuer2-wallet-test")
            val response = client.post(resolved.issuerMetadata.credentialEndpoint) {
                bearerAuth(token.access_token); contentType(ContentType.Application.Json)
                setBody(credentialRequest(scenario.credentialConfigurationId, proof))
            }
            assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())
            response.body<JsonObject>().getValue("transaction_id").jsonPrimitive.content
        }
        val narrowed = wallet.refreshAccessToken(resolved, assertNotNull(token.refresh_token), mapOf(
            "authorization_details" to buildJsonArray { add(buildJsonObject {
                put("type", "openid_credential"); put("credential_configuration_id", scenarios.first().credentialConfigurationId)
            }) }.toString(),
        ))
        suspend fun poll(id: String, accessToken: String) = client.post(assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)) {
            bearerAuth(accessToken); contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("transaction_id", id) })
        }
        assertEquals("invalid_transaction_id", poll(transactions[1], narrowed.access_token).body<JsonObject>().getValue("error").jsonPrimitive.content)
        assertEquals(HttpStatusCode.NoContent, client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete").status)
        assertEquals(HttpStatusCode.OK, poll(transactions[0], narrowed.access_token).status)
        assertEquals(id.walt.issuer2.domain.IssuanceSessionStatus.ACTIVE, client.getSession(offer.offerId).status)
        assertEquals(HttpStatusCode.OK, poll(transactions[1], token.access_token).status)
        assertEquals(id.walt.issuer2.domain.IssuanceSessionStatus.SUCCESSFUL, client.getSession(offer.offerId).status)
    }

    @Test fun deferredAuthorizationCodeOffersKeepOriginalAssociation() {
        val request = CredentialOfferCreateRequest("profile", AuthenticationMethod.AUTHORIZED, deferred = true)
        assertNull(request.issuerStateMode)
        assertFailsWith<IllegalArgumentException> { request.copy(issuerStateMode = IssuerStateMode.OMIT) }
        assertFailsWith<IllegalArgumentException> {
            MultiCredentialOfferCreateRequest(listOf(CredentialOfferCredential("profile")), AuthenticationMethod.AUTHORIZED, issuerStateMode = IssuerStateMode.OMIT, deferred = true)
        }
    }
}
