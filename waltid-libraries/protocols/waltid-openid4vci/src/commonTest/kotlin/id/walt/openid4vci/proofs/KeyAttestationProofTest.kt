package id.walt.openid4vci.proofs


import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.jose.preferredJwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.toPrivateJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.DidService
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.openid4vci.*
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.handlers.credential.MdocCredentialSigner
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInput
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInputProvider
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialEndpointHandler
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.proofs.attestation.*
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.sdjwt.SDJwtVC
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*

class KeyAttestationProofTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val issuer = "https://issuer.example"
    private val verifier = DefaultCredentialProofVerifier(now = { now })
    private val configuration = CredentialConfiguration(
        format = CredentialFormat.SD_JWT_VC,
        vct = "identity",
        cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
        proofTypesSupported = mapOf(ProofType.JWT.value to ProofTypeMetadata(setOf("ES256"), KeyAttestationsRequired())),
    )

    @Test
    fun `nested attested key limit is per attestation and checked before key import`() = runTest {
        val attester = key()
        val holders = List(4) { key() }
        val atDefaultLimit = proof(holders[0], attestation(attester, List(20) { holders[0] }))
        assertEquals(1, verifier.verify(request(atDefaultLimit), configuration, context(attester)).bindings.size)
        val aboveDefaultLimit = proof(holders[0], attestation(attester, List(21) { holders[0] }))
        assertEquals(CredentialErrorCodes.INVALID_PROOF, assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(aboveDefaultLimit), configuration, context(attester))
        }.errorCode)
        assertEquals(1, verifier.verify(request(aboveDefaultLimit), configuration,
            context(attester, maxAttestedKeys = null)).bindings.size)
        val limited = context(attester, maxAttestedKeys = 2).copy(batchCredentialIssuance = BatchCredentialIssuance(2))
        for (count in 1..2) {
            val jwt = proof(holders[0], attestation(attester, holders.take(count)))
            assertEquals(count, verifier.verify(request(jwt), configuration, limited).bindings.size)
        }
        val first = proof(holders[0], attestation(attester, holders.take(2)))
        val second = proof(holders[2], attestation(attester, holders.drop(2)))
        assertEquals(4, verifier.verify(request(first, second), configuration, limited).bindings.size)
        assertEquals(CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
            assertFailsWith<CredentialProofValidationException> {
                verifier.verify(request(first, second, first), configuration, limited)
            }.errorCode)

        val oversized = proof(holders[0], attestation(attester, holders.take(3)))
        assertEquals(3, verifier.verify(request(oversized), configuration, context(attester)).bindings.size)
        val duplicates = proof(holders[0], attestation(attester, List(3) { holders[0] }))
        // Malformed keys would fail import if the count were not checked first.
        val malformed = proof(holders[0], attestation(attester, emptyList(), claims = mapOf(
            "attested_keys" to JsonArray(List(3) { buildJsonObject {} }),
        )))
        for (jwt in listOf(oversized, duplicates, malformed)) {
            val error = assertFailsWith<CredentialProofValidationException> {
                verifier.verify(request(jwt), configuration, limited)
            }
            assertEquals(CredentialErrorCodes.INVALID_PROOF, error.errorCode)
            assertEquals("Key attestation exceeds the configured attested-key limit", error.message)
        }
    }

    @Test
    fun `one proof selects all distinct attested keys in attestation order`() = runTest {
        val attester = key()
        val holders = List(3) { key() }
        val jwt = proof(holders[1], attestation(attester, holders + holders[0]))
        val result = verifier.verify(request(jwt), configuration, context(attester))
        assertEquals(1, result.proofs.size)
        assertEquals(4, assertIs<VerifiedJwtProof>(result.proofs.single()).keyAttestation!!.attestedKeys.size)
        assertEquals(holders.map { Jwk.sha256Thumbprint(it.exportPublicJwk()) }, result.bindings.map { Jwk.sha256Thumbprint(it.holderKey.exportPublicJwk()) })
        assertTrue(result.bindings.all { it.holderDid == null && it.proofIndexes == setOf(0) })
    }

    @Test
    fun `overlapping attestations deduplicate bindings but verify every proof`() = runTest {
        val attester = key()
        val holders = List(3) { key() }
        val first = proof(holders[0], attestation(attester, holders.take(2)))
        val second = proof(holders[2], attestation(attester, holders.drop(1)))
        val result = verifier.verify(request(first, second), configuration, context(attester))
        assertEquals(2, result.proofs.size)
        assertEquals(3, result.bindings.size)
        assertEquals(setOf(0, 1), result.bindings[1].proofIndexes)
        val invalidSecond = proof(holders[2], attestation(key(), holders.drop(1)))
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(first, invalidSecond), configuration, context(attester))
        }
    }

    @Test
    fun `required empty object is mandatory and optional supplied evidence is verified`() = runTest {
        val attester = key()
        val holder = key()
        val optional = configuration.copy(proofTypesSupported = mapOf(ProofType.JWT.value to ProofTypeMetadata(setOf("ES256"))))
        verifier.verify(request(proof(holder, null)), optional, context(attester))
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(proof(holder, null)), configuration, context(attester))
        }
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(proof(holder, attestation(key(), listOf(holder)))), optional, context(attester))
        }
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(proof(holder, attestation(attester, listOf(holder)))), optional, CredentialProofValidationContext(issuer))
        }
    }

    @Test
    fun `outer signing key must be attested and trust is issuer specific`() = runTest {
        val attester = key()
        val holder = key()
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(proof(holder, attestation(attester, listOf(key())))), configuration, context(attester))
        }
        val jwt = proof(holder, attestation(attester, listOf(holder)))
        verifier.verify(request(jwt), configuration, context(attester))
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(jwt), configuration, context(key()))
        }
    }

    @Test
    fun `rejects invalid lifetime type assurances and key material`() = runTest {
        val attester = key()
        val holder = key()
        val invalidClaims = listOf(
            mapOf("exp" to JsonPrimitive(now.epochSeconds - 100)),
            mapOf("iat" to JsonPrimitive(now.epochSeconds + 100)),
            mapOf("iat" to JsonPrimitive(now.epochSeconds.toString())),
            mapOf("exp" to JsonNull),
            mapOf("nonce" to JsonPrimitive(123)),
            mapOf("key_storage" to JsonArray(emptyList())),
            mapOf("user_authentication" to JsonPrimitive("iso_18045_high")),
            mapOf("attested_keys" to JsonArray(emptyList())),
            mapOf("attested_keys" to JsonArray(listOf(Jwk.parse(
                requireNotNull(holder.capabilities.privateKeyExporter).exportPrivateKey().toPrivateJwk(holder.spec),
            )))),
            mapOf("attested_keys" to JsonArray(listOf(buildJsonObject { put("kty", "oct"); put("k", "c2VjcmV0") }))),
        )
        for (claims in invalidClaims) {
            assertFailsWith<CredentialProofValidationException>(claims.toString()) {
                verifier.verify(request(proof(holder, attestation(attester, listOf(holder), claims))), configuration, context(attester))
            }
        }
        for (headers in listOf(mapOf("typ" to JsonPrimitive("oauth-client-attestation+jwt")), mapOf("jwk" to attester.exportPublicJwkObject()))) {
            assertFailsWith<CredentialProofValidationException> {
                verifier.verify(request(proof(holder, attestation(attester, listOf(holder), headers = headers))), configuration, context(attester))
            }
        }
    }

    @Test
    fun `assurance policy uses advertised acceptance sets`() = runTest {
        val attester = key()
        val holder = key()
        val required = configuration.copy(proofTypesSupported = mapOf(ProofType.JWT.value to ProofTypeMetadata(setOf("ES256"), KeyAttestationsRequired(setOf("iso_18045_high"), setOf("iso_18045_moderate")))))
        val claims = mapOf("key_storage" to JsonArray(listOf(JsonPrimitive("iso_18045_high"))), "user_authentication" to JsonArray(listOf(JsonPrimitive("iso_18045_moderate"))))
        verifier.verify(request(proof(holder, attestation(attester, listOf(holder), claims))), required, context(attester))
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(proof(holder, attestation(attester, listOf(holder)))), required, context(attester))
        }
    }

    @Test
    fun `both nonce checks preserve reusable nonce semantics`() = runTest {
        val attester = key()
        val holder = key()
        var checks = 0
        val nonceService = object : CredentialNonceService {
            override suspend fun issue(binding: CredentialNonceBinding) = IssuedCredentialNonce("nonce")
            override suspend fun validate(nonce: String, binding: CredentialNonceBinding): CredentialNonceValidationResult {
                checks++
                return if (nonce == "nonce") CredentialNonceValidationResult.VALID else CredentialNonceValidationResult.INVALID
            }
        }
        val context = context(attester).copy(nonceValidation = CredentialNonceValidationContext(nonceService, CredentialNonceBinding(issuer, "$issuer/credential", "$issuer/nonce")))
        val jwt = proof(holder, attestation(attester, listOf(holder)))
        repeat(2) { verifier.verify(request(jwt), configuration, context) }
        assertEquals(4, checks)
        val error = assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(proof(holder, attestation(attester, listOf(holder), mapOf("nonce" to JsonPrimitive("wrong"))))), configuration, context)
        }
        assertEquals(CredentialErrorCodes.INVALID_NONCE, error.errorCode)
    }

    @Test
    fun `one nested attestation larger than 64 KiB issues credentials for 33 keys when the key limit is disabled`() = runTest {
        val attester = key()
        val holders = List(33) { key() }
        val attestation = attestation(attester, holders, mapOf("padding" to JsonPrimitive("x".repeat(65_536))))
        assertTrue(attestation.length > 65_536)
        val request = request(CompactJws.sign(
            buildJsonObject { put("aud", issuer); put("iat", now.epochSeconds); put("nonce", "nonce") }.toString().encodeToByteArray(),
            holders[0], JwsAlgorithm.ES256,
            buildJsonObject {
                put("typ", "openid4vci-proof+jwt")
                put("jwk", holders[0].exportPublicJwkObject())
                put("key_attestation", attestation)
            },
        ))
        val provider = buildOAuth2Provider(createTestConfig(credentialProofVerifier = verifier))
        var allocated = 0
        val inputs = CredentialIssuanceInputProvider { count ->
            allocated += count
            List(count) { CredentialIssuanceInput(buildJsonObject { put("name", "Alice") }) }
        }
        // batch_size counts proofs, not attested keys or issued credentials.
        val response = assertIs<CredentialResponseResult.Success>(provider.createCredentialResponse(
            request = request, configuration = configuration, issuerKey = Crypto2CredentialSigningKey.select(key(), configuration), issuerId = issuer,
            issuanceInputData = inputs, proofValidationContext = context(attester, maxAttestedKeys = null).copy(batchCredentialIssuance = BatchCredentialIssuance(2)),
        ))
        assertEquals(holders.size, allocated)
        val keys = response.response.credentials!!.map {
            val credential = SDJwtVC.parse(it.credential.jsonPrimitive.content)
            Jwk.sha256Thumbprint(EncodedKey.Jwk(BinaryData(requireNotNull(credential.holderKeyJWK).toString().encodeToByteArray()), privateMaterial = false))
        }
        assertEquals(holders.map { Jwk.sha256Thumbprint(it.exportPublicJwk()) }, keys)
    }

    @Test
    fun `operational trust failures propagate without allocating credentials`() = runTest {
        val attester = key()
        val holder = key()
        val context = context(attester).copy(keyAttestation = KeyAttestationVerificationOptions(KeyAttestationTrustResolver { _, _, _ -> error("KMS unavailable") }))
        val provider = buildOAuth2Provider(createTestConfig(credentialProofVerifier = verifier))
        assertFailsWith<KeyAttestationServiceException> {
            provider.createCredentialResponse(
                request = request(proof(holder, attestation(attester, listOf(holder)))), configuration = configuration,
                issuerKey = Crypto2CredentialSigningKey.select(key(), configuration), issuerId = issuer, proofValidationContext = context,
                issuanceInputData = CredentialIssuanceInputProvider { error("Must not allocate") },
            )
        }
    }

    @Test
    fun `attested keys without a DID can produce W3C credentials when JWK binding is advertised`() = runTest {
        val attester = key()
        val holder = key()
        val provider = buildOAuth2Provider(createTestConfig(credentialProofVerifier = verifier))
        val issuerKey = key()
        val w3c = configuration.copy(format = CredentialFormat.JWT_VC_JSON)
        val response = provider.createCredentialResponse(
            request(proof(holder, attestation(attester, listOf(holder, key())))), w3c,
            Crypto2CredentialSigningKey.select(issuerKey, w3c), issuer, CredentialIssuanceInputProvider { count -> List(count) { CredentialIssuanceInput(w3cData()) } },
            proofValidationContext = context(attester),
        )
        val credentials = assertNotNull(assertIs<CredentialResponseResult.Success>(response).response.credentials)
        assertEquals(2, credentials.size)
        credentials.forEach { CompactJws.verify(it.credential.jsonPrimitive.content, issuerKey, JwsAlgorithm.ES256) }
    }

    @Test
    fun `ordinary JWT bindings use the registered credential handler requirements in both signing APIs`() = runTest {
        val holder = key()
        val jwt = proof(holder, null)
        val request = request(jwt, jwt)
        val w3c = configuration.copy(format = CredentialFormat.JWT_VC_JSON,
            proofTypesSupported = mapOf(ProofType.JWT.value to ProofTypeMetadata(setOf("ES256"))))
        val context = context(key())
        val verified = verifier.verify(request, w3c, context)
        assertEquals(2, verified.bindings.size)
        assertTrue(verified.bindings.all { it.holderDid == null })
        val issuerKey = key()
        val crypto2IssuerKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId("issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        var validations = 0
        var signatures = 0
        val legacy = CredentialEndpointHandler { _, _, _, _, batch, _, _, _, _, _, _, _, _, _, _ ->
            signatures++
            CredentialResponseResult.Success(CredentialResponse(credentials = batch.instances.map { IssuedCredential(JsonPrimitive("custom")) }))
        }
        val crypto2 = Crypto2CredentialEndpointHandler { _, _, _, _, batch, _, _, _, _, _, _, _, _, _, _ ->
            signatures++
            CredentialResponseResult.Success(CredentialResponse(credentials = batch.instances.map { IssuedCredential(JsonPrimitive("custom")) }))
        }
        val custom = object : CredentialEndpointHandler by legacy, Crypto2CredentialEndpointHandler by crypto2 {
            override suspend fun validateBindings(configuration: CredentialConfiguration, bindings: List<VerifiedCredentialBinding>) {
                validations++
                assertEquals(2, bindings.size)
                assertTrue(bindings.all { it.holderDid == null })
                assertEquals(setOf(Jwk.sha256Thumbprint(holder.exportPublicJwk())), bindings.map { Jwk.sha256Thumbprint(it.holderKey.exportPublicJwk()) }.toSet())
            }
        }
        for (useCrypto2 in listOf(false, true)) {
            for (useCustom in listOf(false, true)) {
                val providerConfig = createTestConfig(credentialProofVerifier = verifier)
                if (useCustom) providerConfig.credentialEndpointHandlers.register(CredentialFormat.JWT_VC_JSON, custom)
                val provider = buildOAuth2Provider(providerConfig)
                var allocated = 0
                val inputs = CredentialIssuanceInputProvider { count ->
                    allocated += count
                    List(count) { CredentialIssuanceInput(w3cData()) }
                }
                val response = if (useCrypto2) provider.createCredentialResponse(
                    request, w3c, Crypto2CredentialSigningKey.select(crypto2IssuerKey, w3c), issuer, inputs, proofValidationContext = context,
                ) else provider.createCredentialResponse(request, w3c, LegacyP256TestKey(issuerKey), issuer, inputs, proofValidationContext = context)
                val credentials = assertNotNull(assertIs<CredentialResponseResult.Success>(response).response.credentials)
                assertEquals(2, credentials.size)
                assertEquals(2, allocated)
                if (!useCustom) credentials.forEach { credential ->
                    val jwt = credential.credential.jsonPrimitive.content
                    if (useCrypto2) CompactJws.verify(jwt, crypto2IssuerKey, JwsAlgorithm.ES256)
                    else CompactJws.verify(jwt, issuerKey, JwsAlgorithm.ES256)
                    val payload = Json.parseToJsonElement(CompactJws.decodeUnverified(jwt).payload.decodeToString()).jsonObject
                    assertEquals(issuer, payload["iss"]?.jsonPrimitive?.content)
                    assertTrue(payload["sub"]?.jsonPrimitive?.content.isNullOrEmpty())
                    assertEquals("Alice", payload.getValue("vc").jsonObject.getValue("credentialSubject").jsonObject["name"]?.jsonPrimitive?.content)
                }
            }
        }
        assertEquals(2, validations)
        assertEquals(2, signatures)
    }

    private fun w3cData() = buildJsonObject {
        put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))))
        put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
        putJsonObject("credentialSubject") { put("name", "Alice") }
    }

    @Test
    fun `configuration round trips and required metadata cannot omit trust`() = runTest {
        val config = KeyAttestationConfig(KeyAttestationVerificationMethod.StaticJwk(key().exportPublicJwkObject()))
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToJsonElement(config)
        assertEquals(setOf("verificationMethod", "maxAttestedKeys"), encoded.jsonObject.keys)
        assertEquals(config, json.decodeFromJsonElement<KeyAttestationConfig>(encoded))
        assertFailsWith<IllegalArgumentException> { validateKeyAttestationConfiguration(listOf(configuration), null) }
        validateKeyAttestationConfiguration(listOf(configuration), config)
    }

    @Test
    fun `mixed optional proofs preserve provenance and select distinct keys`() = runTest {
        val attester = key()
        val holders = List(3) { key() }
        val optional = configuration.copy(proofTypesSupported = mapOf(ProofType.JWT.value to ProofTypeMetadata(setOf("ES256"))))
        val result = verifier.verify(request(
            proof(holders[0], attestation(attester, holders.take(2))),
            proof(holders[1], null), proof(holders[2], null),
        ), optional, context(attester))
        assertEquals(3, result.proofs.size)
        assertEquals(3, result.bindings.size)
        assertEquals(setOf(0, 1), result.bindings[1].proofIndexes)
        assertEquals(setOf(2), result.bindings[2].proofIndexes)
    }

    @Test
    fun `inner signing algorithm must be advertised independently of outer algorithm`() = runTest {
        val attester = key(KeySpec.Ec(EcCurve.P384))
        val holder = key()
        val jwt = proof(holder, attestation(attester, listOf(holder)))
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(jwt), configuration, context(attester))
        }
        val accepted = configuration.copy(proofTypesSupported = mapOf(ProofType.JWT.value to ProofTypeMetadata(setOf("ES256", "ES384"))))
        assertEquals(1, verifier.verify(request(jwt), accepted, context(attester)).bindings.size)
    }

    @Test
    fun `DID signing key retains its identifier and additional keys cannot inherit it`() = runTest {
        DidService.minimalInit()
        val attester = key()
        val holder = key()
        val extra = key()
        val did = DidService.registerByKey("jwk", holder).did
        suspend fun didProof(keys: List<Key>) = CompactJws.sign(
            buildJsonObject { put("aud", issuer); put("iat", now.epochSeconds) }.toString().encodeToByteArray(),
            holder, JwsAlgorithm.ES256,
            buildJsonObject {
                put("typ", "openid4vci-proof+jwt"); put("kid", "$did#0")
                put("key_attestation", attestation(attester, keys))
            },
        )
        val didConfiguration = configuration.copy(cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Did("jwk")))
        val single = verifier.verify(request(didProof(listOf(holder))), didConfiguration, context(attester))
        assertEquals(did, single.bindings.single().holderDid)
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(didProof(listOf(holder, extra))), didConfiguration, context(attester))
        }
        val mixedConfiguration = didConfiguration.copy(cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Did("jwk"), CryptographicBindingMethod.Jwk))
        val multiple = verifier.verify(request(didProof(listOf(holder, extra))), mixedConfiguration, context(attester))
        assertEquals(did, multiple.bindings[0].holderDid)
        assertNull(multiple.bindings[1].holderDid)
        assertNull(multiple.bindings[1].holderKid)
    }

    @Test
    fun `unsupported mdoc binding fails before allocating issuance inputs`() = runTest {
        val attester = key()
        val holder = key()
        val rsa = key(KeySpec.Rsa(2048))
        val provider = buildOAuth2Provider(createTestConfig(credentialProofVerifier = verifier))
        val mdoc = configuration.copy(format = CredentialFormat.MSO_MDOC, doctype = "example", vct = null,
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.CoseKey))
        val result = provider.createCredentialResponse(
            request = request(proof(holder, attestation(attester, listOf(holder, rsa)))),
            configuration = mdoc,
            issuerKey = Crypto2CredentialSigningKey.select(key(), mdoc), issuerId = issuer, proofValidationContext = context(attester),
            issuanceInputData = CredentialIssuanceInputProvider { error("Must not allocate") },
        )
        assertEquals(CredentialErrorCodes.INVALID_PROOF, assertIs<CredentialResponseResult.Failure>(result).error.error)
    }

    @Test
    fun `deployment policy sees verified evidence and may reject it`() = runTest {
        val attester = key()
        val holder = key()
        val context = context(attester)
        var policyCalls = 0
        val policy = KeyAttestationPolicy { evidence, _, _ ->
            policyCalls++
            assertEquals(1, evidence.attestedKeys.size)
            false
        }
        val rejecting = context.copy(keyAttestation = context.keyAttestation!!.copy(policy = policy))
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(proof(holder, attestation(key(), listOf(holder)))), configuration, rejecting)
        }
        assertEquals(0, policyCalls)
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(request(proof(holder, attestation(attester, listOf(holder)))), configuration, rejecting)
        }
        assertEquals(1, policyCalls)
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun `mdoc device keys use each selected binding`() = runTest {
        val attester = key()
        val holders = List(3) { key() }
        val request = request(proof(holders[0], attestation(attester, holders)))
        val mdocConfiguration = configuration.copy(format = CredentialFormat.MSO_MDOC, doctype = "example", vct = null,
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.CoseKey))
        val result = verifier.verify(request, mdocConfiguration, context(attester))
        val issuerKey = CryptoRuntime(defaultSoftwareKeyProviders())
            .generateSoftwareKey(GenerateSoftwareKeyRequest(
                KeyId("issuer"), KeySpec.Ec(EcCurve.P256),
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ))
        for (binding in result.bindings) {
            val credential = MdocCredentialSigner.generateMdocCredential(
                credentialData = buildJsonObject { put("example", buildJsonObject { put("name", "Alice") }) },
                issuerKey = issuerKey, signatureAlgorithm = -7,
                issuerCertificate = listOf(CoseCertificate(byteArrayOf(1, 2, 3))),
                docType = "example", verifiedBinding = binding,
            )
            val mso = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(credential.base64UrlDecode()).decodeMobileSecurityObject()
            assertEquals(binding.holderKey.exportPublicJwk().toCoseKey(), mso.deviceKeyInfo.deviceKey)
        }
    }

    private suspend fun key(spec: KeySpec = KeySpec.Ec(EcCurve.P256)): Key =
        CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId("test-key"), spec, setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
    private suspend fun context(attester: Key, maxAttestedKeys: Int? = 20) = CredentialProofValidationContext(
        credentialIssuer = issuer,
        batchCredentialIssuance = BatchCredentialIssuance(10),
        keyAttestation = KeyAttestationConfig(
            KeyAttestationVerificationMethod.StaticJwk(attester.exportPublicJwkObject()), maxAttestedKeys,
        ).toVerificationOptions(),
    )
    private suspend fun attestation(attester: Key, keys: List<Key>, claims: Map<String, JsonElement> = emptyMap(), headers: Map<String, JsonElement> = emptyMap()): String = CompactJws.sign(
        buildJsonObject {
            put("iat", now.epochSeconds)
            put("exp", now.epochSeconds + 300)
            put("nonce", "nonce")
            put("attested_keys", JsonArray(keys.map { it.exportPublicJwkObject() }))
            claims.forEach { (key, value) -> put(key, value) }
        }.toString().encodeToByteArray(),
        attester, attester.preferredJwsAlgorithm(),
        JsonObject(mapOf("typ" to JsonPrimitive("key-attestation+jwt")) + headers),
    )
    private suspend fun proof(holder: Key, attestation: String?): String = CompactJws.sign(
        buildJsonObject { put("aud", issuer); put("iat", now.epochSeconds); put("nonce", "nonce") }.toString().encodeToByteArray(),
        holder, JwsAlgorithm.ES256,
        buildJsonObject {
            put("typ", JsonPrimitive("openid4vci-proof+jwt"))
            put("jwk", holder.exportPublicJwkObject())
            attestation?.let { put("key_attestation", JsonPrimitive(it)) }
        },
    )
    private fun request(vararg proofs: String) = DefaultCredentialRequest(
        client = DefaultClient("client", emptyList(), emptySet(), emptySet(), setOf("credential")),
        credentialIdentifier = null, credentialConfigurationId = "identity",
        proofs = Proofs(jwt = proofs.toList()), credentialResponseEncryption = null,
    )
}
