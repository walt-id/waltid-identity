import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.w3c.schemes.JwsSignatureScheme
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

class X5cIssuerKeyTrustTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    @Test
    fun `issuer key from x5c is rejected without trust roots`() = runTest {
        val signed = x5cJwt(selfSigned("CN=Credential issuer"))

        assertTrue(JwsSignatureScheme().verifyCrypto2(signed, setOf(JwsAlgorithm.ES256)).isFailure)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `deprecated verify rejects issuer key from x5c without trust roots`() = runTest {
        val signed = x5cJwt(selfSigned("CN=Credential issuer"))

        assertTrue(JwsSignatureScheme().verify(signed).isFailure)
    }

    @Test
    fun `issuer key from x5c is accepted only under a given trust root`() = runTest {
        val root = selfSigned("CN=Root CA")
        val signed = x5cJwt(issued(root, "CN=Credential issuer"))

        JwsSignatureScheme().verifyCrypto2(signed, listOf(root.certificate.encodedPem)).getOrThrow()
        val otherRoot = selfSigned("CN=Other CA").certificate.encodedPem
        assertTrue(JwsSignatureScheme().verifyCrypto2(signed, listOf(otherRoot)).isFailure)
    }

    private class Issued(val key: Key, val certificate: X509Certificate)

    private suspend fun generateKey(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private suspend fun selfSigned(dn: String): Issued {
        val key = generateKey(dn)
        return Issued(key, X509CertificateUtil.createSelfSignedCertificate(key, sigAlg) { subjectDn = dn })
    }

    private suspend fun issued(issuer: Issued, dn: String): Issued {
        val key = generateKey(dn)
        val certificate = X509CertificateUtil.createCertificate(issuer.key, issuer.certificate, sigAlg) {
            subjectDn = dn
            subjectPublicKey(key)
        }
        return Issued(key, certificate)
    }

    private suspend fun x5cJwt(signer: Issued): String {
        val payload = buildJsonObject {
            put("iss", "https://issuer.example")
            put("vc", buildJsonObject {
                put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
            })
        }
        return CompactJws.sign(
            payload = Json.encodeToString(payload).encodeToByteArray(),
            key = signer.key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", "JWT")
                put("kid", "issuer-key")
                put("x5c", JsonArray(listOf(JsonPrimitive(Base64.encode(signer.certificate.encodedDer.toByteArray())))))
            },
        )
    }
}
