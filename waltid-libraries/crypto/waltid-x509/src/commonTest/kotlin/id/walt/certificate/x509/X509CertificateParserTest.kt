package id.walt.certificate.x509

import id.walt.certificate.TestKeys.opensslHexFormat
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.x509.X509TestCertificates
import kotlinx.io.bytestring.toHexString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class X509CertificateParserTest {

    @Test
    fun extractsKeyIdentifiersFromCertificateDer() {
        val issuerCert = X509CertificateUtil.parseCertificateDerEncoded(X509TestCertificates.issuerCertificate)
        val leafCert = X509CertificateUtil.parseCertificateDerEncoded(X509TestCertificates.leafCertificate)

        println(issuerCert.encodedPem)

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
    fun extractsSubjectAlternativeDnsNamesFromCertificateDer() {
        val cert = X509CertificateUtil.parseCertificateDerEncoded(X509TestCertificates.sanDnsCertificate)
        assertContentEquals(
            expected = listOf("verifier.example.com"),
            actual = cert.data.extensionSan?.alternativeNames?.map { it.value },
        )
    }
}