package id.walt.openid4vci.proofs

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.openid4vci.*
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.*
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.proofs.attestation.*
import id.walt.openid4vci.proofs.jwt.JwtCredentialProofHandler
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.tokens.jwt.*
import id.walt.sdjwt.SDJwtVC
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StandaloneAttestationProofTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val issuer = "https://issuer.example"
    private val binding = CredentialNonceBinding(issuer, "$issuer/credential", "$issuer/nonce")
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val verifier = DefaultCredentialProofVerifier(now = { now })
    private val configuration = CredentialConfiguration(
        CredentialFormat.SD_JWT_VC, vct = "identity",
        cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
        proofTypesSupported = mapOf(ProofType.ATTESTATION.value to ProofTypeMetadata(setOf("ES256"))),
    )
    private suspend fun key(id: String = "key") = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))
    private fun nonceService(key: Key, time: Instant = now) = JwtCredentialNonceService.crypto2(
        Crypto2JwtSigningKeyResolver { Crypto2JwtSigningKey(key, JwsAlgorithm.ES256) },
        Crypto2JwtVerificationKeyResolver { Crypto2JwtVerificationKey(key, setOf(JwsAlgorithm.ES256)) },
        now = { time },
    )
    private suspend fun context(attester: Key, service: CredentialNonceService) = CredentialProofValidationContext(
        issuer, clientId = "oauth-client",
        nonceValidation = CredentialNonceValidationContext(service, binding),
        keyAttestation = KeyAttestationConfig(KeyAttestationVerificationMethod.StaticJwk(attester.exportPublicJwkObject())).toVerificationOptions(),
    )
    private fun request(vararg tokens: String) = DefaultCredentialRequest(
        client = DefaultClient("client", emptyList(), emptySet(), emptySet(), setOf("credential")),
        credentialIdentifier = null, credentialConfigurationId = "identity",
        proofs = Proofs(attestation = tokens.toList()), credentialResponseEncryption = null,
    )
    private suspend fun token(
        attester: Key, holders: List<Key>, nonce: String,
        claims: Map<String, JsonElement> = emptyMap(), omit: Set<String> = emptySet(),
        header: JsonObject = buildJsonObject { put("typ", "key-attestation+jwt") },
    ): String {
        val payload = buildJsonObject {
            put("iat", now.epochSeconds)
            put("exp", now.epochSeconds + 300)
            put("nonce", nonce)
            put("attested_keys", JsonArray(holders.map { it.exportPublicJwkObject() }))
        }
        return CompactJws.sign(JsonObject((payload + claims) - omit).toString().encodeToByteArray(), attester, JwsAlgorithm.ES256, header)
    }

    @Test
    fun `standalone needs no outer proof or client issuer and selects distinct holder keys`() = runTest {
        val attester = key("attester")
        val holders = List(3) { key("holder-$it") }
        val service = nonceService(key("issuer"))
        val nonce = service.issue(binding).nonce
        val context = context(attester, service)
        for (omit in listOf(emptySet(), setOf("exp"))) {
            val jwt = token(attester, holders + holders[0], nonce, omit = omit)
            val result = verifier.verify(request(jwt), configuration, context)
            val evidence = assertIs<VerifiedAttestationProof>(result.proofs.single())
            assertEquals(4, evidence.attestation.attestedKeys.size)
            assertEquals(holders.map { Jwk.sha256Thumbprint(it.exportPublicJwk()) },
                result.bindings.map { Jwk.sha256Thumbprint(it.holderKey.exportPublicJwk()) })
            assertTrue(result.bindings.all { it.holderDid == null && it.holderKid == null && it.proofIndexes == setOf(0) })
            // The same nonce remains reusable; verification has no replay-store side effects.
            verifier.verify(request(jwt), configuration, context)
            if ("exp" in omit) assertFailsWith<CredentialProofValidationException> {
                KeyAttestationVerifier(now = { now }).verify(jwt, configuration.proofTypesSupported!![ProofType.ATTESTATION.value],
                    context, configuration, context.keyAttestation!!, KeyAttestationUsage.JWT_HEADER)
            }
        }
    }

    @Test
    fun `nonce is mandatory expiring and isolated across issuers and endpoints`() = runTest {
        val attester = key()
        val issuerKey = key()
        val service = nonceService(issuerKey)
        val nonce = service.issue(binding).nonce
        val context = context(attester, service)
        val jwt = token(attester, listOf(key()), nonce)
        val otherIssuer = "https://other.example"
        val contexts = listOf(
            context.copy(nonceValidation = CredentialNonceValidationContext(nonceService(issuerKey, now + 301.seconds), binding)),
            context.copy(credentialIssuer = otherIssuer, nonceValidation = CredentialNonceValidationContext(
                service, CredentialNonceBinding(otherIssuer, "$otherIssuer/credential", "$otherIssuer/nonce"))),
            context.copy(nonceValidation = CredentialNonceValidationContext(service, binding.copy(credentialEndpoint = "$issuer/other"))),
            context.copy(nonceValidation = CredentialNonceValidationContext(service, binding.copy(nonceEndpoint = "$issuer/other"))),
        )
        // No exp makes the nonce, rather than attestation expiry, reject the stale request.
        val withoutExpiry = token(attester, listOf(key()), nonce, omit = setOf("exp"))
        for (invalid in contexts) assertEquals(CredentialErrorCodes.INVALID_NONCE,
            assertFailsWith<CredentialProofValidationException> { verifier.verify(request(withoutExpiry), configuration, invalid) }.errorCode)
        for (bad in listOf(token(attester, listOf(key()), nonce, omit = setOf("nonce")),
            token(attester, listOf(key()), "wrong"))) {
            assertEquals(CredentialErrorCodes.INVALID_NONCE,
                assertFailsWith<CredentialProofValidationException> { verifier.verify(request(bad), configuration, context) }.errorCode)
        }
        for (invalid in listOf(context.copy(nonceValidation = null), context.copy(keyAttestation = null),
            context.copy(credentialIssuer = otherIssuer))) {
            assertFailsWith<CredentialProofServiceException> { verifier.verify(request(jwt), configuration, invalid) }
        }
    }

    @Test
    fun `lifetime signature algorithm and key validation share the nested validator`() = runTest {
        val attester = key()
        val holder = key()
        val service = nonceService(key())
        val nonce = service.issue(binding).nonce
        val context = context(attester, service)
        val invalidClaims = listOf(
            mapOf("exp" to JsonPrimitive(now.epochSeconds - 100)),
            mapOf("exp" to JsonNull),
            mapOf("exp" to JsonPrimitive("1800000300")),
            mapOf("iat" to JsonPrimitive(now.epochSeconds + 61)),
            mapOf("iat" to JsonNull),
            mapOf("nbf" to JsonPrimitive(now.epochSeconds + 61)),
            mapOf("nbf" to JsonPrimitive("future")),
            mapOf("attested_keys" to JsonArray(emptyList())),
            mapOf("attested_keys" to JsonArray(listOf(buildJsonObject { put("kty", "oct"); put("k", "c2VjcmV0") }))),
            mapOf("attested_keys" to JsonArray(listOf(JsonObject(holder.exportPublicJwkObject() + ("d" to JsonPrimitive("private")))))),
        )
        for (claims in invalidClaims) assertFailsWith<CredentialProofValidationException>(claims.toString()) {
            verifier.verify(request(token(attester, listOf(holder), nonce, claims)), configuration, context)
        }
        for (jwt in listOf(token(key(), listOf(holder), nonce),
            token(attester, listOf(holder), nonce, header = buildJsonObject { put("typ", "openid4vci-proof+jwt") }))) {
            assertFailsWith<CredentialProofValidationException> { verifier.verify(request(jwt), configuration, context) }
        }
        val jwt = token(attester, listOf(holder), nonce)
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(jwt), configuration.copy(proofTypesSupported = mapOf(ProofType.ATTESTATION.value to ProofTypeMetadata(setOf("ES384")))), context)
        }
        assertFailsWith<CredentialProofValidationException> { verifier.verify(request(jwt), configuration, context(key(), service)) }
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(jwt, jwt), configuration, context.copy(batchCredentialIssuance = BatchCredentialIssuance(10)))
        }
    }

    @Test
    fun `assurance and policy operate on verified standalone evidence`() = runTest {
        val attester = key()
        val holder = key()
        val service = nonceService(key())
        val nonce = service.issue(binding).nonce
        val context = context(attester, service)
        val required = configuration.copy(proofTypesSupported = mapOf(ProofType.ATTESTATION.value to
            ProofTypeMetadata(setOf("ES256"), KeyAttestationsRequired(keyStorage = setOf("iso_18045_high")))))
        val jwt = token(attester, listOf(holder), nonce)
        assertFailsWith<CredentialProofValidationException> { verifier.verify(request(jwt), required, context) }
        val assured = token(attester, listOf(holder), nonce,
            mapOf("key_storage" to JsonArray(listOf(JsonPrimitive("iso_18045_high")))))
        verifier.verify(request(assured), required, context)
        var calls = 0
        val options = context.keyAttestation!!.copy(policy = KeyAttestationPolicy { _, _, _ -> calls++; false })
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(token(key(), listOf(holder), nonce)), configuration, context.copy(keyAttestation = options))
        }
        assertEquals(0, calls)
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(jwt), configuration, context.copy(keyAttestation = options))
        }
        assertEquals(1, calls)
        val cancellation = CancellationException("cancelled")
        for (failure in listOf(IllegalStateException("unavailable"), cancellation)) {
            val unavailable = context.keyAttestation.copy(policy = KeyAttestationPolicy { _, _, _ -> throw failure })
            val error = assertFails { verifier.verify(request(jwt), configuration, context.copy(keyAttestation = unavailable)) }
            if (failure === cancellation) assertSame(cancellation, error) else assertIs<CredentialProofServiceException>(error)
        }
        val brokenNonce = object : CredentialNonceService {
            override suspend fun issue(binding: CredentialNonceBinding): IssuedCredentialNonce = error("unused")
            override suspend fun validate(nonce: String, binding: CredentialNonceBinding): CredentialNonceValidationResult = error("unavailable")
        }
        assertFailsWith<CredentialProofServiceException> {
            verifier.verify(request(jwt), configuration, context.copy(nonceValidation = CredentialNonceValidationContext(brokenNonce, binding)))
        }
    }

    @Test
    fun `one standalone attestation larger than 64 KiB issues 33 credentials after binding validation`() = runTest {
        val attester = key()
        val holders = List(33) { key() }
        val issuerKey = key()
        val service = nonceService(issuerKey)
        val context = context(attester, service)
        val attestation = token(attester, holders, service.issue(binding).nonce,
            claims = mapOf("padding" to JsonPrimitive("x".repeat(65_536))))
        assertTrue(attestation.length > 65_536)
        val request = request(attestation)
        val provider = buildOAuth2Provider(createTestConfig(credentialProofVerifier = verifier))
        var allocated = 0
        suspend fun issue(selected: CredentialConfiguration = configuration) =
            provider.createCredentialResponse(
                request, selected, Crypto2CredentialSigningKey.select(issuerKey, selected), issuer,
                CredentialIssuanceInputProvider { count ->
                    allocated += count
                    List(count) { CredentialIssuanceInput(buildJsonObject { put("name", "Alice") }) }
                }, proofValidationContext = context,
            )
        for (invalid in listOf(configuration.copy(format = CredentialFormat.JWT_VC_JSON),
            configuration.copy(cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Did("jwk"))))) {
            assertIs<CredentialResponseResult.Failure>(issue(invalid))
            assertEquals(0, allocated)
        }
        // A single attestation can select all its keys without advertising batch issuance.
        assertNull(context.batchCredentialIssuance)
        val response = assertIs<CredentialResponseResult.Success>(issue())
        assertEquals(holders.size, allocated)
        assertEquals(holders.map { Jwk.sha256Thumbprint(it.exportPublicJwk()) }, response.response.credentials!!.map {
            Jwk.sha256Thumbprint(restoreAttestedKey(requireNotNull(SDJwtVC.parse(it.credential.jsonPrimitive.content).holderKeyJWK)).exportPublicJwk())
        })
    }

    @Test
    fun `advertisement needs a handler trust nonce and compatible binding without enabling other types`() = runTest {
        val trust = KeyAttestationConfig(KeyAttestationVerificationMethod.StaticJwk(key().exportPublicJwkObject()))
        assertFailsWith<IllegalArgumentException> { validateKeyAttestationConfiguration(listOf(configuration), null, true) }
        assertFailsWith<IllegalArgumentException> { validateKeyAttestationConfiguration(listOf(configuration), trust) }
        assertFailsWith<IllegalArgumentException> {
            validateKeyAttestationConfiguration(listOf(configuration), trust, true, CredentialProofHandlers(listOf(JwtCredentialProofHandler())))
        }
        for (invalid in listOf(configuration.copy(format = CredentialFormat.JWT_VC_JSON),
            configuration.copy(format = CredentialFormat.JWT_VC_JSON,
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.CoseKey)))) {
            assertFailsWith<IllegalArgumentException> { validateKeyAttestationConfiguration(listOf(invalid), trust, true) }
        }
        validateKeyAttestationConfiguration(listOf(configuration), trust, true)
        for (format in listOf(CredentialFormat.SD_JWT_VC, CredentialFormat.JWT_VC_JSON, CredentialFormat.JWT_VC)) {
            validateKeyAttestationConfiguration(listOf(configuration.copy(format = format,
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.DidJwk))), trust, true)
        }
        val jwtOnly = configuration.copy(proofTypesSupported = mapOf(ProofType.JWT.value to ProofTypeMetadata(setOf("ES256"))))
        validateKeyAttestationConfiguration(listOf(jwtOnly), trust)
        assertEquals(setOf(ProofType.JWT.value), jwtOnly.proofTypesSupported!!.keys)
        assertNull(jwtOnly.proofTypesSupported[ProofType.JWT.value]!!.keyAttestationsRequired)
    }
}
