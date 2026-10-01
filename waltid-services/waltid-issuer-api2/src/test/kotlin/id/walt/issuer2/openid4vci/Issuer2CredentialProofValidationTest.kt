package id.walt.issuer2.openid4vci

import id.walt.openid4vci.proofs.attestation.KeyAttestationConfig
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationMethod
import id.walt.sdjwt.SDJwtVC
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.testsupport.Issuer2CredentialScenario
import id.walt.issuer2.service.openid4vci.CredentialProofKeyAcceptance
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2TxCodeMode
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.ResolvedCredentialOffer
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createWalletFlowCredentialOffer
import id.walt.issuer2.testsupport.credentialRequest
import id.walt.issuer2.testsupport.getSession
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.clientauth.ClientAuthenticationConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationMethodConfig
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.proofs.Proofs
import id.walt.openid4vci.proofs.ProofType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.io.encoding.Base64

class Issuer2CredentialProofValidationTest {

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun `validates proof before key acceptance and signing`() = testApplication {
        val acceptanceCalls = AtomicInteger()
        installIssuer2WithConfigFiles(
            credentialProofKeyAcceptance = CredentialProofKeyAcceptance { _, _ ->
                acceptanceCalls.incrementAndGet()
                true
            }
        )
        val flow = prepareFlow(apiClient())
        val key = JWKKey.generate(KeyType.secp256r1)
        val now = Clock.System.now().epochSeconds

        suspend fun assertRejected(
            proofs: Proofs,
            expectedError: String = CredentialErrorCodes.INVALID_PROOF,
        ) {
            assertRejectedCredentialRequest(flow.request(proofs), expectedError)
            assertEquals(0, acceptanceCalls.get())
            val session = flow.client.getSession(flow.sessionId)
            assertEquals(IssuanceSessionStatus.ACTIVE, session.status)
            assertFalse(session.isClosed)
        }

        assertRejected(proof(key, nonce = flow.nonce(), audience = JsonPrimitive("https://wrong.example")))
        for (audience in listOf(
            JsonArray(listOf(JsonPrimitive(flow.resolvedOffer.issuerMetadata.credentialIssuer))),
            JsonArray(listOf(JsonPrimitive("https://other.example"), JsonPrimitive(flow.resolvedOffer.issuerMetadata.credentialIssuer))),
        )) {
            assertRejected(proof(key, nonce = flow.nonce(), audience = audience))
        }
        assertRejected(proof(key, nonce = flow.nonce(), issuedAt = now - 10.minutes.inWholeSeconds))
        assertRejected(proof(key, nonce = flow.nonce(), issuedAt = now + 2.minutes.inWholeSeconds))
        assertRejected(proof(key, nonce = null), CredentialErrorCodes.INVALID_NONCE)
        assertRejected(proof(key, nonce = UUID.randomUUID().toString()), CredentialErrorCodes.INVALID_NONCE)
        assertRejected(proof(key, nonce = flow.nonce(), type = "JWT"))
        assertRejected(Proofs())
        assertRejected(Proofs(jwt = listOf("not-a-jwt")))
        assertRejected(proof(key, nonce = flow.nonce(), kid = "did:example:unrelated#key-1"))
        val holderDid = DidJwkRegistrar().registerByKey(key, DidJwkCreateOptions(KeyType.secp256r1)).did
        assertRejected(
            withConflictingMalformedJwk(proof(
                key = key,
                nonce = flow.nonce(),
                kid = "$holderDid#0",
                includeJwk = false,
            ), key)
        )
        val reusableNonce = flow.nonce()
        assertRejected(
            proof(
                key = key,
                signingKey = JWKKey.generate(KeyType.secp256r1),
                nonce = reusableNonce,
            )
        )

        val validProof = proof(
            key = key,
            nonce = reusableNonce,
            audience = JsonPrimitive(flow.resolvedOffer.issuerMetadata.credentialIssuer),
        ).jwt.orEmpty().single()
        val response = flow.request(Proofs(jwt = listOf(validProof)))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, acceptanceCalls.get())
    }

    /**
     * Nonces stay valid until they expire, so a rejected proof key can be retried with the same
     * proof. Every attempt must reach key acceptance again and must leave the session issuable.
     */
    @Test
    fun `retrying a rejected proof key keeps the session issuable`() = testApplication {
        val acceptanceCalls = AtomicInteger()
        installIssuer2WithConfigFiles(
            credentialProofKeyAcceptance = CredentialProofKeyAcceptance { _, _ ->
                acceptanceCalls.incrementAndGet()
                false
            }
        )
        val flow = prepareFlow(apiClient())
        val proofs = proof(
            key = JWKKey.generate(KeyType.secp256r1),
            nonce = flow.nonce(),
            audience = JsonPrimitive(flow.resolvedOffer.issuerMetadata.credentialIssuer),
        )

        assertRejectedCredentialRequest(flow.request(proofs))
        assertEquals(1, acceptanceCalls.get())

        assertRejectedCredentialRequest(flow.request(proofs))
        assertEquals(2, acceptanceCalls.get())
        val session = flow.client.getSession(flow.sessionId)
        assertEquals(IssuanceSessionStatus.ACTIVE, session.status)
        assertFalse(session.isClosed)
    }

    @Test
    fun `attested bindings all reach acceptance and produce corresponding credentials`() = testApplication {
        val attester = JWKKey.generate(KeyType.secp256r1)
        val holders = List(3) { JWKKey.generate(KeyType.secp256r1) }
        var acceptedKeys: List<JsonObject>? = null
        var acceptBindings = false
        val attesterJwk = attester.getPublicKey().exportJWKObject()
        installIssuer2WithConfigFiles(
            credentialProofKeyAcceptance = CredentialProofKeyAcceptance { _, keys -> acceptedKeys = keys; acceptBindings },
            configureServiceConfig = { it.copy(keyAttestationConfig = KeyAttestationConfig(
                KeyAttestationVerificationMethod.StaticJwk(attesterJwk),
            )) },
        )
        val flow = prepareFlow(apiClient())
        val nonce = flow.nonce()
        val now = Clock.System.now().epochSeconds
        val inner = attester.signJws(buildJsonObject {
            put("iat", now)
            put("exp", now + 300)
            put("nonce", nonce)
            put("attested_keys", JsonArray(holders.map { it.getPublicKey().exportJWKObject() }))
        }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("key-attestation+jwt")))
        val outer = holders[0].signJws(buildJsonObject {
            put("aud", flow.resolvedOffer.issuerMetadata.credentialIssuer)
            put("iat", now)
            put("nonce", nonce)
        }.toString().encodeToByteArray(), mapOf(
            "typ" to JsonPrimitive("openid4vci-proof+jwt"),
            "jwk" to holders[0].getPublicKey().exportJWKObject(),
            "key_attestation" to JsonPrimitive(inner),
        ))
        val expected = holders.map { it.getThumbprint() }
        assertRejectedCredentialRequest(flow.request(Proofs(jwt = listOf(outer))))
        assertEquals(expected, requireNotNull(acceptedKeys).map { JWKKey.importJWK(it.toString()).getOrThrow().getThumbprint() })
        assertEquals(IssuanceSessionStatus.ACTIVE, flow.client.getSession(flow.sessionId).status)
        acceptBindings = true
        val response = flow.request(Proofs(jwt = listOf(outer)))
        assertEquals(HttpStatusCode.OK, response.status)
        val credentials = response.body<JsonObject>().getValue("credentials").jsonArray
        assertEquals(expected, credentials.map {
            val jwt = it.jsonObject.getValue("credential").jsonPrimitive.content
            JWKKey.importJWK(requireNotNull(SDJwtVC.parse(jwt).holderKeyJWK).toString()).getOrThrow().getThumbprint()
        })
    }

    @Test
    fun `standalone W3C attestation verifies holder DIDs and permits retry after a mismatched key`() = testApplication {
        val attester = JWKKey.generate(KeyType.secp256r1)
        val attesterJwk = attester.getPublicKey().exportJWKObject()
        installIssuer2WithConfigFiles(configureServiceConfig = { it.copy(
            keyAttestationConfig = KeyAttestationConfig(KeyAttestationVerificationMethod.StaticJwk(attesterJwk)),
            clientAuthenticationConfig = ClientAuthenticationConfig(listOf(ClientAuthenticationMethodConfig.PreAuthAnonymous)),
        ) })
        val flow = prepareFlow(apiClient(), Issuer2CredentialScenarios.openBadgeCredential)
        val holders = List(2) { JWKKey.generate(KeyType.secp256r1) }
        val dids = holders.map { DidJwkRegistrar().registerByKey(it, DidJwkCreateOptions(KeyType.secp256r1)).did }
        val nonce = flow.nonce()
        for (valid in listOf(false, true)) {
            val jwt = attester.signJws(buildJsonObject {
                put("iat", Clock.System.now().epochSeconds)
                put("nonce", nonce)
                put("attested_keys", JsonArray(holders.mapIndexed { index, holder ->
                    val did = if (!valid && index == 1) dids[0] else dids[index]
                    JsonObject(holder.getPublicKey().exportJWKObject() + ("kid" to JsonPrimitive("$did#0")))
                }))
            }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("key-attestation+jwt")))
            val example = Issuer2RequestExamples.W3C_CREDENTIAL_REQUEST_WITH_ATTESTATION_PROOF
            val response = flow.client.post(flow.resolvedOffer.issuerMetadata.credentialEndpoint) {
                bearerAuth(flow.accessToken)
                contentType(ContentType.Application.Json)
                setBody(JsonObject(example + ("proofs" to buildJsonObject {
                    put(ProofType.ATTESTATION.value, JsonArray(listOf(JsonPrimitive(jwt))))
                })))
            }
            if (!valid) {
                assertRejectedCredentialRequest(response)
                assertEquals(IssuanceSessionStatus.ACTIVE, flow.client.getSession(flow.sessionId).status)
            } else {
                assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
                val credentials = response.body<JsonObject>().getValue("credentials").jsonArray
                assertEquals(dids, credentials.map {
                    val encoded = it.jsonObject.getValue("credential").jsonPrimitive.content.split('.')[1]
                    kotlinx.serialization.json.Json.parseToJsonElement(Base64.UrlSafe.decode(encoded).decodeToString())
                        .jsonObject.getValue("sub").jsonPrimitive.content
                })
            }
        }
    }

    private suspend fun prepareFlow(
        client: HttpClient,
        scenario: Issuer2CredentialScenario = Issuer2CredentialScenarios.identitySdJwt,
    ): ProofTestFlow {
        val walletFlow = Issuer2WalletFlowDriver(client)
        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = scenario,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        return ProofTestFlow(
            client = client,
            resolvedOffer = resolvedOffer,
            accessToken = tokenResponse.access_token,
            sessionId = createdOffer.offerId,
        )
    }

    private suspend fun proof(
        key: Key,
        nonce: String?,
        signingKey: Key = key,
        audience: JsonElement = JsonPrimitive("http://localhost/openid4vci"),
        issuedAt: Long = Clock.System.now().epochSeconds,
        type: String = "openid4vci-proof+jwt",
        kid: String? = null,
        includeJwk: Boolean = true,
    ): Proofs {
        val payload = buildJsonObject {
            put("aud", audience)
            put("iat", issuedAt)
            nonce?.let { put("nonce", it) }
        }
        val header = buildJsonObject {
            put("typ", type)
            put("alg", key.keyType.jwsAlg)
            if (includeJwk) put("jwk", key.getPublicKey().exportJWKObject())
            kid?.let { put("kid", it) }
        }
        return Proofs(jwt = listOf(signingKey.signJws(payload.toString().encodeToByteArray(), header)))
    }

    private suspend fun assertRejectedCredentialRequest(
        response: HttpResponse,
        expectedError: String = CredentialErrorCodes.INVALID_PROOF,
    ) {
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(expectedError, response.body<JsonObject>()["error"]?.jsonPrimitive?.content)
    }

    private fun withConflictingMalformedJwk(proofs: Proofs, key: Key): Proofs {
        val parts = proofs.jwt.orEmpty().single().split('.')
        val header = buildJsonObject {
            put("typ", "openid4vci-proof+jwt")
            put("alg", key.keyType.jwsAlg)
            put("kid", "did:example:holder#key-1")
            put("jwk", "invalid")
        }
        val encodedHeader = Base64.UrlSafe.encode(header.toString().encodeToByteArray()).trimEnd('=')
        return Proofs(jwt = listOf("$encodedHeader.${parts[1]}.${parts[2]}"))
    }
}

private data class ProofTestFlow(
    val client: HttpClient,
    val resolvedOffer: ResolvedCredentialOffer,
    val accessToken: String,
    val sessionId: String,
) {
    suspend fun nonce(): String =
        client.post(requireNotNull(resolvedOffer.issuerMetadata.nonceEndpoint))
            .body<JsonObject>()["c_nonce"]?.jsonPrimitive?.content
            ?: error("Nonce endpoint returned no c_nonce")

    suspend fun request(proofs: Proofs): HttpResponse =
        client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }
}
