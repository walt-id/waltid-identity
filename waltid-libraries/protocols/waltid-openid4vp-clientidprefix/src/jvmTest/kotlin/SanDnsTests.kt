import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vp.clientidprefix.*
import id.walt.openid4vp.clientidprefix.prefixes.X509SanDns
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SanDnsTests {

    private val authenticator = ClientIdPrefixAuthenticator
    private val validMetadataJson = """{ "vp_formats_supported": {} }"""

    @Test
    fun `x509_san_dns validates trusted chain signature and SAN`() = runTest {
        val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val rootKey = genKey("rootCa")
        val rootCertificate = X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "cn=Test Root"
            extensionBasicConstraints {
                cA = true
                pathLenConstraint = 0
            }
            extensionKeyUsage {
                addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign)
            }
        }
        val leafKey = genKey("leaf")
        val leafCertificate = X509CertificateUtil.createCertificate(rootKey, rootCertificate, sigAlg) {
            subjectDn = "cn=verifier.example.com"
            subjectPublicKey(leafKey)
            extensionKeyUsage {
                addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature)
            }
            extensionExtendedKeyUsage {
                addKeyUsage("1.3.6.1.5.5.7.3.2")
            }
            extensionSan {
                addDnsName("verifier.example.com")
            }
        }

        val requestObject = CompactJws.sign(
            "{}".encodeToByteArray(),
            leafKey,
            JwsAlgorithm.ES256,
            buildJsonObject {
                put(
                    "x5c", JsonArray(
                        listOf(leafCertificate, rootCertificate).map {
                            JsonPrimitive(Base64.Default.encode(it.encodedDer.toByteArray()))
                        }
                    ))
            }
        )
        val context = RequestContext(
            clientId = "x509_san_dns:verifier.example.com",
            clientMetadataString = validMetadataJson,
            requestObjectJws = requestObject,
            responseUri = "https://verifier.example.com/response",
        )
        val clientId = X509SanDns("verifier.example.com", context.clientId)
        val wrongClientId = X509SanDns("wrong.example.com", "wrong.example.com")
        val trust = ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(listOf(rootCertificate)))
        assertIs<ClientValidationResult.Success>(clientId.authenticateX509SanDns(clientId, context, trust))
        assertIs<ClientValidationResult.Failure>(
            wrongClientId.authenticateX509SanDns(wrongClientId, context, trust)
        )
    }

    @Test
    fun `x509_san_dns should reject an untrusted self-signed certificate`() = runTest {
        // 1. Setup: Certificate is for 'verifier.example.com'
        val signedJws =
            "eyJ4NWMiOlsiTUlJQlZqQ0IvYUFEQWdFQ0FnZzlKVTl5cUxUU2xEQUtCZ2dxaGtqT1BRUURBakFmTVIwd0d3WURWUVFEREJSMlpYSnBabWxsY2k1bGVHRnRjR3hsTG1OdmJUQWVGdzB5TlRFd01UUXdOVE0yTVRaYUZ3MHlOakV3TVRRd05UTTJNVFphTUI4eEhUQWJCZ05WQkFNTUZIWmxjbWxtYVdWeUxtVjRZVzF3YkdVdVkyOXRNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUV5K3l0d2hFYTMxL29zNmR6OUI1WkRMa0pwbmlnZWgyRkVocG9STy9hUHdCOFdQS0U2SGtSUUdsVnE0RnVlcjdNQTFXR2dvWmxzT1lEUVB3OXZybzYxS01qTUNFd0h3WURWUjBSQkJnd0ZvSVVkbVZ5YVdacFpYSXVaWGhoYlhCc1pTNWpiMjB3Q2dZSUtvWkl6ajBFQXdJRFNBQXdSUUloQUppY20vZWdENWZlSmdVdWNhNkZzYk1JcVV4UDZiYU9BTGtyRUtldzFHMzRBaUIwc2hDWWZRdGZTZzFrczVTRm85MDY2OWVBQ0E2c25tMjBIalJsSGMyWFBnPT0iXSwiYWxnIjoiRVMyNTYifQ.eyJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4iLCJub25jZSI6IjEyMzQifQ.LCkadcCpkD4eYXe7tkv79IahDatHaMz8U1ZCbC0eykx9gxUpF3dR50bt6omqb3LfcnkB0CLS0nQuimMjLxArJw"

        // 2. Context: Client claims to be 'verifier.example.com'
        val context = RequestContext(
            clientId = "x509_san_dns:verifier.example.com",
            clientMetadataString = validMetadataJson,
            requestObjectJws = signedJws,
        )
        val clientId = ClientIdPrefixParser.parse(context.clientId).getOrThrow()
        assertIs<X509SanDns>(clientId)

        // Authenticate
        val result = authenticator.authenticate(clientId, context)

        if (result is ClientValidationResult.Failure) {
            println(result.error)
        }

        val failure = assertIs<ClientValidationResult.Failure>(result)
        assertEquals(ClientIdError.MissingX509TrustAnchors, failure.error)
    }

    @Test
    fun `x509_san_dns should reject SAN checks without a trusted certificate path`() = runTest {
        // 1. Setup: Certificate is for 'verifier.example.com'
        val signedJws =
            "eyJ4NWMiOlsiTUlJQlZ6Q0IvcUFEQWdFQ0Fna0E1dmdZV0pkK0Nna3dDZ1lJS29aSXpqMEVBd0l3SHpFZE1Cc0dBMVVFQXd3VWRtVnlhV1pwWlhJdVpYaGhiWEJzWlM1amIyMHdIaGNOTWpVeE1ERTBNRFV6TmpFM1doY05Nall4TURFME1EVXpOakUzV2pBZk1SMHdHd1lEVlFRRERCUjJaWEpwWm1sbGNpNWxlR0Z0Y0d4bExtTnZiVEJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSEEwSUFCUHRjSWFTdWRzVGpmeHU1elJrczRQdE9mdEo3SEl3TEFwa3FrakxYVnE4M3VQS0F2V3FsbWw4UWdLZDRmMjVtSjNtRm9pbmxWN2tGa1Y2NUltTHFFdHlqSXpBaE1COEdBMVVkRVFRWU1CYUNGSFpsY21sbWFXVnlMbVY0WVcxd2JHVXVZMjl0TUFvR0NDcUdTTTQ5QkFNQ0EwZ0FNRVVDSVFDaDZraHo5K0V5THRIb0RXL3RnTll5S29QR2gyR2lFT1hVYnNsa2h3ZGJxd0lnTXVCYUY5bFNLa1JtWXQzbGdKY2JoNFRpOXM3Nkd3ZkRBMmp2SUJxRk1Bbz0iXSwiYWxnIjoiRVMyNTYifQ.eyJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4ifQ.r17xxNyhGmn4S3G3WvGHWuC-RdWBjaOjoW0vh2IWpAtRoghXKJ33H1kTi1Ljv1RBfDj3ZDSkhDzkmPV7fvT5vw"

        // 2. Context: Client claims to be 'wrong.example.com'
        val context = RequestContext(
            clientId = "x509_san_dns:wrong.example.com",
            clientMetadataString = validMetadataJson,
            requestObjectJws = signedJws,
        )
        val clientId = ClientIdPrefixParser.parse(context.clientId).getOrThrow()

        // 3. Authenticate
        val result = authenticator.authenticate(clientId, context)

        if (result is ClientValidationResult.Failure) {
            println(result.error)
        }

        assertIs<ClientValidationResult.Failure>(result)
        assertEquals(ClientIdError.MissingX509TrustAnchors, result.error)
    }

    /**
     * Builds a leaf certificate for [dnsName] plus the root that signed it, and a request object
     * signed with the leaf key - the shape an `x509_san_dns` verifier sends.
     */
    private suspend fun signedRequestFor(dnsName: String): Pair<String, X509Certificate> {
        val signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val rootKey = genKey("sanDnsRoot-$dnsName")
        val rootCertificate = X509CertificateUtil.createSelfSignedCertificate(rootKey, signatureAlgorithm) {
            subjectDn = "cn=Test Root"
            extensionBasicConstraints {
                cA = true
                pathLenConstraint = 0
            }
            extensionKeyUsage {
                addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign)
            }
        }
        val leafKey = genKey("sanDnsLeaf-$dnsName")
        val leafCertificate = X509CertificateUtil.createCertificate(rootKey, rootCertificate, signatureAlgorithm) {
            subjectDn = "cn=$dnsName"
            subjectPublicKey(leafKey)
            extensionKeyUsage {
                addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature)
            }
            extensionExtendedKeyUsage {
                addKeyUsage("1.3.6.1.5.5.7.3.2")
            }
            extensionSan {
                addDnsName(dnsName)
            }
        }
        val requestObject = CompactJws.sign(
            "{}".encodeToByteArray(),
            leafKey,
            JwsAlgorithm.ES256,
            buildJsonObject {
                put(
                    "x5c", JsonArray(
                        listOf(leafCertificate, rootCertificate).map {
                            JsonPrimitive(Base64.Default.encode(it.encodedDer.toByteArray()))
                        }
                    )
                )
            },
        )
        return requestObject to rootCertificate
    }

    /**
     * OpenID4VP 1.0 Section 14.3.1 lets the Wallet trust a `response_uri` on the strength of the
     * authenticated Client Identifier Prefix and the signed request, so the Section 5.9.3 FQDN rule -
     * which names `redirect_uri` - must not be applied to it. Enforcing it there rejected every
     * conformant `direct_post` verifier that receives responses on a host other than the one its
     * certificate names, which is both the ordinary deployment shape and what the conformance suite
     * does.
     */
    @Test
    fun `x509_san_dns accepts a response_uri on a different host than the certificate names`() = runTest {
        val (requestObject, rootCertificate) = signedRequestFor("verifier.example.com")
        val context = RequestContext(
            clientId = "x509_san_dns:verifier.example.com",
            clientMetadataString = validMetadataJson,
            requestObjectJws = requestObject,
            responseUri = "https://localhost.emobix.co.uk:8443/test/a/plan/response",
        )
        val clientId = X509SanDns("verifier.example.com", context.clientId)
        val trust = ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(listOf(rootCertificate)))

        assertIs<ClientValidationResult.Success>(clientId.authenticateX509SanDns(clientId, context, trust))
    }

    /**
     * The Section 5.9.3 requirement itself: with no other means of establishing trust in the Client
     * Identifier, a `redirect_uri` on a foreign host must still be refused.
     */
    @Test
    fun `x509_san_dns rejects a redirect_uri whose FQDN does not match the client id`() = runTest {
        val (requestObject, rootCertificate) = signedRequestFor("verifier.example.com")
        val trust = ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(listOf(rootCertificate)))
        val clientId = X509SanDns("verifier.example.com", "x509_san_dns:verifier.example.com")

        val mismatched = RequestContext(
            clientId = "x509_san_dns:verifier.example.com",
            clientMetadataString = validMetadataJson,
            requestObjectJws = requestObject,
            redirectUri = "https://attacker.example.org/cb",
        )
        val failure = clientId.authenticateX509SanDns(clientId, mismatched, trust)
        assertIs<ClientValidationResult.Failure>(failure)
        assertIs<ClientIdError.RedirectUriHostMismatch>(failure.error)

        val matching = RequestContext(
            clientId = "x509_san_dns:verifier.example.com",
            clientMetadataString = validMetadataJson,
            requestObjectJws = requestObject,
            redirectUri = "https://verifier.example.com/cb",
        )
        assertIs<ClientValidationResult.Success>(clientId.authenticateX509SanDns(clientId, matching, trust))
    }

    companion object {
        suspend fun genKey(keyId: String): Key =
            runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    KeyId(keyId),
                    KeySpec.Ec(EcCurve.P256),
                    setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
            )

        val runtime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders())
    }
}
