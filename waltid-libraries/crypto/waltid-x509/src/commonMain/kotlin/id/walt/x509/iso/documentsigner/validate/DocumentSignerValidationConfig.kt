package id.walt.x509.iso.documentsigner.validate

/**
 * Configuration for [DocumentSignerValidator].
 *
 * By default, all validations required by the ISO profile are enabled.
 * Callers can selectively disable checks (e.g., for parsing/inspection use-cases).
 */
data class DocumentSignerValidationConfig(
    val keyType: Boolean = true,
    val principalName: Boolean = true,
    val validityPeriod: Boolean = true,
    val crlDistributionPointUri: Boolean = true,
    val profileDataAgainstIACAProfileData: Boolean = true,
    val requiredCriticalExtensionOIDs: Boolean = true,
    val requiredNonCriticalExtensionOIDs: Boolean = true,
    val signature: Boolean = true,
)

