package id.walt.x509

import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X509CertificateSigningRequestCommonTest {

    @Test
    fun `CSR DER should round-trip via PEM`() {
        val csrDer = CertificateSigningRequestDer(
            bytes = byteArrayOf(0x30, 0x03, 0x02, 0x01, 0x05).toByteString(),
        )

        val pem = csrDer.toPEMEncodedString()
        val reparsed = CertificateSigningRequestDer.fromPEMEncodedString(pem)

        assertEquals(csrDer, reparsed)
    }

    @Test
    fun `CSR profile compatibility should reject unsupported requested usages`() {
        val csrData = X509CertificateSigningRequestBuilder(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("AT"),
                X509SubjectAttributes.commonName("service.example.org"),
            ),
        ).addKeyUsages(
            listOf(
                X509KeyUsage.DigitalSignature,
                X509KeyUsage.KeyCertSign,
            )
        ).build()

        val compatibility = csrData.checkCompatibility(X509KnownCertificateProfiles.GenericEndEntity)

        assertFalse(compatibility.isCompatible)
        assertTrue(compatibility.issues.any { it.contains("key usages") })
    }

    @Test
    fun `CSR profile compatibility should allow omitted profile managed extensions`() {
        val csrData = X509CertificateSigningRequestBuilder(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("AT"),
                X509SubjectAttributes.commonName("service.example.org"),
            ),
        ).addSubjectAlternativeName(X509SubjectAlternativeName.DnsName("service.example.org"))
            .build()

        val compatibility = csrData.checkCompatibility(X509KnownCertificateProfiles.Qwac)

        assertTrue(compatibility.isCompatible)
        assertTrue(compatibility.issues.isEmpty())
    }

    @Test
    fun `CSR profile compatibility should reject incomplete qwac eku requests`() {
        val csrData = X509CertificateSigningRequestBuilder(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("DE"),
                X509SubjectAttributes.commonName("bank.example"),
            ),
        ).addSubjectAlternativeName(
            X509SubjectAlternativeName.DnsName("bank.example")
        ).addExtendedKeyUsage(
            X509ExtendedKeyUsage.ServerAuth
        ).build()

        val compatibility = csrData.checkCompatibility(X509KnownCertificateProfiles.Qwac)

        assertFalse(compatibility.isCompatible)
        assertTrue(compatibility.issues.any { it.contains("missing profile-required usages") })
    }

    @Test
    fun `CSR profile compatibility should reject iso document signer sans`() {
        val csrData = X509CertificateSigningRequestBuilder(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("AT"),
                X509SubjectAttributes.commonName("Example Document Signer"),
            ),
        ).applyProfile(X509KnownProfileIds.IsoDocumentSigner)
            .addSubjectAlternativeName(X509SubjectAlternativeName.DnsName("ds.example.org"))
            .build()

        val compatibility = csrData.checkCompatibility(X509KnownCertificateProfiles.IsoDocumentSigner)

        assertFalse(compatibility.isCompatible)
        assertTrue(compatibility.issues.any { it.contains("subject alternative names") })
    }

    @Test
    fun `CSR builder can apply profile defaults`() {
        val csrData = X509CertificateSigningRequestBuilder(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("AT"),
                X509SubjectAttributes.commonName("Example Document Signer"),
            ),
        ).applyProfile(X509KnownProfileIds.IsoDocumentSigner)
            .build()

        assertEquals(X509KnownCertificateProfiles.IsoDocumentSigner.keyUsages, csrData.keyUsages)
        assertEquals(X509KnownCertificateProfiles.IsoDocumentSigner.extendedKeyUsages, csrData.extendedKeyUsages)
        assertEquals(X509KnownCertificateProfiles.IsoDocumentSigner.basicConstraints, csrData.basicConstraints)
    }
}
