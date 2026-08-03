package id.walt.rpcert.wallet

import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.GenericMeta
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.dcql.models.meta.NoMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.rpcert.models.Claim
import id.walt.rpcert.models.RegistrationCertificateCredential
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationCertificateDcqlMatcherTest {

    @Test
    fun noMetaCoversAnyQuery() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(RegistrationCertificateCredential(format = CredentialFormat.DC_SD_JWT, meta = NoMeta)),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(id = "q1", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf("SomeVct"))),
            ),
        )

        assertTrue(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun formatMismatchIsNotCovered() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(RegistrationCertificateCredential(format = CredentialFormat.DC_SD_JWT, meta = NoMeta)),
        )
        val query = DcqlQuery(
            credentials = listOf(CredentialQuery(id = "q1", format = CredentialFormat.MSO_MDOC, meta = NoMeta)),
        )

        assertFalse(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun msoMdocMetaCoversSameDoctype() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(format = CredentialFormat.MSO_MDOC, meta = MsoMdocMeta("org.iso.18013.5.1.mDL")),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(id = "q1", format = CredentialFormat.MSO_MDOC, meta = MsoMdocMeta("org.iso.18013.5.1.mDL")),
            ),
        )

        assertTrue(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun msoMdocMetaRejectsDifferentDoctype() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(format = CredentialFormat.MSO_MDOC, meta = MsoMdocMeta("org.iso.18013.5.1.mDL")),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(id = "q1", format = CredentialFormat.MSO_MDOC, meta = MsoMdocMeta("com.example.other")),
            ),
        )

        assertFalse(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun sdJwtVcMetaCoversSubsetOfRegisteredVctValues() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.DC_SD_JWT,
                    meta = SdJwtVcMeta(listOf("VCT_A", "VCT_B")),
                ),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(id = "q1", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf("VCT_A"))),
            ),
        )

        assertTrue(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun sdJwtVcMetaRejectsUnregisteredVct() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.DC_SD_JWT,
                    meta = SdJwtVcMeta(listOf("VCT_A")),
                ),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(id = "q1", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf("VCT_C"))),
            ),
        )

        assertFalse(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun jwtVcJsonMetaCoversSubsetOfRegisteredTypeValues() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.JWT_VC_JSON,
                    meta = JwtVcJsonMeta(listOf(listOf("VerifiableCredential", "TypeA"), listOf("VerifiableCredential", "TypeB"))),
                ),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "q1",
                    format = CredentialFormat.JWT_VC_JSON,
                    meta = JwtVcJsonMeta(listOf(listOf("VerifiableCredential", "TypeA"))),
                ),
            ),
        )

        assertTrue(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun jwtVcJsonMetaRejectsUnregisteredTypeValues() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.JWT_VC_JSON,
                    meta = JwtVcJsonMeta(listOf(listOf("VerifiableCredential", "TypeA"))),
                ),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "q1",
                    format = CredentialFormat.JWT_VC_JSON,
                    meta = JwtVcJsonMeta(listOf(listOf("VerifiableCredential", "TypeC"))),
                ),
            ),
        )

        assertFalse(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun genericMetaRequiresExactEquality() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.DC_SD_JWT,
                    meta = GenericMeta(mapOf("k" to JsonPrimitive("v"))),
                ),
            ),
        )
        val matchingQuery = DcqlQuery(
            credentials = listOf(
                CredentialQuery(id = "q1", format = CredentialFormat.DC_SD_JWT, meta = GenericMeta(mapOf("k" to JsonPrimitive("v")))),
            ),
        )
        val differentQuery = DcqlQuery(
            credentials = listOf(
                CredentialQuery(id = "q1", format = CredentialFormat.DC_SD_JWT, meta = GenericMeta(mapOf("k" to JsonPrimitive("other")))),
            ),
        )

        assertTrue(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, matchingQuery))
        assertFalse(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, differentQuery))
    }

    @Test
    fun nullRegisteredClaimsCoversAnyQueriedClaims() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(RegistrationCertificateCredential(format = CredentialFormat.DC_SD_JWT, claim = null)),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "q1",
                    format = CredentialFormat.DC_SD_JWT,
                    meta = NoMeta,
                    claims = listOf(ClaimsQuery(pathStrings = listOf("given_name"))),
                ),
            ),
        )

        assertTrue(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun queryForWholeCredentialIsRejectedWhenOnlySpecificClaimsAreRegistered() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.DC_SD_JWT,
                    claim = listOf(Claim(path = listOf(JsonPrimitive("given_name")))),
                ),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(CredentialQuery(id = "q1", format = CredentialFormat.DC_SD_JWT, meta = NoMeta, claims = null)),
        )

        assertFalse(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun registeredClaimsCoverMatchingPathWithoutValueRestriction() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.DC_SD_JWT,
                    claim = listOf(Claim(path = listOf(JsonPrimitive("given_name")))),
                ),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "q1",
                    format = CredentialFormat.DC_SD_JWT,
                    meta = NoMeta,
                    claims = listOf(ClaimsQuery(pathStrings = listOf("given_name"))),
                ),
            ),
        )

        assertTrue(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun registeredClaimsRejectUnregisteredPath() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.DC_SD_JWT,
                    claim = listOf(Claim(path = listOf(JsonPrimitive("given_name")))),
                ),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "q1",
                    format = CredentialFormat.DC_SD_JWT,
                    meta = NoMeta,
                    claims = listOf(ClaimsQuery(pathStrings = listOf("family_name"))),
                ),
            ),
        )

        assertFalse(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun registeredValueRestrictionCoversSubsetOfAllowedValues() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.DC_SD_JWT,
                    claim = listOf(
                        Claim(path = listOf(JsonPrimitive("nationality")), values = listOf(JsonPrimitive("DE"), JsonPrimitive("AT"))),
                    ),
                ),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "q1",
                    format = CredentialFormat.DC_SD_JWT,
                    meta = NoMeta,
                    claims = listOf(ClaimsQuery(pathStrings = listOf("nationality"), values = listOf(JsonPrimitive("DE")))),
                ),
            ),
        )

        assertTrue(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun registeredValueRestrictionRejectsUnlistedValue() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.DC_SD_JWT,
                    claim = listOf(
                        Claim(path = listOf(JsonPrimitive("nationality")), values = listOf(JsonPrimitive("DE"), JsonPrimitive("AT"))),
                    ),
                ),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "q1",
                    format = CredentialFormat.DC_SD_JWT,
                    meta = NoMeta,
                    claims = listOf(ClaimsQuery(pathStrings = listOf("nationality"), values = listOf(JsonPrimitive("FR")))),
                ),
            ),
        )

        assertFalse(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun registeredValueRestrictionRejectsQueryWithoutValues() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(
                    format = CredentialFormat.DC_SD_JWT,
                    claim = listOf(
                        Claim(path = listOf(JsonPrimitive("nationality")), values = listOf(JsonPrimitive("DE"))),
                    ),
                ),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "q1",
                    format = CredentialFormat.DC_SD_JWT,
                    meta = NoMeta,
                    claims = listOf(ClaimsQuery(pathStrings = listOf("nationality"), values = null)),
                ),
            ),
        )

        assertFalse(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }

    @Test
    fun everyCredentialQueryMustBeCovered() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(RegistrationCertificateCredential(format = CredentialFormat.DC_SD_JWT, meta = NoMeta)),
        )
        val partiallyCoveredQuery = DcqlQuery(
            credentials = listOf(
                CredentialQuery(id = "q1", format = CredentialFormat.DC_SD_JWT, meta = NoMeta),
                CredentialQuery(id = "q2", format = CredentialFormat.MSO_MDOC, meta = NoMeta),
            ),
        )

        assertFalse(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, partiallyCoveredQuery))
    }

    @Test
    fun onlyOneRegisteredCredentialNeedsToCoverEachQuery() {
        val certificate = RpCertTestFixtures.sampleCertificate(
            credentials = listOf(
                RegistrationCertificateCredential(format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf("VCT_A"))),
                RegistrationCertificateCredential(format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf("VCT_B"))),
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(id = "q1", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf("VCT_B"))),
            ),
        )

        assertTrue(RegistrationCertificateDcqlMatcher.matchDcqlQuery(certificate, query))
    }
}
