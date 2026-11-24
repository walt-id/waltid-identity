package id.walt.openid4vci.attestation

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.Key
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationException
import id.walt.openid4vci.proofs.attestation.KeyAttestationConfig
import id.walt.openid4vci.proofs.attestation.KeyAttestationServiceException
import id.walt.openid4vci.proofs.attestation.KeyAttestationTrustResolver
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationMethod
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationOptions
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerifier
import id.walt.openid4vci.proofs.attestation.VerifiedKeyAttestation
import id.walt.openid4vci.proofs.attestation.toVerificationOptions
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock

class KeyAttestationTrustRegistryTest {
    private val now = Clock.System.now()
    private val types = setOf(TrustedEntityType.WALLET_PROVIDER)

    @Test
    fun `registry trust accepts the chain and verifies with the leaf key`() = runTest {
        val pki = TestAttesterPki.create()
        val evaluator = RecordingTrustEvaluator { registryDecision() }

        verify(attestation(pki), method(), evaluator)

        val request = evaluator.requests.single()
        assertEquals(TrustPurpose.ISSUER_WUA_SIGNER, request.purpose)
        assertEquals(TrustSubject.CertificateChain(pki.chainBase64), request.subject)
        assertEquals(types, request.expectedEntityTypes)
    }

    @Test
    fun `registry denials and stale or unauthenticated sources are invalid proofs`() = runTest {
        val jwt = attestation(TestAttesterPki.create())
        val denials = listOf(
            false to TrustDecision(TrustDecisionCode.NOT_TRUSTED),
            false to registryDecision(freshness = FreshnessState.STALE),
            false to registryDecision(entityType = TrustedEntityType.OTHER),
            true to registryDecision(authenticity = AuthenticityState.INTEGRITY_VERIFIED),
        )
        for ((requireAuthenticated, decision) in denials) {
            val error = assertFailsWith<CredentialProofValidationException>("$decision") {
                verify(jwt, method(requireAuthenticated)) { decision }
            }
            assertEquals("Key attestation certificate chain is not trusted", error.message)
        }
    }

    @Test
    fun `an x5c that is not one leaf-first chain is rejected before the registry is asked`() = runTest {
        val pki = TestAttesterPki.create()
        val evaluator = RecordingTrustEvaluator { registryDecision() }
        val reversed = attestation(pki, x5c = JsonArray(pki.x5c.reversed()))

        assertFailsWith<CredentialProofValidationException> { verify(reversed, method(), evaluator) }
        assertTrue(evaluator.requests.isEmpty())
    }

    @Test
    fun `an invalid signature is an invalid proof before the registry is asked`() = runTest {
        val forged = attestation(TestAttesterPki.create(), signer = generateTestP256Key("not-the-leaf"))
        // Even an unavailable registry must not turn a forged attestation into a service error.
        for (answer in listOf(registryDecision(), unavailableDecision)) {
            val evaluator = RecordingTrustEvaluator { answer }
            val error = assertFailsWith<CredentialProofValidationException> { verify(forged, method(), evaluator) }
            assertEquals(CredentialErrorCodes.INVALID_PROOF, error.errorCode)
            assertTrue(evaluator.requests.isEmpty())
        }
    }

    @Test
    fun `a wrapped registry resolver still asks the registry`() = runTest {
        val evaluator = RecordingTrustEvaluator { TrustDecision(TrustDecisionCode.NOT_TRUSTED) }
        val options = KeyAttestationConfig(method()).toVerificationOptions(trustEvaluator = evaluator)
        val wrapped = options.copy(trustResolver = KeyAttestationTrustResolver { header, context, configuration ->
            options.trustResolver.resolve(header, context, configuration)
        })

        assertFailsWith<CredentialProofValidationException> {
            verify(attestation(TestAttesterPki.create()), wrapped)
        }
        assertEquals(1, evaluator.requests.size)
    }

    @Test
    fun `unavailable sources and missing or failing evaluators are service errors`() = runTest {
        val jwt = attestation(TestAttesterPki.create())
        assertFailsWith<KeyAttestationServiceException> { verify(jwt, method()) { unavailableDecision } }
        // Options build without an evaluator: Enterprise links the registry after the issuer is created.
        val missing = assertFailsWith<KeyAttestationServiceException> { verify(jwt, method(), evaluator = null) }
        assertEquals("Key attestation trust registry is not configured", missing.message)

        val failure = RuntimeException("registry store failed")
        val wrapped = assertFailsWith<KeyAttestationServiceException> { verify(jwt, method()) { throw failure } }
        assertSame(failure, wrapped.cause)
        assertFailsWith<CancellationException> { verify(jwt, method()) { throw CancellationException("cancelled") } }
    }

    @Test
    fun `x509-trust-registry config round trips and requires entity types`() {
        val method = KeyAttestationVerificationMethod.X509TrustRegistry(
            setOf(TrustedEntityType.WALLET_PROVIDER, TrustedEntityType.ATTESTATION_PROVIDER),
            requireAuthenticatedSource = true,
        )
        val encoded = Json.encodeToString(KeyAttestationVerificationMethod.serializer(), method)
        assertEquals(method, Json.decodeFromString(KeyAttestationVerificationMethod.serializer(), encoded))
        assertFailsWith<IllegalArgumentException> { KeyAttestationVerificationMethod.X509TrustRegistry(emptySet()) }
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString(
                KeyAttestationVerificationMethod.serializer(),
                """{"type":"x509-trust-registry","expectedEntityTypes":[]}""",
            )
        }
    }

    private fun method(requireAuthenticatedSource: Boolean = false) =
        KeyAttestationVerificationMethod.X509TrustRegistry(types, requireAuthenticatedSource)

    private suspend fun verify(
        jwt: String,
        method: KeyAttestationVerificationMethod,
        evaluator: TrustDecisionEvaluator?,
    ): VerifiedKeyAttestation = verify(jwt, KeyAttestationConfig(method).toVerificationOptions(trustEvaluator = evaluator))

    private suspend fun verify(jwt: String, options: KeyAttestationVerificationOptions): VerifiedKeyAttestation =
        KeyAttestationVerifier(now = { now }).verify(
            jwt,
            null,
            CredentialProofValidationContext("https://issuer.example"),
            CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "identity"),
            options,
        )

    private suspend fun attestation(
        pki: TestAttesterPki,
        x5c: JsonArray = pki.x5c,
        signer: Key = pki.leafKey,
    ): String {
        val holder: Key = generateTestP256Key("holder")
        return CompactJws.sign(
            payload = buildJsonObject {
                put("iat", now.epochSeconds)
                put("exp", now.epochSeconds + 600)
                put("attested_keys", JsonArray(listOf(holder.exportPublicJwkObject())))
            }.toString().encodeToByteArray(),
            key = signer,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", "key-attestation+jwt")
                put("x5c", x5c)
            },
        )
    }
}
