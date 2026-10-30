package id.walt.openid4vci

import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.*
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.proofs.*
import id.walt.openid4vci.proofs.Proofs
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.*

class ProviderCredentialPreparationTest {
    @Test
    fun `both signing APIs validate before allocating and signing`() = runTest {
        for (crypto2 in listOf(false, true)) {
            val steps = mutableListOf<String>()
            assertIs<CredentialResponseResult.Success>(issue(crypto2, steps))
            assertEquals(listOf("proof", "bindings", "allocate", "sign"), steps)
        }
    }

    @Test
    fun `both signing APIs preserve validation errors cancellation and service failures before allocation`() = runTest {
        for (crypto2 in listOf(false, true)) {
            for (stage in listOf("proof", "bindings")) {
                val failures = listOf(
                    CredentialProofValidationException(CredentialErrorCodes.INVALID_NONCE, "invalid nonce"),
                    CancellationException("cancelled"),
                    CredentialProofServiceException("service unavailable"),
                    IllegalStateException("unexpected failure"),
                )
                for (failure in failures) {
                    val steps = mutableListOf<String>()
                    suspend fun attempt() = issue(crypto2, steps) { if (it == stage) throw failure }
                    when (failure) {
                        is CredentialProofValidationException -> {
                            val response = assertIs<CredentialResponseResult.Failure>(attempt())
                            assertEquals(failure.errorCode, response.error.error)
                            assertEquals(failure.message, response.error.description)
                        }
                        is CancellationException -> assertSame(failure, assertFailsWith<CancellationException> { attempt() })
                        is CredentialProofServiceException -> assertSame(failure, assertFailsWith<CredentialProofServiceException> { attempt() })
                        else -> assertSame(failure, assertFailsWith<CredentialProofServiceException> { attempt() }.cause)
                    }
                    assertEquals(if (stage == "proof") listOf("proof") else listOf("proof", "bindings"), steps)
                }
            }
        }
    }

    @Test
    fun `allocation and signing failures stay outside proof error mapping in both APIs`() = runTest {
        for (crypto2 in listOf(false, true)) {
            for (stage in listOf("allocate", "sign")) {
                for (failure in listOf(
                    CredentialProofValidationException(CredentialErrorCodes.INVALID_PROOF, "downstream failure"),
                    IllegalStateException("downstream failure"),
                    CancellationException("cancelled"),
                )) {
                    val steps = mutableListOf<String>()
                    assertSame(failure, assertFails { issue(crypto2, steps) { if (it == stage) throw failure } })
                    val expected = listOf("proof", "bindings", "allocate") + if (stage == "sign") listOf("sign") else emptyList()
                    assertEquals(expected, steps)
                }
            }
        }
    }

    @Test
    fun `crypto2 compatibility is checked after proofs and before binding validation or allocation`() = runTest {
        val steps = mutableListOf<String>()
        val response = assertIs<CredentialResponseResult.Failure>(issue(true, steps, supportsCrypto2 = false))
        assertEquals(CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION, response.error.error)
        assertEquals(listOf("proof"), steps)
    }

    @Test
    fun `accepted requests persist public bindings and never revalidate the original proof`() = runTest {
        for (crypto2 in listOf(false, true)) {
            val steps = mutableListOf<String>()
            assertIs<CredentialResponseResult.Success>(issue(crypto2, steps, deferred = true))
            assertEquals(listOf("proof", "bindings", "bindings", "allocate", "sign"), steps)
        }
    }

    /** Custom collaborators isolate provider orchestration; cryptographic proof tests cover evidence validation. */
    private suspend fun issue(
        crypto2: Boolean,
        steps: MutableList<String>,
        supportsCrypto2: Boolean = true,
        deferred: Boolean = false,
        onStep: (String) -> Unit = {},
    ): CredentialResponseResult {
        fun step(name: String) { steps += name; onStep(name) }
        val configuration = CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC, vct = "identity",
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
            proofTypesSupported = mapOf(ProofType.JWT.value to ProofTypeMetadata(setOf("ES256"))),
        )
        val key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId("test-key"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val binding = VerifiedCredentialBinding(key, proofIndexes = setOf(0))
        val providerConfig = createTestConfig(credentialProofVerifier = CredentialProofVerifier { _, _, _ ->
            step("proof")
            CredentialProofVerificationResult(
                listOf(object : VerifiedCredentialProof { override val proofType = ProofType.JWT }), listOf(binding),
            )
        })
        suspend fun sign(batch: CredentialIssuanceBatch): CredentialResponseResult {
            step("sign")
            assertEquals(listOf(Jwk.sha256Thumbprint(binding.holderKey.exportPublicJwk())), batch.bindings.map { Jwk.sha256Thumbprint(it.holderKey.exportPublicJwk()) })
            assertEquals(1, batch.inputs.size)
            return CredentialResponseResult.Success(CredentialResponse(credentials = listOf(IssuedCredential(JsonPrimitive("issued")))))
        }
        val legacy = CredentialEndpointHandler { _, _, _, _, batch, _, _, _, _, _, _, _, _, _, _ -> sign(batch) }
        val legacyWithValidation = object : CredentialEndpointHandler by legacy {
            override suspend fun validateBindings(configuration: CredentialConfiguration, bindings: List<VerifiedCredentialBinding>) {
                step("bindings")
                assertEquals(listOf(Jwk.sha256Thumbprint(binding.holderKey.exportPublicJwk())), bindings.map { Jwk.sha256Thumbprint(it.holderKey.exportPublicJwk()) })
            }
        }
        val crypto2Handler = Crypto2CredentialEndpointHandler { _, _, _, _, batch, _, _, _, _, _, _, _, _, _, _ -> sign(batch) }
        val handler = if (supportsCrypto2) {
            object : CredentialEndpointHandler by legacyWithValidation, Crypto2CredentialEndpointHandler by crypto2Handler {}
        } else legacyWithValidation
        providerConfig.credentialEndpointHandlers.register(configuration.format, handler)
        val provider = buildOAuth2Provider(providerConfig)
        val request = DefaultCredentialRequest(
            client = DefaultClient("client", emptyList(), emptySet(), emptySet(), setOf("credential")),
            credentialIdentifier = null, credentialConfigurationId = "identity",
            proofs = Proofs(jwt = listOf("handled-by-test-verifier")), credentialResponseEncryption = null,
        )
        val inputs = CredentialIssuanceInputProvider { count ->
            step("allocate")
            assertEquals(1, count)
            List(count) { CredentialIssuanceInput(buildJsonObject {}) }
        }
        val context = CredentialProofValidationContext("https://issuer.example")
        val issuanceRequest = if (deferred) {
            val accepted = provider.acceptCredentialRequest(request, configuration, "credential-id", "identity", context)
            val encoded = kotlinx.serialization.json.Json.encodeToString(accepted)
            assertFalse(encoded.contains("handled-by-test-verifier"))
            assertNull(accepted.request.proofs)
            val restored = kotlinx.serialization.json.Json.decodeFromString<id.walt.openid4vci.requests.credential.AcceptedCredentialRequest>(encoded)
            restored.restore()
        } else request
        return if (crypto2) provider.createCredentialResponse(
            issuanceRequest, configuration, Crypto2CredentialSigningKey.select(key, configuration), context.credentialIssuer,
            inputs, proofValidationContext = context,
        ) else provider.createCredentialResponse(
            issuanceRequest, configuration, LegacyP256TestKey(key), context.credentialIssuer,
            inputs, proofValidationContext = context,
        )
    }
}
