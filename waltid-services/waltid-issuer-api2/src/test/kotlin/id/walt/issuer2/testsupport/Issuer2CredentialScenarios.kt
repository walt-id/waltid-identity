package id.walt.issuer2.testsupport

enum class Issuer2CredentialFamily {
    JWT_VC_JSON,
    SD_JWT_VC,
    MDOC,
}

data class Issuer2CredentialScenario(
    val profileId: String,
    val credentialConfigurationId: String,
    val format: String,
    val family: Issuer2CredentialFamily,
    val authorizationScope: String = credentialConfigurationId,
)

object Issuer2CredentialScenarios {
    const val UNIVERSITY_DEGREE_PROFILE_ID = "universityDegree"
    const val UNIVERSITY_DEGREE_CONFIGURATION_ID = "UniversityDegree_jwt_vc_json"

    const val ISO_PHOTO_ID_PROFILE_ID = "isoPhotoId"
    const val ISO_PHOTO_ID_CONFIGURATION_ID = "org.iso.23220.photoid.1"

    const val ISO_MDL_PROFILE_ID = "isoMdl"
    const val ISO_MDL_CONFIGURATION_ID = "org.iso.18013.5.1.mDL"

    const val IDENTITY_SD_JWT_PROFILE_ID = "identityCredentialSdJwt"
    const val IDENTITY_SD_JWT_CONFIGURATION_ID = "identity_credential"

    const val JWT_VC_JSON_FORMAT = "jwt_vc_json"
    const val MSO_MDOC_FORMAT = "mso_mdoc"
    const val SD_JWT_VC_FORMAT = "dc+sd-jwt"

    val universityDegree = Issuer2CredentialScenario(
        profileId = UNIVERSITY_DEGREE_PROFILE_ID,
        credentialConfigurationId = UNIVERSITY_DEGREE_CONFIGURATION_ID,
        format = JWT_VC_JSON_FORMAT,
        family = Issuer2CredentialFamily.JWT_VC_JSON,
    )

    val isoPhotoId = Issuer2CredentialScenario(
        profileId = ISO_PHOTO_ID_PROFILE_ID,
        credentialConfigurationId = ISO_PHOTO_ID_CONFIGURATION_ID,
        format = MSO_MDOC_FORMAT,
        family = Issuer2CredentialFamily.MDOC,
    )

    val isoMdl = Issuer2CredentialScenario(
        profileId = ISO_MDL_PROFILE_ID,
        credentialConfigurationId = ISO_MDL_CONFIGURATION_ID,
        format = MSO_MDOC_FORMAT,
        family = Issuer2CredentialFamily.MDOC,
    )

    val identitySdJwt = Issuer2CredentialScenario(
        profileId = IDENTITY_SD_JWT_PROFILE_ID,
        credentialConfigurationId = IDENTITY_SD_JWT_CONFIGURATION_ID,
        format = SD_JWT_VC_FORMAT,
        family = Issuer2CredentialFamily.SD_JWT_VC,
    )

    private fun jwtVcJson(
        profileId: String,
        credentialConfigurationId: String,
    ) = Issuer2CredentialScenario(
        profileId = profileId,
        credentialConfigurationId = credentialConfigurationId,
        format = JWT_VC_JSON_FORMAT,
        family = Issuer2CredentialFamily.JWT_VC_JSON,
    )

    private fun sdJwtVc(
        profileId: String,
        credentialConfigurationId: String,
    ) = Issuer2CredentialScenario(
        profileId = profileId,
        credentialConfigurationId = credentialConfigurationId,
        format = SD_JWT_VC_FORMAT,
        family = Issuer2CredentialFamily.SD_JWT_VC,
    )

    // Keep this list explicit. If a configured credential disappears, the tests should fail
    // instead of shrinking coverage to whatever the config still happens to expose.
    val configured: List<Issuer2CredentialScenario> = listOf(
        jwtVcJson("alpsTourReservation", "AlpsTourReservation_jwt_vc_json"),
        jwtVcJson("bankId", "BankId_jwt_vc_json"),
        jwtVcJson("boardingPass", "BoardingPass_jwt_vc_json"),
        jwtVcJson("dataspaceParticipantCredential", "DataspaceParticipantCredential_jwt_vc_json"),
        jwtVcJson("educationalID", "EducationalID_jwt_vc_json"),
        jwtVcJson("eID", "eID_jwt_vc_json"),
        jwtVcJson("emailVerificationCredential", "EmailVerificationCredential_jwt_vc_json"),
        jwtVcJson("enrollmentCredential", "EnrollmentCredential_jwt_vc_json"),
        jwtVcJson("ePassport", "ePassport_jwt_vc_json"),
        jwtVcJson("gaiaXTermsAndConditions", "GaiaXTermsAndConditions_jwt_vc_json"),
        jwtVcJson("hotelReservation", "HotelReservation_jwt_vc_json"),
        jwtVcJson("identityCredential", "IdentityCredential_jwt_vc_json"),
        jwtVcJson("iso18013DriversLicenseCredential", "Iso18013DriversLicenseCredential_jwt_vc_json"),
        jwtVcJson("kiwiAccessCredential", "KiwiAccessCredential_jwt_vc_json"),
        jwtVcJson("kycChecksCredential", "KycChecksCredential_jwt_vc_json"),
        jwtVcJson("kycCredential", "KycCredential_jwt_vc_json"),
        jwtVcJson("kycDataCredential", "KycDataCredential_jwt_vc_json"),
        jwtVcJson("legalPerson", "LegalPerson_jwt_vc_json"),
        jwtVcJson("legalRegistrationNumberEORI", "LegalRegistrationNumberEORI_jwt_vc_json"),
        jwtVcJson("legalRegistrationNumberLeiCode", "LegalRegistrationNumberLeiCode_jwt_vc_json"),
        jwtVcJson("legalRegistrationNumberVatId", "LegalRegistrationNumberVatId_jwt_vc_json"),
        jwtVcJson("mortgageEligibility", "MortgageEligibility_jwt_vc_json"),
        jwtVcJson("naturalPersonVerifiableID", "NaturalPersonVerifiableID_jwt_vc_json"),
        jwtVcJson("openBadgeCredential", "OpenBadgeCredential_jwt_vc_json"),
        jwtVcJson("passportCh", "PassportCh_jwt_vc_json"),
        jwtVcJson("photoIDCredential", "PhotoIDCredential_jwt_vc_json"),
        jwtVcJson("pnd91Credential", "PND91Credential_jwt_vc_json"),
        jwtVcJson("proofOfAddress", "ProofOfAddress_jwt_vc_json"),
        jwtVcJson("taxCredential", "TaxCredential_jwt_vc_json"),
        jwtVcJson("taxReceipt", "TaxReceipt_jwt_vc_json"),
        universityDegree,
        jwtVcJson("vaccinationCertificate", "VaccinationCertificate_jwt_vc_json"),
        jwtVcJson("verifiableId", "VerifiableId_jwt_vc_json"),
        jwtVcJson("verifiablePortableDocumentA1", "VerifiablePortableDocumentA1_jwt_vc_json"),
        jwtVcJson("visa", "Visa_jwt_vc_json"),
        jwtVcJson("walletHolderCredential", "WalletHolderCredential_jwt_vc_json"),
        isoPhotoId,
        isoMdl,
        sdJwtVc("taxIdCredentialSdJwt", "asit.tax-id-credential"),
        sdJwtVc("certificateOfResidenceSdJwt", "urn:eu.europa.ec.eudi:cor:1"),
        sdJwtVc("powerOfRepresentationSdJwt", "urn:eu.europa.ec.eudi:por:1"),
        sdJwtVc("ehicSdJwt", "urn:eudi:ehic:1"),
        sdJwtVc("eudiPidSdJwt", "urn:eudi:pid:1"),
        identitySdJwt,
    )
}
