package id.walt.x509

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class X509CertificateProfileTest {

    @Test
    fun `profile foundation holds generic issuance primitives`() {
        val profile = X509CertificateProfile(
            profileId = X509ProfileId("tls.server.default"),
            subject = X509Subject(
                attributes = listOf(
                    X509SubjectAttribute(oid = "2.5.4.3", value = "example.org", shortName = "CN"),
                    X509SubjectAttribute(oid = "2.5.4.10", value = "walt.id", shortName = "O"),
                )
            ),
            subjectAlternativeNames = setOf(
                X509SubjectAlternativeName.DnsName("example.org"),
                X509SubjectAlternativeName.DnsName("api.example.org"),
                X509SubjectAlternativeName.Uri("spiffe://example/service"),
            ),
            keyUsages = setOf(
                X509KeyUsage.DigitalSignature,
                X509KeyUsage.KeyEncipherment,
            ),
            extendedKeyUsages = setOf(
                X509ExtendedKeyUsage.ServerAuth,
                X509ExtendedKeyUsage.ClientAuth,
            ),
            basicConstraints = X509BasicConstraints(
                isCA = false,
                pathLengthConstraint = 0,
            ),
            validityPolicy = X509ValidityPolicy(
                maximumValidity = 397.days,
                backdate = 1.hours,
                renewBeforeExpiry = 30.days,
                alignWithIssuerValidity = true,
            ),
            description = "Generic TLS server profile",
        )

        assertEquals(X509ProfileId("tls.server.default"), profile.profileId)
        assertEquals(2, profile.subject!!.attributes.size)
        assertEquals(3, profile.subjectAlternativeNames.size)
        assertEquals(setOf(X509ExtendedKeyUsage.ServerAuth, X509ExtendedKeyUsage.ClientAuth), profile.extendedKeyUsages)
        assertEquals(397.days, profile.validityPolicy!!.maximumValidity)
    }

    @Test
    fun `profile id rejects blank values`() {
        assertFailsWith<IllegalArgumentException> {
            X509ProfileId("   ")
        }
    }

    @Test
    fun `validity policy rejects negative durations`() {
        assertFailsWith<IllegalArgumentException> {
            X509ValidityPolicy(
                maximumValidity = (-1).days,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            X509ValidityPolicy(
                maximumValidity = 30.days,
                backdate = (-1).hours,
            )
        }
    }

    @Test
    fun `known profiles are resolvable by profile id`() {
        val isoIaca = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.IsoIaca)
        val isoDocumentSigner = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.IsoDocumentSigner)
        val genericCa = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.GenericCa)
        val genericEndEntity = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.GenericEndEntity)

        assertNotNull(isoIaca)
        assertNotNull(isoDocumentSigner)
        assertNotNull(genericCa)
        assertNotNull(genericEndEntity)

        assertEquals(X509KnownProfileIds.IsoIaca, isoIaca.profileId)
        assertEquals(X509KnownProfileIds.IsoDocumentSigner, isoDocumentSigner.profileId)
        assertEquals(X509KnownProfileIds.GenericCa, genericCa.profileId)
        assertEquals(X509KnownProfileIds.GenericEndEntity, genericEndEntity.profileId)
    }

    @Test
    fun `generic profile approach models representative certificate families`() {
        val isoIaca = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.IsoIaca)
        val isoDocumentSigner = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.IsoDocumentSigner)
        val genericCa = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.GenericCa)
        val genericEndEntity = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.GenericEndEntity)
        val qwac = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.Qwac)
        val qsealc = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.Qsealc)
        val psd2Transport = X509KnownCertificateProfiles.registry.resolve(X509KnownProfileIds.Psd2Transport)

        assertNotNull(isoIaca)
        assertNotNull(isoDocumentSigner)
        assertNotNull(genericCa)
        assertNotNull(genericEndEntity)
        assertNotNull(qwac)
        assertNotNull(qsealc)
        assertNotNull(psd2Transport)

        assertEquals(X509KnownProfileIds.IsoIaca, isoIaca.profileId)
        assertEquals(setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign), isoIaca.keyUsages)
        assertEquals(true, isoIaca.basicConstraints?.isCA)

        assertEquals(X509KnownProfileIds.IsoDocumentSigner, isoDocumentSigner.profileId)
        assertEquals(setOf(X509KeyUsage.DigitalSignature), isoDocumentSigner.keyUsages)
        assertEquals(false, isoDocumentSigner.basicConstraints?.isCA)
        assertEquals(1, isoDocumentSigner.extendedKeyUsages.size)

        assertEquals(X509KnownProfileIds.GenericCa, genericCa.profileId)
        assertEquals(true, genericCa.basicConstraints?.isCA)

        assertEquals(X509KnownProfileIds.GenericEndEntity, genericEndEntity.profileId)
        assertEquals(false, genericEndEntity.basicConstraints?.isCA)

        assertEquals(X509KnownProfileIds.Qwac, qwac.profileId)
        assertEquals(setOf(X509ExtendedKeyUsage.ServerAuth, X509ExtendedKeyUsage.ClientAuth), qwac.extendedKeyUsages)
        assertEquals(false, qwac.basicConstraints?.isCA)

        assertEquals(X509KnownProfileIds.Qsealc, qsealc.profileId)
        assertEquals(setOf(X509KeyUsage.DigitalSignature, X509KeyUsage.NonRepudiation), qsealc.keyUsages)
        assertEquals(false, qsealc.basicConstraints?.isCA)

        assertEquals(X509KnownProfileIds.Psd2Transport, psd2Transport.profileId)
        assertEquals(setOf(X509ExtendedKeyUsage.ServerAuth, X509ExtendedKeyUsage.ClientAuth), psd2Transport.extendedKeyUsages)
        assertEquals(false, psd2Transport.basicConstraints?.isCA)
    }

    @Test
    fun `subject helpers expose common attribute values`() {
        val subject = x509SubjectOf(
            X509SubjectAttributes.country("AT"),
            X509SubjectAttributes.commonName("Example CA"),
            X509SubjectAttributes.organization("walt.id"),
        )

        assertEquals("AT", subject.getFirstAttributeValue(X509SubjectAttributeOids.CountryName))
        assertEquals("Example CA", subject.getFirstAttributeValue(X509SubjectAttributeOids.CommonName))
        assertEquals("walt.id", subject.getFirstAttributeValue(X509SubjectAttributeOids.OrganizationName))
    }

    @Test
    fun `known profile definitions validate successfully`() {
        X509KnownCertificateProfiles.all.forEach { profile ->
            val validation = profile.validateDefinition()
            assertTrue(validation.isValid, "${profile.profileId.value}: ${validation.issues.joinToString()}")
        }
    }

    @Test
    fun `qwac build data requires dns or ip san`() {
        val validation = X509CertificateBuildData(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("DE"),
                X509SubjectAttributes.commonName("bank.example"),
            ),
            validityPeriod = X509ValidityPeriod(
                notBefore = Instant.parse("2026-01-01T00:00:00Z"),
                notAfter = Instant.parse("2026-06-01T00:00:00Z"),
            ),
        ).checkCompatibility(X509KnownCertificateProfiles.Qwac)

        assertTrue(!validation.isValid)
        assertTrue(validation.issues.any { it.contains("DNS or IP") })
    }

    @Test
    fun `iso iaca build data requires issuer alternative name and forbids subject sans`() {
        val validation = X509CertificateBuildData(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("US"),
                X509SubjectAttributes.commonName("Example IACA"),
            ),
            validityPeriod = X509ValidityPeriod(
                notBefore = Instant.parse("2026-01-01T00:00:00Z"),
                notAfter = Instant.parse("2027-01-01T00:00:00Z"),
            ),
            subjectAlternativeNames = setOf(
                X509SubjectAlternativeName.DnsName("iaca.example.org"),
            ),
        ).checkCompatibility(X509KnownCertificateProfiles.IsoIaca)

        assertTrue(!validation.isValid)
        assertTrue(validation.issues.any { it.contains("subject alternative names") })
        assertTrue(validation.issues.any { it.contains("issuer alternative name") })
    }
}
