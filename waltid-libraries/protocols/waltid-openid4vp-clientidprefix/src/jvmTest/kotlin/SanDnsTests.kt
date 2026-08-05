import id.walt.openid4vp.clientidprefix.*
import id.walt.openid4vp.clientidprefix.prefixes.X509SanDns
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.GenericX509CertificateBuilder
import id.walt.x509.GenericX509CertificateProfileData
import id.walt.x509.X509DistinguishedName
import id.walt.x509.X509KeyUsage
import id.walt.x509.X509SubjectAlternativeNames
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SanDnsTests {

    private val authenticator = ClientIdPrefixAuthenticator
    private val validMetadataJson = """{ "vp_formats_supported": {} }"""

    @Test
    fun `x509_san_dns validates trusted chain signature and SAN`() = runTest {
        val rootName = X509DistinguishedName("Test Root")
        val rootKey = JWKKey.generate(KeyType.secp256r1)
        val rootCertificate = GenericX509CertificateBuilder().build(
            profileData = GenericX509CertificateProfileData(
                subjectName = rootName,
                isCertificateAuthority = true,
                pathLengthConstraint = 0,
                keyUsage = setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign),
            ),
            subjectPublicKey = rootKey.getPublicKey(),
            signingKey = rootKey,
        ).certificateDer
        val leafKey = JWKKey.generate(KeyType.secp256r1)
        val leafCertificate = GenericX509CertificateBuilder().build(
            profileData = GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName("verifier.example.com"),
                issuerName = rootName,
                keyUsage = setOf(X509KeyUsage.DigitalSignature),
                extendedKeyUsageOids = setOf("1.3.6.1.5.5.7.3.2"),
                subjectAlternativeNames = X509SubjectAlternativeNames(dnsNames = listOf("verifier.example.com")),
            ),
            subjectPublicKey = leafKey.getPublicKey(),
            signingKey = rootKey,
        ).certificateDer
        val requestObject = leafKey.signJws(
            "{}".encodeToByteArray(),
            mapOf(
                "x5c" to JsonArray(
                    listOf(leafCertificate, rootCertificate).map {
                        JsonPrimitive(Base64.Default.encode(it.bytes.toByteArray()))
                    }
                )
            ),
        )
        val context = RequestContext(
            clientId = "x509_san_dns:verifier.example.com",
            clientMetadataString = validMetadataJson,
            requestObjectJws = requestObject,
            responseUri = "https://verifier.example.com/response",
        )
        val clientId = X509SanDns("verifier.example.com", context.clientId)
        val wrongClientId = X509SanDns("wrong.example.com", "wrong.example.com")
        val trust = ClientIdTrustConfiguration(x509TrustAnchors = listOf(rootCertificate))

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

}
