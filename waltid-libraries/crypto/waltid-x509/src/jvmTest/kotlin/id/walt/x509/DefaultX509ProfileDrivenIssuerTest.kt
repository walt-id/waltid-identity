package id.walt.x509

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

class X509ProfileDrivenIssuerTest {

    private val issuer = X509ProfileDrivenIssuer()

    @Test
    fun `issues generic ca certificate`() = runTest {
        val caKey = JWKKey.generate(KeyType.secp256r1)
        val validityPeriod = X509ValidityPeriod(
            notBefore = Instant.parse("2026-01-01T00:00:00Z"),
            notAfter = Instant.parse("2030-01-01T00:00:00Z"),
        )

        val issued = issuer.issue(
            X509SelfSignedCertificateIssuanceSpec(
                profileId = X509KnownProfileIds.GenericCa,
                certificateData = X509CertificateBuildData(
                    subject = x509SubjectOf(
                        X509SubjectAttributes.country("AT"),
                        X509SubjectAttributes.commonName("Example Generic CA"),
                        X509SubjectAttributes.organization("walt.id"),
                    ),
                    validityPeriod = validityPeriod,
                    subjectAlternativeNames = setOf(
                        X509SubjectAlternativeName.Uri("https://ca.example.org"),
                    ),
                ),
                signingKey = caKey,
            )
        )

        val jcaCertificate = issued.certificateDer.toJcaX509Certificate()

        assertEquals(X509KnownProfileIds.GenericCa, issued.profile.profileId)
        assertEquals(issued.certificateData.subject, issued.certificateData.issuer)
        assertEquals(setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign), jcaCertificate.x509KeyUsages)
        assertTrue(jcaCertificate.x509BasicConstraints.isCA)
        assertTrue(issued.certificateData.checkCompatibility(issued.profile).isValid)
    }

    @Test
    fun `issues generic end entity certificate signed by generic ca`() = runTest {
        val caKey = JWKKey.generate(KeyType.secp256r1)
        val eeKey = JWKKey.generate(KeyType.secp256r1)

        val caIssued = issuer.issue(
            X509SelfSignedCertificateIssuanceSpec(
                profileId = X509KnownProfileIds.GenericCa,
                certificateData = X509CertificateBuildData(
                    subject = x509SubjectOf(
                        X509SubjectAttributes.country("AT"),
                        X509SubjectAttributes.commonName("Option 1 Root CA"),
                    ),
                    validityPeriod = X509ValidityPeriod(
                        notBefore = Instant.parse("2026-01-01T00:00:00Z"),
                        notAfter = Instant.parse("2030-01-01T00:00:00Z"),
                    ),
                ),
                signingKey = caKey,
            )
        )

        val eeIssued = issuer.issue(
            X509IssuerSignedCertificateIssuanceSpec(
                profileId = X509KnownProfileIds.GenericEndEntity,
                certificateData = X509CertificateBuildData(
                    subject = x509SubjectOf(
                        X509SubjectAttributes.country("AT"),
                        X509SubjectAttributes.commonName("service.example.org"),
                    ),
                    validityPeriod = X509ValidityPeriod(
                        notBefore = Instant.parse("2026-01-02T00:00:00Z"),
                        notAfter = Instant.parse("2027-03-31T00:00:00Z"),
                    ),
                    subjectAlternativeNames = setOf(
                        X509SubjectAlternativeName.DnsName("service.example.org"),
                        X509SubjectAlternativeName.DnsName("api.example.org"),
                    ),
                ),
                publicKey = eeKey.getPublicKey(),
                issuer = X509CertificateSignerSpec(
                    profileId = X509KnownProfileIds.GenericCa,
                    certificateData = caIssued.certificateData.toBuildData(),
                    signingKey = caKey,
                ),
            )
        )

        validateCertificateChain(
            leaf = eeIssued.certificateDer,
            chain = listOf(caIssued.certificateDer),
            trustAnchors = listOf(caIssued.certificateDer),
        )

        val jcaCertificate = eeIssued.certificateDer.toJcaX509Certificate()
        assertEquals(setOf(X509KeyUsage.DigitalSignature), jcaCertificate.x509KeyUsages)
        assertTrue(!jcaCertificate.x509BasicConstraints.isCA)
        assertTrue(eeIssued.certificateData.checkCompatibility(eeIssued.profile).isValid)
    }

    @Test
    fun `dispatches iso iaca profile`() = runTest {
        val iacaKey = JWKKey.generate(KeyType.secp256r1)

        val issued = issuer.issue(
            X509SelfSignedCertificateIssuanceSpec(
                profileId = X509KnownProfileIds.IsoIaca,
                certificateData = X509CertificateBuildData(
                    subject = x509SubjectOf(
                        X509SubjectAttributes.country("US"),
                        X509SubjectAttributes.commonName("Example IACA"),
                        X509SubjectAttributes.organization("walt.id"),
                    ),
                    validityPeriod = X509ValidityPeriod(
                        notBefore = Instant.parse("2025-01-01T00:00:00Z"),
                        notAfter = Instant.parse("2030-01-01T00:00:00Z"),
                    ),
                    issuerAlternativeNames = setOf(
                        X509SubjectAlternativeName.Uri("https://iaca.example.org"),
                    ),
                ),
                signingKey = iacaKey,
            )
        )

        assertEquals(X509KnownProfileIds.IsoIaca, issued.profile.profileId)
        assertTrue(issued.certificateData.issuerAlternativeNames.contains(X509SubjectAlternativeName.Uri("https://iaca.example.org")))
        assertEquals(setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign), issued.certificateData.keyUsages)
    }

    @Test
    fun `dispatches iso document signer profile`() = runTest {
        val iacaKey = JWKKey.generate(KeyType.secp256r1)
        val dsKey = JWKKey.generate(KeyType.secp256r1)

        val iacaCertificateData = X509CertificateBuildData(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("US"),
                X509SubjectAttributes.commonName("Example IACA"),
            ),
            validityPeriod = X509ValidityPeriod(
                notBefore = Instant.parse("2025-01-01T00:00:00Z"),
                notAfter = Instant.parse("2030-01-01T00:00:00Z"),
            ),
            issuerAlternativeNames = setOf(
                X509SubjectAlternativeName.Uri("https://iaca.example.org"),
            ),
        )

        val issued = issuer.issue(
            X509IssuerSignedCertificateIssuanceSpec(
                profileId = X509KnownProfileIds.IsoDocumentSigner,
                certificateData = X509CertificateBuildData(
                    subject = x509SubjectOf(
                        X509SubjectAttributes.country("US"),
                        X509SubjectAttributes.commonName("Example DS"),
                        X509SubjectAttributes.locality("New York"),
                    ),
                    validityPeriod = X509ValidityPeriod(
                        notBefore = Instant.parse("2026-01-02T00:00:00Z"),
                        notAfter = Instant.parse("2027-03-31T00:00:00Z"),
                    ),
                    crlDistributionPointUri = "https://iaca.example.org/crl",
                ),
                publicKey = dsKey.getPublicKey(),
                issuer = X509CertificateSignerSpec(
                    profileId = X509KnownProfileIds.IsoIaca,
                    certificateData = iacaCertificateData,
                    signingKey = iacaKey,
                ),
            )
        )

        assertEquals(X509KnownProfileIds.IsoDocumentSigner, issued.profile.profileId)
        assertTrue(issued.certificateData.extendedKeyUsages.any { it.oid == id.walt.x509.iso.DocumentSignerEkuOID })
        assertEquals("https://iaca.example.org/crl", issued.certificateData.crlDistributionPointUri)
        assertTrue(issued.certificateData.checkCompatibility(issued.profile).isValid)
    }

    @Test
    fun `rejects generic end entity validity outside issuer validity`() = runTest {
        val caKey = JWKKey.generate(KeyType.secp256r1)
        val eeKey = JWKKey.generate(KeyType.secp256r1)

        val caIssued = issuer.issue(
            X509SelfSignedCertificateIssuanceSpec(
                profileId = X509KnownProfileIds.GenericCa,
                certificateData = X509CertificateBuildData(
                    subject = x509SubjectOf(
                        X509SubjectAttributes.country("AT"),
                        X509SubjectAttributes.commonName("Example Generic CA"),
                    ),
                    validityPeriod = X509ValidityPeriod(
                        notBefore = Instant.parse("2026-01-01T00:00:00Z"),
                        notAfter = Instant.parse("2027-01-01T00:00:00Z"),
                    ),
                ),
                signingKey = caKey,
            )
        )

        val error = try {
            issuer.issue(
                X509IssuerSignedCertificateIssuanceSpec(
                    profileId = X509KnownProfileIds.GenericEndEntity,
                    certificateData = X509CertificateBuildData(
                        subject = x509SubjectOf(
                            X509SubjectAttributes.country("AT"),
                            X509SubjectAttributes.commonName("service.example.org"),
                        ),
                        validityPeriod = X509ValidityPeriod(
                            notBefore = Instant.parse("2026-01-02T00:00:00Z"),
                            notAfter = Instant.parse("2027-03-31T00:00:00Z"),
                        ),
                    ),
                    publicKey = eeKey.getPublicKey(),
                    issuer = X509CertificateSignerSpec(
                        profileId = X509KnownProfileIds.GenericCa,
                        certificateData = caIssued.certificateData.toBuildData(),
                        signingKey = caKey,
                    ),
                )
            )
            fail("Expected issuance compatibility validation to fail")
        } catch (exception: IllegalArgumentException) {
            exception
        }

        assertTrue(error.message!!.contains("must not be after issuer notAfter"))
    }

    @Test
    fun `rejects iso document signer build data with issuer alternative names`() = runTest {
        val iacaKey = JWKKey.generate(KeyType.secp256r1)
        val dsKey = JWKKey.generate(KeyType.secp256r1)

        val error = try {
            issuer.issue(
                X509IssuerSignedCertificateIssuanceSpec(
                    profileId = X509KnownProfileIds.IsoDocumentSigner,
                    certificateData = X509CertificateBuildData(
                        subject = x509SubjectOf(
                            X509SubjectAttributes.country("US"),
                            X509SubjectAttributes.commonName("Example DS"),
                        ),
                        validityPeriod = X509ValidityPeriod(
                            notBefore = Instant.parse("2026-01-02T00:00:00Z"),
                            notAfter = Instant.parse("2026-12-31T00:00:00Z"),
                        ),
                        issuerAlternativeNames = setOf(
                            X509SubjectAlternativeName.Uri("https://should-not-be-here.example.org"),
                        ),
                        crlDistributionPointUri = "https://iaca.example.org/crl",
                    ),
                    publicKey = dsKey.getPublicKey(),
                    issuer = X509CertificateSignerSpec(
                        profileId = X509KnownProfileIds.IsoIaca,
                        certificateData = X509CertificateBuildData(
                            subject = x509SubjectOf(
                                X509SubjectAttributes.country("US"),
                                X509SubjectAttributes.commonName("Example IACA"),
                            ),
                            validityPeriod = X509ValidityPeriod(
                                notBefore = Instant.parse("2025-01-01T00:00:00Z"),
                                notAfter = Instant.parse("2030-01-01T00:00:00Z"),
                            ),
                            issuerAlternativeNames = setOf(
                                X509SubjectAlternativeName.Uri("https://iaca.example.org"),
                            ),
                        ),
                        signingKey = iacaKey,
                    ),
                )
            )
            fail("Expected issuance compatibility validation to fail")
        } catch (exception: IllegalArgumentException) {
            exception
        }

        assertTrue(error.message!!.contains("does not accept issuer alternative names"))
    }

    private fun X509IssuedCertificateData.toBuildData() = X509CertificateBuildData(
        subject = subject,
        validityPeriod = validityPeriod,
        subjectAlternativeNames = subjectAlternativeNames,
        issuerAlternativeNames = issuerAlternativeNames,
        crlDistributionPointUri = crlDistributionPointUri,
    )
}
