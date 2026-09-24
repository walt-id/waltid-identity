package id.walt.wallet2.handlers

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.handlers.SignProofTestSupport.CONFIG_ID
import id.walt.wallet2.handlers.SignProofTestSupport.ISSUER
import id.walt.wallet2.handlers.SignProofTestSupport.issuerMetadataClient
import io.ktor.http.Url
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

class KeyAttestationProofTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `required attestation is signed and bound to proof key and nonce`() = runTest {
        val attester = newKey("attester")
        val wallet = walletWithProofKey().attachKeyAttestationProvider(TestProvider(attester))
        val proof = sign(wallet)
        val attestation = assertNotNull(CompactJws.decodeUnverified(proof).protectedHeader["key_attestation"])
            .jsonPrimitive.content
        val verified = CompactJws.verify(attestation, attester, JwsAlgorithm.ES256)
        val claims = Json.parseToJsonElement(verified.payload.decodeToString()) as JsonObject
        assertEquals("nonce", claims["nonce"]?.jsonPrimitive?.content)
        assertEquals("key-attestation+jwt", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
    }

    @Test
    fun `required attestation fails without provider`() = runTest {
        assertFailsWith<IllegalArgumentException> { sign(walletWithProofKey()) }
    }

    @Test
    fun `attestation for another key is rejected before proof is sent`() = runTest {
        val wrongKey = newKey("other")
        val wallet = walletWithProofKey().attachKeyAttestationProvider(TestProvider(newKey("attester"), payload = {
            request -> claims(request, attestedKey = wrongKey)
        }))
        val error = assertFailsWith<IllegalArgumentException> { sign(wallet) }
        assertTrue(error.message.orEmpty().contains("does not contain the credential proof key"))
    }

    @Test
    fun `attestation with wrong nonce is rejected`() = runTest {
        val wallet = walletWithProofKey().attachKeyAttestationProvider(TestProvider(newKey("attester"), payload = {
            request -> claims(request, nonce = "different")
        }))
        val error = assertFailsWith<IllegalArgumentException> { sign(wallet) }
        assertTrue(error.message.orEmpty().contains("nonce does not match"))
    }

    @Test
    fun `expired attestation is rejected`() = runTest {
        val wallet = walletWithProofKey().attachKeyAttestationProvider(TestProvider(newKey("attester"), payload = {
            request -> claims(request, expiresAt = Clock.System.now().toEpochMilliseconds() / 1000 - 1)
        }))
        val error = assertFailsWith<IllegalArgumentException> { sign(wallet) }
        assertTrue(error.message.orEmpty().contains("not currently valid"))
    }

    @Test
    fun `attestation signed by another provider is rejected`() = runTest {
        val wallet = walletWithProofKey().attachKeyAttestationProvider(
            TestProvider(newKey("expected-attester"), signingKey = newKey("other-attester"))
        )
        assertFailsWith<IllegalArgumentException> { sign(wallet) }
    }

    @Test
    fun `numeric nonce is rejected even when its text matches`() = runTest {
        val request = attestationRequest()
        val provider = TestProvider(newKey("attester"), payload = {
            JsonObject(claims(it) + ("nonce" to JsonPrimitive(123)))
        })
        assertFailsWith<IllegalArgumentException> { provider.validatedAttestation(request) }
    }

    @Test
    fun `every attested key must be a complete public JWK`() = runTest {
        val request = attestationRequest()
        val provider = TestProvider(newKey("attester"), payload = {
            JsonObject(claims(it) + ("attested_keys" to JsonArray(listOf(
                Json.parseToJsonElement(request.proofKey.data.toByteArray().decodeToString()),
                buildJsonObject { put("kty", "EC") },
            ))))
        })
        assertFailsWith<IllegalArgumentException> { provider.validatedAttestation(request) }
    }

    @Test
    fun `present constraints are validated even when issuer did not require them`() = runTest {
        val request = attestationRequest()
        val provider = TestProvider(newKey("attester"), payload = {
            JsonObject(claims(it) + ("key_storage" to JsonPrimitive("malformed")))
        })
        assertFailsWith<IllegalArgumentException> { provider.validatedAttestation(request) }
    }

    @Test
    fun `all constraint entries are validated before matching an accepted value`() = runTest {
        val request = attestationRequest(KeyAttestationsRequired(keyStorage = setOf("iso_18045")))
        val provider = TestProvider(newKey("attester"), payload = {
            JsonObject(claims(it) + ("key_storage" to JsonArray(listOf(
                JsonPrimitive("iso_18045"), JsonPrimitive(123),
            ))))
        })
        assertFailsWith<IllegalArgumentException> { provider.validatedAttestation(request) }
    }

    @Test
    fun `declared constraints must satisfy issuer requirements`() = runTest {
        val request = attestationRequest(KeyAttestationsRequired(
            keyStorage = setOf("secure_element"), userAuthentication = setOf("biometric"),
        ))
        val provider = TestProvider(newKey("attester"), payload = {
            JsonObject(claims(it) + mapOf(
                "key_storage" to JsonArray(listOf(JsonPrimitive("secure_element"))),
                "user_authentication" to JsonArray(listOf(JsonPrimitive("biometric"))),
            ))
        })
        provider.validatedAttestation(request)
        val unsatisfied = TestProvider(newKey("other-attester"), payload = {
            JsonObject(claims(it) + mapOf(
                "key_storage" to JsonArray(listOf(JsonPrimitive("secure_element"))),
                "user_authentication" to JsonArray(listOf(JsonPrimitive("pin"))),
            ))
        })
        assertFailsWith<IllegalArgumentException> { unsatisfied.validatedAttestation(request) }
    }

    @Test
    fun `protected header JWK cannot contain private material or be a non-object`() = runTest {
        val request = attestationRequest()
        val attester = newKey("attester")
        val privateJwk = JsonObject(Json.parseToJsonElement(
            attester.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(attester.spec)
                .data.toByteArray().decodeToString(),
        ).jsonObject + ("d" to JsonPrimitive("private-material")))
        listOf(privateJwk, JsonPrimitive("invalid")).forEach { jwk ->
            val provider = TestProvider(attester, headerJwk = jwk)
            assertFailsWith<IllegalArgumentException> { provider.validatedAttestation(request) }
        }
    }

    @Test
    fun `attestation algorithm must be accepted by the issuer`() = runTest {
        val request = attestationRequest()
        val provider = TestProvider(newKey("attester"))
        assertFailsWith<IllegalArgumentException> {
            provider.validatedAttestation(request, setOf("ES384"))
        }
        provider.validatedAttestation(request, setOf("ES256"))
    }

    @Test
    fun `pre-authorized issuance retries with an attestation for each fresh nonce and actual proof key`() = runTest {
        val proofKey = newKey("proof")
        val requests = mutableListOf<KeyAttestationRequest>()
        val proofs = mutableListOf<String>()
        var nonceCalls = 0
        val provider = TestProvider(newKey("attester"), payload = { request ->
            requests += request
            claims(request)
        })
        val wallet = walletWithProofKey(proofKey).attachKeyAttestationProvider(provider)
        val client = issuanceClient(
            nonce = { if (++nonceCalls == 1) "stale" else "fresh" },
            credential = { proof ->
                proofs += proof
                if (proofs.size == 1) HttpStatusCode.BadRequest to """{"error":"invalid_nonce"}"""
                else HttpStatusCode.Accepted to """{"transaction_id":"deferred","interval":5}"""
            },
        )
        WalletIssuanceHandler.receiveCredential(
            wallet, ReceiveCredentialRequest(offerJson = preAuthorizedOffer()), httpClient = client,
        )
        assertEquals(listOf("stale", "fresh"), requests.map { it.nonce })
        assertEquals(2, proofs.size)
        assertEquals(2, nonceCalls)
        for ((index, proof) in proofs.withIndex()) {
            val verifiedProof = CompactJws.verify(proof, proofKey, JwsAlgorithm.ES256)
            val attestation = verifiedProof.protectedHeader.getValue("key_attestation").jsonPrimitive.content
            val verifiedAttestation = CompactJws.verify(attestation, provider.verificationKey, JwsAlgorithm.ES256)
            val proofNonce = Json.parseToJsonElement(verifiedProof.payload.decodeToString())
                .jsonObject["nonce"]?.jsonPrimitive?.content
            val attestationNonce = Json.parseToJsonElement(verifiedAttestation.payload.decodeToString())
                .jsonObject["nonce"]?.jsonPrimitive?.content
            assertEquals(requests[index].nonce, attestationNonce)
            assertEquals(proofNonce, attestationNonce)
            assertEquals(Jwk.sha256Thumbprint(proofKey.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(proofKey.spec)),
                Jwk.sha256Thumbprint(requests[index].proofKey))
        }
    }

    @Test
    fun `authorization-code issuance sends a proof with the validated attestation`() = runTest {
        val proofKey = newKey("proof")
        val wallet = walletWithProofKey(proofKey).attachKeyAttestationProvider(TestProvider(newKey("attester")))
        val proofs = mutableListOf<String>()
        val client = issuanceClient(
            nonce = { "auth-nonce" },
            credential = { proof ->
                proofs += proof
                HttpStatusCode.OK to """{"credentials":[{"credential":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential"],"issuer":"did:example:issuer","credentialSubject":{"id":"did:example:holder"}}}]}"""
            },
        )
        WalletIssuanceHandler.receiveCredentialAuthCode(
            wallet,
            ReceiveAuthorizedCredentialRequest(
                code = "auth-code", credentialIssuer = ISSUER,
                credentialEndpoint = Url("$ISSUER/credential"), credentialConfigurationId = CONFIG_ID,
                nonceEndpoint = Url("$ISSUER/nonce"),
            ),
            httpClient = client,
        )
        assertEquals(1, proofs.size)
        assertNotNull(CompactJws.decodeUnverified(proofs.single()).protectedHeader["key_attestation"])
    }

    @Test
    fun `session issuance also sends the validated attestation`() = runTest {
        val wallet = walletWithProofKey().attachKeyAttestationProvider(TestProvider(newKey("attester")))
        val proofs = mutableListOf<String>()
        val client = issuanceClient(
            nonce = { "session-nonce" },
            credential = { proof ->
                proofs += proof
                HttpStatusCode.Accepted to """{"transaction_id":"deferred","interval":5}"""
            },
        )
        val service = WalletIssuanceSessionService(wallet, httpClient = client)
        val session = service.start(WalletIssuanceSessionRequest(
            offerJson = preAuthorizedOffer(), clientId = "wallet-client", redirectUri = Url("openid://"),
        ))
        val outcome = service.continuePreAuthorized(session.id)
        assertEquals(1, proofs.size, outcome.toString())
        assertIs<WalletIssuanceOutcome.Deferred>(outcome, outcome.toString())
        assertNotNull(CompactJws.decodeUnverified(proofs.single()).protectedHeader["key_attestation"])
    }

    @Test
    fun `provider is not invoked when issuer has no attestation requirement`() = runTest {
        val unusedAttester = newKey("unused-attester")
        val wallet = walletWithProofKey().attachKeyAttestationProvider(object : KeyAttestationProvider {
            override val verificationKey = unusedAttester
            override suspend fun attest(request: KeyAttestationRequest): String = error("Unexpected attestation")
        })
        val proof = WalletIssuanceHandler.signProof(
            wallet = wallet,
            request = SignProofRequest(Url(ISSUER), CONFIG_ID, "nonce"),
            httpClient = issuerMetadataClient(),
        ).proofJwt
        assertTrue("key_attestation" !in CompactJws.decodeUnverified(proof).protectedHeader)
    }

    private suspend fun sign(wallet: Wallet): String = WalletIssuanceHandler.signProof(
        wallet = wallet,
        request = SignProofRequest(Url(ISSUER), CONFIG_ID, "nonce"),
        httpClient = issuerMetadataClient(requiresKeyAttestation = true),
    ).proofJwt

    private suspend fun walletWithProofKey(key: Crypto2Key? = null): Wallet = Wallet(
        id = "wallet",
        keyStores = listOf(InMemoryKeyStore().also { it.addCrypto2Key(key ?: newKey("proof")) }),
        credentialStores = listOf(InMemoryCredentialStore()),
    )

    private fun preAuthorizedOffer() = Json.parseToJsonElement("""
        {"credential_issuer":"$ISSUER","credential_configuration_ids":["$CONFIG_ID"],
         "grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code"}}}
    """.trimIndent()).jsonObject

    private fun issuanceClient(
        nonce: () -> String,
        credential: (String) -> Pair<HttpStatusCode, String>,
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler { request ->
            val (status, body) = when (request.url.toString()) {
                "$ISSUER/.well-known/openid-credential-issuer" -> HttpStatusCode.OK to """
                    {"credential_issuer":"$ISSUER","credential_endpoint":"$ISSUER/credential",
                     "deferred_credential_endpoint":"$ISSUER/deferred",
                     "nonce_endpoint":"$ISSUER/nonce","credential_configurations_supported":{
                       "$CONFIG_ID":{"format":"jwt_vc_json",
                         "credential_definition":{"type":["VerifiableCredential","TestCredential"]},
                         "cryptographic_binding_methods_supported":["jwk"],
                         "proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"],
                           "key_attestations_required":{}}}}}}
                """.trimIndent()
                "$ISSUER/.well-known/oauth-authorization-server" -> HttpStatusCode.OK to """
                    {"issuer":"$ISSUER","authorization_endpoint":"$ISSUER/authorize",
                     "token_endpoint":"$ISSUER/token","response_types_supported":["code"],
                     "grant_types_supported":["authorization_code","urn:ietf:params:oauth:grant-type:pre-authorized_code"]}
                """.trimIndent()
                "$ISSUER/token" -> HttpStatusCode.OK to """{"access_token":"access","token_type":"Bearer"}"""
                "$ISSUER/nonce" -> HttpStatusCode.OK to """{"c_nonce":"${nonce()}"}"""
                "$ISSUER/credential" -> {
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    val proof = Json.parseToJsonElement(body).jsonObject.getValue("proofs")
                        .jsonObject.getValue("jwt").jsonArray.single().jsonPrimitive.content
                    credential(proof)
                }
                else -> error("Unexpected issuance request: ${request.url}")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        } }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun newKey(id: String): Crypto2Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private suspend fun attestationRequest(requirements: KeyAttestationsRequired = KeyAttestationsRequired()): KeyAttestationRequest {
        val key = newKey("proof")
        return KeyAttestationRequest(
            ISSUER, key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec),
            "123", requirements,
        )
    }

    private inner class TestProvider(
        override val verificationKey: Crypto2Key,
        private val payload: suspend (KeyAttestationRequest) -> JsonObject = { claims(it) },
        private val signingKey: Crypto2Key = verificationKey,
        private val headerJwk: kotlinx.serialization.json.JsonElement? = null,
    ) : KeyAttestationProvider {
        override suspend fun attest(request: KeyAttestationRequest): String {
            val attesterJwk = verificationKey.capabilities.publicKeyExporter!!.exportPublicKey()
                .toPublicJwk(verificationKey.spec)
            return CompactJws.sign(
                payload = payload(request).toString().encodeToByteArray(),
                key = signingKey,
                algorithm = JwsAlgorithm.ES256,
                protectedHeader = buildJsonObject {
                    put("typ", "key-attestation+jwt")
                    put("jwk", headerJwk ?: Json.parseToJsonElement(attesterJwk.data.toByteArray().decodeToString()))
                },
            )
        }
    }

    private suspend fun claims(
        request: KeyAttestationRequest,
        attestedKey: Crypto2Key? = null,
        nonce: String? = request.nonce,
        expiresAt: Long = Clock.System.now().toEpochMilliseconds() / 1000 + 300,
    ): JsonObject {
        val now = Clock.System.now().toEpochMilliseconds() / 1000
        val jwk = attestedKey?.let { key ->
            key.capabilities.publicKeyExporter?.exportPublicKey()?.toPublicJwk(key.spec)
        }
            ?: request.proofKey
        return buildJsonObject {
            put("iat", now)
            put("exp", expiresAt)
            nonce?.let { put("nonce", it) }
            put("attested_keys", Json.parseToJsonElement("[${jwk.data.toByteArray().decodeToString()}]"))
        }
    }
}
