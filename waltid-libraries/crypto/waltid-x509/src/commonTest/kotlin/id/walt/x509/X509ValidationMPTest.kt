package id.walt.x509

import kotlin.test.Test
import kotlin.test.assertFailsWith

class X509ValidationMPTest {
/*
   // @Test
    fun validatesLeafWithProvidedTrustAnchor() {
        validateCertificateChain(
            leaf = X509TestCertificates.leafCertificate,
            chain = listOf(X509TestCertificates.leafCertificate),
            trustAnchors = listOf(X509TestCertificates.issuerCertificate),
        )
    }

   // @Test
    fun validatesLeafWithTrustedRootIncludedInChain() {
        validateCertificateChain(
            leaf = X509TestCertificates.leafCertificate,
            chain = listOf(
                X509TestCertificates.leafCertificate,
                X509TestCertificates.issuerCertificate,
            ),
            enableTrustedChainRoot = true,
        )
    }

   // @Test
    fun validatesChainWhenCertificatesAreOutOfOrder() {
        validateCertificateChain(
            leaf = X509TestCertificates.leafCertificate,
            chain = listOf(
                X509TestCertificates.issuerCertificate,
                X509TestCertificates.leafCertificate,
            ),
            enableTrustedChainRoot = true,
        )
    }

   // @Test
    fun rejectsTamperedLeafCertificate() {
        assertFailsWith<X509ValidationException> {
            validateCertificateChain(
                leaf = X509TestCertificates.tamperedLeafCertificate,
                chain = listOf(X509TestCertificates.tamperedLeafCertificate),
                trustAnchors = listOf(X509TestCertificates.issuerCertificate),
            )
        }
    }

   // @Test
    fun rejectsMissingTrustAnchor() {
        assertFailsWith<X509ValidationException> {
            validateCertificateChain(
                leaf = X509TestCertificates.leafCertificate,
                chain = listOf(X509TestCertificates.leafCertificate),
            )
        }
    }*/
}
