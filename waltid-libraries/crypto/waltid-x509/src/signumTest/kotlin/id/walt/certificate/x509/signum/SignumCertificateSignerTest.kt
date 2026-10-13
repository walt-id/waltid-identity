package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.asn1.Asn1Exception
import id.walt.certificate.TestKeys
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateSerialNumberGenerator
import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.decodePublicKeyPem
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class SignumCertificateSignerTest {

    @Test
    fun certificateSigningRejectsRawEcOutputForDerAlgorithm() = runTest {
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = object : X509CertificateSerialNumberGenerator {
                override fun next() = ByteString(byteArrayOf(1))
            },
            issuerDnRaw = ByteString(),
            subjectDn = "CN=DER signature test",
            validity = X509Certificate.Validity(
                notBefore = Instant.parse("2026-01-01T00:00:00Z"),
                notAfter = Instant.parse("2027-01-01T00:00:00Z"),
            ),
        )

        assertFailsWith<Asn1Exception> {
            SignumCertificateSigner().signCertificate(rawEcSigningKey, derAlgorithm, builder)
        }
    }

    @Test
    fun csrSigningRejectsRawEcOutputForDerAlgorithm() = runTest {
        val builder = Pkcs10CertificateSigningRequestBuilder("CN=DER signature test")

        assertFailsWith<Asn1Exception> {
            SignumCertificateSigner().signCsr(rawEcSigningKey, derAlgorithm, builder)
        }
    }

    @Test
    fun csrSigningRejectsNonDerAlgorithmBeforeSigning() = runTest {
        val rawAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.IEEE_P1363)
        val key = object : Key by rawEcSigningKey {
            override val capabilities = KeyCapabilities(
                signatureAlgorithms = setOf(rawAlgorithm),
                signer = Signer { _, _ -> error("Rejected CSR algorithm reached the signer") },
                publicKeyExporter = rawEcSigningKey.capabilities.publicKeyExporter,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            SignumCertificateSigner().signCsr(
                key, rawAlgorithm, Pkcs10CertificateSigningRequestBuilder("CN=DER signature test")
            )
        }
    }

    private val derAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    private val rawEcSigningKey = object : Key {
        override val id = KeyId("raw-ec-output")
        override val spec = KeySpec.Ec(EcCurve.P256)
        override val usages = setOf(KeyUsage.SIGN)
        override val capabilities = KeyCapabilities(
            signatureAlgorithms = setOf(derAlgorithm),
            signer = Signer { _, algorithm ->
                assertEquals(derAlgorithm, algorithm)
                // Valid-width raw P-256 components violate the requested DER encoding.
                ByteArray(64) { 0x11 }
            },
            publicKeyExporter = PublicKeyExporter { TestKeys.ecP256PublicKeyPem.decodePublicKeyPem() },
        )
    }
}
