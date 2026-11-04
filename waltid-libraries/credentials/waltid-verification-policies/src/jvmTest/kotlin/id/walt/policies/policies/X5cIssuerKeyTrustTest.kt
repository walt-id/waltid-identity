package id.walt.policies.policies

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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

class X5cIssuerKeyTrustTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    @Test
    fun sdJwtVcSignatureRejectsX5cKeyWithoutTrustedRoots() = runTest {
        val sdJwt = x5cJwt(selfSigned("CN=Credential issuer"), sdJwt = true)

        assertTrue(SdJwtVCSignaturePolicy().verify(sdJwt, null, emptyMap()).isFailure)
    }

    @Test
    fun jwtSignatureRejectsX5cKeyWithoutTrustedRoots() = runTest {
        val jwt = x5cJwt(selfSigned("CN=Credential issuer"), sdJwt = false)

        assertTrue(JwtSignaturePolicy().verify(jwt, null, emptyMap()).isFailure)
    }

    @Test
    fun sdJwtVcSignatureAcceptsX5cKeyUnderTrustedRoot() = runTest {
        val root = selfSigned("CN=Root CA")
        val sdJwt = x5cJwt(issued(root, "CN=Credential issuer"), sdJwt = true)

        SdJwtVCSignaturePolicy().verify(sdJwt, null, trustedRoots(root)).getOrThrow()
        assertTrue(SdJwtVCSignaturePolicy().verify(sdJwt, null, trustedRoots(selfSigned("CN=Other CA"))).isFailure)
    }

    @Test
    fun jwtSignatureAcceptsX5cKeyUnderTrustedRoot() = runTest {
        val root = selfSigned("CN=Root CA")
        val jwt = x5cJwt(issued(root, "CN=Credential issuer"), sdJwt = false)

        JwtSignaturePolicy().verify(jwt, null, trustedRoots(root)).getOrThrow()
        assertTrue(JwtSignaturePolicy().verify(jwt, null, trustedRoots(selfSigned("CN=Other CA"))).isFailure)
    }

    private class Issued(val key: Key, val certificate: X509Certificate)

    private fun trustedRoots(root: Issued) = mapOf(TRUSTED_ROOT_CAS_CONTEXT_KEY to listOf(root.certificate.encodedPem))

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

    private suspend fun x5cJwt(signer: Issued, sdJwt: Boolean): String {
        val payload = buildJsonObject {
            put("iss", "https://issuer.example")
            if (sdJwt) {
                put("vct", "https://issuer.example/identity-credential")
            } else {
                put("vc", buildJsonObject {
                    put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
                })
            }
        }
        return CompactJws.sign(
            payload = Json.encodeToString(payload).encodeToByteArray(),
            key = signer.key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", if (sdJwt) "vc+sd-jwt" else "JWT")
                put("kid", "issuer-key")
                put("x5c", JsonArray(listOf(JsonPrimitive(Base64.encode(signer.certificate.encodedDer.toByteArray())))))
            },
        )
    }
}
