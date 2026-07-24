package id.walt.certificate.x509

import id.walt.certificate.TestData
import id.walt.certificate.TestKeys
import id.walt.certificate.TestKeys.opensslHexFormat
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsRootR4CrtPem
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsWe2CrtPem
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.X509TestCertificates
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.toHexString
import kotlin.test.*

/**
 * This test is to debug the Signum implementation.
 * Java is easier to debug
 */
class SignumImplementationTest {

    @Test
    fun shouldValidateGoogleCertificateChainWithOneEntry() = runTest {
        val certificatePem = gtsWe2CrtPem
        assertNotNull(certificatePem)
        val result = signumCertUtil.validatePemCertificateChain(certificatePem)
        if (!result.valid) {
            result.log
                .filter { it.severity == ValidationResult.Severity.ERROR }
                .forEach { println("${it.validatorId}: ${it.subjectDn} - ${it.message}") }
        }
        assertTrue(result.valid)
        result.log.filter { it.validatorId == X509CertificateSignatureValidator.ID }
            .also { signatureValidatorLog ->
                assertEquals(2, signatureValidatorLog.size)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[0].severity)
                assertEquals("C=US,O=Google Trust Services,CN=WE2", signatureValidatorLog[0].subjectDn)
            }
    }


    @Test
    fun extractsKeyIdentifiersFromCertificateDer() {
        val issuerCert = signumCertUtil.parseCertificateDerEncoded(X509TestCertificates.issuerCertificate)
        val leafCert = signumCertUtil.parseCertificateDerEncoded(X509TestCertificates.leafCertificate)

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

        val validationResult = signumCertUtil.validateCertificateChain(
            listOf(X509TestCertificates.tamperedLeafCertificate.let {
                signumCertUtil.parseCertificateDerEncoded(it)
            })
        )
        assertFalse(validationResult.valid)
    }

    @Test
    fun verifiesLeafSignedByIssuer() = runTest {
        val validationResult = signumCertUtil.validateCertificateChain(
            listOf(X509TestCertificates.leafCertificate.let {
                signumCertUtil.parseCertificateDerEncoded(it)
            }),
            InMemoryTrustStore(
                listOf(X509TestCertificates.issuerCertificate)
                    .map { signumCertUtil.parseCertificateDerEncoded(it) })
        )
        assertTrue(validationResult.valid)
    }

    @Test
    fun shouldParseCsr() {
        assertNotNull(signumCertUtil.parseCsrPem(TestData.csrPem)) { csr ->
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
        val cert = signumCertUtil.parseCertificatePem(TestData.GOOGLE_CERTIFICATE_PEM)
        assertNotNull(cert)
        assertEquals("CN=*.google.com", cert.data.subjectDn)
    }

    @Test
    fun shouldSignCsr() = runTest {
        val key = JWKKey.importPEM(TestKeys.ecP256KeyPem).getOrThrow()
        val csr = signumCertUtil.createCsr(key) {
            requestedCertificate.apply {
                subjectDn = "CN=Example Leaf,O=Example Org,C=US"
                extensionSan {
                    addDnsName("leaf.example.com")
                }
            }
        }

        val pem = csr.encodedPem
        assertTrue(pem.contains("BEGIN CERTIFICATE REQUEST"))

        val parsed = signumCertUtil.parseCsrPem(pem)

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
        val trustStore = InMemoryTrustStore(
            listOf(gtsRootR4CrtPem)
                .map { X509CertificateUtil.parseCertificatePem(it) })

        val signumCertUtil = X509CertificateUtil {
            setTrust(trustStore)
            signumImplementation()
        }
    }
}