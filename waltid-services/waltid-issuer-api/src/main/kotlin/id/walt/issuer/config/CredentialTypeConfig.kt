package id.walt.issuer.config

import id.walt.commons.config.WaltConfig

private fun vc(vararg extra: String): List<String> = listOf(*extra)

data class CredentialTypeConfig(
    val supportedCredentialTypes: Map<String, List<String>> = mapOf(
        "BankId" to vc("BankId"),
        "KycChecksCredential" to vc("VerifiableAttestation", "KycChecksCredential"),
        "KycCredential" to vc("VerifiableAttestation", "KycCredential"),
        "KycDataCredential" to vc("VerifiableAttestation", "KycDataCredential"),
        "PassportCh" to vc("VerifiableAttestation", "VerifiableId", "PassportCh"),
        "PND91Credential" to vc("PND91Credential"),
        "MortgageEligibility" to vc("VerifiableAttestation", "VerifiableId", "MortgageEligibility"),
        "PortableDocumentA1" to vc("VerifiableAttestation", "PortableDocumentA1"),
        "OpenBadgeCredential" to vc("OpenBadgeCredential"),
        "VaccinationCertificate" to vc("VerifiableAttestation", "VaccinationCertificate"),
        "WalletHolderCredential" to vc("WalletHolderCredential"),
        "UniversityDegree" to vc("UniversityDegree"),
        "VerifiableId" to vc("VerifiableAttestation", "VerifiableId"),
        "CTWalletSameAuthorisedInTime" to vc("VerifiableAttestation", "CTWalletSameAuthorisedInTime"),
        "CTWalletSameAuthorisedDeferred" to vc("VerifiableAttestation", "CTWalletSameAuthorisedDeferred"),
        "CTWalletSamePreAuthorisedInTime" to vc("VerifiableAttestation", "CTWalletSamePreAuthorisedInTime"),
        "CTWalletSamePreAuthorisedDeferred" to vc("VerifiableAttestation", "CTWalletSamePreAuthorisedDeferred"),
        "AlpsTourReservation" to vc("VerifiableAttestation", "AlpsTourReservation"),
        "EducationalID" to vc("VerifiableAttestation", "EducationalID"),
        "HotelReservation" to vc("VerifiableAttestation", "HotelReservation"),
        "Iso18013DriversLicenseCredential" to vc("VerifiableAttestation", "Iso18013DriversLicenseCredential"),
        "TaxReceipt" to vc("VerifiableAttestation", "TaxReceipt"),
        "VerifiablePortableDocumentA1" to vc("VerifiableAttestation", "VerifiablePortableDocumentA1"),
        "Visa" to vc("VerifiableAttestation", "Visa"),
        "eID" to vc("VerifiableAttestation", "eID"),
        "NaturalPersonVerifiableID" to vc("VerifiableAttestation", "NaturalPersonVerifiableID"),
        "BoardingPass" to vc("VerifiableAttestation", "BoardingPass")
    ),
) : WaltConfig()
