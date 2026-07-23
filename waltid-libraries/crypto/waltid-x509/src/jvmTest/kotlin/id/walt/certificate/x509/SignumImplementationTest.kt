package id.walt.certificate.x509

import id.walt.certificate.TestData
import id.walt.certificate.TestKeys.opensslHexFormat
import id.walt.certificate.x509.X509CertificateSignatureValidationTest.Companion.trustStore
import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.signum.SignumDefaults
import id.walt.certificate.x509.signum.SignumSignatureValidator
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.x509.X509TestCertificates
import id.walt.x509.createX509TestKey
import id.walt.x509.id.walt.certificate.x509.JavaX509CertificateSerialNumberGenerator
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString
import kotlin.test.*

/**
 * Java is easier to debug
 */
class SignumImplementationTest {


    @Test
    fun extractsKeyIdentifiersFromCertificateDer() {
        val issuerCert = parseCertificateDerEncoded(X509TestCertificates.issuerCertificate)
        val leafCert = parseCertificateDerEncoded(X509TestCertificates.leafCertificate)

        val issuerSubjectKeyId =
            assertNotNull(issuerCert.data.extensionSubjectKeyIdentifier?.keyIdentifier, "issuerSubjectKeyId is null")
        val leafAuthKeyId =
            assertNotNull(leafCert.data.extensionAuthorityKeyIdentifier?.keyIdentifier, "leafAuthorityKeyId is null")
        assertEquals(issuerSubjectKeyId, leafAuthKeyId)
        assertEquals(
            "B1:3A:CD:04:B5:00:E5:DE:1F:FC:B1:3C:3C:EC:8F:60:BC:62:03:74",
            issuerSubjectKeyId.toHexString(opensslHexFormat)
        )
    }

    @Test
    fun rejectsLeafWithTamperedSignature() = runTest {

        val validationResult = validateCertificateChain(
            listOf(X509TestCertificates.tamperedLeafCertificate.let {
                parseCertificateDerEncoded(it)
            }),
            trustStore
        )
        assertFalse(validationResult.valid)
    }

    @Test
    fun verifiesLeafSignedByIssuer() = runTest {
        val validationResult = validateCertificateChain(
            listOf(X509TestCertificates.leafCertificate.let {
                parseCertificateDerEncoded(it)
            }),
            trustStore
        )
        assertTrue(validationResult.valid)
    }

    @Test
    fun shouldParseCsr() {
        assertNotNull(parseCsrPem(TestData.csrPem)) { csr ->
            assertEquals("C=AT,ST=Vienna,L=Vienna,O=Walt.id,CN=://walt.id", csr.requestedCertificate.subjectDn)
            assertEquals("1.2.840.10045.2.1", csr.requestedCertificate.subjectPublicKeyInfo.algorithmOid)
            assertEquals("id-ecPublicKey", csr.requestedCertificate.subjectPublicKeyInfo.algorithmName)
            assertEquals(
                "040f62d46bb95bb0aef9cac3e291191042839ed4670c1c0121e58eff26983511bdef383cf9e352cbd4f520abebd262072b514cad988979853fd69dc25b00e97793",
                csr.requestedCertificate.subjectPublicKeyInfo.keyValueHex
            )
            assertEquals("1.2.840.10045.4.3.2", csr.signatureAlgorithmOid)
            assertEquals("ecdsa-with-SHA256", csr.signatureAlgorithmName)
            assertEquals(
                "3046022100ed434954325834e6b6108f9bd28f5a038409866dee4b470e92f709d21c0c221a022100e7ac8154cd9d2928f98deb08c3b7821c2be2a1edbd92186cacb177f2476a42a8",
                csr.signatureValueHex
            )
            assertEquals(1, csr.requestedCertificate.extensions.size)
        }
    }

    @Test
    fun shouldParseCertificate() {
        val cert = parseCertificatePem(TestData.GOOGLE_CERTIFICATE_PEM)
        assertNotNull(cert)
        assertEquals("CN=*.google.com", cert.data.subjectDn)
    }

    @Test
    fun shouldSignCsr() = runTest {
        val key = createX509TestKey(KeyType.secp256r1)
        val csr = createCsr(key) {
            requestedCertificate.apply {
                subjectDn = "CN=Example Leaf,O=Example Org,C=US"
                extensionSan {
                    addDnsName("leaf.example.com")
                }
            }
        }

        val pem = csr.encodedPem
        assertTrue(pem.contains("BEGIN CERTIFICATE REQUEST"))

        val parsed = parseCsrPem(pem)

        assertEquals("CN=Example Leaf,O=Example Org,C=US", parsed.requestedCertificate.subjectDn)
        assertNotNull(parsed.requestedCertificate.extensionSan)
        assertEquals(
            listOf("leaf.example.com"), parsed.requestedCertificate.extensionSan
                ?.alternativeNames
                ?.filter { it.type == GeneralName.NameType.dNSName }
                ?.map { it.value })

        //TODO: check signature
    }

    companion object {
        val defaults = SignumDefaults(
            JavaX509CertificateSerialNumberGenerator(),
            SignumSignatureValidator()
        )

        fun parseCsrPem(pem: String): Pkcs10CertificateSigningRequest =
            X509CertificateUtil.parseCsrPem(defaults, pem)

        suspend fun createCsr(
            holderKey: Key,
            block: suspend Pkcs10CertificateSigningRequestBuilder.() -> Unit
        ) =
            X509CertificateUtil.createCsr(defaults, holderKey, block)

        fun parseCertificatePem(pem: String): X509Certificate =
            X509CertificateUtil.parseCertificatePem(defaults, pem)

        fun parseCertificateDerEncoded(derEncoded: ByteString): X509Certificate =
            X509CertificateUtil.parseCertificateDerEncoded(defaults, derEncoded)

        suspend fun createSelfSignedCertificate(
            issuerKey: Key,
            block: suspend X509CertificateDataBuilder.() -> Unit
        ): X509Certificate =
            X509CertificateUtil.createSelfSignedCertificate(defaults, issuerKey, block)

        suspend fun validateCertificateChain(
            certificateChain: Collection<X509Certificate>,
            additionalTrust: X509CertificateTrustStore? = null
        ): ValidationResult =
            X509CertificateUtil.validateCertificateChain(defaults, certificateChain, additionalTrust)
    }
}