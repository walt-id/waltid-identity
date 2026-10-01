package id.walt.mdoc.verification

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class IssuerAuthenticationMsoSignedTimeTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val notBefore = Instant.parse("2026-09-10T00:00:00Z")
    private val notAfter = Instant.parse("2026-09-20T00:00:00Z")

    @Test
    fun `accepts MSO signed at both certificate boundaries`() = runTest {
        verifyAt(notBefore)
        verifyAt(notAfter)
    }

    @Test
    fun `rejects MSO signed before certificate notBefore`() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            verifyAt(Instant.parse("2026-09-09T23:59:59Z"))
        }
        assertTrue(error.message!!.contains("notBefore"))
    }

    @Test
    fun `rejects MSO signed after certificate notAfter`() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            verifyAt(Instant.parse("2026-09-20T00:00:01Z"))
        }
        assertTrue(error.message!!.contains("notAfter"))
    }

    private suspend fun verifyAt(signedAt: Instant) {
        val issuerKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("issuer-auth-$signedAt"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val holderKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder-auth-$signedAt"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val holderCoseKey = (holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            issuerKey,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) {
            subjectDn = "CN=issuer-auth"
            validity = X509Certificate.Validity(notBefore, notAfter)
        }
        val issued = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = holderCoseKey,
            docType = "org.example.mdoc",
            data = MdocIssuer.MdocUniversalIssuanceData(
                mapOf("org.example" to JsonObject(mapOf("given_name" to JsonPrimitive("Jane"))))
            ),
            signedAt = signedAt,
            validFrom = signedAt,
            validUntil = signedAt.plus(kotlin.time.Duration.parse("365d")),
        )
        verifyIssuerAuthentication(
            document = Document(docType = "org.example.mdoc", issuerSigned = issued),
            validateCertificateConstraints = false,
        )
    }
}
