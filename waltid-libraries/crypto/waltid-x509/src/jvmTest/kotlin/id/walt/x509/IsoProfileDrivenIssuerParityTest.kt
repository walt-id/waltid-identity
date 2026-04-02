package id.walt.x509

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.iso.DocumentSignerEkuOID
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.documentsigner.builder.DocumentSignerCertificateBuilder
import id.walt.x509.iso.documentsigner.builder.IACASignerSpecification
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.documentsigner.parser.DocumentSignerCertificateParser
import id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator
import id.walt.x509.iso.iaca.builder.IACACertificateBuilder
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.iaca.parser.IACACertificateParser
import id.walt.x509.iso.iaca.validate.IACAValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class IsoProfileDrivenIssuerParityTest {

    private val issuer = DefaultX509ProfileDrivenIssuer()
    private val iacaBuilder = IACACertificateBuilder()
    private val iacaParser = IACACertificateParser()
    private val iacaValidator = IACAValidator()
    private val documentSignerBuilder = DocumentSignerCertificateBuilder()
    private val documentSignerParser = DocumentSignerCertificateParser()
    private val documentSignerValidator = DocumentSignerValidator()

    @Test
    fun `generic iso iaca issuance matches specific iaca semantics`() = runTest {
        val signingKey = JWKKey.generate(KeyType.secp256r1)
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)

        val genericBuildData = X509CertificateBuildData(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("US"),
                X509SubjectAttributes.commonName("Parity IACA"),
                X509SubjectAttributes.stateOrProvince("CA"),
                X509SubjectAttributes.organization("walt.id"),
            ),
            validityPeriod = X509ValidityPeriod(
                notBefore = now.minus(1.days),
                notAfter = now.plus(365.days),
            ),
            issuerAlternativeNames = setOf(
                X509SubjectAlternativeName.Uri("https://issuer.example/iaca"),
                X509SubjectAlternativeName.EmailAddress("iaca@example.org"),
            ),
            crlDistributionPointUri = "https://issuer.example/iaca/crl",
        )

        val specificProfileData = IACACertificateProfileData(
            principalName = IACAPrincipalName(
                country = "US",
                commonName = "Parity IACA",
                stateOrProvinceName = "CA",
                organizationName = "walt.id",
            ),
            validityPeriod = genericBuildData.validityPeriod,
            issuerAlternativeName = IssuerAlternativeName(
                uri = "https://issuer.example/iaca",
                email = "iaca@example.org",
            ),
            crlDistributionPointUri = "https://issuer.example/iaca/crl",
        )

        val genericIssued = issuer.issue(
            X509SelfSignedCertificateIssuanceSpec(
                profileId = X509KnownProfileIds.IsoIaca,
                certificateData = genericBuildData,
                signingKey = signingKey,
            )
        )
        val specificIssued = iacaBuilder.build(
            profileData = specificProfileData,
            signingKey = signingKey,
        )

        val genericDecoded = iacaParser.parse(genericIssued.certificateDer)
        val specificDecoded = iacaParser.parse(specificIssued.certificateDer)

        assertIacaSemanticsEqual(
            expected = specificDecoded,
            actual = genericDecoded,
        )
        assertIssuedIacaDataMatches(
            issued = genericIssued.certificateData,
            decoded = genericDecoded,
        )
        assertEqualSubjectAlternativeNameCounts(
            first = genericIssued.certificateDer,
            second = specificIssued.certificateDer,
        )
        assertNoSubjectAlternativeNames(genericIssued.certificateDer)

        iacaValidator.validate(genericDecoded)
    }

    @Test
    fun `generic iso document signer issuance matches specific document signer semantics`() = runTest {
        val iacaSigningKey = JWKKey.generate(KeyType.secp256r1)
        val documentSignerKey = JWKKey.generate(KeyType.secp256r1)
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)

        val iacaBuildData = X509CertificateBuildData(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("US"),
                X509SubjectAttributes.commonName("Parity IACA"),
                X509SubjectAttributes.stateOrProvince("CA"),
                X509SubjectAttributes.organization("walt.id"),
            ),
            validityPeriod = X509ValidityPeriod(
                notBefore = now.minus(1.days),
                notAfter = now.plus(730.days),
            ),
            issuerAlternativeNames = setOf(
                X509SubjectAlternativeName.Uri("https://issuer.example/iaca"),
                X509SubjectAlternativeName.EmailAddress("iaca@example.org"),
            ),
            crlDistributionPointUri = "https://issuer.example/iaca/crl",
        )

        val genericIacaIssued = issuer.issue(
            X509SelfSignedCertificateIssuanceSpec(
                profileId = X509KnownProfileIds.IsoIaca,
                certificateData = iacaBuildData,
                signingKey = iacaSigningKey,
            )
        )

        val specificIacaProfileData = IACACertificateProfileData(
            principalName = IACAPrincipalName(
                country = "US",
                commonName = "Parity IACA",
                stateOrProvinceName = "CA",
                organizationName = "walt.id",
            ),
            validityPeriod = iacaBuildData.validityPeriod,
            issuerAlternativeName = IssuerAlternativeName(
                uri = "https://issuer.example/iaca",
                email = "iaca@example.org",
            ),
            crlDistributionPointUri = "https://issuer.example/iaca/crl",
        )

        val genericDocumentSignerBuildData = X509CertificateBuildData(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("US"),
                X509SubjectAttributes.commonName("Parity Document Signer"),
                X509SubjectAttributes.stateOrProvince("CA"),
                X509SubjectAttributes.organization("walt.id"),
                X509SubjectAttributes.locality("San Francisco"),
            ),
            validityPeriod = X509ValidityPeriod(
                notBefore = now,
                notAfter = now.plus(180.days),
            ),
            crlDistributionPointUri = "https://issuer.example/iaca/crl",
        )

        val specificDocumentSignerProfileData = DocumentSignerCertificateProfileData(
            principalName = DocumentSignerPrincipalName(
                country = "US",
                commonName = "Parity Document Signer",
                stateOrProvinceName = "CA",
                organizationName = "walt.id",
                localityName = "San Francisco",
            ),
            validityPeriod = genericDocumentSignerBuildData.validityPeriod,
            crlDistributionPointUri = "https://issuer.example/iaca/crl",
        )

        val genericIssued = issuer.issue(
            X509IssuerSignedCertificateIssuanceSpec(
                profileId = X509KnownProfileIds.IsoDocumentSigner,
                certificateData = genericDocumentSignerBuildData,
                publicKey = documentSignerKey.getPublicKey(),
                issuer = X509CertificateSignerSpec(
                    profileId = X509KnownProfileIds.IsoIaca,
                    certificateData = genericIacaIssued.certificateData.toBuildData(),
                    signingKey = iacaSigningKey,
                ),
            )
        )
        val specificIssued = documentSignerBuilder.build(
            profileData = specificDocumentSignerProfileData,
            publicKey = documentSignerKey.getPublicKey(),
            iacaSignerSpec = IACASignerSpecification(
                profileData = specificIacaProfileData,
                signingKey = iacaSigningKey,
            ),
        )

        val genericIacaDecoded = iacaParser.parse(genericIacaIssued.certificateDer)
        val genericDecoded = documentSignerParser.parse(genericIssued.certificateDer)
        val specificDecoded = documentSignerParser.parse(specificIssued.certificateDer)

        assertDocumentSignerSemanticsEqual(
            expected = specificDecoded,
            actual = genericDecoded,
        )
        assertIssuedDocumentSignerDataMatches(
            issued = genericIssued.certificateData,
            decoded = genericDecoded,
        )
        assertEqualSubjectAlternativeNameCounts(
            first = genericIssued.certificateDer,
            second = specificIssued.certificateDer,
        )
        assertNoSubjectAlternativeNames(genericIssued.certificateDer)

        documentSignerValidator.validate(
            dsDecodedCert = genericDecoded,
            iacaDecodedCert = genericIacaDecoded,
        )
        validateCertificateChain(
            leaf = genericIssued.certificateDer,
            chain = listOf(genericIacaIssued.certificateDer),
            trustAnchors = listOf(genericIacaIssued.certificateDer),
        )
    }

    private fun assertIacaSemanticsEqual(
        expected: IACADecodedCertificate,
        actual: IACADecodedCertificate,
    ) {
        assertEquals(expected.principalName, actual.principalName)
        assertEquals(expected.validityPeriod, actual.validityPeriod)
        assertEquals(expected.keyUsage, actual.keyUsage)
        assertEquals(expected.basicConstraints, actual.basicConstraints)
        assertEquals(expected.issuerAlternativeName, actual.issuerAlternativeName)
        assertEquals(expected.crlDistributionPointUri, actual.crlDistributionPointUri)
    }

    private fun assertDocumentSignerSemanticsEqual(
        expected: DocumentSignerDecodedCertificate,
        actual: DocumentSignerDecodedCertificate,
    ) {
        assertEquals(expected.principalName, actual.principalName)
        assertEquals(expected.issuerPrincipalName, actual.issuerPrincipalName)
        assertEquals(expected.validityPeriod, actual.validityPeriod)
        assertEquals(expected.keyUsage, actual.keyUsage)
        assertEquals(expected.extendedKeyUsage, actual.extendedKeyUsage)
        assertEquals(expected.basicConstraints, actual.basicConstraints)
        assertEquals(expected.issuerAlternativeName, actual.issuerAlternativeName)
        assertEquals(expected.crlDistributionPointUri, actual.crlDistributionPointUri)
    }

    private fun assertIssuedIacaDataMatches(
        issued: X509IssuedCertificateData,
        decoded: IACADecodedCertificate,
    ) {
        assertEquals(decoded.principalName.toSubject(), issued.subject)
        assertEquals(decoded.principalName.toSubject(), issued.issuer)
        assertEquals(decoded.validityPeriod, issued.validityPeriod)
        assertEquals(decoded.keyUsage, issued.keyUsages)
        assertEquals(decoded.basicConstraints, issued.basicConstraints)
        assertEquals(decoded.issuerAlternativeName.toAlternativeNames(), issued.issuerAlternativeNames)
        assertEquals(decoded.crlDistributionPointUri, issued.crlDistributionPointUri)
    }

    private fun assertIssuedDocumentSignerDataMatches(
        issued: X509IssuedCertificateData,
        decoded: DocumentSignerDecodedCertificate,
    ) {
        assertEquals(decoded.principalName.toSubject(), issued.subject)
        assertEquals(decoded.issuerPrincipalName.toSubject(), issued.issuer)
        assertEquals(decoded.validityPeriod, issued.validityPeriod)
        assertEquals(decoded.keyUsage, issued.keyUsages)
        assertEquals(decoded.extendedKeyUsage, issued.extendedKeyUsages.map { it.oid }.toSet())
        assertEquals(decoded.basicConstraints, issued.basicConstraints)
        assertEquals(decoded.issuerAlternativeName.toAlternativeNames(), issued.issuerAlternativeNames)
        assertEquals(decoded.crlDistributionPointUri, issued.crlDistributionPointUri)
        assertTrue(issued.extendedKeyUsages.any { it.oid == DocumentSignerEkuOID })
    }

    private fun assertEqualSubjectAlternativeNameCounts(
        first: CertificateDer,
        second: CertificateDer,
    ) {
        assertEquals(
            first.toJcaX509Certificate().subjectAlternativeNames?.size ?: 0,
            second.toJcaX509Certificate().subjectAlternativeNames?.size ?: 0,
        )
    }

    private fun assertNoSubjectAlternativeNames(certificate: CertificateDer) {
        val subjectAlternativeNames = certificate.toJcaX509Certificate().subjectAlternativeNames
        assertTrue(subjectAlternativeNames == null || subjectAlternativeNames.isEmpty())
    }

    private fun X509IssuedCertificateData.toBuildData() = X509CertificateBuildData(
        subject = subject,
        validityPeriod = validityPeriod,
        subjectAlternativeNames = subjectAlternativeNames,
        issuerAlternativeNames = issuerAlternativeNames,
        crlDistributionPointUri = crlDistributionPointUri,
    )

    private fun IACAPrincipalName.toSubject() = x509SubjectOf(
        X509SubjectAttributes.country(country),
        X509SubjectAttributes.commonName(commonName),
        *listOfNotNull(
            stateOrProvinceName?.let(X509SubjectAttributes::stateOrProvince),
            organizationName?.let(X509SubjectAttributes::organization),
        ).toTypedArray(),
    )

    private fun DocumentSignerPrincipalName.toSubject() = x509SubjectOf(
        X509SubjectAttributes.country(country),
        X509SubjectAttributes.commonName(commonName),
        *listOfNotNull(
            stateOrProvinceName?.let(X509SubjectAttributes::stateOrProvince),
            organizationName?.let(X509SubjectAttributes::organization),
            localityName?.let(X509SubjectAttributes::locality),
        ).toTypedArray(),
    )

    private fun IssuerAlternativeName.toAlternativeNames(): Set<X509SubjectAlternativeName> = buildSet {
        uri?.let { add(X509SubjectAlternativeName.Uri(it)) }
        email?.let { add(X509SubjectAlternativeName.EmailAddress(it)) }
    }
}
