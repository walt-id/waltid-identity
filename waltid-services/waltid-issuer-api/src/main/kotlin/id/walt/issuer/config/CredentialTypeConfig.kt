package id.walt.issuer.config

import id.walt.commons.config.WaltConfig

private fun vc(vararg extra: String): List<String> = listOf(*extra)

data class CredentialTypeConfig(
    val supportedCredentialTypes: Map<String, List<String>> = mapOf(
        "BankId" to vc("VerifiableCredential", "BankId"),
        "KycChecksCredential" to vc("VerifiableCredential", "VerifiableAttestation", "KycChecksCredential"),
        "KycCredential" to vc("VerifiableCredential", "VerifiableAttestation", "KycCredential"),
        "KycDataCredential" to vc("VerifiableCredential", "VerifiableAttestation", "KycDataCredential"),
        "PassportCh" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiableId", "PassportCh"),
        "PND91Credential" to vc("VerifiableCredential", "PND91Credential"),
        "MortgageEligibility" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiableId", "MortgageEligibility"),
        "PortableDocumentA1" to vc("VerifiableCredential", "VerifiableAttestation", "PortableDocumentA1"),
        "OpenBadgeCredential" to vc("VerifiableCredential", "OpenBadgeCredential"),
        "VaccinationCertificate" to vc("VerifiableCredential", "VerifiableAttestation", "VaccinationCertificate"),
        "WalletHolderCredential" to vc("VerifiableCredential", "WalletHolderCredential"),
        "UniversityDegree" to vc("VerifiableCredential", "UniversityDegree"),
        "VerifiableId" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiableId"),
        "CTWalletSameAuthorisedInTime" to vc("VerifiableCredential", "VerifiableCredential", "VerifiableAttestation", "CTWalletSameAuthorisedInTime"),
        "CTWalletSameAuthorisedDeferred" to vc("VerifiableCredential", "VerifiableAttestation", "CTWalletSameAuthorisedDeferred"),
        "CTWalletSamePreAuthorisedInTime" to vc("VerifiableCredential", "VerifiableAttestation", "CTWalletSamePreAuthorisedInTime"),
        "CTWalletSamePreAuthorisedDeferred" to vc("VerifiableCredential", "VerifiableAttestation", "CTWalletSamePreAuthorisedDeferred"),
        "AlpsTourReservation" to vc("VerifiableCredential", "VerifiableAttestation", "AlpsTourReservation"),
        "EducationalID" to vc("VerifiableCredential", "VerifiableAttestation", "EducationalID"),
        "HotelReservation" to vc("VerifiableCredential", "VerifiableAttestation", "HotelReservation"),
        "Iso18013DriversLicenseCredential" to vc("VerifiableCredential", "VerifiableAttestation", "Iso18013DriversLicenseCredential"),
        "TaxReceipt" to vc("VerifiableCredential", "VerifiableAttestation", "TaxReceipt"),
        "VerifiablePortableDocumentA1" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiablePortableDocumentA1"),
        "Visa" to vc("VerifiableCredential", "VerifiableAttestation", "Visa"),
        "eID" to vc("VerifiableCredential", "VerifiableAttestation", "eID"),
        "NaturalPersonVerifiableID" to vc("VerifiableCredential", "VerifiableAttestation", "NaturalPersonVerifiableID"),
        "BoardingPass" to vc("VerifiableCredential", "VerifiableAttestation", "BoardingPass")
    ),
) : WaltConfig()
