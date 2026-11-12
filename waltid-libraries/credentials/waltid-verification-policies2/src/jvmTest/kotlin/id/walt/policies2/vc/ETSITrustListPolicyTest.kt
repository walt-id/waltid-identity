package id.walt.policies2.vc

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.W3C11
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.crypto.keys.Key as LegacyKey
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.DidResolver
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.ETSITrustListPolicy
import id.walt.policies2.vc.policies.PolicyExecutionContext
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.documentsigner.builder.Crypto2IACASignerSpecification
import id.walt.x509.iso.documentsigner.builder.DocumentSignerCertificateBuilder
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.iaca.builder.IACACertificateBuilder
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Unit tests for ETSITrustListPolicy.
 * 
 * Note: These tests validate the policy configuration and error handling.
 * Full integration tests with real credentials and a running trust-registry
 * service should be added separately.
 */
class ETSITrustListPolicyTest {

    @Test
    fun `test policy requires trustRegistryUrl`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ETSITrustListPolicy(
                trustRegistryUrl = ""
            )
        }
        assertTrue(exception.message!!.contains("trustRegistryUrl"))
    }

    @Test
    fun `test policy requires http or https url`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ETSITrustListPolicy(
                trustRegistryUrl = "ftp://example.com"
            )
        }
        assertTrue(exception.message!!.contains("http"))
    }

    @Test
    fun `test policy accepts http url`() {
        val policy = ETSITrustListPolicy(
            trustRegistryUrl = "http://localhost:7000"
        )
        assertEquals("etsi-trust-list", policy.id)
        assertEquals("http://localhost:7000", policy.trustRegistryUrl)
    }

    @Test
    fun `test policy accepts https url`() {
        val policy = ETSITrustListPolicy(
            trustRegistryUrl = "https://trust.example.com"
        )
        assertEquals("etsi-trust-list", policy.id)
        assertEquals("https://trust.example.com", policy.trustRegistryUrl)
    }

    @Test
    fun `test policy with all options`() {
        val policy = ETSITrustListPolicy(
            trustRegistryUrl = "https://trust.example.com",
            expectedEntityType = "PID_PROVIDER",
            expectedServiceType = "QCert",
            allowStaleSource = true,
            requireAuthenticated = true
        )
        
        assertEquals("etsi-trust-list", policy.id)
        assertEquals("https://trust.example.com", policy.trustRegistryUrl)
        assertEquals("PID_PROVIDER", policy.expectedEntityType)
        assertEquals("QCert", policy.expectedServiceType)
        assertTrue(policy.allowStaleSource)
        assertTrue(policy.requireAuthenticated)
    }

    @Test
    fun `test policy serialization round-trip`() {
        val originalPolicy = ETSITrustListPolicy(
            trustRegistryUrl = "https://trust.example.com",
            expectedEntityType = "PID_PROVIDER",
            allowStaleSource = true
        )
        
        val json = Json.encodeToString(ETSITrustListPolicy.serializer(), originalPolicy)
        // Note: The "policy" discriminator only appears in polymorphic serialization 
        // (when serializing as CredentialVerificationPolicy2), not when serializing directly
        assertTrue(json.contains("\"trustRegistryUrl\":\"https://trust.example.com\""))
        assertTrue(json.contains("\"expectedEntityType\":\"PID_PROVIDER\""))
        assertTrue(json.contains("\"allowStaleSource\":true"))
        
        val deserializedPolicy = Json.decodeFromString(ETSITrustListPolicy.serializer(), json)
        assertEquals(originalPolicy, deserializedPolicy)
    }

    @Test
    fun `authenticated source is required even when stale source is allowed`() {
        val result = ETSITrustListPolicy(
            allowStaleSource = true,
            requireAuthenticated = true
        ).evaluateDecision(
            ETSITrustListPolicy.TrustDecisionResponse(
                decision = "STALE_SOURCE",
                sourceFreshness = "EXPIRED",
                authenticity = "UNVERIFIED"
            )
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("not authenticated"))
    }

    @Test
    fun `structured source assurance is accepted from new service responses`() {
        val result = ETSITrustListPolicy(requireAuthenticated = true).evaluateDecision(
            ETSITrustListPolicy.TrustDecisionResponse(
                decision = "TRUSTED",
                sourceFreshness = "FRESH",
                sourceAssurance = ETSITrustListPolicy.SourceAssuranceDto(
                    signatureStatus = "VALID",
                    signerTrust = "TRUSTED",
                    authenticityState = "AUTHENTICATED",
                    accepted = true
                )
            )
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `failed source authenticity is rejected in permissive mode`() {
        val result = ETSITrustListPolicy(
            allowStaleSource = true,
            requireAuthenticated = false
        ).evaluateDecision(
            ETSITrustListPolicy.TrustDecisionResponse(
                decision = "TRUSTED",
                sourceFreshness = "FRESH",
                authenticity = "FAILED"
            )
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("authenticity validation failed"))
    }

    // ---------------------------------------------------------------------------
    // verify() with a no-x5c, DID-based JWT issuer — no network access needed:
    // did:jwk is self-contained, and the "unresolvable issuer" case never reaches
    // DID resolution at all (see below).
    // ---------------------------------------------------------------------------

    @Test
    fun `verify trusts a no-x5c JWT credential whose did-jwk issuer is in the inline trust list`() = runTest {
        val (credential, did) = didJwkSignedCredential(KeyId("trusted-issuer-key"))
        val policy = ETSITrustListPolicy(trustLists = listOf(loteRegisteringDidJwkIssuer(did)))

        val result = policy.verify(credential, PolicyExecutionContext.Empty)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
    }

    @Test
    fun `verify rejects a no-x5c JWT credential whose did-jwk issuer is resolvable but not in the trust list`() = runTest {
        val (credential, _) = didJwkSignedCredential(KeyId("untrusted-issuer-key"))
        // Trust list registers a different, unrelated did:jwk — not the credential's issuer.
        val (_, otherDid) = didJwkSignedCredential(KeyId("some-other-registered-key"))
        val policy = ETSITrustListPolicy(trustLists = listOf(loteRegisteringDidJwkIssuer(otherDid)))

        val result = policy.verify(credential, PolicyExecutionContext.Empty)

        assertTrue(result.isFailure)
    }

    @Test
    fun `verify fails with a resolution error when the JWT issuer identifier is neither a DID nor an HTTPS URL`() = runTest {
        val (key, _) = generateIssuerKeyAndDid(KeyId("unresolvable-issuer-key"))
        val signed = CompactJws.sign(
            payload = Json.encodeToString(
                buildJsonObject { put("iss", "not-a-did-or-url") },
            ).encodeToByteArray(),
            key = key,
            algorithm = JwsAlgorithm.ES256,
        )
        val decoded = CompactJws.decodeUnverified(signed)
        val payload = Json.parseToJsonElement(decoded.payload.decodeToString()) as JsonObject
        val credential: DigitalCredential = W3C11(
            credentialData = payload,
            signature = JwtCredentialSignature(signed, decoded.protectedHeader),
            signed = signed,
        )
        // Key resolution fails before the trust list is ever loaded, so its content is
        // irrelevant here — any validly-formed source works, e.g. one registering an unrelated key.
        val (_, unrelatedDid) = generateIssuerKeyAndDid(KeyId("irrelevant-key"))
        val policy = ETSITrustListPolicy(trustLists = listOf(loteRegisteringDidJwkIssuer(unrelatedDid)))

        val result = policy.verify(credential, PolicyExecutionContext.Empty)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()!!.message!!.contains("issuer's public key could not be resolved"),
            result.exceptionOrNull()?.message,
        )
    }

    // ---------------------------------------------------------------------------
    // verify() with an x5c chain. The trust list registers only the root; the
    // untrusted signer is self-signed and appends that root to its own x5c (WAL-1638).
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
        "sd-jwt-x5c-ds.txt, true",
        "sd-jwt-x5c-ds-root.txt, true",
        "sd-jwt-x5c-untrusted-root.txt, false",
        "mdoc-x5chain-ds.txt, true",
        "mdoc-x5chain-untrusted-root.txt, false",
    )
    fun `verify trusts an x5c chain only when its signer chains to the listed root`(
        fixture: String,
        trusted: Boolean,
    ) = runTest {
        val credential = CredentialParser.detectAndParse(x5cFixture("wal-1638", fixture)).second
        val policy = ETSITrustListPolicy(trustLists = listOf(x5cFixture("wal-1638", "lote-iaca-root.json")))

        val result = policy.verify(credential, PolicyExecutionContext.Empty)

        assertEquals(trusted, result.isSuccess, result.exceptionOrNull()?.message)
    }

    // ---------------------------------------------------------------------------
    // verify() alongside the `signature` policy: trust must be decided for the key
    // that verified the signature. With a DID `iss`, that is the DID key, even when
    // an `x5c` chain is present (WAL-1637).
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
        "wal-1638, sd-jwt-x5c-ds.txt, true",
        "wal-1637, sd-jwt-didjwk-iss.txt, false",
        "wal-1637, sd-jwt-didjwk-iss-ds-x5c.txt, false",
    )
    fun `verify trusts only the key the signature policy verified with`(
        directory: String,
        fixture: String,
        trusted: Boolean,
    ) = runTest {
        DidService.minimalInit()
        val credential = CredentialParser.detectAndParse(x5cFixture(directory, fixture)).second
        val policy = ETSITrustListPolicy(trustLists = listOf(x5cFixture("wal-1638", "lote-iaca-root.json")))

        val signature = CredentialSignaturePolicy().verify(credential, PolicyExecutionContext.Empty)
        val trust = policy.verify(credential, PolicyExecutionContext.Empty)

        assertTrue(signature.isSuccess, signature.exceptionOrNull()?.message)
        assertEquals(trusted, trust.isSuccess, trust.exceptionOrNull()?.message)
    }

    @Test
    fun `verify resolves the issuer key from the signed JWT, not from the parsed credential data`() = runTest {
        val (signerKey, signerDid) = generateIssuerKeyAndDid(KeyId("signing-key"))
        val (_, listedDid) = generateIssuerKeyAndDid(KeyId("listed-key"))
        val signed = CompactJws.sign(
            payload = Json.encodeToString(buildJsonObject { put("iss", signerDid) }).encodeToByteArray(),
            key = signerKey,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject { put("kid", "$signerDid#0") },
        )
        val credential: DigitalCredential = W3C11(
            credentialData = buildJsonObject { put("iss", listedDid) },
            signature = JwtCredentialSignature(signed, CompactJws.decodeUnverified(signed).protectedHeader),
            signed = signed,
        )
        val policy = ETSITrustListPolicy(trustLists = listOf(loteRegisteringDidJwkIssuer(listedDid)))

        val result = policy.verify(credential, PolicyExecutionContext.Empty)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()!!.message!!.startsWith("Certificate not trusted"),
            result.exceptionOrNull()?.message,
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `verify checks the x5c chain when its leaf certificate holds the DID issuer key`(rootListed: Boolean) = runTest {
        val (signerKey, signerDid) = generateIssuerKeyAndDid(KeyId("document-signer-key"))
        val chain = documentSignerChain(signerKey)
        val credential = signedSdJwtVc(signerKey, signerDid, "$signerDid#0", chain.signerCertificate)
        // Lists only a root: a public-key match on the Document Signer key alone would fail.
        val listedRoot = if (rootListed) chain.rootCertificate
        else documentSignerChain(generateP256Key(KeyId("unrelated-signer-key"))).rootCertificate
        val policy = ETSITrustListPolicy(trustLists = listOf(loteListingRoot(listedRoot)))

        val signature = CredentialSignaturePolicy().verify(credential, PolicyExecutionContext.Empty)
        val trust = policy.verify(credential, PolicyExecutionContext.Empty)

        assertTrue(signature.isSuccess, signature.exceptionOrNull()?.message)
        assertEquals(rootListed, trust.isSuccess, trust.exceptionOrNull()?.message)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `verify rejects a did-web issuer whose document changes after the signature check`(
        signerCertificateInX5c: Boolean,
    ) = runTest {
        DidService.minimalInit()
        val did = "did:web:issuer.example"
        val attackerKey = generateP256Key(KeyId("attacker-key"))
        val listedKey = generateP256Key(KeyId("listed-signer-key"))
        val chain = documentSignerChain(listedKey)
        val credential = signedSdJwtVc(
            attackerKey, did, "$did#key-1", chain.signerCertificate.takeIf { signerCertificateInX5c }
        )
        val policy = ETSITrustListPolicy(
            trustLists = listOf(
                if (signerCertificateInX5c) loteListingRoot(chain.rootCertificate)
                else loteWithServiceIdentity(""""PublicKeyValues": [${publicJwk(listedKey)}]""")
            )
        )
        val didWeb = SwitchableDidWebResolver(publicJwk(attackerKey))
        val previous = DidService.resolverMethods["web"]
        DidService.registerResolverForMethod("web", didWeb)
        try {
            val signature = CredentialSignaturePolicy().verify(credential, PolicyExecutionContext.Empty)
            // The DID host now serves the listed key instead of the one that signed the credential.
            didWeb.jwk = publicJwk(listedKey)
            val trust = policy.verify(credential, PolicyExecutionContext.Empty)

            assertTrue(signature.isSuccess, signature.exceptionOrNull()?.message)
            assertTrue(trust.isFailure)
            assertTrue(
                trust.exceptionOrNull()!!.message!!.contains("does not verify the credential signature"),
                trust.exceptionOrNull()?.message,
            )
        } finally {
            previous?.let { DidService.registerResolverForMethod("web", it) }
        }
    }

    // ---------------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------------

    private fun x5cFixture(directory: String, name: String): String =
        this::class.java.classLoader.getResource("etsi-trust-list/$directory/$name")!!.readText().trim()

    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    private suspend fun generateP256Key(keyId: KeyId): Crypto2Key =
        runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = keyId,
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )

    private suspend fun publicJwk(key: Crypto2Key): JsonObject =
        Json.parseToJsonElement(
            key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec).data.toByteArray().decodeToString()
        ).jsonObject

    /** Generates a P-256 key and derives its self-contained `did:jwk:` identifier. */
    private suspend fun generateIssuerKeyAndDid(keyId: KeyId): Pair<Crypto2Key, String> {
        DidService.minimalInit()
        val key = generateP256Key(keyId)
        val did = "did:jwk:" + base64Url.encode(publicJwk(key).toString().encodeToByteArray())
        return key to did
    }

    /** Base64 DER certificates of a fresh IACA root and a Document Signer certificate for [signerKey]. */
    private class DocumentSignerChain(val rootCertificate: String, val signerCertificate: String)

    private suspend fun documentSignerChain(signerKey: Crypto2Key): DocumentSignerChain {
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val now = Clock.System.now()
        val rootKey = generateP256Key(KeyId("iaca-root-key"))
        val rootProfile = IACACertificateProfileData(
            principalName = IACAPrincipalName(country = "AT", commonName = "Test IACA"),
            validityPeriod = X509ValidityPeriod(now - 1.days, now + 365.days),
            issuerAlternativeName = IssuerAlternativeName(uri = "https://iaca.example"),
        )
        val rootDer = IACACertificateBuilder().buildDer(rootProfile, rootKey, algorithm)
        val signerDer = DocumentSignerCertificateBuilder().buildDer(
            profileData = DocumentSignerCertificateProfileData(
                principalName = DocumentSignerPrincipalName(country = "AT", commonName = "Test Document Signer"),
                validityPeriod = X509ValidityPeriod(now, now + 30.days),
                crlDistributionPointUri = "https://iaca.example/crl",
            ),
            publicKey = signerKey,
            iacaSignerSpec = Crypto2IACASignerSpecification(rootProfile, rootKey, algorithm),
        )
        return DocumentSignerChain(
            rootCertificate = Base64.encode(rootDer.bytes.toByteArray()),
            signerCertificate = Base64.encode(signerDer.bytes.toByteArray()),
        )
    }

    /** An SD-JWT VC without disclosures, signed by [key], with an optional single-certificate `x5c`. */
    private suspend fun signedSdJwtVc(
        key: Crypto2Key,
        iss: String,
        kid: String,
        x5cCertificate: String?,
    ): DigitalCredential {
        val signed = CompactJws.sign(
            payload = Json.encodeToString(
                buildJsonObject {
                    put("iss", iss)
                    put("vct", "https://example.org/vct/a")
                }
            ).encodeToByteArray(),
            key = key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", "dc+sd-jwt")
                put("kid", kid)
                x5cCertificate?.let { putJsonArray("x5c") { add(it) } }
            },
        )
        return CredentialParser.detectAndParse("$signed~").second
    }

    /** A `did:web` resolver serving one verification method, `#key-1`, whose key the test can swap. */
    private class SwitchableDidWebResolver(var jwk: JsonObject) : DidResolver {
        override val name: String = "switchable-did-web-resolver"

        override suspend fun getSupportedMethods(): Result<Set<String>> = Result.success(setOf("web"))

        override suspend fun resolve(did: String): Result<JsonObject> = Result.success(
            buildJsonObject {
                put("id", did)
                putJsonArray("assertionMethod") { add("$did#key-1") }
                putJsonArray("verificationMethod") {
                    add(buildJsonObject {
                        put("id", "$did#key-1")
                        put("type", "JsonWebKey2020")
                        put("controller", did)
                        put("publicKeyJwk", jwk)
                    })
                }
            }
        )

        override suspend fun resolveToKey(did: String): Result<LegacyKey> =
            Result.failure(UnsupportedOperationException("crypto2 key resolution only"))

        override suspend fun resolveToKeys(did: String): Result<Set<LegacyKey>> =
            Result.failure(UnsupportedOperationException("crypto2 key resolution only"))
    }

    /** A minimal ETSI TS 119 602 LoTE JSON source registering only the root certificate [rootCertificate]. */
    private fun loteListingRoot(rootCertificate: String): String =
        loteWithServiceIdentity(""""X509Certificates": [{"val": "$rootCertificate"}]""")

    /**
     * Builds a JWT VC signed by a fresh `did:jwk` issuer key, with no `x5c` header — exercising
     * the same no-certificate path [ETSITrustListPolicy] uses for DID-based issuers.
     */
    private suspend fun didJwkSignedCredential(keyId: KeyId): Pair<DigitalCredential, String> {
        val (key, did) = generateIssuerKeyAndDid(keyId)
        // did:jwk resolves to exactly one key when no `kid` is given, but Crypto2JwtKeyResolver's
        // did:jwk branch only skips the kid check when kid is null OR equals "$did#0" — match that.
        val header = buildJsonObject { put("kid", "$did#0") }
        val signed = CompactJws.sign(
            payload = Json.encodeToString(buildJsonObject { put("iss", did) }).encodeToByteArray(),
            key = key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = header,
        )
        val decoded = CompactJws.decodeUnverified(signed)
        val payload = Json.parseToJsonElement(decoded.payload.decodeToString()) as JsonObject
        val credential: DigitalCredential = W3C11(
            credentialData = payload,
            signature = JwtCredentialSignature(signed, decoded.protectedHeader),
            signed = signed,
        )
        return credential to did
    }

    /** A minimal ETSI TS 119 602 LoTE JSON source registering [did] by public key alone (no certificate). */
    private suspend fun loteRegisteringDidJwkIssuer(did: String): String {
        val publicJwkJson = Json.parseToJsonElement(
            base64Url.decode(did.removePrefix("did:jwk:")).decodeToString()
        )
        return loteWithServiceIdentity(""""PublicKeyValues": [$publicJwkJson]""")
    }

    /** A minimal ETSI TS 119 602 LoTE JSON source with one service whose `ServiceDigitalIdentity` holds [serviceDigitalIdentity]. */
    private fun loteWithServiceIdentity(serviceDigitalIdentity: String): String {
        return """
            {
              "LoTE": {
                "ListAndSchemeInformation": {
                  "LoTEVersionIdentifier": 1,
                  "LoTESequenceNumber": 1,
                  "LoTEType": "https://trust.example.org/ns/lote-type/issuer-list",
                  "SchemeOperatorName": [{"lang": "en", "value": "Example Trust Scheme Operator"}],
                  "SchemeTerritory": "US",
                  "ListIssueDateTime": "2026-01-01T00:00:00Z",
                  "NextUpdate": "2099-01-01T00:00:00Z"
                },
                "TrustedEntitiesList": [
                  {
                    "TrustedEntityInformation": {
                      "TEName": [{"lang": "en", "value": "Example Credential Issuer"}],
                      "TEAddress": {
                        "TEPostalAddress": [{"lang": "en", "StreetAddress": "1 Example Street", "Locality": "Example City", "Country": "US"}],
                        "TEElectronicAddress": [{"lang": "en", "uriValue": "mailto:trust@example.org"}]
                      },
                      "TEInformationURI": [{"lang": "en", "uriValue": "https://trust.example.org/ListOfTrustedEntities/example-issuer"}]
                    },
                    "TrustedEntityServices": [
                      {
                        "ServiceInformation": {
                          "ServiceName": [{"lang": "en", "value": "SD-JWT Credential Issuance"}],
                          "ServiceDigitalIdentity": {
                            $serviceDigitalIdentity
                          },
                          "ServiceTypeIdentifier": "https://trust.example.org/ns/svc-type/issuer/credential-issuance",
                          "ServiceStatus": "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted",
                          "StatusStartingTime": "2026-01-01T00:00:00Z"
                        }
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
