package id.walt.x509

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class OrderedCertificateChainTest {

    @Test
    fun verifiesLeafSignedByIssuer() {
        verifyOrderedCertificateChainSignatures(
            listOf(
                X509TestCertificates.leafCertificate,
                X509TestCertificates.issuerCertificate,
            )
        )
    }

    @Test
    fun rejectsLeafWithTamperedSignature() {
        assertFailsWith<X509ValidationException> {
            verifyOrderedCertificateChainSignatures(
                listOf(
                    X509TestCertificates.tamperedLeafCertificate,
                    X509TestCertificates.issuerCertificate,
                )
            )
        }
    }

    @Test
    fun rejectsIssuerSubjectMismatch() {
        assertFailsWith<X509ValidationException> {
            verifyOrderedCertificateChainSignatures(
                listOf(
                    X509TestCertificates.issuerCertificate,
                    X509TestCertificates.leafCertificate,
                )
            )
        }
    }

    @Test
    fun extractsKeyIdentifiersFromCertificateDer() {
        assertContentEquals(
            expected = X509TestCertificates.issuerCertificate.subjectKeyIdentifier,
            actual = X509TestCertificates.leafCertificate.authorityKeyIdentifier,
        )
    }

    @Test
    fun extractsSubjectAlternativeDnsNamesFromCertificateDer() {
        assertContentEquals(
            expected = listOf("verifier.example.com"),
            actual = X509TestCertificates.sanDnsCertificate.subjectAlternativeDnsNames,
        )
    }
}
