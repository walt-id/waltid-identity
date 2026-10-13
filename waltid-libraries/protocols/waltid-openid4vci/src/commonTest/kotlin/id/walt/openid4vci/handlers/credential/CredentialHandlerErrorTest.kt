package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto.keys.Key as LegacyKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.cose.verify
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.LegacyP256TestKey
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.*
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.CredentialProofServiceException
import id.walt.openid4vci.proofs.CredentialProofValidationException
import id.walt.openid4vci.proofs.VerifiedCredentialBinding
import id.walt.openid4vci.proofs.Proofs
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.sdjwt.SDJwt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Instant

class CredentialHandlerErrorTest {
    private val formats = listOf(CredentialFormat.SD_JWT_VC, CredentialFormat.JWT_VC_JSON, CredentialFormat.MSO_MDOC)

    @Test
    fun `missing bindings fail even when the request supplies a JWT in both signing APIs`() = runTest {
        val fixture = fixture()
        val requests = listOf(
            null, fixture.jwtProof(), Proofs(jwt = emptyList()), Proofs(jwt = listOf("malformed")),
            Proofs(attestation = listOf("handled-by-verifier")),
        )
        for (format in formats) for (crypto2 in listOf(false, true)) for (proofs in requests) {
            val failure = assertIs<CredentialResponseResult.Failure>(
                fixture.issue(format, crypto2, proofs = proofs, bindings = emptyList()),
                "$format, crypto2=$crypto2, proofs=$proofs",
            )
            assertEquals(CredentialErrorCodes.INVALID_PROOF, failure.error.error)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `each credential uses its selected binding regardless of request proofs in both APIs`() = runTest {
        val fixture = fixture()
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val bindings = List(2) { index ->
            val holder = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
                KeyId("holder-$index"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ))
            VerifiedCredentialBinding(holder, holderDid = "did:example:holder-$index")
        }
        // Call handlers directly: proof validation belongs to the provider/verifier and is covered separately.
        // The JWT names a different key from both selected bindings, so fallback would bind the wrong key.
        val requests = listOf(
            null, fixture.jwtProof(), Proofs(jwt = listOf("malformed")),
            Proofs(attestation = listOf("handled-by-verifier")),
        )
        for (format in formats) for (crypto2 in listOf(false, true)) for (proofs in requests) {
            val response = assertIs<CredentialResponseResult.Success>(fixture.issue(format, crypto2, proofs, bindings)).response
            val credentials = assertNotNull(response.credentials)
            assertEquals(bindings.size, credentials.size)
            credentials.zip(bindings).forEach { (credential, binding) ->
                val encoded = credential.credential.jsonPrimitive.content
                when (format) {
                    CredentialFormat.SD_JWT_VC -> {
                        CompactJws.verify(encoded.substringBefore('~'), fixture.issuer, JwsAlgorithm.ES256)
                        val jwk = SDJwt.parse(encoded).fullPayload.getValue("cnf").jsonObject.getValue("jwk").jsonObject
                        val encodedJwk = EncodedKey.Jwk(BinaryData(jwk.toString().encodeToByteArray()), privateMaterial = false)
                        assertEquals(Jwk.sha256Thumbprint(binding.holderKey.exportPublicJwk()), Jwk.sha256Thumbprint(encodedJwk))
                        assertEquals(binding.holderKey.id.value, jwk["kid"]?.jsonPrimitive?.content)
                    }
                    CredentialFormat.JWT_VC_JSON -> {
                        val payload = Json.parseToJsonElement(CompactJws.verify(encoded, fixture.issuer, JwsAlgorithm.ES256).payload.decodeToString()).jsonObject
                        assertEquals(binding.holderDid, payload["sub"]?.jsonPrimitive?.content)
                    }
                    CredentialFormat.MSO_MDOC -> {
                        val issuerSigned = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(encoded.base64UrlDecode())
                        assertTrue(issuerSigned.issuerAuth.verify(fixture.issuer, -7))
                        val mso = issuerSigned.decodeMobileSecurityObject()
                        assertEquals(binding.holderKey.exportPublicJwk().toCoseKey(), mso.deviceKeyInfo.deviceKey)
                    }
                    else -> error("Unexpected test format")
                }
            }
        }
    }

    @Test
    fun `signing failures escape all built in handlers regardless of message in both APIs`() = runTest {
        val fixture = fixture()
        for (format in formats) for (crypto2 in listOf(false, true)) {
            for (failure in listOf(
                IllegalStateException("holder key signing service unavailable"),
                IllegalArgumentException("Proof JWT signer configuration is invalid"),
                CredentialProofServiceException("Missing JWT proof storage connection"),
                CancellationException("cancelled"),
            )) {
                assertSame(failure, assertFails {
                    fixture.issue(format, crypto2, signingFailure = failure)
                }, "$format, crypto2=$crypto2")
            }
        }
    }

    @Test
    fun `holder key export failures are operational in both APIs`() = runTest {
        val fixture = fixture()
        for (format in listOf(CredentialFormat.SD_JWT_VC, CredentialFormat.MSO_MDOC)) {
            for (crypto2 in listOf(false, true)) for (failure in listOf(
                IllegalArgumentException("holder key export unavailable"), CancellationException("cancelled"),
            )) {
                val unavailableKey = object : Key by fixture.issuer {
                    override val capabilities = fixture.issuer.capabilities.copy(
                        publicKeyExporter = PublicKeyExporter { throw failure },
                    )
                }
                assertSame(failure, assertFails {
                    fixture.issue(format, crypto2, bindings = listOf(VerifiedCredentialBinding(unavailableKey)))
                })
            }
        }
    }

    @Test
    fun `extension handler preserves typed error codes independently of wording`() = runTest {
        val fixture = fixture()
        for (code in listOf(CredentialErrorCodes.INVALID_PROOF, CredentialErrorCodes.INVALID_NONCE)) {
            for (message in listOf("Rejected input", "Missing JWT proof", "holder key")) {
                val handler = msoHandler { throw CredentialProofValidationException(code, message) }
                val failure = assertIs<CredentialResponseResult.Failure>(fixture.issue(
                    CredentialFormat.MSO_MDOC, crypto2 = false, handler = handler,
                ))
                assertEquals(code, failure.error.error)
                assertEquals(message, failure.error.description)
            }
        }
        for (failure in listOf(IllegalStateException("holder key service failed"), CancellationException("cancelled"))) {
            assertSame(failure, assertFails {
                fixture.issue(CredentialFormat.MSO_MDOC, crypto2 = false, handler = msoHandler { throw failure })
            })
        }
        val missingKey = assertIs<CredentialResponseResult.Failure>(fixture.issue(
            CredentialFormat.MSO_MDOC, crypto2 = false, handler = msoHandler { error("Must not sign") },
            bindings = emptyList(),
        ))
        assertEquals(CredentialErrorCodes.INVALID_PROOF, missingKey.error.error)
    }

    @Test
    fun `existing explicit request errors keep their codes`() = runTest {
        val fixture = fixture()
        for (crypto2 in listOf(false, true)) {
            val missingVct = assertIs<CredentialResponseResult.Failure>(fixture.issue(
                CredentialFormat.SD_JWT_VC, crypto2,
                configuration = CredentialConfiguration(CredentialFormat.SD_JWT_VC),
            ))
            assertEquals(CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST, missingVct.error.error)
            val unsupported = assertIs<CredentialResponseResult.Failure>(fixture.issue(
                CredentialFormat.SD_JWT_VC, crypto2, handler = W3cJwtVcCredentialHandler(),
            ))
            assertEquals(CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION, unsupported.error.error)
        }
    }

    private fun msoHandler(issue: () -> String) = object : MsoMdocCredentialHandler() {
        override suspend fun issueMdoc(
            docType: String, namespaceData: Map<String, JsonObject>, holderKey: Key,
            issuerKey: LegacyKey, x5Chain: List<X509Certificate>?, validFrom: Instant?, validUntil: Instant?,
            expectedUpdate: Instant?, validityDays: Int,
        ): String = issue()
    }

    private suspend fun fixture(): Fixture {
        val issuer = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId("issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            issuer, SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) { subjectDn = "CN=credential handler test" }
        return Fixture(issuer, LegacyP256TestKey(issuer), certificate)
    }

    private class Fixture(val issuer: Key, val legacyIssuer: LegacyKey, val certificate: X509Certificate) {
        suspend fun jwtProof() = Proofs(jwt = listOf(CompactJws.sign(
            buildJsonObject { put("aud", "https://issuer.example"); put("iat", kotlin.time.Clock.System.now().epochSeconds) }
                .toString().encodeToByteArray(),
            issuer, JwsAlgorithm.ES256,
            buildJsonObject { put("typ", "openid4vci-proof+jwt"); put("jwk", issuer.exportPublicJwkObject()) },
        )))

        suspend fun issue(
            format: CredentialFormat,
            crypto2: Boolean,
            proofs: Proofs? = null,
            bindings: List<VerifiedCredentialBinding> = listOf(VerifiedCredentialBinding(issuer, holderDid = "did:example:holder")),
            signingFailure: Exception? = null,
            configuration: CredentialConfiguration = CredentialConfiguration(format, vct = "identity", doctype = "org.example.identity"),
            handler: CredentialEndpointHandler = when (format) {
                CredentialFormat.SD_JWT_VC -> SdJwtVcCredentialHandler()
                CredentialFormat.MSO_MDOC -> MdocCredentialHandler()
                else -> W3cJwtVcCredentialHandler()
            },
        ): CredentialResponseResult {
            val request = DefaultCredentialRequest(
                client = DefaultClient("client", emptyList(), emptySet(), emptySet()),
                credentialIdentifier = null, credentialConfigurationId = "identity",
                proofs = proofs, credentialResponseEncryption = null,
            )
            val data = when (format) {
                CredentialFormat.MSO_MDOC -> buildJsonObject { putJsonObject("org.example") { put("given_name", "Jane") } }
                CredentialFormat.JWT_VC_JSON -> buildJsonObject {
                    put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))))
                    put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
                    putJsonObject("credentialSubject") { put("given_name", "Jane") }
                }
                else -> buildJsonObject { put("given_name", "Jane") }
            }
            val batch = CredentialIssuanceBatch(List(bindings.size.coerceAtLeast(1)) { CredentialIssuanceInput(data) }, bindings)
            if (crypto2) {
                val key = if (signingFailure == null) issuer else object : Key by issuer {
                    override val capabilities = issuer.capabilities.copy(signer = Signer { _, _ -> throw signingFailure })
                }
                return (handler as Crypto2CredentialEndpointHandler).sign(
                    request, configuration, Crypto2CredentialSigningKey.select(key, configuration), "https://issuer.example", batch,
                    dataMapping = null, selectiveDisclosure = null, x5Chain = listOf(certificate), display = null,
                    w3cVersion = null, mDocNameSpacesDataMappingConfig = null, authorizedTransactionDataTypes = null,
                    validFrom = null, validUntil = null, expectedUpdate = null,
                )
            }
            return handler.sign(
                request, configuration, signingFailure?.let { FailingSigningKey(legacyIssuer, it) } ?: legacyIssuer,
                "https://issuer.example", batch,
                dataMapping = null, selectiveDisclosure = null, x5Chain = listOf(certificate), display = null,
                w3cVersion = null, mDocNameSpacesDataMappingConfig = null, authorizedTransactionDataTypes = null,
                validFrom = null, validUntil = null, expectedUpdate = null,
            )
        }
    }

    /** Keep real key metadata and public material; fail precisely when a legacy signer is invoked. */
    private class FailingSigningKey(private val key: LegacyKey, private val failure: Exception) : LegacyKey() {
        override val keyType get() = key.keyType
        override val hasPrivateKey get() = key.hasPrivateKey
        override suspend fun getKeyId() = key.getKeyId()
        override suspend fun getThumbprint() = key.getThumbprint()
        override suspend fun exportJWK() = key.exportJWK()
        override suspend fun exportJWKObject() = key.exportJWKObject()
        override suspend fun exportPEM() = key.exportPEM()
        override suspend fun getPublicKey() = key.getPublicKey()
        override suspend fun getPublicKeyRepresentation() = key.getPublicKeyRepresentation()
        override suspend fun getMeta() = key.getMeta()
        override suspend fun deleteKey() = key.deleteKey()
        override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?) =
            key.verifyRaw(signed, detachedPlaintext, customSignatureAlgorithm)
        override suspend fun verifyJws(signedJws: String) = key.verifyJws(signedJws)
        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String = throw failure
        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any = throw failure
    }
}
