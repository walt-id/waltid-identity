package id.walt.openid4vci.proofs

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.*
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.handlers.endpoints.credential.*
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.proofs.attestation.KeyAttestationTrustResolver
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationOptions
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** A test-only object handler exercises the extension contract, not DI VP verification. */
class CredentialProofPipelineTest {
    private data class ObjectEvidence(val value: JsonElement, override val proofType: ProofType = ProofType.DI_VP) : VerifiedCredentialProof
    private fun handler(verify: suspend (JsonElement) -> CredentialProofHandlerResult) = object : CredentialProofHandler {
        override val proofType = ProofType.DI_VP
        override suspend fun verify(proof: JsonElement, proofMetadata: ProofTypeMetadata?, configuration: CredentialConfiguration,
            context: CredentialProofValidationContext) = verify(proof)
    }
    private val configuration = CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "test",
        cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk, CryptographicBindingMethod.Did("example")),
        proofTypesSupported = mapOf(ProofType.DI_VP.value to ProofTypeMetadata(setOf("test"))))
    private val context = CredentialProofValidationContext("https://issuer.example", batchCredentialIssuance = BatchCredentialIssuance(3))
    private suspend fun key() = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId("holder"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))
    private fun request(proofs: Proofs? = Proofs(diVp = listOf(buildJsonObject { put("index", 0) }))) = DefaultCredentialRequest(
        client = DefaultClient("client", emptyList(), emptySet(), emptySet(), setOf("credential")),
        credentialIdentifier = null, credentialConfigurationId = "test", proofs = proofs, credentialResponseEncryption = null,
    )
    private fun verifier(handler: CredentialProofHandler) = DefaultCredentialProofVerifier(handlers = CredentialProofHandlers(listOf(handler)))

    @Test
    fun `object handler produces multiple bindings and merges only matching identities`() = runTest {
        val keys = List(3) { key() }
        val first = listOf(VerifiedCredentialBindingCandidate(keys[0]), VerifiedCredentialBindingCandidate(keys[1]))
        val second = listOf(VerifiedCredentialBindingCandidate(keys[1], "did:example:holder#key", "did:example:holder"),
            VerifiedCredentialBindingCandidate(keys[2]))
        val handler = handler { value ->
            assertIs<JsonObject>(value)
            val candidates = if (value["index"]!!.jsonPrimitive.int == 0) first else second
            CredentialProofHandlerResult(ObjectEvidence(value), candidates, CredentialBindingMultiplicity.DISTINCT_KEYS)
        }
        val proofs = Proofs(diVp = listOf(buildJsonObject { put("index", 0) }, buildJsonObject { put("index", 1) }))
        val result = verifier(handler).verify(request(proofs), configuration, context)
        assertEquals(2, result.proofs.size)
        assertTrue(result.proofs.all { it is ObjectEvidence })
        assertEquals(keys.map { Jwk.sha256Thumbprint(it.exportPublicJwk()) }, result.bindings.map { Jwk.sha256Thumbprint(it.holderKey.exportPublicJwk()) })
        assertEquals(listOf(setOf(0), setOf(0, 1), setOf(1)), result.bindings.map { it.proofIndexes })
        assertEquals(listOf(null, "did:example:holder", null), result.bindings.map { it.holderDid })
        assertEquals(listOf(null, "did:example:holder#key", null), result.bindings.map { it.holderKid })
        val preserving = handler { value ->
            CredentialProofHandlerResult(ObjectEvidence(value), List(130) { first[0] }, CredentialBindingMultiplicity.PRESERVE_OCCURRENCES)
        }
        assertEquals(260, verifier(preserving).verify(request(proofs), configuration, context).bindings.size)
        val provider = buildOAuth2Provider(createTestConfig(credentialProofVerifier = verifier(handler)))
        val signingKey = Crypto2CredentialSigningKey.select(key(), configuration)
        val response = provider.createCredentialResponse(request(proofs), configuration, signingKey, context.credentialIssuer,
            CredentialIssuanceInputProvider { count ->
                assertEquals(3, count)
                List(count) { CredentialIssuanceInput(buildJsonObject { put("name", "Alice") }) }
            }, proofValidationContext = context)
        assertEquals(3, assertIs<CredentialResponseResult.Success>(response).response.credentials!!.size)
    }

    @Test
    fun `distinct bindings keep first identity and key order while merging all proof indexes`() = runTest {
        val keys = List(3) { key() }
        val latestFirstKey = object : Key by keys[0] {}
        val candidates = listOf(
            listOf(
                VerifiedCredentialBindingCandidate(keys[0], "initial-key-id"),
                VerifiedCredentialBindingCandidate(keys[1]),
                VerifiedCredentialBindingCandidate(keys[2], holderDid = "did:example:first-third"),
            ),
            listOf(
                VerifiedCredentialBindingCandidate(keys[1], "later-key-id"),
                VerifiedCredentialBindingCandidate(keys[0], "did:example:first#key", "did:example:first"),
                VerifiedCredentialBindingCandidate(keys[2], "did:example:later-third#key", "did:example:later-third"),
            ),
            listOf(
                VerifiedCredentialBindingCandidate(latestFirstKey, "did:example:later#key", "did:example:later"),
                VerifiedCredentialBindingCandidate(keys[1], "last-key-id"),
                VerifiedCredentialBindingCandidate(keys[2]),
            ),
        )
        val handler = handler { value -> CredentialProofHandlerResult(
            ObjectEvidence(value), candidates[value.jsonObject.getValue("index").jsonPrimitive.int],
            CredentialBindingMultiplicity.DISTINCT_KEYS,
        ) }
        val proofs = Proofs(diVp = List(3) { index -> buildJsonObject { put("index", index) } })
        val result = verifier(handler).verify(request(proofs), configuration, context)

        assertEquals(listOf(
            VerifiedCredentialBinding(latestFirstKey, "did:example:first#key", "did:example:first", setOf(0, 1, 2)),
            VerifiedCredentialBinding(keys[1], proofIndexes = setOf(0, 1, 2)),
            VerifiedCredentialBinding(keys[2], holderDid = "did:example:first-third", proofIndexes = setOf(0, 1, 2)),
        ), result.bindings)
        assertSame(latestFirstKey, result.bindings.first().holderKey)
    }

    @Test
    fun `binding methods are checked after matching identities have been merged`() = runTest {
        val holder = key()
        val handler = handler { value ->
            val index = value.jsonObject.getValue("index").jsonPrimitive.int
            val candidate = if (index == 0) VerifiedCredentialBindingCandidate(holder)
                else VerifiedCredentialBindingCandidate(holder, "did:example:holder#key", "did:example:holder")
            CredentialProofHandlerResult(ObjectEvidence(value), listOf(candidate), CredentialBindingMultiplicity.DISTINCT_KEYS)
        }
        val proofs = Proofs(diVp = List(2) { index -> buildJsonObject { put("index", index) } })
        val didOnly = configuration.copy(cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Did("example")))

        assertEquals(listOf(VerifiedCredentialBinding(holder, "did:example:holder#key", "did:example:holder", setOf(0, 1))),
            verifier(handler).verify(request(proofs), didOnly, context).bindings)
        assertFailsWith<CredentialProofValidationException> {
            verifier(handler).verify(request(), didOnly, context)
        }
    }

    @Test
    fun `mixed multiplicity deduplicates bindings and propagates key export failures`() = runTest {
        val keys = List(3) { key() }
        var lastKey: Key = keys[2]
        var verified = 0
        val handler = handler { value ->
            verified++
            val index = value.jsonObject.getValue("index").jsonPrimitive.int
            CredentialProofHandlerResult(
                ObjectEvidence(value),
                listOf(keys[0], if (index == 0) keys[1] else lastKey).map { VerifiedCredentialBindingCandidate(it) },
                if (index == 0) CredentialBindingMultiplicity.PRESERVE_OCCURRENCES else CredentialBindingMultiplicity.DISTINCT_KEYS,
            )
        }
        val proofs = Proofs(diVp = List(2) { index -> buildJsonObject { put("index", index) } })
        assertEquals(listOf(
            VerifiedCredentialBinding(keys[0], proofIndexes = setOf(0, 1)),
            VerifiedCredentialBinding(keys[1], proofIndexes = setOf(0)),
            VerifiedCredentialBinding(keys[2], proofIndexes = setOf(1)),
        ), verifier(handler).verify(request(proofs), configuration, context).bindings)

        assertEquals(2, verified)

        for (exportFailure in listOf(IllegalStateException("key unavailable"), CancellationException("cancelled"))) {
            lastKey = object : Key by keys[2] {
                override val capabilities = keys[2].capabilities.copy(
                    publicKeyExporter = PublicKeyExporter { throw exportFailure },
                )
            }
            if (exportFailure is CancellationException) {
                assertSame(exportFailure, assertFailsWith<CancellationException> {
                    verifier(handler).verify(request(proofs), configuration, context)
                })
            } else {
                assertSame(exportFailure, assertFailsWith<CredentialProofServiceException> {
                    verifier(handler).verify(request(proofs), configuration, context)
                }.cause)
            }
        }
    }

    @Test
    fun `distinct custom bindings are independent of configured attestation trust`() = runTest {
        val keys = List(2) { key() }
        val handler = handler { value -> CredentialProofHandlerResult(
            ObjectEvidence(value),
            (keys + keys[0]).map { VerifiedCredentialBindingCandidate(it) },
            CredentialBindingMultiplicity.DISTINCT_KEYS,
        ) }
        val configured = context.copy(keyAttestation = KeyAttestationVerificationOptions(
            trustResolver = KeyAttestationTrustResolver { _, _, _ -> error("No attestation was submitted") },
        ))
        val withoutTrust = verifier(handler).verify(request(), configuration, context)
        val withTrust = verifier(handler).verify(request(), configuration, configured)
        assertEquals(2, withTrust.bindings.size)
        assertEquals(withoutTrust.bindings, withTrust.bindings)
        assertTrue(withTrust.bindings.all { it.proofIndexes == setOf(0) })
    }

    @Test
    fun `registry rejects duplicate handlers and dispatch honors advertised capabilities`() = runTest {
        val handler = handler { error("Must not dispatch") }
        assertFailsWith<IllegalArgumentException> { CredentialProofHandlers(listOf(handler, handler)) }
        val entries = mutableListOf(handler)
        val registry = CredentialProofHandlers(entries)
        entries.clear()
        assertEquals(setOf(ProofType.DI_VP), registry.supportedTypes)
        assertSame(handler, registry[ProofType.DI_VP])
        assertNull(registry[ProofType.JWT])
        for (invalid in listOf(
            DefaultCredentialProofVerifier() to configuration,
            verifier(handler) to configuration.copy(proofTypesSupported = mapOf(ProofType.JWT.value to ProofTypeMetadata(setOf("ES256")))),
        )) assertFailsWith<CredentialProofValidationException> { invalid.first.verify(request(), invalid.second, context) }
    }

    @Test
    fun `metadata preserves wire identifiers while registry validation requires a matching handler`() {
        val registry = CredentialProofHandlers(listOf(handler { error("Must not dispatch") }))
        val capabilities = CredentialProofCapabilities(keyAttestation = false, issuerBoundNonce = false)
        registry.validateConfiguration(listOf(configuration), capabilities)

        for (type in listOf("custom_proof", ProofType.DI_VP.name, ProofType.JWT.value)) {
            val advertised = configuration.copy(proofTypesSupported = mapOf(type to ProofTypeMetadata(setOf("test"))))
            val decoded = Json.decodeFromString(
                CredentialConfiguration.serializer(),
                Json.encodeToString(CredentialConfiguration.serializer(), advertised),
            )
            assertEquals(advertised.proofTypesSupported, decoded.proofTypesSupported)
            val failure = assertFailsWith<IllegalArgumentException> {
                registry.validateConfiguration(listOf(decoded), capabilities)
            }
            assertEquals("No credential proof handler registered for $type", failure.message)
        }
    }

    @Test
    fun `parser and direct calls reject empty mixed incorrectly typed or excessive proofs`() = runTest {
        for (json in listOf("{}", """{"jwt":[]}""", """{"jwt":["a"],"attestation":[]}""",
            """{"jwt":[12]}""", """{"attestation":[true]}""", """{"attestation":[null]}""",
            """{"attestation":["a","b"]}""", """{"jwt":["a"],"di_vp":[{}]}""")) {
            assertFailsWith<IllegalArgumentException>(json) { Proofs.fromJsonObject(Json.parseToJsonElement(json).jsonObject) }
        }
        var called = 0
        val handler = handler { called++; error("Must not dispatch") }
        for (proofs in listOf(Proofs(), Proofs(diVp = emptyList()), Proofs(diVp = listOf(buildJsonObject {}), jwt = emptyList()),
            Proofs(diVp = List(4) { buildJsonObject {} }))) {
            assertFailsWith<CredentialProofValidationException> { verifier(handler).verify(request(proofs), configuration, context) }
        }
        assertEquals(0, called)
        val noProof = verifier(handler).verify(request(null), configuration.copy(proofTypesSupported = null, cryptographicBindingMethodsSupported = null), context)
        assertTrue(noProof.proofs.isEmpty() && noProof.bindings.isEmpty())
        assertFailsWith<CredentialProofValidationException> { verifier(handler).verify(request(null), configuration, context) }
    }

    @Test
    fun `handler contract errors are operational and cancellation propagates`() = runTest {
        val candidate = VerifiedCredentialBindingCandidate(key())
        val badResults = listOf(
            CredentialProofHandlerResult(ObjectEvidence(JsonNull, ProofType.JWT), listOf(candidate), CredentialBindingMultiplicity.DISTINCT_KEYS),
            CredentialProofHandlerResult(ObjectEvidence(JsonNull), emptyList(), CredentialBindingMultiplicity.DISTINCT_KEYS),
        )
        for (result in badResults) assertFailsWith<CredentialProofServiceException> {
            verifier(handler { result }).verify(request(), configuration, context)
        }
        val cancellation = CancellationException("cancelled")
        assertSame(cancellation, assertFailsWith<CancellationException> {
            verifier(handler { throw cancellation }).verify(request(), configuration, context)
        })
        val failure = IllegalStateException("handler unavailable")
        assertSame(failure, assertFailsWith<CredentialProofServiceException> {
            verifier(handler { throw failure }).verify(request(), configuration, context)
        }.cause)
        val mdoc = configuration.copy(format = CredentialFormat.MSO_MDOC, vct = null, doctype = "test")
        val issuerKey = Crypto2CredentialSigningKey.select(key(), mdoc)
        for (exportFailure in listOf(failure, cancellation)) {
            val unavailableKey = object : Key by candidate.holderKey {
                override val capabilities = candidate.holderKey.capabilities.copy(
                    publicKeyExporter = PublicKeyExporter { throw exportFailure },
                )
            }
            val unavailable = handler { value -> CredentialProofHandlerResult(ObjectEvidence(value),
                listOf(VerifiedCredentialBindingCandidate(unavailableKey)), CredentialBindingMultiplicity.PRESERVE_OCCURRENCES) }
            val provider = buildOAuth2Provider(createTestConfig(credentialProofVerifier = verifier(unavailable)))
            suspend fun issue() = provider.createCredentialResponse(request(), mdoc, issuerKey, context.credentialIssuer,
                CredentialIssuanceInputProvider { error("Must not allocate") }, proofValidationContext = context)
            if (exportFailure is CancellationException) {
                assertSame(exportFailure, assertFailsWith<CancellationException> { issue() })
            } else {
                assertSame(exportFailure, assertFailsWith<CredentialProofServiceException> { issue() }.cause)
            }
        }
    }

    @Test
    fun `custom verifiers cannot bypass proof shape batch size or evidence provenance before allocation`() = runTest {
        val holder = key()
        val issuerKey = Crypto2CredentialSigningKey.select(key(), configuration)
        val evidence = ObjectEvidence(buildJsonObject {})
        val binding = VerifiedCredentialBinding(holder, proofIndexes = setOf(0))
        val invalidResults = listOf(
            CredentialProofVerificationResult(emptyList(), emptyList()),
            CredentialProofVerificationResult(listOf(evidence), emptyList()),
            CredentialProofVerificationResult(listOf(evidence.copy(proofType = ProofType.JWT)), listOf(binding)),
            CredentialProofVerificationResult(listOf(evidence), listOf(binding.copy(proofIndexes = emptySet()))),
            CredentialProofVerificationResult(listOf(evidence), listOf(binding.copy(proofIndexes = setOf(1)))),
        )
        var allocations = 0
        var verifications = 0
        val inputs = CredentialIssuanceInputProvider { allocations++; error("Must not allocate") }
        for (result in invalidResults) {
            val provider = buildOAuth2Provider(createTestConfig(credentialProofVerifier = CredentialProofVerifier { _, _, _ ->
                verifications++
                result
            }))
            assertFailsWith<CredentialProofServiceException> {
                provider.createCredentialResponse(request(), configuration, issuerKey, context.credentialIssuer, inputs, proofValidationContext = context)
            }
            val count = verifications
            for (proofs in listOf(Proofs(diVp = emptyList()), Proofs(diVp = List(4) { buildJsonObject {} }))) {
                assertIs<CredentialResponseResult.Failure>(
                    provider.createCredentialResponse(request(proofs), configuration, issuerKey, context.credentialIssuer, inputs,
                        proofValidationContext = context))
            }
            assertEquals(count, verifications)
        }
        assertEquals(0, allocations)
    }
}
