package id.walt.issuer2.openid4vci

import id.walt.openid4vci.proofs.attestation.KeyAttestationConfig
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationMethod
import id.walt.sdjwt.SDJwtVC
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JweUtils
import id.walt.issuer2.domain.IssuanceSessionStatus
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
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.prooftypes.Proofs
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.io.encoding.Base64

class Issuer2CredentialProofValidationTest {

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun `encrypted credential response prohibits caching through the HTTP route`() = testApplication {
        val issuerKey = JWKKey.generate(KeyType.secp256r1)
        val walletKey = JWKKey.generate(KeyType.secp256r1)
        val serializedIssuerKey = KeySerialization.serializeKey(issuerKey)
        installIssuer2WithConfigFiles(configureServiceConfig = {
            it.copy(credentialEncryptionKey = serializedIssuerKey)
        })
        val flow = prepareFlow(apiClient())
        val proofs = proof(walletKey, flow.nonce())
        val encryptionJwk = JsonObject(walletKey.getPublicKey().exportJWKObject() + mapOf(
            "alg" to JsonPrimitive(CredentialEncryptionProfile.ALG_ECDH_ES),
            "use" to JsonPrimitive(CredentialEncryptionProfile.KEY_USE_ENC),
        ))
        val payload = JsonObject(credentialRequest(
            flow.resolvedOffer.offer.credentialConfigurationIds.single(), proofs,
        ) + ("credential_response_encryption" to buildJsonObject {
            put("jwk", encryptionJwk)
            put("enc", CredentialEncryptionProfile.ENC_A128GCM)
        }))
        val requestJwe = JweUtils.toJWE(
            payload = payload,
            jwk = issuerKey.getPublicKey().exportJWK(),
            alg = CredentialEncryptionProfile.ALG_ECDH_ES,
            enc = CredentialEncryptionProfile.ENC_A128GCM,
            headerParams = mapOf("kid" to JsonPrimitive(issuerKey.getKeyId())),
        )
        val response = flow.client.post(flow.resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(flow.accessToken)
            contentType(ContentType.parse(CredentialEncryptionProfile.MEDIA_TYPE_JWT))
            setBody(requestJwe)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
        assertEquals(CredentialEncryptionProfile.MEDIA_TYPE_JWT, response.contentType()?.withoutParameters()?.toString())
        val decrypted = JweUtils.parseJWE(response.bodyAsText(), walletKey.exportJWK()).payload
        assertTrue((decrypted["credentials"] as JsonArray).isNotEmpty())
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

        assertRejected(proof(key, nonce = flow.nonce(), audience = listOf("https://wrong.example")))
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
            audience = listOf("https://other.example", flow.resolvedOffer.issuerMetadata.credentialIssuer),
        ).jwt.orEmpty().single()
        val response = flow.request(Proofs(jwt = listOf(validProof)))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
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
            audience = listOf(flow.resolvedOffer.issuerMetadata.credentialIssuer),
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

    private suspend fun prepareFlow(client: HttpClient): ProofTestFlow {
        val walletFlow = Issuer2WalletFlowDriver(client)
        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = Issuer2CredentialScenarios.identitySdJwt,
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
        audience: List<String> = listOf("http://localhost/openid4vci"),
        issuedAt: Long = Clock.System.now().epochSeconds,
        type: String = "openid4vci-proof+jwt",
        kid: String? = null,
        includeJwk: Boolean = true,
    ): Proofs {
        val payload = buildJsonObject {
            val audienceClaim = if (audience.size == 1) {
                JsonPrimitive(audience.single())
            } else {
                JsonArray(audience.map(::JsonPrimitive))
            }
            put("aud", audienceClaim)
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
        assertEquals("no-store", response.headers["Cache-Control"])
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
