package id.walt.policies2.vc

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.W3C11
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.crypto2.CryptoRuntime
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
import id.walt.policies2.vc.policies.ETSITrustListPolicy
import id.walt.policies2.vc.policies.PolicyExecutionContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    // Test fixtures
    // ---------------------------------------------------------------------------

    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** Generates a P-256 key and derives its self-contained `did:jwk:` identifier. */
    private suspend fun generateIssuerKeyAndDid(keyId: KeyId): Pair<id.walt.crypto2.keys.Key, String> {
        DidService.minimalInit()
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = keyId,
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val publicJwkJson = key.capabilities.publicKeyExporter!!.exportPublicKey()
            .toPublicJwk(key.spec).data.toByteArray().decodeToString()
        val did = "did:jwk:" + base64Url.encode(publicJwkJson.encodeToByteArray())
        return key to did
    }

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
                            "PublicKeyValues": [$publicJwkJson]
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
