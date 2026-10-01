package id.walt.openid4vci.proofs

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.*
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.*
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.proofs.attestation.*
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.openid4vci.tokens.jwt.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Instant

/** Exercise metadata decisions with real signed evidence, independently of credential formatting. */
class CredentialProofMetadataTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val issuer = "https://issuer.example"
    private val optional = ProofTypeMetadata(setOf("ES256"))
    private val required = ProofTypeMetadata(setOf("ES256"), KeyAttestationsRequired())
    private enum class RequestKind { NO_PROOF, JWT, NESTED_ATTESTATION, STANDALONE_ATTESTATION }

    @Test
    fun `JWT metadata without attestation requirements accepts plain and nested proofs`() = runTest {
        checkConfiguration(mapOf(ProofType.JWT.value to optional), setOf(RequestKind.JWT, RequestKind.NESTED_ATTESTATION))
    }

    @Test
    fun `JWT metadata with empty attestation requirements requires nested evidence only`() = runTest {
        checkConfiguration(mapOf(ProofType.JWT.value to required), setOf(RequestKind.NESTED_ATTESTATION))
    }

    @Test
    fun `attestation metadata accepts exactly one standalone attestation`() = runTest {
        checkConfiguration(mapOf(ProofType.ATTESTATION.value to optional), setOf(RequestKind.STANDALONE_ATTESTATION))
    }

    @Test
    fun `advertising both types accepts either carrier but never mixed carriers`() = runTest {
        checkConfiguration(mapOf(ProofType.JWT.value to optional, ProofType.ATTESTATION.value to optional),
            setOf(RequestKind.JWT, RequestKind.NESTED_ATTESTATION, RequestKind.STANDALONE_ATTESTATION))
    }

    @Test
    fun `absent proof metadata permits omission and validates optional supplied evidence`() = runTest {
        checkConfiguration(null, RequestKind.entries.toSet())
    }

    @Test
    fun `attestation requirements belong to the selected proof type`() = runTest {
        checkConfiguration(mapOf(ProofType.JWT.value to required, ProofType.ATTESTATION.value to optional),
            setOf(RequestKind.NESTED_ATTESTATION, RequestKind.STANDALONE_ATTESTATION))
    }

    private suspend fun checkConfiguration(metadata: Map<String, ProofTypeMetadata>?, accepted: Set<RequestKind>) {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val issuerKey = key("issuer")
        val holder = key("holder")
        val attester = key("attester")
        val nonceService = JwtCredentialNonceService.crypto2(
            Crypto2JwtSigningKeyResolver { Crypto2JwtSigningKey(issuerKey, JwsAlgorithm.ES256) },
            Crypto2JwtVerificationKeyResolver { Crypto2JwtVerificationKey(issuerKey, setOf(JwsAlgorithm.ES256)) },
            now = { now },
        )
        val nonceBinding = CredentialNonceBinding(issuer, "$issuer/credential", "$issuer/nonce")
        val nonce = nonceService.issue(nonceBinding).nonce
        val context = CredentialProofValidationContext(
            credentialIssuer = issuer,
            nonceValidation = CredentialNonceValidationContext(nonceService, nonceBinding),
            keyAttestation = KeyAttestationConfig(
                KeyAttestationVerificationMethod.StaticJwk(attester.exportPublicJwkObject()),
            ).toVerificationOptions(),
        )
        val configuration = CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC, vct = "identity",
            cryptographicBindingMethodsSupported = metadata?.let { setOf(CryptographicBindingMethod.Jwk) },
            proofTypesSupported = metadata,
        )
        // No key_storage or user_authentication claims: empty requirements must not invent assurances.
        val attestation = CompactJws.sign(buildJsonObject {
            put("iat", now.epochSeconds)
            put("exp", now.epochSeconds + 300)
            put("nonce", nonce)
            put("attested_keys", JsonArray(listOf(holder.exportPublicJwkObject())))
        }.toString().encodeToByteArray(), attester, JwsAlgorithm.ES256,
            buildJsonObject { put("typ", "key-attestation+jwt") })
        suspend fun jwt(nested: String? = null) = CompactJws.sign(buildJsonObject {
            put("aud", issuer)
            put("iat", now.epochSeconds)
            put("nonce", nonce)
        }.toString().encodeToByteArray(), holder, JwsAlgorithm.ES256, buildJsonObject {
            put("typ", "openid4vci-proof+jwt")
            put("jwk", holder.exportPublicJwkObject())
            nested?.let { put("key_attestation", it) }
        })
        val plainJwt = jwt()
        val requests = mapOf(
            RequestKind.NO_PROOF to null,
            RequestKind.JWT to Proofs(jwt = listOf(plainJwt)),
            RequestKind.NESTED_ATTESTATION to Proofs(jwt = listOf(jwt(attestation))),
            RequestKind.STANDALONE_ATTESTATION to Proofs(attestation = listOf(attestation)),
        )
        val verifier = DefaultCredentialProofVerifier(now = { now })
        var signed = 0
        // A format handler supporting unbound credentials lets the test isolate the proof contract.
        fun sign(batch: CredentialIssuanceBatch): CredentialResponseResult {
            signed++
            assertEquals(batch.bindings.size.coerceAtLeast(1), batch.instances.size)
            return CredentialResponseResult.Success(CredentialResponse(
                credentials = batch.instances.map { IssuedCredential(JsonPrimitive("issued")) },
            ))
        }
        val legacy = CredentialEndpointHandler { _, _, _, _, batch, _, _, _, _, _, _, _, _, _ -> sign(batch) }
        val crypto2 = Crypto2CredentialEndpointHandler { _, _, _, _, batch, _, _, _, _, _, _, _, _, _ -> sign(batch) }
        val providerConfig = createTestConfig(credentialProofVerifier = verifier)
        providerConfig.credentialEndpointHandlers.register(CredentialFormat.SD_JWT_VC,
            object : CredentialEndpointHandler by legacy, Crypto2CredentialEndpointHandler by crypto2 {})
        val provider = buildOAuth2Provider(providerConfig)
        suspend fun check(proofs: Proofs?, succeeds: Boolean, label: String) {
            val request = DefaultCredentialRequest(
                client = DefaultClient("client", emptyList(), emptySet(), emptySet(), setOf("credential")),
                credentialIdentifier = null, credentialConfigurationId = "identity",
                proofs = proofs, credentialResponseEncryption = null,
            )
            if (succeeds) {
                val result = verifier.verify(request, configuration, context)
                assertEquals(if (proofs == null) 0 else 1, result.proofs.size, label)
                assertEquals(result.proofs.size, result.bindings.size, label)
            } else {
                assertEquals(CredentialErrorCodes.INVALID_PROOF, assertFailsWith<CredentialProofValidationException>(label) {
                    verifier.verify(request, configuration, context)
                }.errorCode)
            }
            var allocated = 0
            val previousSigned = signed
            val response = provider.createCredentialResponse(
                request, configuration, Crypto2CredentialSigningKey.select(issuerKey, configuration), issuer,
                CredentialIssuanceInputProvider { count ->
                    allocated += count
                    List(count) { CredentialIssuanceInput(buildJsonObject { put("name", "Alice") }) }
                }, proofValidationContext = context,
            )
            if (succeeds) {
                assertEquals(1, assertIs<CredentialResponseResult.Success>(response, label).response.credentials!!.size)
                assertEquals(1, allocated, label)
                assertEquals(previousSigned + 1, signed, label)
            } else {
                assertEquals(CredentialErrorCodes.INVALID_PROOF, assertIs<CredentialResponseResult.Failure>(response, label).error.error)
                assertEquals(0, allocated, label)
                assertEquals(previousSigned, signed, label)
            }
        }
        for ((kind, proofs) in requests) check(proofs, kind in accepted, kind.name)
        for (proofs in listOf(
            Proofs(), Proofs(jwt = emptyList()), Proofs(attestation = emptyList()),
            Proofs(jwt = listOf(plainJwt), attestation = listOf(attestation)),
            Proofs(attestation = listOf(attestation, attestation)),
            Proofs(jwt = listOf(jwt("invalid-attestation"))),
            Proofs(attestation = listOf("invalid-attestation")),
        )) check(proofs, false, "Invalid supplied proofs: $proofs")
    }
}
