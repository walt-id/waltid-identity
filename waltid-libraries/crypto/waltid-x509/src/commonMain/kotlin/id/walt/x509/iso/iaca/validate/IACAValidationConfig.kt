package id.walt.x509.iso.iaca.validate

/**
 * Configuration for [IACAValidator].
 *
 * By default, all validations required by the ISO profile are enabled.
 * Callers can selectively disable checks (e.g., for parsing/inspection use-cases).
 */
data class IACAValidationConfig(
    val signingKeyHasPrivateKey: Boolean = true,
    val keyType: Boolean = true,
    val principalName: Boolean = true,
    val issuerAlternativeName: Boolean = true,
    val validityPeriod: Boolean = true,
    val crlDistributionPointUri: Boolean = true,
    val requiredCriticalExtensionOIDs: Boolean = true,
    val requiredNonCriticalExtensionOIDs: Boolean = true,
    val signature: Boolean = true,
)

