package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.Key
import id.walt.openid4vci.attestation.RecordingTrustEvaluator
import id.walt.openid4vci.attestation.TestAttesterPki
import id.walt.openid4vci.attestation.generateTestP256Key
import id.walt.openid4vci.attestation.registryDecision
import id.walt.openid4vci.attestation.unavailableDecision
import id.walt.openid4vci.clientauth.ClientAuthenticationConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationContext
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationMethodConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationResult
import id.walt.openid4vci.clientauth.ClientAuthenticationService
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import id.walt.openid4vci.clientauth.attestation.ClientAttestationJwtTypes
import id.walt.trust.decision.TrustDecisionEvaluator
import id.walt.trust.decision.TrustPurpose
import id.walt.trust.decision.TrustSubject
import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.FreshnessState
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.io.encoding.Base64
import kotlin.time.Clock

class X509TrustRegistryClientAttestationVerifierTest {
    private val types = setOf(TrustedEntityType.WALLET_PROVIDER, TrustedEntityType.ATTESTATION_PROVIDER)

    @Test
    fun `registry trust accepts the chain and keeps the leaf as status signer`() = runTest {
        val pki = TestAttesterPki.create()
        val evaluator = RecordingTrustEvaluator { registryDecision() }

        val result = verify(X509TrustRegistryClientAttestationVerifier(evaluator, types), attestation(pki))

        val verified = assertIs<ClientAttestationVerificationResult.VerifiedWithSigner>(result)
        assertEquals(pki.leafKey.exportPublicJwkObject(), verified.publicJwk)
        val request = evaluator.requests.single()
        assertEquals(TrustPurpose.ISSUER_WUA_SIGNER, request.purpose)
        assertEquals(TrustSubject.CertificateChain(pki.chainBase64), request.subject)
        assertEquals(types, request.expectedEntityTypes)
    }

    @Test
    fun `registry denials and stale or unauthenticated sources are rejected`() = runTest {
        val pki = TestAttesterPki.create()
        val jwt = attestation(pki)
        val denials = listOf(
            false to TrustDecision(TrustDecisionCode.NOT_TRUSTED),
            false to registryDecision(freshness = FreshnessState.STALE),
            false to registryDecision(entityType = TrustedEntityType.OTHER),
            true to registryDecision(authenticity = AuthenticityState.INTEGRITY_VERIFIED),
        )
        for ((requireAuthenticated, decision) in denials) {
            val verifier = X509TrustRegistryClientAttestationVerifier({ decision }, types, requireAuthenticated)
            val rejected = assertIs<ClientAttestationVerificationResult.Rejected>(verify(verifier, jwt), "$decision")
            assertEquals("Client attestation signer is not trusted", rejected.reason)
        }
        val unauthenticated = registryDecision(authenticity = AuthenticityState.INTEGRITY_VERIFIED)
        assertIs<ClientAttestationVerificationResult.VerifiedWithSigner>(
            verify(X509TrustRegistryClientAttestationVerifier({ unauthenticated }, types), jwt),
        )
    }

    @Test
    fun `invalid signature is rejected before the registry is asked`() = runTest {
        val pki = TestAttesterPki.create()
        val evaluator = RecordingTrustEvaluator { registryDecision() }

        val result = verify(
            X509TrustRegistryClientAttestationVerifier(evaluator, types),
            attestation(pki, signer = generateTestP256Key("not-the-leaf")),
        )

        assertEquals("Client attestation signature is invalid", assertIs<ClientAttestationVerificationResult.Rejected>(result).reason)
        assertTrue(evaluator.requests.isEmpty())
    }

    @Test
    fun `an x5c that is not one leaf-first chain is rejected before the registry is asked`() = runTest {
        val pki = TestAttesterPki.create()
        val evaluator = RecordingTrustEvaluator { registryDecision() }
        val leaf = pki.x5c.first()
        val ca = pki.x5c.last()
        val unrelatedRoot = TestAttesterPki.create().x5c.last()
        // The leaf comes first in each, so the leaf signature alone would accept them.
        val malformed = listOf(
            JsonArray(listOf(leaf, ca, ca)),
            JsonArray(listOf(leaf, ca, unrelatedRoot)),
        )

        for (x5c in malformed) {
            val result = verify(X509TrustRegistryClientAttestationVerifier(evaluator, types), attestation(pki, x5c = x5c))
            assertEquals(
                "Client attestation x5c must contain one leaf-first certificate chain",
                assertIs<ClientAttestationVerificationResult.Rejected>(result, "$x5c").reason,
            )
        }
        assertTrue(evaluator.requests.isEmpty())
    }

    @Test
    fun `unavailable sources and missing or failing evaluators are server errors`() = runTest {
        val jwt = attestation(TestAttesterPki.create())
        assertFailsWith<ClientAttestationServiceException> {
            verify(X509TrustRegistryClientAttestationVerifier({ unavailableDecision }, types), jwt)
        }
        assertFailsWith<ClientAttestationServiceException> {
            verify(X509TrustRegistryClientAttestationVerifier(null, types), jwt)
        }
        val failure = RuntimeException("registry store failed")
        assertSame(failure, assertFailsWith<ClientAttestationServiceException> {
            verify(X509TrustRegistryClientAttestationVerifier({ throw failure }, types), jwt)
        }.cause)
        assertFailsWith<CancellationException> {
            verify(X509TrustRegistryClientAttestationVerifier({ throw CancellationException("cancelled") }, types), jwt)
        }
    }

    @Test
    fun `configured method authenticates with the injected evaluator and never reports an outage as invalid_client`() = runTest {
        val pki = TestAttesterPki.create()
        val holder = generateTestP256Key("client-instance")
        val wia = attestation(pki, holder = holder)
        suspend fun authenticate(evaluator: TrustDecisionEvaluator) = authenticate(evaluator, wia, proofOfPossession(holder))

        val authenticated = assertIs<ClientAuthenticationResult.Authenticated>(authenticate { registryDecision() })
        assertEquals(CLIENT_ID, authenticated.client.id)
        val denied = assertIs<ClientAuthenticationResult.Failure>(authenticate { TrustDecision(TrustDecisionCode.NOT_TRUSTED) })
        assertEquals("invalid_client", denied.error.error)
        val outages = listOf(
            TrustDecisionEvaluator { unavailableDecision },
            TrustDecisionEvaluator { throw RuntimeException("registry store failed") },
        )
        for (outage in outages) {
            assertEquals("server_error", assertIs<ClientAuthenticationResult.Failure>(authenticate(outage)).error.error)
        }
    }

    @Test
    fun `a kid that is not a string is invalid_client and never reaches the registry`() = runTest {
        val pki = TestAttesterPki.create()
        val holder = generateTestP256Key("client-instance")
        val evaluator = RecordingTrustEvaluator { registryDecision() }
        val malformed = listOf(buildJsonObject { put("kid", "nested") }, JsonArray(listOf(JsonPrimitive("a"))), JsonPrimitive(7))

        for (kid in malformed) {
            val failure = assertIs<ClientAuthenticationResult.Failure>(
                authenticate(evaluator, attestation(pki, holder = holder, kid = kid), proofOfPossession(holder)), "$kid",
            )
            assertEquals("invalid_client", failure.error.error, "$kid")
            assertEquals("Client attestation kid header must be a string", failure.error.description, "$kid")
        }
        assertTrue(evaluator.requests.isEmpty())
        assertIs<ClientAuthenticationResult.Authenticated>(
            authenticate(evaluator, attestation(pki, holder = holder, kid = JsonPrimitive("wallet-key-1")), proofOfPossession(holder)),
        )
    }

    @Test
    fun `x5c verifiers verify with the parsed leaf and ignore kid`() = runTest {
        val pki = TestAttesterPki.create()
        val jwt = attestation(pki, kid = buildJsonObject { put("kid", "nested") })
        val ca = X509CertificateUtil.parseCertificateDerEncoded(ByteString(Base64.decode(pki.chainBase64.last())))
        val verifiers = listOf(
            X509TrustRegistryClientAttestationVerifier({ registryDecision() }, types),
            X509ChainClientAttestationVerifier(InMemoryTrustStore(listOf(ca))),
        )
        for (verifier in verifiers) {
            val verified = assertIs<ClientAttestationVerificationResult.VerifiedWithSigner>(verify(verifier, jwt), "$verifier")
            assertEquals(pki.leafKey.exportPublicJwkObject(), verified.publicJwk)
        }
    }

    @Test
    fun `a confirmation key member that is not a primitive is invalid_client`() = runTest {
        val pki = TestAttesterPki.create()
        val holder = generateTestP256Key("client-instance")
        val holderJwk = holder.exportPublicJwkObject()
        val malformed = listOf(
            JsonObject(holderJwk + ("kty" to buildJsonObject { put("kty", "EC") })),
            JsonObject(holderJwk + ("crv" to JsonArray(listOf(JsonPrimitive("P-256"))))),
            JsonObject(holderJwk + ("alg" to buildJsonObject { put("alg", "ES256") })),
        )
        for (confirmationKey in malformed) {
            val failure = assertIs<ClientAuthenticationResult.Failure>(
                authenticate(
                    RecordingTrustEvaluator { registryDecision() },
                    attestation(pki, holder = holder, confirmationKey = confirmationKey),
                    proofOfPossession(holder),
                ),
                "$confirmationKey",
            )
            assertEquals("invalid_client", failure.error.error, "$confirmationKey")
        }
    }

    @Test
    fun `x509-trust-registry config round trips and requires entity types`() {
        val method = ClientAttestationVerificationMethod.X509TrustRegistry(types, requireAuthenticatedSource = true)
        val encoded = Json.encodeToString(ClientAttestationVerificationMethod.serializer(), method)
        assertEquals(method, Json.decodeFromString(ClientAttestationVerificationMethod.serializer(), encoded))
        assertEquals(
            ClientAttestationVerificationMethod.X509TrustRegistry(setOf(TrustedEntityType.WALLET_PROVIDER)),
            Json.decodeFromString(
                ClientAttestationVerificationMethod.serializer(),
                """{"type":"x509-trust-registry","expectedEntityTypes":["WALLET_PROVIDER"]}""",
            ),
        )
        assertFailsWith<IllegalArgumentException> { ClientAttestationVerificationMethod.X509TrustRegistry(emptySet()) }
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString(
                ClientAttestationVerificationMethod.serializer(),
                """{"type":"x509-trust-registry","expectedEntityTypes":[]}""",
            )
        }
    }

    private suspend fun verify(verifier: ClientAttestationVerifier, jwt: String): ClientAttestationVerificationResult {
        val decoded = CompactJws.decodeUnverified(jwt)
        return verifier.verifyAttestationJwt(
            jwt = jwt,
            header = decoded.protectedHeader,
            payload = Json.parseToJsonElement(decoded.payload.decodeToString()).jsonObject,
        )
    }

    private suspend fun authenticate(evaluator: TrustDecisionEvaluator, attestationJwt: String, popJwt: String) =
        ClientAuthenticationService(
            ClientAuthenticationConfig(
                listOf(
                    ClientAuthenticationMethodConfig.ClientAttestation(
                        ClientAttestationVerifierConfig(ClientAttestationVerificationMethod.X509TrustRegistry(types)),
                    ),
                ),
            ).toClientAuthenticationServiceConfig(trustEvaluator = evaluator),
        ).authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(attestationJwt),
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(popJwt),
            ),
            context = ClientAuthenticationContext(authorizationServerIssuer = ISSUER),
        )

    private suspend fun attestation(
        pki: TestAttesterPki,
        signer: Key = pki.leafKey,
        holder: Key? = null,
        x5c: JsonArray = pki.x5c,
        kid: JsonElement? = null,
        confirmationKey: JsonObject? = null,
    ): String {
        val now = Clock.System.now().epochSeconds
        val cnfJwk: JsonObject = confirmationKey ?: (holder ?: generateTestP256Key("client-instance")).exportPublicJwkObject()
        return CompactJws.sign(
            payload = buildJsonObject {
                put("sub", CLIENT_ID)
                put("iat", now)
                put("exp", now + 300)
                putJsonObject("cnf") { put("jwk", cnfJwk) }
            }.toString().encodeToByteArray(),
            key = signer,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", ClientAttestationJwtTypes.CLIENT_ATTESTATION)
                put("x5c", x5c)
                kid?.let { put("kid", it) }
            },
        )
    }

    private suspend fun proofOfPossession(holder: Key): String = CompactJws.sign(
        payload = buildJsonObject {
            put("aud", ISSUER)
            put("iat", Clock.System.now().epochSeconds)
            put("jti", "pop-1")
        }.toString().encodeToByteArray(),
        key = holder,
        algorithm = JwsAlgorithm.ES256,
        protectedHeader = buildJsonObject { put("typ", ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP) },
    )

    private companion object {
        const val CLIENT_ID = "wallet-client"
        const val ISSUER = "https://issuer.example/openid4vci"
    }
}
