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
    const val OPEN_BADGE_PROFILE_ID = "openBadgeCredential"
    const val OPEN_BADGE_CONFIGURATION_ID = "OpenBadgeCredential_jwt_vc_json"

    const val ISO_PHOTO_ID_PROFILE_ID = "isoPhotoId"
    const val ISO_PHOTO_ID_CONFIGURATION_ID = "org.iso.23220.photoid.1"

    const val ISO_MDL_PROFILE_ID = "isoMdl"
    const val ISO_MDL_CONFIGURATION_ID = "org.iso.18013.5.1.mDL"

    const val ISO_MDL_AAMVA_PROFILE_ID = "isoMdlAamva"
    const val ISO_MDL_AAMVA_CONFIGURATION_ID = "org.iso.18013.5.1.mDL.aamva"

    const val IDENTITY_SD_JWT_PROFILE_ID = "identityCredentialSdJwt"
    const val IDENTITY_SD_JWT_CONFIGURATION_ID = "identity_credential"

    const val JWT_VC_JSON_FORMAT = "jwt_vc_json"
    const val MSO_MDOC_FORMAT = "mso_mdoc"
    const val SD_JWT_VC_FORMAT = "dc+sd-jwt"

    val openBadgeCredential = Issuer2CredentialScenario(
        profileId = OPEN_BADGE_PROFILE_ID,
        credentialConfigurationId = OPEN_BADGE_CONFIGURATION_ID,
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

    val isoMdlAamva = Issuer2CredentialScenario(
        profileId = ISO_MDL_AAMVA_PROFILE_ID,
        credentialConfigurationId = ISO_MDL_AAMVA_CONFIGURATION_ID,
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

    private fun mdoc(
        profileId: String,
        credentialConfigurationId: String,
    ) = Issuer2CredentialScenario(
        profileId = profileId,
        credentialConfigurationId = credentialConfigurationId,
        format = MSO_MDOC_FORMAT,
        family = Issuer2CredentialFamily.MDOC,
    )

    // Keep this list explicit. If a configured credential disappears, the tests should fail
    // instead of shrinking coverage to whatever the config still happens to expose.
    val configured: List<Issuer2CredentialScenario> = listOf(
        jwtVcJson("alpsTourReservation", "AlpsTourReservation_jwt_vc_json"),
        jwtVcJson("bankId", "BankId_jwt_vc_json"),
        jwtVcJson("hotelReservation", "HotelReservation_jwt_vc_json"),
        jwtVcJson("kycCredential", "KycCredential_jwt_vc_json"),
        openBadgeCredential,
        jwtVcJson("pnd91Credential", "PND91Credential_jwt_vc_json"),
        jwtVcJson("proofOfAddress", "ProofOfAddress_jwt_vc_json"),
        isoMdl,
        isoMdlAamva,
        isoPhotoId,
        mdoc("eudiPidMdoc", "eu.europa.ec.eudi.pid.1"),
        mdoc("euAgeVerificationMdoc", "eu.europa.ec.av.1"),
        mdoc("idAustriaMdoc", "at.gv.id-austria.2023.iso"),
        mdoc("googleIdCardMdoc", "com.google.wallet.idcard.1"),
        sdJwtVc("taxIdCredentialSdJwt", "asit.tax-id-credential"),
        sdJwtVc("certificateOfResidenceSdJwt", "urn:eu.europa.ec.eudi:cor:1"),
        sdJwtVc("powerOfRepresentationSdJwt", "urn:eu.europa.ec.eudi:por:1"),
        sdJwtVc("ehicSdJwt", "urn:eudi:ehic:1"),
        sdJwtVc("eudiPidSdJwt", "urn:eudi:pid:1"),
        identitySdJwt,
    )
}
